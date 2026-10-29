package dev.cocoa.speech.record

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

// 16 kHz mono AAC in an ADTS stream ("audio/aac", a Gemini-supported type). Gemini downsamples audio to 16 kbps
// anyway, so a higher rate only adds upload time. 3 minutes is about 1 MB, well under the 20 MB inline limit.
class AndroidAudioRecorder(private val context: Context) : AudioRecorder {
    override val mimeType = "audio/aac"

    private var recorder: MediaRecorder? = null
    private val file get() = File(context.cacheDir, "recording.aac")

    private var accumulatedMs = 0L
    private var runningSinceMs: Long? = null

    override fun start() {
        cancel()
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(48_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        accumulatedMs = 0
        runningSinceMs = SystemClock.elapsedRealtime()
    }

    override fun pause() {
        val since = runningSinceMs ?: return
        recorder?.pause()
        accumulatedMs += SystemClock.elapsedRealtime() - since
        runningSinceMs = null
    }

    override fun resume() {
        if (runningSinceMs != null || recorder == null) return
        recorder?.resume()
        runningSinceMs = SystemClock.elapsedRealtime()
    }

    override fun elapsedSeconds(): Double {
        val running = runningSinceMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L
        return (accumulatedMs + running) / 1000.0
    }

    override fun stop(): ByteArray {
        val r = recorder ?: return ByteArray(0)
        try {
            r.stop()
            return file.readBytes()
        } finally {
            release(r)
        }
    }

    override fun cancel() {
        val r = recorder ?: return
        // stop() throws if nothing was captured yet; the recording is being thrown away either way.
        runCatching { r.stop() }
        release(r)
    }

    private fun release(r: MediaRecorder) {
        r.release()
        recorder = null
        runningSinceMs = null
        file.delete()
    }
}
