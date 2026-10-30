package dev.cocoa.speech

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.cocoa.speech.setup.PickedMaterial
import java.io.File
import java.io.InputStream

// The system document picker behind "발표 자료 불러오기": a PDF (the slides) or a plain text file. A text file's contents
// go into the memo; a PDF is copied into app-private storage (files/material, one kept, replaced on the next pick) so
// it can be sent to the model with the run. Must be created before the activity is STARTED
// (registerForActivityResult rule), so construct it in onCreate. A file that cannot be read is ignored.
class ActivityMaterialPicker(private val activity: ComponentActivity) {
    private var pending: ((PickedMaterial) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val onPicked = pending
        pending = null
        if (uri != null && onPicked != null) read(uri)?.let(onPicked)
    }

    fun pick(onPicked: (PickedMaterial) -> Unit) {
        pending = onPicked
        launcher.launch(arrayOf(PDF, "text/*"))
    }

    private fun read(uri: Uri): PickedMaterial? = try {
        val resolver = activity.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val isPdf = resolver.getType(uri) == PDF || name.endsWith(".pdf", ignoreCase = true)
        resolver.openInputStream(uri)?.use { input ->
            if (isPdf) copyPdf(input)?.let { PickedMaterial.Pdf(name, it.absolutePath) }
            else head(input).decodeToString().removePrefix("﻿").takeIf { it.isNotBlank() }?.let { PickedMaterial.Text(name, it) }
        }
    } catch (e: Exception) {
        println("ActivityMaterialPicker: read failed: ${e::class.simpleName}")
        null
    }

    // Copies through a temp file and gives up past the model's 50 MB document limit.
    private fun copyPdf(input: InputStream): File? {
        val dir = File(activity.filesDir, "material").apply { mkdirs() }
        val tmp = File(dir, "deck.pdf.tmp")
        var total = 0L
        tmp.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_PDF_BYTES) {
                    out.close()
                    tmp.delete()
                    println("ActivityMaterialPicker: PDF over ${MAX_PDF_BYTES / 1_000_000} MB, not attached")
                    return null
                }
                out.write(buffer, 0, n)
            }
        }
        val deck = File(dir, "deck.pdf")
        deck.delete()
        return if (tmp.renameTo(deck)) deck else null
    }

    private fun head(input: InputStream): ByteArray {
        val buffer = ByteArray(MAX_TEXT_BYTES)
        var total = 0
        while (total < MAX_TEXT_BYTES) {
            val n = input.read(buffer, total, MAX_TEXT_BYTES - total)
            if (n < 0) break
            total += n
        }
        return buffer.copyOf(total)
    }

    private companion object {
        const val PDF = "application/pdf"

        // The memo keeps 2,000 characters; this only bounds how much is read. Gemini takes PDFs up to 50 MB.
        const val MAX_TEXT_BYTES = 64 * 1024
        const val MAX_PDF_BYTES = 50L * 1000 * 1000
    }
}
