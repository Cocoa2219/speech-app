package dev.cocoa.speech.video

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.cocoa.speech.record.Capture
import dev.cocoa.speech.record.PausableClock
import dev.cocoa.speech.record.Recording
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import androidx.camera.video.Recording as CameraRecording

// Rear camera with sound (pipeline step 1). Video is compressed on device by recording at SD quality and a capped
// bitrate. For now only the audio track is analyzed (Layer 1); the video file is deleted after the audio is copied out.
// TODO(Layer 2): keep the video for the scene timeline. At 1 Mbps, 3 minutes is ~23 MB, above Gemini's 20 MB inline
// request limit, so video will need the Files API (and deletion right after processing, per CLAUDE.md privacy rules).
class CameraCapture(private val context: Context) : Capture {
    private val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
        .setTargetVideoEncodingBitRate(VIDEO_BITRATE)
        .build()
    private val videoCapture = VideoCapture.withOutput(recorder)

    private var provider: ProcessCameraProvider? = null
    private var recording: CameraRecording? = null
    private var finalized: CompletableDeferred<VideoRecordEvent.Finalize>? = null
    private var discard = false
    private val clock = PausableClock()

    private val videoFile get() = File(context.cacheDir, "recording.mp4")
    private val audioFile get() = File(context.cacheDir, "recording.m4a")

    // Binds the preview and video capture to the lifecycle. Call once the camera permission is granted.
    suspend fun bind(owner: LifecycleOwner, preview: Preview) {
        val provider = awaitProvider().also { provider = it }
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
    }

    fun unbind() {
        cancel()
        provider?.unbindAll()
    }

    // The permission (camera and microphone) is requested by the record screen before start() can be called.
    @SuppressLint("MissingPermission")
    override fun start() {
        cancel()
        discard = false
        val done = CompletableDeferred<VideoRecordEvent.Finalize>().also { finalized = it }
        recording = recorder.prepareRecording(context, FileOutputOptions.Builder(videoFile).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (discard) videoFile.delete()
                    done.complete(event)
                }
            }
        clock.start()
    }

    override fun pause() {
        if (!clock.running) return
        recording?.pause()
        clock.pause()
    }

    override fun resume() {
        if (clock.running || recording == null) return
        recording?.resume()
        clock.resume()
    }

    override fun elapsedSeconds(): Double = clock.seconds()

    override suspend fun finish(): Recording {
        val active = recording ?: error("Not recording")
        clock.stop()
        active.stop()
        recording = null
        val event = finalized!!.await()
        try {
            // Some finalize errors still leave a usable file (for example the source went inactive), so check the file.
            if (!videoFile.exists() || videoFile.length() == 0L) error("Recording failed (CameraX error ${event.error})")
            return withContext(Dispatchers.IO) {
                AudioTrackExtractor.extract(videoFile, audioFile)
                Recording(audioFile.readBytes(), AudioTrackExtractor.MIME_TYPE, clock.seconds())
            }
        } finally {
            videoFile.delete()
            audioFile.delete()
        }
    }

    override fun cancel() {
        val active = recording ?: return
        discard = true
        active.stop() // finalizes asynchronously; the listener deletes the file
        recording = null
        clock.stop()
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cont.resumeWith(runCatching { future.get() }) },
            ContextCompat.getMainExecutor(context),
        )
    }

    private companion object {
        const val VIDEO_BITRATE = 1_000_000
    }
}
