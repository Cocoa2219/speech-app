package dev.cocoa.speech.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GeminiClientTest {
    private val schema = buildJsonObject { put("type", "object") }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: String, seen: MutableList<HttpRequestData>) =
        GeminiClient(
            apiKey = "test-key",
            http = HttpClient(MockEngine { request ->
                seen += request
                respond(body, status, jsonHeaders)
            }),
        )

    private fun okBody(vararg parts: String) =
        """{"candidates":[{"content":{"parts":[${parts.joinToString(",")}]},"finishReason":"STOP"}]}"""

    @Test
    fun sendsKeyHeaderModelPathAndStoreFalse() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        client(body = okBody("""{"text":"{}"}"""), seen = seen)
            .generateJson("gemini-3.8-flash", "sys", listOf(GeminiClient.Part.Text("hi")), schema)

        val request = seen.single()
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", request.url.encodedPath)
        assertEquals("test-key", request.headers["x-goog-api-key"])
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertFalse(body.getValue("store").jsonPrimitive.boolean)
        assertFalse("key" in request.url.parameters.names()) // key never goes in the URL
    }

    @Test
    fun requestBodyUsesResponseFormatThinkingAndInlineMedia() {
        val body = GeminiClient.requestBody(
            systemInstruction = "sys",
            parts = listOf(GeminiClient.Part.Media("audio/mp4", byteArrayOf(1, 2, 3)), GeminiClient.Part.Text("go")),
            schema = schema,
            thinking = GeminiClient.Thinking.MEDIUM,
        )
        val config = body.getValue("generationConfig").jsonObject
        val format = config.getValue("responseFormat").jsonObject.getValue("text").jsonObject
        assertEquals("APPLICATION_JSON", format.getValue("mimeType").jsonPrimitive.content)
        assertEquals(schema, format.getValue("schema") as JsonObject)
        assertEquals("MEDIUM", config.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content)
        assertFalse("responseSchema" in config) // deprecated

        val parts = body.getValue("contents").jsonArray.single().jsonObject.getValue("parts").jsonArray
        val media = parts[0].jsonObject.getValue("inlineData").jsonObject
        assertEquals("audio/mp4", media.getValue("mimeType").jsonPrimitive.content)
        assertEquals("AQID", media.getValue("data").jsonPrimitive.content)
        assertEquals("go", parts[1].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(
            "sys",
            body.getValue("systemInstruction").jsonObject.getValue("parts").jsonArray.single()
                .jsonObject.getValue("text").jsonPrimitive.content,
        )
    }

    @Test
    fun returnsTextSkippingThoughtParts() = runTest {
        val text = client(body = okBody("""{"text":"thinking...","thought":true}""", """{"text":"{\"a\":"}""", """{"text":"1}"}"""), seen = mutableListOf())
            .generateJson("m", "sys", emptyList(), schema)
        assertEquals("""{"a":1}""", text)
    }

    @Test
    fun httpErrorCarriesStatusAndReason() = runTest {
        val e = assertFailsWith<GeminiException> {
            client(
                status = HttpStatusCode.TooManyRequests,
                body = """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}""",
                seen = mutableListOf(),
            ).generateJson("m", "sys", emptyList(), schema)
        }
        assertEquals(429, e.httpStatus)
        assertEquals("RESOURCE_EXHAUSTED", e.reason)
        assertEquals("Quota exceeded", e.message)
    }

    @Test
    fun blockedPromptAndEmptyOutputThrow() = runTest {
        val blocked = assertFailsWith<GeminiException> {
            client(body = """{"promptFeedback":{"blockReason":"SAFETY"}}""", seen = mutableListOf())
                .generateJson("m", "sys", emptyList(), schema)
        }
        assertEquals("SAFETY", blocked.reason)

        val empty = assertFailsWith<GeminiException> {
            client(body = """{"candidates":[{"content":{"parts":[]},"finishReason":"MAX_TOKENS"}]}""", seen = mutableListOf())
                .generateJson("m", "sys", emptyList(), schema)
        }
        assertEquals("MAX_TOKENS", empty.reason)
    }

    @Test
    fun hangingResponseTimesOut() = runTest {
        val hanging = GeminiClient(
            apiKey = "test-key",
            http = HttpClient(MockEngine { awaitCancellation() }),
            requestTimeoutMillis = 200, // real time: HttpTimeout does not use the test's virtual clock
        )
        assertFailsWith<HttpRequestTimeoutException> {
            hanging.generateJson("m", "sys", emptyList(), schema)
        }
    }
}
