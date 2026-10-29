package dev.cocoa.speech.logic

import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.MismatchType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.TermLevel

// A spot where a listener would raise a hand. The persona model call turns each trigger into a PersonaEvent
// by writing `reason`, `fix`, and (kid only) `question`; it does not decide where hands go up.
data class PersonaTrigger(
    val persona: Persona,
    val t: Double,
    val type: EventType,
    val quote: String,
)

object PersonaRules {
    // Grounds step quotes against the transcript first; the per-persona rules assume that has been done.
    fun triggers(analyzer: AnalyzerOutput): List<PersonaTrigger> {
        val grounded = analyzer.copy(steps = QuoteValidator(analyzer).groundSteps(analyzer.steps))
        return (classmate(grounded) + teacher(grounded) + kid(grounded)).sortedBy { it.t }
    }

    // Undefined school/specialist terms, dense slides, and steps whose premises were never stated or came too late.
    fun classmate(analyzer: AnalyzerOutput): List<PersonaTrigger> {
        val terms = DefinitionLedger(analyzer.terms).undefinedAtFirstUse()
            .map { PersonaTrigger(Persona.CLASSMATE, it.t, EventType.UNDEFINED_TERM, it.term) }
        val dense = analyzer.mismatches.filter { it.type == MismatchType.DENSE_SLIDE }
            .map { PersonaTrigger(Persona.CLASSMATE, it.t, EventType.DENSE_SLIDE, it.quote) }
        val skipped = analyzer.steps
            .filter { step -> step.requires.any { it.statedAt == null || it.statedAt > step.t } }
            .map { PersonaTrigger(Persona.CLASSMATE, it.t, EventType.SKIPPED_STEP, it.claimQuote) }
        return terms + dense + skipped
    }

    // Claims with no reason and nothing on screen backing them; "as you can see" with nothing shown.
    fun teacher(analyzer: AnalyzerOutput): List<PersonaTrigger> {
        val claims = analyzer.claims.filter { !it.hasReason && it.evidenceOnScreen == null }
            .map { PersonaTrigger(Persona.TEACHER, it.t, EventType.NO_EVIDENCE, it.text) }
        val unshown = analyzer.mismatches.filter { it.type == MismatchType.SAID_NOT_SHOWN }
            .map { PersonaTrigger(Persona.TEACHER, it.t, EventType.NO_EVIDENCE, it.quote) }
        return claims + unshown
    }

    // "Why?" for claims without a reason; "What's that?" for specialist terms and unexplained visuals.
    fun kid(analyzer: AnalyzerOutput): List<PersonaTrigger> {
        val why = analyzer.claims.filter { !it.hasReason }
            .map { PersonaTrigger(Persona.KID, it.t, EventType.WHY_QUESTION, it.text) }
        val terms = DefinitionLedger(analyzer.terms).undefinedAtFirstUse()
            .filter { it.level == TermLevel.SPECIALIST }
            .map { PersonaTrigger(Persona.KID, it.t, EventType.WHATS_THAT, it.term) }
        val visuals = analyzer.mismatches
            .filter { it.type == MismatchType.POINTING_UNNAMED || it.type == MismatchType.SHOWN_NOT_SAID }
            .map { PersonaTrigger(Persona.KID, it.t, EventType.WHATS_THAT, it.quote) }
        return why + terms + visuals
    }
}
