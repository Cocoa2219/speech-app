package dev.cocoa.speech.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiFilesTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val uploadUrl = "https://upload.example/session-1"

    // A fake Files API: start -> upload (PROCESSING) -> GET (states in order) -> DELETE.
    private fun files(seen: MutableList<HttpRequestData>, states: List<String> = listOf("ACTIVE")): GeminiFiles {
        var polls = 0
        val engine = MockEngine { request ->
            seen += request
            val url = request.url.toString()
            when {
                url.endsWith("/upload/v1beta/files") -> respond(
                    "{}", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-goog-upload-url" to listOf(uploadUrl)),
                )
                url == uploadUrl -> respond(file("PROCESSING", wrapped = true), HttpStatusCode.OK, json)
                request.method == HttpMethod.Get -> respond(file(states[minOf(polls++, states.lastIndex)]), HttpStatusCode.OK, json)
                request.method == HttpMethod.Delete -> respond("{}", HttpStatusCode.OK, json)
                else -> error("unexpected $url")
            }
        }
        return GeminiFiles("test-key", HttpClient(engine), root = "https://api.example", pollIntervalMs = 10, log = {})
    }

    private fun file(state: String, wrapped: Boolean = false): String {
        val f = """{"name":"files/abc","uri":"https://api.example/v1beta/files/abc","mimeType":"video/mp4","state":"$state"}"""
        return if (wrapped) """{"file":$f}""" else f
    }

    @Test
    fun uploadsWaitsForActiveUsesAndDeletes() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val ref = files(seen, states = listOf("PROCESSING", "ACTIVE"))
            .withUploaded(byteArrayOf(1, 2, 3), "video/mp4") { it }

        assertEquals(GeminiClient.Part.FileRef("video/mp4", "https://api.example/v1beta/files/abc"), ref)
        val start = seen[0]
        assertEquals("resumable", start.headers["X-Goog-Upload-Protocol"])
        assertEquals("start", start.headers["X-Goog-Upload-Command"])
        assertEquals("3", start.headers["X-Goog-Upload-Header-Content-Length"])
        assertEquals("video/mp4", start.headers["X-Goog-Upload-Header-Content-Type"])
        assertEquals("test-key", start.headers["x-goog-api-key"])

        val upload = seen[1]
        assertEquals(uploadUrl, upload.url.toString())
        assertEquals("upload, finalize", upload.headers["X-Goog-Upload-Command"])
        assertEquals("0", upload.headers["X-Goog-Upload-Offset"])
        assertNull(upload.headers["x-goog-api-key"]) // the upload URL carries its own authorization

        assertEquals(2, seen.count { it.method == HttpMethod.Get }) // PROCESSING, then ACTIVE
        val delete = seen.last()
        assertEquals(HttpMethod.Delete, delete.method)
        assertEquals("https://api.example/v1beta/files/abc", delete.url.toString())
    }

    @Test
    fun deletesEvenWhenProcessingFailsOrTheBlockThrows() = runTest {
        val failed = mutableListOf<HttpRequestData>()
        val e = assertFailsWith<GeminiException> {
            files(failed, states = listOf("FAILED")).withUploaded(byteArrayOf(1), "video/mp4") { it }
        }
        assertEquals("FAILED", e.reason)
        assertEquals(HttpMethod.Delete, failed.last().method)

        val thrown = mutableListOf<HttpRequestData>()
        assertFailsWith<IllegalStateException> {
            files(thrown).withUploaded(byteArrayOf(1), "video/mp4") { error("model call failed") }
        }
        assertEquals(HttpMethod.Delete, thrown.last().method)
    }

    @Test
    fun missingUploadUrlIsAnError() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK, json) }
        val files = GeminiFiles("k", HttpClient(engine), root = "https://api.example", log = {})
        val e = assertFailsWith<GeminiException> { files.upload(byteArrayOf(1), "video/mp4") }
        assertTrue(e.message!!.contains("upload URL"))
    }
}
