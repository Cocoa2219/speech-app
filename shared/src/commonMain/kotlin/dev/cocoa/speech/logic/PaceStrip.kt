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

data class PaceStrip(
    val samples: List<PaceSample>,
    val pauses: List<Pause>,
    // Syllables over speaking time (pauses between segments excluded). 0 when there is no timed speech.
    val averageSyllablesPerSecond: Double = 0.0,
)

object PaceAnalyzer {
    // Gaps between segments at least this long count as pauses.
    private const val PAUSE_SECONDS = 1.0

    // Standalone Korean filler words. "그" and "저" are left out: they are usually real words.
    private val fillerWords = setOf("음", "으음", "어", "어어", "아", "에", "엄")

    private val latinWord = Regex("[A-Za-z]+")
    private val vowelGroup = Regex("[aeiouy]+")

    fun analyze(segments: List<Segment>): PaceStrip {
        val sorted = segments.sortedBy { it.tStart }
        val samples = sorted.map { s ->
            val duration = s.tEnd - s.tStart
            PaceSample(
                tStart = s.tStart,
                tEnd = s.tEnd,
                syllablesPerSecond = if (duration > 0) countSyllables(s.text) / duration else 0.0,
                fillers = countFillers(s.text),
            )
        }
        val pauses = sorted.zipWithNext()
            .map { (a, b) -> Pause(t = a.tEnd, seconds = b.tStart - a.tEnd) }
            .filter { it.seconds >= PAUSE_SECONDS }
        val timed = sorted.filter { it.tEnd > it.tStart }
        val speakingSeconds = timed.sumOf { it.tEnd - it.tStart }
        val average = if (speakingSeconds > 0) timed.sumOf { countSyllables(it.text) } / speakingSeconds else 0.0
        return PaceStrip(samples, pauses, average)
    }

    // Hangul and digits: one syllable per character. English words: vowel groups (photosynthesis -> 5), at least one
    // per word; an all-caps word is read letter by letter (DNA -> 3). Approximate, which is fine for a descriptive strip.
    fun countSyllables(text: String): Int {
        val latin = latinWord.findAll(text).sumOf { m ->
            val word = m.value
            if (word.length > 1 && word.all { it.isUpperCase() }) word.length
            else vowelGroup.findAll(word.lowercase()).count().coerceAtLeast(1)
        }
        val other = text.count { it.isLetterOrDigit() && !(it in 'A'..'Z' || it in 'a'..'z') }
        return latin + other
    }

    fun countFillers(text: String): Int =
        text.split(' ').count { QuoteValidator.normalize(it) in fillerWords }
}
