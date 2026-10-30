package dev.cocoa.speech.gemini

import dev.cocoa.speech.model.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

// Minimal REST client for models.generateContent with JSON output.
// Request shape verified against https://ai.google.dev/api/generate-content on 2026-10-29:
// - generationConfig.responseFormat.text {mimeType: APPLICATION_JSON, schema: JSON Schema}
//   (responseSchema and _responseJsonSchema are deprecated)
// - generationConfig.thinkingConfig.thinkingLevel: MINIMAL | LOW | MEDIUM | HIGH (gemini-3.8-flash rejects MINIMAL)
// - store=false on every request so a project-level logging setting never keeps student audio or transcripts.
// Media is sent inline (Files API is only needed above 100 MB), so nothing is left on Google's side to delete.
class GeminiClient(
    private val apiKey: String,
    http: HttpClient,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    requestTimeoutMillis: Long = REQUEST_TIMEOUT_MS,
    // Called with token counts after each successful call. Counts only; never request or response text.
    private val onUsage: (UsageMetadata) -> Unit = {},
) : JsonGenerator {
    // Explicit timeouts: OkHttp's default 10 s read timeout is far below observed generation latency
    // (gemini-3.8-flash took ~48 s for a one-line prompt on 2026-10-29). A timeout surfaces as a retryable stage failure.
    private val http = http.config {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = requestTimeoutMillis
            this.requestTimeoutMillis = requestTimeoutMillis
        }
    }

    sealed interface Part {
        data class Text(val text: String) : Part
        class Media(val mimeType: String, val bytes: ByteArray) : Part

        // A file uploaded with GeminiFiles (e.g. video). highResolution sets the part's mediaResolution to
        // MEDIA_RESOLUTION_HIGH (Part.mediaResolution.level, verified in https://ai.google.dev/api/generate-content on
        // 2026-10-30): 280 tokens per video frame instead of the default 70, for reading slide text.
        data class FileRef(val mimeType: String, val uri: String, val highResolution: Boolean = false) : Part
    }

    enum class Thinking { LOW, MEDIUM, HIGH }

    // Returns the model's JSON text. Throws GeminiException on HTTP errors, blocked prompts, or empty output.
    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        parts: List<Part>,
        schema: JsonObject,
        thinking: Thinking,
    ): String {
        val requestJson = requestBody(systemInstruction, parts, schema, thinking).toString()
        // Overload errors (429/500/503) were common in testing on 2026-10-29; retry them a few times before the stage
        // fails and the user has to tap retry.
        for (wait in RETRY_DELAYS_MS) {
            try {
                return post(model, requestJson)
            } catch (e: GeminiException) {
                if (e.httpStatus !in RETRYABLE_STATUS) throw e
            }
            delay(wait)
        }
        return post(model, requestJson)
    }

    private suspend fun post(model: String, requestJson: String): String {
        val response = http.post("$baseUrl/models/$model:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestJson)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw GeminiException.from(response.status.value, body)
        }
        val decoded = AppJson.decodeFromString<GenerateContentResponse>(body)
        decoded.usageMetadata?.let(onUsage)
        return extractText(decoded)
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 120_000L
        val RETRY_DELAYS_MS = listOf(1_000L, 3_000L)
        val RETRYABLE_STATUS = setOf(429, 500, 503)

        fun requestBody(systemInstruction: String, parts: List<Part>, schema: JsonObject, thinking: Thinking): JsonObject =
            buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            for (part in parts) addJsonObject {
                                when (part) {
                                    is Part.Text -> put("text", part.text)
                                    is Part.Media -> putJsonObject("inlineData") {
                                        put("mimeType", part.mimeType)
                                        put("data", Base64.encode(part.bytes))
                                    }
                                    is Part.FileRef -> {
                                        putJsonObject("fileData") {
                                            put("mimeType", part.mimeType)
                                            put("fileUri", part.uri)
                                        }
                                        if (part.highResolution) {
                                            putJsonObject("mediaResolution") { put("level", "MEDIA_RESOLUTION_HIGH") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    putJsonObject("thinkingConfig") { put("thinkingLevel", thinking.name) }
                    putJsonObject("responseFormat") {
                        putJsonObject("text") {
                            put("mimeType", "APPLICATION_JSON")
                            put("schema", schema)
                        }
                    }
                }
                put("store", false)
            }

        // Joins the non-thought text parts of the first candidate.
        fun extractText(response: GenerateContentResponse): String {
            response.promptFeedback?.blockReason?.let { throw GeminiException(200, it, "Prompt blocked: $it") }
            val candidate = response.candidates.firstOrNull()
                ?: throw GeminiException(200, null, "No candidates returned")
            val text = candidate.content?.parts.orEmpty().filter { it.thought != true }.mapNotNull { it.text }.joinToString("")
            if (text.isBlank()) throw GeminiException(200, candidate.finishReason, "Empty output (finishReason=${candidate.finishReason})")
            return text
        }
    }
}

class GeminiException(val httpStatus: Int, val reason: String?, message: String) : Exception(message) {
    companion object {
        // Builds the exception from an error response body ({"error": {code, message, status}}).
        fun from(httpStatus: Int, body: String): GeminiException {
            val error = runCatching { AppJson.decodeFromString<ErrorEnvelope>(body).error }.getOrNull()
            return GeminiException(httpStatus, error?.status, error?.message ?: "HTTP $httpStatus")
        }
    }
}

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
    val usageMetadata: UsageMetadata? = null,
) {
    @Serializable
    data class Candidate(val content: Content? = null, val finishReason: String? = null)

    @Serializable
    data class Content(val parts: List<ResponsePart> = emptyList())

    @Serializable
    data class ResponsePart(val text: String? = null, val thought: Boolean? = null)

    @Serializable
    data class PromptFeedback(val blockReason: String? = null)
}

@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null) {
    @Serializable
    data class ErrorBody(val code: Int? = null, val message: String? = null, val status: String? = null)
}

// Token counts from usageMetadata (field names verified against https://ai.google.dev/api/generate-content on
// 2026-10-29). Thinking tokens are billed as output, so output cost = candidates + thoughts.
@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val thoughtsTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
)
