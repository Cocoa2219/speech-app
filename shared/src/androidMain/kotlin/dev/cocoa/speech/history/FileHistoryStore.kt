package dev.cocoa.speech.history

import dev.cocoa.speech.model.AppJson
import java.io.File
import java.util.Calendar

// One JSON file per run in app-private storage (files/history). allowBackup is off, so it never leaves the device.
// A file that fails to parse (an older format, a torn write) is skipped rather than breaking home.
class FileHistoryStore(private val dir: File) : HistoryStore {
    override fun list(): List<HistoryEntry> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            try {
                AppJson.decodeFromString<HistoryEntry>(file.readText())
            } catch (e: Exception) {
                println("FileHistoryStore: skipped ${file.name}: ${e::class.simpleName}")
                null
            }
        }

    override fun save(entry: HistoryEntry) {
        dir.mkdirs()
        val tmp = File(dir, "${entry.id}.json.tmp")
        tmp.writeText(AppJson.encodeToString(entry))
        tmp.renameTo(File(dir, "${entry.id}.json"))
    }
}

// The device's local date and time. Calendar rather than java.time, which needs API 26 (minSdk is 24).
object DeviceClock : HistoryClock {
    override fun now(): LocalStamp {
        val c = Calendar.getInstance()
        val date = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return LocalStamp(date, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }

    override fun newId(): String = System.currentTimeMillis().toString()
}
