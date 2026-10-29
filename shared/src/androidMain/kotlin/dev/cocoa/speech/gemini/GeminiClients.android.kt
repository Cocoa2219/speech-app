package dev.cocoa.speech.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File

// The model client, the Files API uploader (sharing one connection pool), and the answer cache.
class GeminiServices(val generator: JsonGenerator, val files: MediaUploader, val cache: ResponseCache)

// cacheDir is set in debug builds only (see FileResponseCache). Token counts go to the log, never request text.
fun createGemini(apiKey: String, cacheDir: File? = null): GeminiServices {
    val http = HttpClient(OkHttp)
    val client = GeminiClient(apiKey, http, onUsage = { u ->
        println(
            "GeminiUsage: prompt=${u.promptTokenCount} output=${u.candidatesTokenCount} " +
                "thoughts=${u.thoughtsTokenCount} total=${u.totalTokenCount}",
        )
    })
    val cache = if (cacheDir != null) FileResponseCache(cacheDir) else ResponseCache.None
    val generator = if (cacheDir != null) CachingGenerator(client, cache) else client
    return GeminiServices(generator, GeminiFiles(apiKey, http), cache)
}
