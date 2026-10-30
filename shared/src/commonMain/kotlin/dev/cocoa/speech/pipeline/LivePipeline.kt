package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.gemini.MediaUploader
import dev.cocoa.speech.gemini.ResponseCache
import dev.cocoa.speech.logic.PromptTemplate
import dev.cocoa.speech.record.Recording
import dev.cocoa.speech.setup.TalkSetup
import speech_app.shared.generated.resources.Res

// Builds an AnalysisRun for one recording, with prompts read from composeResources/files/prompts.
// uploader (Files API) is needed only for video; without it, video recordings are analyzed audio-only.
// cache (debug builds) also covers the video scene call, keyed by the video's content.
// readDeck loads the PDF attached at setup (app storage); null when the file is gone, and the run goes on without it.
class LivePipeline(
    private val client: JsonGenerator,
    private val uploader: MediaUploader? = null,
    private val cache: ResponseCache = ResponseCache.None,
    private val readDeck: suspend (path: String) -> ByteArray? = { null },
    private val log: (String) -> Unit = ::println,
) {
    suspend fun newRun(recording: Recording, setup: TalkSetup): AnalysisRun {
        val deck = setup.deckPath?.let { readDeck(it) }
        if (setup.deckPath != null && deck == null) log("LivePipeline: attached PDF not found; running without it")
        val material = Material(setup.memo, deck)
        // Sizes only, never the memo's text.
        log("LivePipeline: notes ${material.notes.length} chars, deck ${deck?.size ?: 0} bytes")
        return AnalysisRun(
            audio = recording.audio,
            mimeType = recording.mimeType,
            transcriber = Transcriber(client, prompt("transcribe.md")),
            analyzer = Analyzer(client, PromptTemplate.analyzer(prompt("analyzer.md"), setup.grade, recording.durationSeconds), material),
            writer = PersonaWriter(client, PromptTemplate.persona(prompt("persona.md"), setup.grade), material),
            video = recording.video,
            describer = if (recording.video != null && uploader != null) {
                SceneDescriber(client, uploader, prompt("video.md"), cache)
            } else {
                null
            },
        )
    }

    private suspend fun prompt(name: String): String = Res.readBytes("files/prompts/$name").decodeToString()
}
