package dev.cocoa.speech.replay

import android.content.Context
import dev.cocoa.speech.record.Recording
import dev.cocoa.speech.record.ReplayMedia
import dev.cocoa.speech.record.VideoInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

// The one kept recording (app-private, not backed up), for replay on the results screen and for debug re-analysis.
// Starting a new recording clears it. Besides the media, it keeps the exact audio that was sent and the duration, so
// re-analyzing it sends byte-identical requests and every stage is answered from the debug answer cache.
object ReplayFiles {
    private const val META = "last.properties"

    fun dir(context: Context) = dir(context.filesDir)

    fun dir(filesDir: File) = File(filesDir, "replay").apply { mkdirs() }

    fun clear(context: Context) = clear(dir(context))

    fun clear(dir: File) {
        dir.listFiles()?.forEach { it.delete() }
    }

    // Moves the finished audio (and video, if any) into dir and returns the Recording the pipeline should analyze.
    fun keep(dir: File, audio: File, audioMimeType: String, video: File?, durationSeconds: Double): Recording {
        clear(dir)
        move(audio, File(dir, "last." + extension(audioMimeType)))
        video?.let { move(it, File(dir, "last.mp4")) }
        val meta = Properties().apply {
            setProperty("audioMimeType", audioMimeType)
            setProperty("durationSeconds", durationSeconds.toString())
            setProperty("video", (video != null).toString())
        }
        File(dir, META).outputStream().use { meta.store(it, null) }
        return last(dir) ?: error("Could not keep the recording")
    }

    // The kept recording, or null if there is none.
    fun last(dir: File): Recording? {
        val metaFile = File(dir, META)
        if (!metaFile.exists()) return null
        val meta = Properties().apply { metaFile.inputStream().use { load(it) } }
        val audioMimeType = meta.getProperty("audioMimeType") ?: return null
        val duration = meta.getProperty("durationSeconds")?.toDoubleOrNull() ?: return null
        val audio = File(dir, "last." + extension(audioMimeType)).takeIf { it.exists() } ?: return null
        val video = File(dir, "last.mp4").takeIf { meta.getProperty("video") == "true" && it.exists() }
        return Recording(
            audio = audio.readBytes(),
            mimeType = audioMimeType,
            durationSeconds = duration,
            video = video?.let { VideoInput("video/mp4", read = { withContext(Dispatchers.IO) { it.readBytes() } }) },
            replay = ReplayMedia((video ?: audio).absolutePath, isVideo = video != null),
        )
    }

    private fun extension(mimeType: String) = when (mimeType) {
        "audio/aac" -> "aac"
        "audio/m4a" -> "m4a"
        "audio/wav" -> "wav"
        else -> "audio"
    }

    private fun move(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }
}
