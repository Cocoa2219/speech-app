package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.GeminiClient

// The speaker's own preparation from setup: the typed memo and an attached PDF deck. The analyzer and persona prompts
// treat it as reference only: listeners never heard or saw it, so it cannot define a term, state a premise, or ground a
// quote. It is sent inline with store=false (Gemini takes PDFs inline up to 50 MB, checked 2026-10-30), so nothing is
// left on Google's side to delete, and the debug answer cache keys on its bytes.
class Material(notes: String = "", val deck: ByteArray? = null) {
    val notes: String = notes.trim()

    // The deck goes before the text part, as Gemini's document examples order them.
    fun parts(): List<GeminiClient.Part> = deck?.let { listOf(GeminiClient.Part.Media(PDF, it)) }.orEmpty()

    companion object {
        const val PDF = "application/pdf"
        val None = Material()
    }
}
