package dev.cocoa.speech.pipeline

import dev.cocoa.speech.GeminiModels
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.Segment
import kotlinx.serialization.Serializable

// Audio -> transcript segments with times in seconds. The model returns MM:SS strings (the format the Gemini audio
// docs use for timestamps); code converts them, so a malformed time drops one segment instead of the whole stage.
class Transcriber(private val client: JsonGenerator, private val prompt: String) {
    suspend fun transcribe(audio: ByteArray, mimeType: String): List<Segment> {
        val json = client.generateJson(
            model = GeminiModels.TRANSCRIBE,
            systemInstruction = prompt,
            parts = listOf(GeminiClient.Part.Media(mimeType, audio), GeminiClient.Part.Text("Transcribe this recording.")),
            schema = Schemas.transcript,
        )
        return toSegments(AppJson.decodeFromString<TranscriptDto>(json))
    }

    companion object {
        fun toSegments(dto: TranscriptDto): List<Segment> =
            dto.segments.mapNotNull { s ->
                val start = parseTimestamp(s.start) ?: return@mapNotNull null
                val end = parseTimestamp(s.end) ?: start
                val text = s.text.trim()
                if (text.isEmpty()) null else Segment(start, maxOf(start, end), text)
            }.sortedBy { it.tStart }

        // Accepts SS, MM:SS, and HH:MM:SS, each with optional decimals ("01:05.4"). Returns null when unparseable.
        fun parseTimestamp(value: String): Double? {
            val trimmed = value.trim()
            if (!TIMESTAMP.matches(trimmed)) return null
            return trimmed.split(':').fold(0.0) { acc, part -> acc * 60 + part.toDouble() }
        }

        private val TIMESTAMP = Regex("""\d+(\.\d+)?(:\d+(\.\d+)?){0,2}""")
    }
}

@Serializable
data class TranscriptDto(val segments: List<SegmentDto> = emptyList()) {
    @Serializable
    data class SegmentDto(val start: String, val end: String, val text: String)
}
