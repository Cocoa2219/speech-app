package dev.cocoa.speech.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesAnalyzerOutputWithSnakeCaseKeys() {
        val raw = """
            {
              "segments": [{"t_start": 0.0, "t_end": 4.2, "text": "광합성은 빛으로 양분을 만드는 과정이에요."}],
              "terms": [{"term": "엽록체", "t": 12.0, "level": "school", "defined_at": null}],
              "claims": [{"t": 20.5, "text": "그래서 식물은 초록색이에요.", "has_reason": false, "evidence_on_screen": null}],
              "visual": [{"t": 18.0, "summary": "잎 단면 그림", "slide_index": 2}],
              "mismatches": [{"t": 33.0, "type": "pointing_unnamed", "quote": "여기 보면"}]
            }
        """.trimIndent()

        val out = json.decodeFromString<AnalyzerOutput>(raw)

        assertEquals(4.2, out.segments.single().tEnd)
        assertEquals(TermLevel.SCHOOL, out.terms.single().level)
        assertNull(out.terms.single().definedAt)
        assertEquals(false, out.claims.single().hasReason)
        assertEquals(2, out.visual.single().slideIndex)
        assertEquals(MismatchType.POINTING_UNNAMED, out.mismatches.single().type)
    }

    @Test
    fun personaEventRoundTrips() {
        val event = PersonaEvent(
            persona = Persona.KID,
            t = 42.0,
            type = EventType.WHY_QUESTION,
            quote = "그래서 식물은 초록색이에요",
            reason = "The claim has no reason.",
            fix = "Say why chlorophyll makes leaves look green.",
            question = "왜 초록색이에요?",
        )

        val encoded = json.encodeToString(PersonaEvent.serializer(), event)

        assertEquals(true, "\"why_question\"" in encoded)
        assertEquals(event, json.decodeFromString<PersonaEvent>(encoded))
    }
}
