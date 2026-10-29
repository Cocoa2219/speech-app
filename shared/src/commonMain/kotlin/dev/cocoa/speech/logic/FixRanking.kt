package dev.cocoa.speech.logic

import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import kotlin.math.abs

// One spot in the talk to fix, with every listener event that points at it.
data class RankedFix(
    val t: Double,
    val quote: String,
    val events: List<PersonaEvent>,
) {
    val personas: Set<Persona> get() = events.map { it.persona }.toSet()
}

object FixRanking {
    // Events this close in time whose quotes overlap are the same spot.
    private const val SAME_SPOT_SECONDS = 3.0

    // Same listener, same type, same quote: keep the first.
    fun dedupe(events: List<PersonaEvent>): List<PersonaEvent> =
        events.distinctBy { Triple(it.persona, it.type, QuoteValidator.normalize(it.quote)) }

    // Groups events by spot and ranks spots by how many distinct listeners they help, then by event count, then time.
    fun rank(events: List<PersonaEvent>): List<RankedFix> {
        val groups = mutableListOf<MutableList<PersonaEvent>>()
        for (event in dedupe(events).sortedBy { it.t }) {
            // An event that overlaps several spots joins them into one (e.g. "캘빈 회로" bridges a slide and a sentence).
            val matching = groups.filter { g -> g.any { sameSpot(it, event) } }
            groups.removeAll(matching)
            groups.add((matching.flatten() + event).toMutableList())
        }
        return groups
            .map { g -> RankedFix(t = g.minOf { it.t }, quote = g.maxBy { it.quote.length }.quote, events = g) }
            .sortedWith(
                compareByDescending<RankedFix> { it.personas.size }
                    .thenByDescending { it.events.size }
                    .thenBy { it.t }
            )
    }

    private fun sameSpot(a: PersonaEvent, b: PersonaEvent): Boolean {
        if (abs(a.t - b.t) > SAME_SPOT_SECONDS) return false
        val qa = QuoteValidator.normalize(a.quote)
        val qb = QuoteValidator.normalize(b.quote)
        return qa in qb || qb in qa
    }

    // Event counts per equal-width time bin, for the heatmap bar.
    fun heatmap(events: List<PersonaEvent>, durationSeconds: Double, bins: Int): List<Int> {
        val counts = MutableList(bins) { 0 }
        if (durationSeconds <= 0.0 || bins <= 0) return counts
        for (event in events) {
            val i = (event.t / durationSeconds * bins).toInt().coerceIn(0, bins - 1)
            counts[i]++
        }
        return counts
    }
}
