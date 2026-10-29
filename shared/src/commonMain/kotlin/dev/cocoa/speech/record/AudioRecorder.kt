package dev.cocoa.speech.record

// One recording session, microphone only (AndroidAudioRecorder) or camera with sound (androidMain CameraCapture).
// Either way the pipeline gets audio: Layer 1 analyzes audio only.
interface Capture {
    fun start()
    fun pause()
    fun resume()

    // Recorded time so far, excluding pauses.
    fun elapsedSeconds(): Double

    // Ends the recording and returns its audio. Temporary files are deleted.
    suspend fun finish(): Recording

    // Discards any recording in progress. Safe to call when idle.
    fun cancel()
}

// A runtime permission request (microphone, or camera plus microphone). Android: ActivityResult launchers in androidApp.
interface Permission {
    suspend fun request(): Boolean
}

class Recording(val audio: ByteArray, val mimeType: String, val durationSeconds: Double)

// Pipeline step 1 limits from CLAUDE.md.
object RecordLimits {
    const val MIN_SECONDS = 20.0
    const val WARN_SECONDS = 150.0
    const val MAX_SECONDS = 180.0
}
