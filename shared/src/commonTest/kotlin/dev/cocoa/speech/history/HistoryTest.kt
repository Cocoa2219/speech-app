package dev.cocoa.speech.history

import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.logic.VideoStatus
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import dev.cocoa.speech.record.ReplayMedia
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {
    // 2026-10-30 is a Friday.
    private val today = LocalStamp("2026-10-30", 16 * 60)

    private fun at(date: String) = HistoryGrouping.groupOf(LocalStamp(date, 600), today)

    @Test
    fun civilDatesRoundTripAndKnowTheirWeekday() {
        assertEquals(0L, CivilDate.parse("1970-01-01"))
        assertEquals(4, CivilDate.weekday(0)) // Thursday
        for (iso in listOf("2024-02-29", "2026-10-30", "2000-01-01", "1999-12-31", "2026-03-01")) {
            val (y, m, d) = CivilDate.toCivil(CivilDate.parse(iso))
            assertEquals(iso, "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}")
        }
        assertEquals(5, CivilDate.weekday(CivilDate.parse("2026-10-30"))) // Friday
        assertEquals(0, CivilDate.mondayIndex(CivilDate.parse("2026-10-26"))) // Monday
        assertEquals(6, CivilDate.mondayIndex(CivilDate.parse("2026-11-01"))) // Sunday
    }

    @Test
    fun groupsByTodayYesterdayWeekThenMonth() {
        assertEquals(HistoryGroup.Today, at("2026-10-30"))
        assertEquals(HistoryGroup.Yesterday, at("2026-10-29"))
        assertEquals(HistoryGroup.ThisWeek, at("2026-10-26")) // Monday of this week
        assertEquals(HistoryGroup.LastWeek, at("2026-10-25")) // Sunday before
        assertEquals(HistoryGroup.LastWeek, at("2026-10-19"))
        assertEquals(HistoryGroup.Month(2026, 10), at("2026-10-18"))
        assertEquals(HistoryGroup.Month(2026, 9), at("2026-09-26"))
        assertEquals(HistoryGroup.Month(2025, 12), at("2025-12-31"))
    }

    @Test
    fun yesterdayWinsOverLastWeekOnAMonday() {
        val monday = LocalStamp("2026-10-26", 600)
        assertEquals(HistoryGroup.Yesterday, HistoryGrouping.groupOf(LocalStamp("2026-10-25", 600), monday))
    }

    @Test
    fun groupsKeepNewestFirst() {
        val report = report()
        fun entry(id: String, date: String, minute: Int) =
            HistoryEntry.of(id, "t", "중학교 2학년", "", LocalStamp(date, minute), report, replay = null)
        val groups = HistoryGrouping.group(
            listOf(entry("a", "2026-10-29", 600), entry("b", "2026-10-30", 500), entry("c", "2026-10-30", 900)),
            today,
        )
        assertEquals(listOf(HistoryGroup.Today, HistoryGroup.Yesterday), groups.map { it.first })
        assertEquals(listOf("c", "b"), groups[0].second.map { it.id })
    }

    @Test
    fun blankTopicFallsBackToTheFirstWordsSaid() {
        val entry = HistoryEntry.of("a", "  ", "중학교 2학년", "", today, report(), replay = null)
        assertEquals("오늘은 광합성에 대해서 설명해 볼게요.".take(20).trimEnd() + "…", entry.topic)
    }

    @Test
    fun entrySurvivesJsonAndRebuildsTheSameReport() {
        val original = report()
        val entry = HistoryEntry.of("a", "광합성", "중학교 2학년", "메모", today, original, replay = ReplayMedia("/files/replay/last.mp4", isVideo = true))
        val back = AppJson.decodeFromString<HistoryEntry>(AppJson.encodeToString(entry))
        assertEquals(entry, back)
        val restored = back.report()
        assertEquals(original.events, restored.events)
        assertEquals(original.fixes, restored.fixes)
        assertEquals(original.pace, restored.pace)
        assertEquals(VideoStatus.FAILED, restored.video)
        assertEquals(listOf(Persona.CLASSMATE, Persona.KID), back.raised)
        assertEquals(2, back.fixCount)
        assertEquals(ReplayMedia("/files/replay/last.mp4", isVideo = true).path, back.replay?.path)
    }

    private fun report(): ResultsReport {
        val analyzer = AnalyzerOutput(
            segments = listOf(
                Segment(0.0, 6.0, "오늘은 광합성에 대해서 설명해 볼게요."),
                Segment(6.0, 14.0, "엽록체에서 일어나요."),
                Segment(14.0, 22.0, "그래서 초록색으로 보여요."),
            ),
        )
        fun event(persona: Persona, t: Double, quote: String) =
            PersonaEvent(persona = persona, t = t, type = EventType.WHATS_THAT, quote = quote, reason = "r", fix = "f")
        return ResultsReport.build(
            analyzer,
            listOf(event(Persona.CLASSMATE, 7.0, "엽록체"), event(Persona.KID, 18.0, "초록색으로 보여요")),
            VideoStatus.FAILED,
        )
    }
}
