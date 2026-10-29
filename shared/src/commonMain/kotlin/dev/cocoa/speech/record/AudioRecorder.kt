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

// audio always feeds the pipeline; video (camera recordings) adds the scene timeline when present. replay is the
// file the results screen plays back.
class Recording(
    val audio: ByteArray,
    val mimeType: String,
    val durationSeconds: Double,
    val video: VideoInput? = null,
    val replay: ReplayMedia? = null,
)

// A recorded video for the scene timeline. Read lazily: ~23 MB for 3 minutes.
class VideoInput(val mimeType: String, val read: suspend () -> ByteArray)

// The latest recording, kept in app-private storage for replay with synced pins (developer decision, 2026-10-29).
// Only one is kept: starting a new recording deletes it. It never leaves the device except as the pipeline needs.
class ReplayMedia(val path: String, val isVideo: Boolean)

// Pipeline step 1 limits from CLAUDE.md.
object RecordLimits {
    const val MIN_SECONDS = 20.0
    const val WARN_SECONDS = 150.0
    const val MAX_SECONDS = 180.0
}
