package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.gemini.MediaUploader
import dev.cocoa.speech.logic.PromptTemplate
import dev.cocoa.speech.record.Recording
import speech_app.shared.generated.resources.Res

// Builds an AnalysisRun for one recording, with prompts read from composeResources/files/prompts.
// uploader (Files API) is needed only for video; without it, video recordings are analyzed audio-only.
class LivePipeline(private val client: JsonGenerator, private val uploader: MediaUploader? = null) {
    suspend fun newRun(recording: Recording, grade: String): AnalysisRun =
        AnalysisRun(
            audio = recording.audio,
            mimeType = recording.mimeType,
            transcriber = Transcriber(client, prompt("transcribe.md")),
            analyzer = Analyzer(client, PromptTemplate.analyzer(prompt("analyzer.md"), grade, recording.durationSeconds)),
            writer = PersonaWriter(client, PromptTemplate.persona(prompt("persona.md"), grade)),
            video = recording.video,
            describer = if (recording.video != null && uploader != null) {
                SceneDescriber(client, uploader, prompt("video.md"))
            } else {
                null
            },
        )

    private suspend fun prompt(name: String): String = Res.readBytes("files/prompts/$name").decodeToString()
}
