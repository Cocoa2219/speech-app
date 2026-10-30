package dev.cocoa.speech

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.cocoa.speech.setup.SlideText
import java.io.InputStream
import java.util.zip.ZipInputStream

// The system document picker for the setup memo: a PowerPoint deck (.pptx, its slide text is read on the device) or a
// plain text file. Must be created before the activity is STARTED (registerForActivityResult rule), so construct it
// in onCreate. A file that cannot be read is ignored.
class ActivityTextFilePicker(private val activity: ComponentActivity) {
    private var pending: ((String, String) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val onPicked = pending
        pending = null
        if (uri != null && onPicked != null) read(uri)?.let { (name, text) -> onPicked(name, text) }
    }

    fun pick(onPicked: (name: String, text: String) -> Unit) {
        pending = onPicked
        launcher.launch(arrayOf(PPTX, "text/*"))
    }

    private fun read(uri: Uri): Pair<String, String>? = try {
        val resolver = activity.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val isDeck = resolver.getType(uri) == PPTX || name.endsWith(".pptx", ignoreCase = true)
        val text = resolver.openInputStream(uri)?.use { if (isDeck) slides(it) else head(it).decodeToString().removePrefix("﻿") }
        text?.takeIf { it.isNotBlank() }?.let { name to it }
    } catch (e: Exception) {
        println("ActivityTextFilePicker: read failed: ${e::class.simpleName}")
        null
    }

    // Walks the zip once; only slide XML is read, so pictures and media are skipped over, not loaded.
    private fun slides(input: InputStream): String {
        val slides = mutableMapOf<Int, List<String>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val n = SlideText.slideNumber(entry.name) ?: continue
                slides[n] = SlideText.lines(head(zip, MAX_SLIDE_BYTES).decodeToString())
            }
        }
        return SlideText.format(slides)
    }

    private fun head(input: InputStream, max: Int = MAX_TEXT_BYTES): ByteArray {
        val buffer = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = input.read(buffer, total, max - total)
            if (n < 0) break
            total += n
        }
        return buffer.copyOf(total)
    }

    private companion object {
        const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

        // The memo keeps 2,000 characters; these only bound how much is read.
        const val MAX_TEXT_BYTES = 64 * 1024
        const val MAX_SLIDE_BYTES = 1024 * 1024
    }
}
