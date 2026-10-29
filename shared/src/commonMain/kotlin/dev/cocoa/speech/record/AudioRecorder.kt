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

// audio always feeds the pipeline; video (camera recordings) adds the scene timeline when present.
class Recording(
    val audio: ByteArray,
    val mimeType: String,
    val durationSeconds: Double,
    val video: VideoInput? = null,
)

// A recorded video kept on device only until its run ends. Read lazily: ~23 MB for 3 minutes. discard() deletes it.
class VideoInput(val mimeType: String, val read: suspend () -> ByteArray, val discard: () -> Unit)

// Pipeline step 1 limits from CLAUDE.md.
object RecordLimits {
    const val MIN_SECONDS = 20.0
    const val WARN_SECONDS = 150.0
    const val MAX_SECONDS = 180.0
}
