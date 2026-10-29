package dev.cocoa.speech.record

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dev.cocoa.speech.replay.ReplayFiles
import java.io.File

// The finished file is moved to ReplayFiles for replay on the results screen.
// 16 kHz mono AAC in an ADTS stream ("audio/aac", a Gemini-supported type). Gemini downsamples audio to 16 kbps
// anyway, so a higher rate only adds upload time. 3 minutes is about 1 MB, well under the 20 MB inline limit.
class AndroidAudioRecorder(private val context: Context) : Capture {
    private var recorder: MediaRecorder? = null
    private val file get() = File(context.cacheDir, "recording.aac")
    private val clock = PausableClock()

    override fun start() {
        cancel()
        ReplayFiles.clear(context) // only the latest recording is kept for replay
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
        clock.start()
    }

    override fun pause() {
        if (!clock.running) return
        recorder?.pause()
        clock.pause()
    }

    override fun resume() {
        if (clock.running || recorder == null) return
        recorder?.resume()
        clock.resume()
    }

    override fun elapsedSeconds(): Double = clock.seconds()

    override suspend fun finish(): Recording {
        val r = recorder ?: error("Not recording")
        clock.stop()
        try {
            r.stop()
            return ReplayFiles.keep(ReplayFiles.dir(context), file, "audio/aac", video = null, clock.seconds())
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
        clock.stop()
        file.delete()
    }
}
