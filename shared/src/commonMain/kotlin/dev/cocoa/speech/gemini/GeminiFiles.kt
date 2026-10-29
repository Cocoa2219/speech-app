package dev.cocoa.speech.gemini

import dev.cocoa.speech.model.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.TimeSource

// Uploads media too large to send inline (video), for use as a fileData part. Protocol verified against
// https://ai.google.dev/gemini-api/docs/files and https://ai.google.dev/api/files on 2026-10-29:
// - start: POST {root}/upload/v1beta/files with X-Goog-Upload-* headers; the upload URL is in x-goog-upload-url
// - upload: POST the bytes to that URL with "X-Goog-Upload-Command: upload, finalize"; response is {"file": File}
// - GET {root}/v1beta/{name} until state is ACTIVE (PROCESSING before, FAILED on error)
// - DELETE {root}/v1beta/{name}
// Files are kept 48 h unless deleted. Privacy rule (CLAUDE.md): delete right after processing, so withUploaded
// deletes in a finally block, even when processing fails or is cancelled.
class GeminiFiles(
    private val apiKey: String,
    http: HttpClient,
    private val root: String = "https://generativelanguage.googleapis.com",
    private val pollIntervalMs: Long = 2_000,
    private val maxWaitMs: Long = 180_000,
    private val log: (String) -> Unit = ::println,
) : MediaUploader {
    // Uploads of ~23 MB over a phone connection can take a while; allow more than a generate call.
    private val http = http.config {
        install(HttpTimeout) {
            connectTimeoutMillis = GeminiClient.CONNECT_TIMEOUT_MS
            socketTimeoutMillis = UPLOAD_TIMEOUT_MS
            requestTimeoutMillis = UPLOAD_TIMEOUT_MS
        }
    }

    override suspend fun <T> withUploaded(bytes: ByteArray, mimeType: String, block: suspend (GeminiClient.Part.FileRef) -> T): T {
        val uploaded = upload(bytes, mimeType)
        try {
            val active = awaitActive(uploaded)
            return block(GeminiClient.Part.FileRef(active.mimeType.ifBlank { mimeType }, active.uri))
        } finally {
            withContext(NonCancellable) {
                runCatching { delete(uploaded.name) }
                    .onFailure { log("GeminiFiles: delete failed (${it::class.simpleName}); the file expires in 48 h") }
            }
        }
    }

    suspend fun upload(bytes: ByteArray, mimeType: String): GeminiFile {
        val start = http.post("$root/upload/v1beta/files") {
            header("x-goog-api-key", apiKey)
            header("X-Goog-Upload-Protocol", "resumable")
            header("X-Goog-Upload-Command", "start")
            header("X-Goog-Upload-Header-Content-Length", bytes.size.toString())
            header("X-Goog-Upload-Header-Content-Type", mimeType)
            contentType(ContentType.Application.Json)
            // No display name: it would only be another place for identifying text to end up.
            setBody(buildJsonObject { putJsonObject("file") {} }.toString())
        }
        start.requireSuccess()
        val uploadUrl = start.headers["x-goog-upload-url"]
            ?: throw GeminiException(start.status.value, null, "No upload URL in the start response")

        // The upload URL carries its own authorization; the documented request sends no API key.
        val upload = http.post(uploadUrl) {
            header("X-Goog-Upload-Offset", "0")
            header("X-Goog-Upload-Command", "upload, finalize")
            setBody(ByteArrayContent(bytes, ContentType.parse(mimeType)))
        }
        upload.requireSuccess()
        return AppJson.decodeFromString<UploadResponse>(upload.bodyAsText()).file
    }

    suspend fun awaitActive(file: GeminiFile): GeminiFile {
        val mark = TimeSource.Monotonic.markNow()
        var current = file
        while (true) {
            when (current.state) {
                "ACTIVE" -> return current
                "FAILED" -> throw GeminiException(200, "FAILED", "File processing failed")
            }
            if (mark.elapsedNow().inWholeMilliseconds > maxWaitMs) {
                throw GeminiException(200, current.state, "File still ${current.state} after ${maxWaitMs / 1000} s")
            }
            delay(pollIntervalMs)
            val response = http.get("$root/v1beta/${current.name}") { header("x-goog-api-key", apiKey) }
            response.requireSuccess()
            current = AppJson.decodeFromString<GeminiFile>(response.bodyAsText())
        }
    }

    suspend fun delete(name: String) {
        http.delete("$root/v1beta/$name") { header("x-goog-api-key", apiKey) }.requireSuccess()
    }

    private suspend fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) throw GeminiException.from(status.value, bodyAsText())
    }

    private companion object {
        const val UPLOAD_TIMEOUT_MS = 300_000L
    }
}

// What the pipeline needs to send large media: upload, use the file, always delete it.
interface MediaUploader {
    suspend fun <T> withUploaded(bytes: ByteArray, mimeType: String, block: suspend (GeminiClient.Part.FileRef) -> T): T
}

@Serializable
data class GeminiFile(
    val name: String,
    val uri: String = "",
    val mimeType: String = "",
    val state: String = "STATE_UNSPECIFIED",
)

@Serializable
private data class UploadResponse(val file: GeminiFile)
