package dev.cocoa.speech

// Every Gemini model ID lives here. Verified against https://ai.google.dev/gemini-api/docs/models on 2026-10-29.
object GeminiModels {
    // Stable. Inputs: text, image, video, audio, PDF. Structured outputs supported.
    // Thinking levels: low, medium, high ("minimal" returns an error).
    const val FLASH = "gemini-3.8-flash"

    const val TRANSCRIBE = FLASH
    const val VIDEO = FLASH
    const val ANALYZER = FLASH
    const val PERSONA = FLASH
}
