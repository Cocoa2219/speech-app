package dev.cocoa.speech.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File

// cacheDir is set in debug builds only (see CachingGenerator). Token counts go to the log, never request text.
fun createGeminiClient(apiKey: String, cacheDir: File? = null): JsonGenerator {
    val client = GeminiClient(apiKey, HttpClient(OkHttp), onUsage = { u ->
        println(
            "GeminiUsage: prompt=${u.promptTokenCount} output=${u.candidatesTokenCount} " +
                "thoughts=${u.thoughtsTokenCount} total=${u.totalTokenCount}",
        )
    })
    return if (cacheDir != null) CachingGenerator(client, cacheDir) else client
}
