package dev.cocoa.speech.logic

import dev.cocoa.speech.model.Segment

// Descriptive pace, pause, and filler data from transcript timing. Never a score.
data class PaceSample(
    val tStart: Double,
    val tEnd: Double,
    val syllablesPerSecond: Double,
    val fillers: Int,
)

data class Pause(val t: Double, val seconds: Double)

data class PaceStrip(val samples: List<PaceSample>, val pauses: List<Pause>)

object PaceAnalyzer {
    // Gaps between segments at least this long count as pauses.
    private const val PAUSE_SECONDS = 1.0

    // Standalone Korean filler words. "그" and "저" are left out: they are usually real words.
    private val fillerWords = setOf("음", "으음", "어", "어어", "아", "에", "엄")

    fun analyze(segments: List<Segment>): PaceStrip {
        val sorted = segments.sortedBy { it.tStart }
        val samples = sorted.map { s ->
            val duration = s.tEnd - s.tStart
            PaceSample(
                tStart = s.tStart,
                tEnd = s.tEnd,
                syllablesPerSecond = if (duration > 0) s.text.count { it.isLetterOrDigit() } / duration else 0.0,
                fillers = countFillers(s.text),
            )
        }
        val pauses = sorted.zipWithNext()
            .map { (a, b) -> Pause(t = a.tEnd, seconds = b.tStart - a.tEnd) }
            .filter { it.seconds >= PAUSE_SECONDS }
        return PaceStrip(samples, pauses)
    }

    fun countFillers(text: String): Int =
        text.split(' ').count { QuoteValidator.normalize(it) in fillerWords }
}
