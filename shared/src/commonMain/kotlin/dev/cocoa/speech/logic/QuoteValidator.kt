package dev.cocoa.speech.logic

import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import dev.cocoa.speech.model.Step

// No quote, no hand-raise. Every quote check compares normalized text (letters and digits only, lowercased), so
// spacing, punctuation, and quote-mark style never decide a match. Adjacent transcript segments are joined, so a quote
// may span a segment boundary.
//
// `log` receives step ids and times only, never transcript text (privacy rule).
class QuoteValidator(
    analyzer: AnalyzerOutput,
    private val log: (String) -> Unit = ::println,
) {
    private val segments = analyzer.segments.sortedBy { it.tStart }
    private val transcript = join(segments)
    private val visuals = analyzer.visual.map { normalize(it.summary) }

    // Transcript or on-screen text.
    fun isGrounded(quote: String): Boolean = isInTranscript(quote) || matches(quote, visuals)

    fun isInTranscript(quote: String): Boolean = matches(quote, listOf(transcript))

    // Only transcript segments overlapping [t - window, t + window].
    fun isInTranscriptNear(quote: String, t: Double, windowSeconds: Double = STATED_WINDOW_SECONDS): Boolean {
        val near = segments.filter { it.tEnd >= t - windowSeconds && it.tStart <= t + windowSeconds }
        return matches(quote, listOf(join(near)))
    }

    fun filter(events: List<PersonaEvent>): List<PersonaEvent> = events.filter { isGrounded(it.quote) }

    // Step quotes must come from the transcript; on-screen text never grounds them.
    // - claim_quote not found: the step is dropped.
    // - stated_at null: the premise was never stated and is kept (this is what raises a skipped-step hand).
    // - stated_at set but stated_quote missing or not found within ±10 s of stated_at: the premise is dropped, not
    //   flagged, because we cannot tell a paraphrase from a premise that was never said.
    fun groundSteps(steps: List<Step>): List<Step> = steps.mapNotNull { step ->
        if (!isInTranscript(step.claimQuote)) {
            log("QuoteValidator: dropped step ${step.id} at ${step.t}s (claim_quote not in transcript)")
            return@mapNotNull null
        }
        val requires = step.requires.mapIndexedNotNull { i, p ->
            when {
                p.statedAt == null -> p.copy(statedQuote = null)
                p.statedQuote != null && isInTranscriptNear(p.statedQuote, p.statedAt) -> p
                else -> {
                    log("QuoteValidator: dropped premise $i of step ${step.id} " +
                        "(stated_quote not found within ±${STATED_WINDOW_SECONDS.toInt()}s of ${p.statedAt}s)")
                    null
                }
            }
        }
        step.copy(requires = requires)
    }

    private fun matches(quote: String, haystack: List<String>): Boolean {
        val q = normalize(quote)
        return q.isNotEmpty() && haystack.any { q in it }
    }

    companion object {
        const val STATED_WINDOW_SECONDS = 10.0

        // Letters and digits only, lowercased. Drops all whitespace, punctuation, and quote marks.
        fun normalize(text: String): String = buildString {
            for (c in text) if (c.isLetterOrDigit()) append(c.lowercaseChar())
        }

        private fun join(segments: List<Segment>): String = segments.joinToString("") { normalize(it.text) }
    }
}
