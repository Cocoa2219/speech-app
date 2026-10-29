package dev.cocoa.speech.pipeline

// Pipeline switches that trade speed for requests. Model IDs live in GeminiModels.
object PipelineConfig {
    // true: 3 parallel persona calls (faster, 5 requests per run). false: 1 call (3 requests per run).
    // Off while the API key is on the free tier (20 requests per day for gemini-3.6-flash, hit on 2026-10-29).
    const val PARALLEL_PERSONA_CALLS = false
}
