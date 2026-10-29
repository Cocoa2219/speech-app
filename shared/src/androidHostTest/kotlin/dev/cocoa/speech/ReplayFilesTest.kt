package dev.cocoa.speech

import dev.cocoa.speech.replay.ReplayFiles
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayFilesTest {
    private fun tempFile(dir: File, name: String, bytes: ByteArray) = File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun keptRecordingReloadsByteIdenticalSoReanalysisHitsTheCache() = runTest {
        val cache = Files.createTempDirectory("cache").toFile()
        val dir = ReplayFiles.dir(Files.createTempDirectory("files").toFile())

        val first = ReplayFiles.keep(
            dir,
            audio = tempFile(cache, "recording.m4a", byteArrayOf(1, 2, 3)),
            audioMimeType = "audio/m4a",
            video = tempFile(cache, "recording.mp4", byteArrayOf(9, 8, 7, 6)),
            durationSeconds = 62.347,
        )
        val again = ReplayFiles.last(dir)!!

        assertContentEquals(first.audio, again.audio)
        assertEquals("audio/m4a", again.mimeType)
        assertEquals(62.347, again.durationSeconds) // the analyzer prompt is filled from it, so it must not drift
        assertContentEquals(byteArrayOf(9, 8, 7, 6), again.video!!.read())
        assertTrue(again.replay!!.isVideo)
        assertFalse(File(cache, "recording.mp4").exists()) // moved, not copied
    }

    @Test
    fun audioOnlyRecordingHasNoVideoAndANewKeepReplacesTheOld() {
        val cache = Files.createTempDirectory("cache").toFile()
        val dir = ReplayFiles.dir(Files.createTempDirectory("files").toFile())
        ReplayFiles.keep(dir, tempFile(cache, "a.m4a", byteArrayOf(1)), "audio/m4a", tempFile(cache, "a.mp4", byteArrayOf(2)), 30.0)
        val audioOnly = ReplayFiles.keep(dir, tempFile(cache, "b.aac", byteArrayOf(3)), "audio/aac", null, 25.0)

        assertNull(audioOnly.video)
        assertFalse(audioOnly.replay!!.isVideo)
        assertEquals(setOf("last.aac", "last.properties"), dir.list()!!.toSet()) // only one recording is kept
    }

    @Test
    fun nothingKeptMeansNull() {
        val dir = ReplayFiles.dir(Files.createTempDirectory("files").toFile())
        assertNull(ReplayFiles.last(dir))
        ReplayFiles.keep(dir, tempFile(dir.parentFile, "c.aac", byteArrayOf(1)), "audio/aac", null, 20.0)
        ReplayFiles.clear(dir)
        assertNull(ReplayFiles.last(dir))
    }
}
