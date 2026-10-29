package dev.cocoa.speech.logic

import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment

// Everything the results screen shows, computed once from analyzer output and persona events.
data class ResultsReport(
    val durationSeconds: Double,
    val segments: List<Segment>,
    val events: List<PersonaEvent>,
    val fixes: List<RankedFix>,
    val questions: List<PersonaEvent>,
    val heatmap: List<Int>,
    val pace: PaceStrip,
) {
    val topFixes: List<RankedFix> get() = fixes.take(TOP_FIXES)

    companion object {
        const val TOP_FIXES = 3
        const val HEATMAP_BINS = 30

        // Ungrounded events are dropped here, so nothing downstream can show a hand-raise without evidence.
        fun build(analyzer: AnalyzerOutput, events: List<PersonaEvent>): ResultsReport {
            val grounded = FixRanking.dedupe(QuoteValidator(analyzer).filter(events)).sortedBy { it.t }
            val duration = analyzer.segments.maxOfOrNull { it.tEnd } ?: 0.0
            return ResultsReport(
                durationSeconds = duration,
                segments = analyzer.segments.sortedBy { it.tStart },
                events = grounded,
                fixes = FixRanking.rank(grounded),
                questions = grounded.filter { it.persona == Persona.KID && !it.question.isNullOrBlank() },
                heatmap = FixRanking.heatmap(grounded, duration, HEATMAP_BINS),
                pace = PaceAnalyzer.analyze(analyzer.segments),
            )
        }

        fun formatTime(seconds: Double): String {
            val s = seconds.toInt().coerceAtLeast(0)
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }
    }
}
