package dev.cocoa.speech.gemini

import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest

// Debug builds only: answers each model request from FileResponseCache when the whole request (model, prompt, text
// parts, audio bytes, schema, thinking level) has been seen before. A changed prompt or new recording misses and goes
// live. Uploaded video is cached one level up, by content, in SceneDescriber (its upload URI changes every time).
class CachingGenerator(private val delegate: JsonGenerator, private val cache: ResponseCache) : JsonGenerator {
    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        parts: List<GeminiClient.Part>,
        schema: JsonObject,
        thinking: GeminiClient.Thinking,
    ): String {
        val key = buildList {
            add(model.toByteArray())
            add(systemInstruction.toByteArray())
            for (part in parts) when (part) {
                is GeminiClient.Part.Text -> add(("text:" + part.text).toByteArray())
                is GeminiClient.Part.Media -> {
                    add(("media:" + part.mimeType).toByteArray())
                    add(part.bytes)
                }
                is GeminiClient.Part.FileRef -> add(("file:" + part.mimeType + ":" + part.uri).toByteArray())
            }
            add(schema.toString().toByteArray())
            add(thinking.name.toByteArray())
        }
        return cache.getOrPut(key) { delegate.generateJson(model, systemInstruction, parts, schema, thinking) }
    }
}

// Answers on disk in the app's private files, named by a SHA-256 of the key. Survives app updates (adb install -r).
// Clear with: adb shell run-as dev.cocoa.speech rm -r files/gemini-cache
class FileResponseCache(private val dir: File, private val log: (String) -> Unit = ::println) : ResponseCache {
    override suspend fun getOrPut(key: List<ByteArray>, compute: suspend () -> String): String {
        val name = hash(key)
        val file = File(dir, "$name.json")
        if (file.exists()) {
            log("GeminiCache: hit ${name.take(8)}")
            return file.readText()
        }
        val answer = compute()
        dir.mkdirs()
        // Write then rename, so a crash mid-write never leaves a truncated answer that would be replayed.
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(answer)
        tmp.renameTo(file)
        log("GeminiCache: stored ${name.take(8)}")
        return answer
    }

    private fun hash(key: List<ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (bytes in key) {
            // Length-prefixed so field boundaries cannot shift between requests.
            digest.update(bytes.size.toString().toByteArray())
            digest.update(0)
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
