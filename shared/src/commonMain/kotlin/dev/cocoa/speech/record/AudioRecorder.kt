package dev.cocoa.speech.record

// Microphone recording. Android: MediaRecorder (androidMain). One recording at a time.
interface AudioRecorder {
    // MIME type of the bytes stop() returns, as Gemini expects it.
    val mimeType: String

    fun start()
    fun pause()
    fun resume()

    // Recorded time so far, excluding pauses.
    fun elapsedSeconds(): Double

    // Ends the recording and returns the audio. The temporary file is deleted.
    fun stop(): ByteArray

    // Discards any recording in progress. Safe to call when idle.
    fun cancel()
}

// RECORD_AUDIO permission. Android: an ActivityResult launcher in androidApp.
interface MicPermission {
    suspend fun request(): Boolean
}

class Recording(val audio: ByteArray, val mimeType: String, val durationSeconds: Double)

// Pipeline step 1 limits from CLAUDE.md.
object RecordLimits {
    const val MIN_SECONDS = 20.0
    const val WARN_SECONDS = 150.0
    const val MAX_SECONDS = 180.0
}
