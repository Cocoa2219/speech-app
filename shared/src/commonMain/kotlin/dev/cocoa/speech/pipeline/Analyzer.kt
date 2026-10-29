package dev.cocoa.speech.pipeline

import dev.cocoa.speech.GeminiModels
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.Segment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Transcript -> neutral analysis (prompts/analyzer.md, already filled with grade and duration).
// Audio only for now: no scene timeline is sent, so visual and mismatches come back empty.
class Analyzer(private val client: JsonGenerator, private val prompt: String) {
    suspend fun analyze(segments: List<Segment>): AnalyzerOutput {
        val json = client.generateJson(
            model = GeminiModels.ANALYZER,
            systemInstruction = prompt,
            parts = listOf(GeminiClient.Part.Text(input(segments))),
            schema = Schemas.analyzer,
            thinking = GeminiClient.Thinking.MEDIUM,
        )
        // The model does not echo segments; attach the transcript code already has.
        return AppJson.decodeFromString<AnalyzerOutput>(json).copy(segments = segments)
    }

    companion object {
        fun input(segments: List<Segment>): String = buildJsonObject { put("segments", segmentsJson(segments)) }.toString()

        fun segmentsJson(segments: List<Segment>): JsonArray = buildJsonArray {
            for (s in segments) addJsonObject {
                put("t_start", s.tStart)
                put("t_end", s.tEnd)
                put("text", s.text)
            }
        }
    }
}
