package dev.cocoa.speech.logic

import dev.cocoa.speech.model.Term
import dev.cocoa.speech.model.TermLevel

// "What has been defined so far", kept in code rather than trusted to the model.
class DefinitionLedger(terms: List<Term>) {
    // A definition this soon after first use still counts ("오늘은 광합성… 광합성은 ~이에요").
    private val graceSeconds = 10.0

    private val sorted = terms.sortedBy { it.t }

    // Terms that a listener would not know at the moment they are first used.
    fun undefinedAtFirstUse(): List<Term> {
        val seen = mutableSetOf<String>()
        return sorted.filter { term ->
            seen.add(term.term) &&
                term.level != TermLevel.EVERYDAY &&
                (term.definedAt == null || term.definedAt > term.t + graceSeconds)
        }
    }
}
