package dev.cocoa.speech.history

import dev.cocoa.speech.logic.FixRanking
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.logic.VideoStatus
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import dev.cocoa.speech.model.Visual
import dev.cocoa.speech.record.ReplayMedia
import kotlinx.serialization.Serializable

// One finished practice run, kept on the device so home can list it and reopen its results. Holds transcript, pins and
// report inputs only, never audio or video (CLAUDE.md privacy rule). The recording itself is kept for the newest run
// only (ReplayFiles reuses one file name), so replayPath may be used for the newest entry only.
@Serializable
data class HistoryEntry(
    val id: String,
    val topic: String,
    val grade: String,
    val memo: String = "",
    val stamp: LocalStamp,
    val durationSeconds: Double,
    val segments: List<Segment>,
    val events: List<PersonaEvent>,
    val visual: List<Visual> = emptyList(),
    val video: VideoStatus = VideoStatus.NONE,
    val replayPath: String? = null,
    val replayIsVideo: Boolean = false,
) {
    // Hand-raises on one moment merge into one fix, so this is what the student can act on.
    val fixCount: Int get() = FixRanking.rank(events).size
    val raised: List<Persona> get() = Persona.entries.filter { p -> events.any { it.persona == p } }

    fun report(): ResultsReport = ResultsReport.restore(durationSeconds, segments, events, visual, video)

    val replay: ReplayMedia? get() = replayPath?.let { ReplayMedia(it, replayIsVideo) }

    companion object {
        private const val TOPIC_FALLBACK_CHARS = 20

        // A blank topic falls back to the start of what was said, so every row has a readable name.
        fun of(id: String, topic: String, grade: String, memo: String, stamp: LocalStamp, report: ResultsReport, replay: ReplayMedia?) =
            HistoryEntry(
                id = id,
                topic = topic.trim().ifEmpty {
                    report.segments.firstOrNull()?.text?.trim()?.let {
                        if (it.length > TOPIC_FALLBACK_CHARS) it.take(TOPIC_FALLBACK_CHARS).trimEnd() + "…" else it
                    }.orEmpty()
                },
                grade = grade,
                memo = memo.trim(),
                stamp = stamp,
                durationSeconds = report.durationSeconds,
                segments = report.segments,
                events = report.events,
                visual = report.visual,
                video = report.video,
                replayPath = replay?.path,
                replayIsVideo = replay?.isVideo == true,
            )
    }
}

// Local calendar date (ISO yyyy-MM-dd) and minute of the day when the run finished. The platform supplies it, so
// common code needs no date library.
@Serializable
data class LocalStamp(val date: String, val minuteOfDay: Int) : Comparable<LocalStamp> {
    val epochDay: Long get() = CivilDate.parse(date)
    override fun compareTo(other: LocalStamp) = compareValuesBy(this, other, { it.date }, { it.minuteOfDay })
}

interface HistoryStore {
    fun list(): List<HistoryEntry>
    fun save(entry: HistoryEntry)
}

// Local time and fresh ids come from the platform.
interface HistoryClock {
    fun now(): LocalStamp
    fun newId(): String
}

// Home's sections, newest first: today, yesterday, the rest of this week (Monday start), last week, then by month.
sealed interface HistoryGroup {
    data object Today : HistoryGroup
    data object Yesterday : HistoryGroup
    data object ThisWeek : HistoryGroup
    data object LastWeek : HistoryGroup
    data class Month(val year: Int, val month: Int) : HistoryGroup
}

object HistoryGrouping {
    fun groupOf(stamp: LocalStamp, today: LocalStamp): HistoryGroup {
        val day = stamp.epochDay
        val now = today.epochDay
        val weekStart = now - CivilDate.mondayIndex(now)
        return when {
            day >= now -> HistoryGroup.Today
            day == now - 1 -> HistoryGroup.Yesterday
            day >= weekStart -> HistoryGroup.ThisWeek
            day >= weekStart - 7 -> HistoryGroup.LastWeek
            else -> {
                val (y, m) = CivilDate.yearMonth(day)
                HistoryGroup.Month(y, m)
            }
        }
    }

    fun group(entries: List<HistoryEntry>, today: LocalStamp): List<Pair<HistoryGroup, List<HistoryEntry>>> =
        entries.sortedByDescending { it.stamp }.groupBy { groupOf(it.stamp, today) }.toList()
}

// Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's days_from_civil), and back.
object CivilDate {
    fun parse(iso: String): Long {
        val (y, m, d) = iso.split("-").map { it.toInt() }
        return fromCivil(y, m, d)
    }

    fun fromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    // Returns year, month, day.
    fun toCivil(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }

    fun yearMonth(epochDay: Long): Pair<Int, Int> = toCivil(epochDay).let { it.first to it.second }

    // 0 = Sunday … 6 = Saturday. 1970-01-01 was a Thursday.
    fun weekday(epochDay: Long): Int = (((epochDay % 7) + 7 + 4) % 7).toInt()

    // Days since the most recent Monday: 0 = Monday … 6 = Sunday.
    fun mondayIndex(epochDay: Long): Int = (weekday(epochDay) + 6) % 7
}
