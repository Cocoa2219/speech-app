package dev.cocoa.speech.gemini

import kotlinx.serialization.json.JsonObject

// What the pipeline stages need from Gemini. GeminiClient calls the API; debug builds wrap it in a response cache
// (androidMain CachingGenerator) so repeated runs of the same input spend no requests.
interface JsonGenerator {
    suspend fun generateJson(
        model: String,
        systemInstruction: String,
        parts: List<GeminiClient.Part>,
        schema: JsonObject,
        thinking: GeminiClient.Thinking = GeminiClient.Thinking.LOW,
    ): String
}
