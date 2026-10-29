package dev.cocoa.speech

// Every Gemini model ID lives here. Verified against https://ai.google.dev/gemini-api/docs/models on 2026-10-29.
object GeminiModels {
    // TEMPORARY: gemini-3.8-flash is the intended model (stable, thinking levels low/medium/high), but on 2026-10-29 it
    // took ~48 s for a one-line prompt on our key, far over the ~20 s pipeline target. gemini-3.6-flash answered the
    // same JSON-schema request in ~3 s. TODO: re-time gemini-3.8-flash before demo day and switch back if it is fast.
    const val FLASH = "gemini-3.6-flash"

    const val TRANSCRIBE = FLASH
    const val VIDEO = FLASH
    const val ANALYZER = FLASH
    const val PERSONA = FLASH
}
