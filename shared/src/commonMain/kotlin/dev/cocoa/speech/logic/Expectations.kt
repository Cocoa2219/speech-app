package dev.cocoa.speech.logic

import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Hand-written expectations for a sample recording (fixtures/<sample>/expected.json), used to judge a prompt or rule
// change by more than eye. Results carry expectation ids only, so they can be logged without transcript text.
@Serializable
data class Expectations(
    val must: List<Expected> = emptyList(),
    @SerialName("must_not") val mustNot: List<Expected> = emptyList(),
)

@Serializable
data class Expected(
    val id: String,
    val persona: Persona? = null,
    val type: EventType? = null,
    @SerialName("quote_contains") val quoteContains: String,
    val note: String = "",
)

data class ExpectationResult(val met: List<String>, val missed: List<String>, val violated: List<String>) {
    fun summary(): String =
        "Expectations: ${met.size}/${met.size + missed.size} met; missed=$missed; violated=$violated"
}

object ExpectationCheck {
    // Shorter normalized quotes than this never match by containment; avoids "음" matching everything.
    private const val MIN_MATCH_CHARS = 4

    fun check(expectations: Expectations, events: List<PersonaEvent>): ExpectationResult {
        val (met, missed) = expectations.must.partition { e -> events.any { matches(e, it) } }
        val violated = expectations.mustNot.filter { e -> events.any { matches(e, it) } }
        return ExpectationResult(met.map { it.id }, missed.map { it.id }, violated.map { it.id })
    }

    fun matches(expected: Expected, event: PersonaEvent): Boolean {
        if (expected.persona != null && expected.persona != event.persona) return false
        if (expected.type != null && expected.type != event.type) return false
        val want = QuoteValidator.normalize(expected.quoteContains)
        val got = QuoteValidator.normalize(event.quote)
        val shorter = minOf(want.length, got.length)
        return shorter >= MIN_MATCH_CHARS && (want in got || got in want)
    }
}
