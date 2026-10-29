package dev.cocoa.speech

import dev.cocoa.speech.gemini.CachingGenerator
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CachingGeneratorTest {
    private val schema = buildJsonObject { put("type", "object") }

    private class Fake(var fail: Boolean = false) : JsonGenerator {
        var calls = 0
        override suspend fun generateJson(
            model: String,
            systemInstruction: String,
            parts: List<GeminiClient.Part>,
            schema: JsonObject,
            thinking: GeminiClient.Thinking,
        ): String {
            calls++
            if (fail) error("offline")
            return """{"n":$calls}"""
        }
    }

    private fun audio(vararg bytes: Byte) = listOf(GeminiClient.Part.Media("audio/aac", bytes), GeminiClient.Part.Text("go"))

    @Test
    fun sameRequestIsAnsweredFromDisk() = runTest {
        val fake = Fake()
        val cache = CachingGenerator(fake, Files.createTempDirectory("gemini-cache").toFile(), log = {})

        val first = cache.generateJson("m", "prompt", audio(1, 2), schema)
        val second = cache.generateJson("m", "prompt", audio(1, 2), schema)

        assertEquals(first, second)
        assertEquals(1, fake.calls)
    }

    @Test
    fun anyChangeInTheRequestMisses() = runTest {
        val fake = Fake()
        val cache = CachingGenerator(fake, Files.createTempDirectory("gemini-cache").toFile(), log = {})

        cache.generateJson("m", "prompt", audio(1, 2), schema)
        cache.generateJson("m", "prompt", audio(1, 3), schema) // different recording
        cache.generateJson("m", "prompt v2", audio(1, 2), schema) // changed prompt
        cache.generateJson("m", "prompt", audio(1, 2), schema, GeminiClient.Thinking.MEDIUM) // changed thinking

        assertEquals(4, fake.calls)
    }

    @Test
    fun failuresAreNotStored() = runTest {
        val dir = Files.createTempDirectory("gemini-cache").toFile()
        val fake = Fake(fail = true)
        val cache = CachingGenerator(fake, dir, log = {})

        assertFailsWith<IllegalStateException> { cache.generateJson("m", "prompt", audio(1), schema) }
        assertTrue(dir.listFiles().orEmpty().isEmpty())

        fake.fail = false
        cache.generateJson("m", "prompt", audio(1), schema)
        assertEquals(2, fake.calls)
    }

    @Test
    fun logsCarryOnlyAKeyPrefix() = runTest {
        val logs = mutableListOf<String>()
        val cache = CachingGenerator(Fake(), Files.createTempDirectory("gemini-cache").toFile(), log = logs::add)
        cache.generateJson("m", "비밀 전사문", audio(1), schema)
        cache.generateJson("m", "비밀 전사문", audio(1), schema)
        assertEquals(2, logs.size)
        assertTrue(logs.none { "비밀" in it })
    }
}
