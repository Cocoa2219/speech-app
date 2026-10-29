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

// Transcript (+ scene timeline when there is video) -> neutral analysis (prompts/analyzer.md, already filled with
// grade and duration). Without scenes, visual and mismatches stay empty.
class Analyzer(private val client: JsonGenerator, private val prompt: String) {
    suspend fun analyze(segments: List<Segment>, scenes: List<Scene> = emptyList()): AnalyzerOutput {
        val json = client.generateJson(
            model = GeminiModels.ANALYZER,
            systemInstruction = prompt,
            parts = listOf(GeminiClient.Part.Text(input(segments, scenes))),
            schema = Schemas.analyzer,
            thinking = GeminiClient.Thinking.MEDIUM,
        )
        // The model echoes neither; attach the transcript and scene timeline code already has.
        return AppJson.decodeFromString<AnalyzerOutput>(json)
            .copy(segments = segments, visual = scenes.map { it.toVisual() })
    }

    companion object {
        fun input(segments: List<Segment>, scenes: List<Scene> = emptyList()): String = buildJsonObject {
            put("segments", segmentsJson(segments))
            if (scenes.isNotEmpty()) put("scenes", scenesJson(scenes))
        }.toString()

        fun segmentsJson(segments: List<Segment>): JsonArray = buildJsonArray {
            for (s in segments) addJsonObject {
                put("t_start", s.tStart)
                put("t_end", s.tEnd)
                put("text", s.text)
            }
        }

        fun scenesJson(scenes: List<Scene>): JsonArray = buildJsonArray {
            for (s in scenes) addJsonObject {
                put("t", s.t)
                put("summary", s.summary)
                put("on_screen_text", s.onScreenText)
                put("slide_index", s.slideIndex)
                put("pointing_at", s.pointingAt)
            }
        }
    }
}
