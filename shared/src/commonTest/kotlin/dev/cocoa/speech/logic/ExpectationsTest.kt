package dev.cocoa.speech.logic

import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpectationsTest {
    private fun event(persona: Persona, type: EventType, quote: String) = PersonaEvent(persona, 0.0, type, quote, "r", "f")

    private val expectations = AppJson.decodeFromString<Expectations>(
        """{"about":"ignored","must":[
            {"id":"term","persona":"classmate","type":"undefined_term","quote_contains":"공전 궤도면"},
            {"id":"skip","persona":"classmate","type":"skipped_step","quote_contains":"호주에서는 크리스마스를 여름에"}],
          "must_not":[{"id":"intro","quote_contains":"오늘은 계절이 생기는 이유를"}]}""",
    )

    @Test
    fun reportsMetMissedAndViolatedById() {
        val result = ExpectationCheck.check(
            expectations,
            listOf(
                event(Persona.CLASSMATE, EventType.UNDEFINED_TERM, "공전궤도면"), // spacing differs
                event(Persona.TEACHER, EventType.NO_EVIDENCE, "음, 오늘은 계절이 생기는 이유를 설명해 볼게요."),
            ),
        )
        assertEquals(listOf("term"), result.met)
        assertEquals(listOf("skip"), result.missed)
        assertEquals(listOf("intro"), result.violated)
        assertEquals("Expectations: 1/2 met; missed=[skip]; violated=[intro]", result.summary())
    }

    @Test
    fun matchesEitherDirectionButNotOnTinyQuotesOrWrongPersona() {
        val skip = expectations.must[1]
        // The event quote may be the whole sentence or a shorter span of it.
        assertTrue(ExpectationCheck.matches(skip, event(Persona.CLASSMATE, EventType.SKIPPED_STEP, "그래서 호주에서는 크리스마스를 여름에 보내요")))
        assertTrue(ExpectationCheck.matches(skip, event(Persona.CLASSMATE, EventType.SKIPPED_STEP, "호주에서는 크리스마스")))
        assertFalse(ExpectationCheck.matches(skip, event(Persona.CLASSMATE, EventType.SKIPPED_STEP, "호주")))
        assertFalse(ExpectationCheck.matches(skip, event(Persona.KID, EventType.SKIPPED_STEP, "호주에서는 크리스마스를 여름에")))
    }
}
