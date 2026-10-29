package dev.cocoa.speech.logic

import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultsReportTest {
    private val analyzer = AnalyzerOutput(
        segments = listOf(
            Segment(0.0, 10.0, "엽록체에서 일어나요."),
            Segment(10.0, 20.0, "그래서 초록색으로 보여요."),
        ),
    )

    private fun event(persona: Persona, t: Double, quote: String, question: String? = null) = PersonaEvent(
        persona = persona, t = t, type = EventType.WHATS_THAT, quote = quote, reason = "r", fix = "f", question = question,
    )

    @Test
    fun dropsUngroundedEventsAndKeepsKidQuestionsOnly() {
        val report = ResultsReport.build(
            analyzer,
            listOf(
                event(Persona.KID, 12.0, "초록색으로 보여요", question = "왜 초록색이에요?"),
                event(Persona.CLASSMATE, 2.0, "엽록체"),
                event(Persona.TEACHER, 5.0, "지어낸 말"),
                event(Persona.KID, 3.0, "엽록체", question = " "),
            ),
        )
        assertEquals(listOf(2.0, 3.0, 12.0), report.events.map { it.t })
        assertEquals(listOf("왜 초록색이에요?"), report.questions.map { it.question })
        assertEquals(20.0, report.durationSeconds)
        assertEquals(3, report.heatmap.sum())
    }

    @Test
    fun topFixesAreCappedAtThree() {
        // Distinct quotes 4 s apart: five separate spots.
        val events = listOf("엽록체", "일어나요", "그래서", "초록색", "보여요")
            .mapIndexed { i, quote -> event(Persona.KID, i * 4.0, quote) }
        assertEquals(3, ResultsReport.build(analyzer, events).topFixes.size)
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:09", ResultsReport.formatTime(9.8))
        assertEquals("1:05", ResultsReport.formatTime(65.0))
        assertEquals("0:00", ResultsReport.formatTime(-1.0))
    }
}
