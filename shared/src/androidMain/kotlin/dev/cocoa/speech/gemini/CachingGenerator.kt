package dev.cocoa.speech.gemini

import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest

// Debug builds only: keeps each model answer in the app's private files, keyed by a hash of the whole request
// (model, prompt, text parts, audio bytes, schema, thinking level). The same request again is answered from disk and
// spends no API request; a changed prompt or new recording misses and goes live. Only successful answers are stored.
// Clear with: adb shell run-as dev.cocoa.speech rm -r files/gemini-cache
class CachingGenerator(
    private val delegate: JsonGenerator,
    private val dir: File,
    private val log: (String) -> Unit = ::println,
) : JsonGenerator {
    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        parts: List<GeminiClient.Part>,
        schema: JsonObject,
        thinking: GeminiClient.Thinking,
    ): String {
        val key = key(model, systemInstruction, parts, schema, thinking)
        val file = File(dir, "$key.json")
        if (file.exists()) {
            log("GeminiCache: hit ${key.take(8)}")
            return file.readText()
        }
        val answer = delegate.generateJson(model, systemInstruction, parts, schema, thinking)
        dir.mkdirs()
        // Write then rename, so a crash mid-write never leaves a truncated answer that would be replayed.
        val tmp = File(dir, "$key.tmp")
        tmp.writeText(answer)
        tmp.renameTo(file)
        log("GeminiCache: stored ${key.take(8)}")
        return answer
    }

    private fun key(
        model: String,
        systemInstruction: String,
        parts: List<GeminiClient.Part>,
        schema: JsonObject,
        thinking: GeminiClient.Thinking,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(bytes: ByteArray) {
            // Length-prefixed so field boundaries cannot shift between requests.
            digest.update(bytes.size.toString().toByteArray())
            digest.update(0)
            digest.update(bytes)
        }
        field(model.toByteArray())
        field(systemInstruction.toByteArray())
        for (part in parts) when (part) {
            is GeminiClient.Part.Text -> field(("text:" + part.text).toByteArray())
            is GeminiClient.Part.Media -> {
                field(("media:" + part.mimeType).toByteArray())
                field(part.bytes)
            }
        }
        field(schema.toString().toByteArray())
        field(thinking.name.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
