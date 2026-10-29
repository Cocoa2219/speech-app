package dev.cocoa.speech.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

// Copies the first audio track of a video into its own MPEG-4 audio file (.m4a) without re-encoding, so the audio
// pipeline (Layer 1) can run on a camera recording. CameraX records AAC, which Gemini accepts as "audio/m4a".
object AudioTrackExtractor {
    const val MIME_TYPE = "audio/m4a"

    fun extract(video: File, out: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(video.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track in the recording")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                DEFAULT_BUFFER_SIZE
            }

            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                val buffer = ByteBuffer.allocate(bufferSize)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(0, size, extractor.sampleTime, flags)
                    muxer.writeSampleData(outTrack, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            extractor.release()
        }
    }

    private const val DEFAULT_BUFFER_SIZE = 1 shl 20
}
