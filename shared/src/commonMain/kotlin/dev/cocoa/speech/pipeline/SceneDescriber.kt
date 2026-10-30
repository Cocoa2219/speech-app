package dev.cocoa.speech.pipeline

import dev.cocoa.speech.GeminiModels
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.gemini.MediaUploader
import dev.cocoa.speech.gemini.ResponseCache
import dev.cocoa.speech.logic.PersonaTrigger
import dev.cocoa.speech.logic.QuoteValidator
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.Visual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Video -> scene timeline (prompts/video.md). The video is uploaded with the Files API (too large to send inline) and
// deleted as soon as the call returns. Default media resolution and ~1 fps sampling: on-screen text matters, and slide
// changes are slow enough for 1 fps.
// The answer is cached by the video's content (not its upload URI, which changes every time), checked before
// uploading: a video seen before is neither uploaded nor sent to the model again (debug builds).
// deck: the PDF attached at setup, sent inline next to the video so each scene can name the PDF page on screen
// (deck_page) and copy that page's text from the PDF (deck_text). Our SD camera reads titles, not body text; the PDF
// has the exact slide. A page match is the model's judgment, checked in toScenes where the frame has readable words.
// The video is sent at high media resolution: at the default, small slide text was unreadable and got filled in from
// the PDF in testing (2026-10-30).
class SceneDescriber(
    private val client: JsonGenerator,
    private val uploader: MediaUploader,
    private val prompt: String,
    private val cache: ResponseCache = ResponseCache.None,
    private val deck: ByteArray? = null,
    private val log: (String) -> Unit = ::println,
) {
    suspend fun describe(video: ByteArray, mimeType: String): List<Scene> {
        val key = listOf(
            "scenes-high".encodeToByteArray(),
            GeminiModels.VIDEO.encodeToByteArray(),
            prompt.encodeToByteArray(),
            mimeType.encodeToByteArray(),
            video,
            deck ?: ByteArray(0),
            Schemas.video.toString().encodeToByteArray(),
        )
        val json = cache.getOrPut(key) {
            uploader.withUploaded(video, mimeType) { file ->
                client.generateJson(
                    model = GeminiModels.VIDEO,
                    systemInstruction = prompt,
                    parts = listOf(file.copy(highResolution = true)) + Material(deck = deck).parts() + GeminiClient.Part.Text(INSTRUCTION),
                    schema = Schemas.video,
                )
            }
        }
        return toScenes(AppJson.decodeFromString<SceneDto>(json), deckAttached = deck != null, log)
    }

    companion object {
        private const val INSTRUCTION = "Describe what is visible in this video."

        // A deck page is kept only with its text, and only when a PDF was sent. If the frame has readable text, at least
        // one of its words must be on the matched page; a picture-only slide has nothing to check and keeps the match.
        // Logs carry times only, never slide text.
        fun toScenes(dto: SceneDto, deckAttached: Boolean = false, log: (String) -> Unit = {}): List<Scene> =
            dto.scenes.mapNotNull { s ->
                val t = Transcriber.parseTimestamp(s.t) ?: return@mapNotNull null
                val onScreenText = s.onScreenText?.trim()?.takeIf { it.isNotEmpty() }
                var deckText = s.deckText?.trim()?.takeIf { it.isNotEmpty() && deckAttached && s.deckPage != null }
                if (deckText != null && onScreenText != null && !sharesAWord(onScreenText, deckText)) {
                    log("SceneDescriber: deck page dropped at ${t}s (no readable word on the matched page)")
                    deckText = null
                }
                Scene(
                    t = t,
                    summary = s.summary.trim(),
                    onScreenText = onScreenText,
                    slideIndex = s.slideIndex,
                    pointingAt = s.pointingAt?.trim()?.takeIf { it.isNotEmpty() },
                    deckPage = s.deckPage.takeIf { deckText != null },
                    deckText = deckText,
                    deckPartial = deckText != null && s.deckPartial == true,
                )
            }.sortedBy { it.t }

        // Words of two or more letters or digits, compared normalized as in QuoteValidator.
        private fun sharesAWord(frameText: String, pageText: String): Boolean {
            val page = QuoteValidator.normalize(pageText)
            return frameText.split(Regex("\\s+|/")).map(QuoteValidator::normalize).any { it.length >= 2 && it in page }
        }
    }
}

// One entry of the scene timeline, in seconds.
data class Scene(
    val t: Double,
    val summary: String,
    val onScreenText: String? = null,
    val slideIndex: Int? = null,
    val pointingAt: String? = null,
    // The PDF page on screen (1-based) and its text copied from the PDF; both null when no page was matched.
    val deckPage: Int? = null,
    val deckText: String? = null,
    // True while the slide builds up: only part of the PDF page is visible yet (the PDF holds the finished slide).
    val deckPartial: Boolean = false,
) {
    // The contract's Visual has one text field. On-screen text and the matched page's text go into it verbatim, so
    // quotes the analyzer takes from the screen (dense_slide, shown_not_said) can be grounded by QuoteValidator.
    fun toVisual(): Visual = Visual(
        t = t,
        summary = buildString {
            append(summary)
            if (onScreenText != null) append(" On screen: \"").append(onScreenText).append('"')
            if (deckText != null) append(" PDF page ").append(deckPage).append(": \"").append(deckText).append('"')
            if (pointingAt != null) append(" Pointing at: ").append(pointingAt)
        },
        slideIndex = slideIndex,
    )

    companion object {
        // The PDF page on screen at time t: the latest scene starting at or before t, if it matched a page.
        fun deckPageAt(scenes: List<Scene>, t: Double): Int? = at(scenes, t)?.deckPage

        // A slide that is still building up is not dense to the audience, whatever the finished PDF page holds. Drops
        // dense_slide triggers whose scene is partial; the analyzer prompt asks for this too, this makes sure.
        fun withoutDenseOnPartialSlides(triggers: List<PersonaTrigger>, scenes: List<Scene>): List<PersonaTrigger> =
            triggers.filterNot { it.type == EventType.DENSE_SLIDE && at(scenes, it.t)?.deckPartial == true }

        private fun at(scenes: List<Scene>, t: Double): Scene? = scenes.lastOrNull { it.t <= t }
    }
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
        @SerialName("deck_page") val deckPage: Int? = null,
        @SerialName("deck_text") val deckText: String? = null,
        @SerialName("deck_partial") val deckPartial: Boolean? = null,
    )
}
