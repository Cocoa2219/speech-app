package dev.cocoa.speech.pipeline

import dev.cocoa.speech.GeminiModels
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.gemini.MediaUploader
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.Visual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Video -> scene timeline (prompts/video.md). The video is uploaded with the Files API (too large to send inline) and
// deleted as soon as the call returns. Default media resolution and ~1 fps sampling: on-screen text matters, and slide
// changes are slow enough for 1 fps.
class SceneDescriber(
    private val client: JsonGenerator,
    private val uploader: MediaUploader,
    private val prompt: String,
) {
    suspend fun describe(video: ByteArray, mimeType: String): List<Scene> {
        val json = uploader.withUploaded(video, mimeType) { file ->
            client.generateJson(
                model = GeminiModels.VIDEO,
                systemInstruction = prompt,
                parts = listOf(file, GeminiClient.Part.Text("Describe what is visible in this video.")),
                schema = Schemas.video,
            )
        }
        return toScenes(AppJson.decodeFromString<SceneDto>(json))
    }

    companion object {
        fun toScenes(dto: SceneDto): List<Scene> =
            dto.scenes.mapNotNull { s ->
                val t = Transcriber.parseTimestamp(s.t) ?: return@mapNotNull null
                Scene(
                    t = t,
                    summary = s.summary.trim(),
                    onScreenText = s.onScreenText?.trim()?.takeIf { it.isNotEmpty() },
                    slideIndex = s.slideIndex,
                    pointingAt = s.pointingAt?.trim()?.takeIf { it.isNotEmpty() },
                )
            }.sortedBy { it.t }
    }
}

// One entry of the scene timeline, in seconds.
data class Scene(
    val t: Double,
    val summary: String,
    val onScreenText: String? = null,
    val slideIndex: Int? = null,
    val pointingAt: String? = null,
) {
    // The contract's Visual has one text field. On-screen text goes into it verbatim, so quotes the analyzer takes from
    // the screen (dense_slide, shown_not_said) can be grounded by QuoteValidator.
    fun toVisual(): Visual = Visual(
        t = t,
        summary = buildString {
            append(summary)
            if (onScreenText != null) append(" On screen: \"").append(onScreenText).append('"')
            if (pointingAt != null) append(" Pointing at: ").append(pointingAt)
        },
        slideIndex = slideIndex,
    )
}

@Serializable
data class SceneDto(val scenes: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val t: String,
        val summary: String = "",
        @SerialName("on_screen_text") val onScreenText: String? = null,
        @SerialName("slide_index") val slideIndex: Int? = null,
        @SerialName("pointing_at") val pointingAt: String? = null,
    )
}
