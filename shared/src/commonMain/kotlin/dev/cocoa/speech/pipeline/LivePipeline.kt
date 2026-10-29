package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.logic.PromptTemplate
import speech_app.shared.generated.resources.Res

// Builds an AnalysisRun for one recording, with prompts read from composeResources/files/prompts.
class LivePipeline(private val client: GeminiClient) {
    suspend fun newRun(audio: ByteArray, mimeType: String, durationSeconds: Double, grade: String): AnalysisRun =
        AnalysisRun(
            audio = audio,
            mimeType = mimeType,
            transcriber = Transcriber(client, prompt("transcribe.md")),
            analyzer = Analyzer(client, PromptTemplate.analyzer(prompt("analyzer.md"), grade, durationSeconds)),
            writer = PersonaWriter(client, PromptTemplate.persona(prompt("persona.md"), grade)),
        )

    private suspend fun prompt(name: String): String = Res.readBytes("files/prompts/$name").decodeToString()
}
