package dev.cocoa.speech.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File

// The model client and the Files API uploader, sharing one connection pool.
class GeminiServices(val generator: JsonGenerator, val files: MediaUploader)

// cacheDir is set in debug builds only (see CachingGenerator). Token counts go to the log, never request text.
fun createGemini(apiKey: String, cacheDir: File? = null): GeminiServices {
    val http = HttpClient(OkHttp)
    val client = GeminiClient(apiKey, http, onUsage = { u ->
        println(
            "GeminiUsage: prompt=${u.promptTokenCount} output=${u.candidatesTokenCount} " +
                "thoughts=${u.thoughtsTokenCount} total=${u.totalTokenCount}",
        )
    })
    val generator = if (cacheDir != null) CachingGenerator(client, cacheDir) else client
    return GeminiServices(generator, GeminiFiles(apiKey, http))
}
