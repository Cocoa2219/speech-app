package dev.cocoa.speech.logic

import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.Claim
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Mismatch
import dev.cocoa.speech.model.MismatchType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Premise
import dev.cocoa.speech.model.Segment
import dev.cocoa.speech.model.Step
import dev.cocoa.speech.model.Term
import dev.cocoa.speech.model.TermLevel
import dev.cocoa.speech.model.Visual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun event(persona: Persona, t: Double, quote: String, type: EventType = EventType.WHATS_THAT) =
    PersonaEvent(persona = persona, t = t, type = type, quote = quote, reason = "r", fix = "f")

class QuoteValidatorTest {
    private val analyzer = AnalyzerOutput(
        segments = listOf(Segment(0.0, 4.0, "그래서 식물은 초록색으로 보여요.")),
        visual = listOf(Visual(3.0, "명반응, 암반응, 캘빈 회로")),
    )
    private val validator = QuoteValidator(analyzer)

    @Test
    fun acceptsQuotesFromTranscriptOrScreenIgnoringPunctuation() {
        assertTrue(validator.isGrounded("초록색으로 보여요"))
        assertTrue(validator.isGrounded("식물은  초록색으로, 보여요!"))
        assertTrue(validator.isGrounded("캘빈 회로"))
    }

    @Test
    fun rejectsBlankAndInventedQuotes() {
        assertFalse(validator.isGrounded(""))
        assertFalse(validator.isGrounded("  . "))
        assertFalse(validator.isGrounded("엽록체"))
    }

    @Test
    fun filterDropsUngroundedEvents() {
        val kept = validator.filter(listOf(event(Persona.KID, 1.0, "보여요"), event(Persona.KID, 1.0, "없는 말")))
        assertEquals(listOf("보여요"), kept.map { it.quote })
    }
}

class DefinitionLedgerTest {
    @Test
    fun flagsTermsNotDefinedAtFirstUse() {
        val ledger = DefinitionLedger(
            listOf(
                Term("광합성", 0.0, TermLevel.SCHOOL, definedAt = 4.5), // defined within grace
                Term("양분", 4.5, TermLevel.EVERYDAY),                   // everyday never needs a definition
                Term("엽록체", 9.8, TermLevel.SCHOOL),                   // never defined
                Term("포도당", 20.0, TermLevel.SCHOOL, definedAt = 50.0), // defined too late
                Term("엽록체", 30.0, TermLevel.SCHOOL),                  // repeat use, already flagged once
            )
        )
        assertEquals(listOf("엽록체", "포도당"), ledger.undefinedAtFirstUse().map { it.term })
    }
}

class PersonaRulesTest {
    private val analyzer = AnalyzerOutput(
        terms = listOf(
            Term("엽록체", 9.8, TermLevel.SCHOOL),
            Term("캘빈 회로", 39.5, TermLevel.SPECIALIST),
        ),
        claims = listOf(
            Claim(4.5, "광합성은 양분을 만드는 과정이에요.", hasReason = true),
            Claim(21.0, "그래서 식물은 초록색으로 보여요.", hasReason = false),
            Claim(30.0, "그래프처럼 늘어나요.", hasReason = false, evidenceOnScreen = "증가 그래프"),
        ),
        mismatches = listOf(
            Mismatch(26.4, MismatchType.POINTING_UNNAMED, "여기 보시면"),
            Mismatch(39.0, MismatchType.DENSE_SLIDE, "명반응, 암반응"),
            Mismatch(50.0, MismatchType.SAID_NOT_SHOWN, "보시다시피"),
        ),
    )

    @Test
    fun classmateRaisesHandForUndefinedTermsAndDenseSlides() {
        assertEquals(
            listOf(EventType.UNDEFINED_TERM to "엽록체", EventType.UNDEFINED_TERM to "캘빈 회로", EventType.DENSE_SLIDE to "명반응, 암반응"),
            PersonaRules.classmate(analyzer).map { it.type to it.quote },
        )
    }

    @Test
    fun teacherSkipsClaimsBackedByTheScreen() {
        assertEquals(
            listOf(21.0, 50.0),
            PersonaRules.teacher(analyzer).map { it.t },
        )
    }

    @Test
    fun kidAsksWhyAndWhatsThat() {
        val kid = PersonaRules.kid(analyzer)
        assertEquals(
            setOf(
                EventType.WHY_QUESTION to "그래서 식물은 초록색으로 보여요.",
                EventType.WHY_QUESTION to "그래프처럼 늘어나요.",
                EventType.WHATS_THAT to "캘빈 회로",
                EventType.WHATS_THAT to "여기 보시면",
            ),
            kid.map { it.type to it.quote }.toSet(),
        )
        assertFalse(kid.any { it.quote == "엽록체" }) // school-level terms are the classmate's job
    }

    @Test
    fun triggersAreSortedByTime() {
        val times = PersonaRules.triggers(analyzer).map { it.t }
        assertEquals(times.sorted(), times)
    }
}

class SkippedStepTest {
    private val segments = listOf(
        Segment(0.0, 5.0, "지구는 태양 주위를 돌아요."),
        Segment(5.0, 10.0, "그래서 여름에는 더워요."),
        Segment(15.0, 20.0, "자전축은 기울어져 있어요."),
    )

    private fun step(vararg requires: Premise) = Step("s1", 5.0, "여름에는 더워요", requires.toList())

    private fun skipped(vararg requires: Premise) =
        PersonaRules.triggers(AnalyzerOutput(segments = segments, steps = listOf(step(*requires))))
            .filter { it.type == EventType.SKIPPED_STEP }

    @Test
    fun premiseNeverStatedRaisesHand() {
        val flagged = skipped(Premise("자전축이 기울어져 있다"))
        assertEquals(listOf(Persona.CLASSMATE to "여름에는 더워요"), flagged.map { it.persona to it.quote })
        assertEquals(5.0, flagged.single().t)
    }

    @Test
    fun premiseStatedAfterTheClaimRaisesHand() {
        assertEquals(1, skipped(Premise("자전축이 기울어져 있다", 15.0, "자전축은 기울어져 있어요")).size)
    }

    @Test
    fun premiseStatedBeforeTheClaimDoesNot() {
        assertTrue(skipped(Premise("지구가 태양 주위를 돈다", 0.0, "태양 주위를 돌아요")).isEmpty())
    }

    @Test
    fun anyLateOrMissingPremiseIsEnough() {
        val flagged = skipped(
            Premise("지구가 태양 주위를 돈다", 0.0, "태양 주위를 돌아요"),
            Premise("자전축이 기울어져 있다", 15.0, "자전축은 기울어져 있어요"),
        )
        assertEquals(1, flagged.size)
    }

    @Test
    fun stepWithClaimQuoteNotInTranscriptIsDropped() {
        val analyzer = AnalyzerOutput(segments = segments, steps = listOf(Step("s1", 5.0, "겨울에는 추워요", listOf(Premise("x")))))
        assertTrue(PersonaRules.triggers(analyzer).isEmpty())
    }

    @Test
    fun onScreenTextDoesNotGroundStepQuotes() {
        val analyzer = AnalyzerOutput(visual = listOf(Visual(5.0, "여름에는 더워요")))
        assertTrue(QuoteValidator(analyzer, log = {}).groundSteps(listOf(step())).isEmpty())
    }
}

class StatedQuoteGroundingTest {
    private val segments = listOf(
        Segment(0.0, 5.0, "지구는 태양 주위를 돌아요."),
        Segment(5.0, 10.0, "그래서 여름에는 더워요."),
        Segment(30.0, 35.0, "자전축은 23.5도 기울어져 있어요."),
    )
    private val logs = mutableListOf<String>()
    private val validator = QuoteValidator(AnalyzerOutput(segments = segments), log = { logs += it })

    private fun ground(premise: Premise) =
        validator.groundSteps(listOf(Step("s7", 40.0, "그래서 여름에는 더워요", listOf(premise)))).single().requires

    @Test
    fun exactMatchIsKept() {
        val p = Premise("지구가 태양 주위를 돈다", 0.0, "지구는 태양 주위를 돌아요")
        assertEquals(listOf(p), ground(p))
        assertTrue(logs.isEmpty())
    }

    @Test
    fun spacingPunctuationAndQuoteMarksAreIgnored() {
        val p = Premise("자전축이 기울어져 있다", 32.0, "“자전축은, 23.5도  기울어져있어요!”")
        assertEquals(listOf(p), ground(p))
    }

    @Test
    fun quoteMayCrossASegmentBoundary() {
        val p = Premise("지구가 태양 주위를 돈다", 4.0, "돌아요 그래서")
        assertEquals(listOf(p), ground(p))
    }

    @Test
    fun trueParaphraseIsDroppedNotFlagged() {
        val requires = ground(Premise("지구가 태양 주위를 돈다", 0.0, "지구가 태양을 공전해요"))
        assertTrue(requires.isEmpty())
        assertEquals(listOf("QuoteValidator: dropped premise 0 of step s7 (stated_quote not found within ±10s of 0.0s)"), logs)
    }

    @Test
    fun matchOutsideTheTimeWindowIsDropped() {
        // The words exist at 30-35 s, but stated_at claims 2 s, so the ±10 s window (−8 to 12 s) misses them.
        assertTrue(ground(Premise("자전축이 기울어져 있다", 2.0, "자전축은 23.5도 기울어져 있어요")).isEmpty())
    }

    @Test
    fun statedAtWithoutQuoteIsDropped() {
        assertTrue(ground(Premise("자전축이 기울어져 있다", 32.0, null)).isEmpty())
    }

    @Test
    fun droppedPremiseRaisesNoHand() {
        val analyzer = AnalyzerOutput(
            segments = segments,
            steps = listOf(Step("s7", 40.0, "그래서 여름에는 더워요", listOf(Premise("x", 0.0, "지구가 태양을 공전해요")))),
        )
        assertTrue(PersonaRules.triggers(analyzer).none { it.type == EventType.SKIPPED_STEP })
    }

    @Test
    fun claimQuoteUsesNormalizedMatching() {
        val kept = validator.groundSteps(listOf(Step("s1", 5.0, "여름에는, 더워요!", emptyList())))
        assertEquals(1, kept.size)
    }

    @Test
    fun logsNeverContainTranscriptText() {
        validator.groundSteps(listOf(Step("s9", 5.0, "겨울에는 추워요", emptyList())))
        assertEquals(listOf("QuoteValidator: dropped step s9 at 5.0s (claim_quote not in transcript)"), logs)
    }
}

class FixRankingTest {
    @Test
    fun dedupeKeepsOnePerListenerTypeAndQuote() {
        val events = listOf(
            event(Persona.KID, 10.0, "캘빈 회로"),
            event(Persona.KID, 10.5, "캘빈 회로."),
            event(Persona.CLASSMATE, 10.0, "캘빈 회로"),
        )
        assertEquals(2, FixRanking.dedupe(events).size)
    }

    @Test
    fun ranksSpotsByHowManyListenersTheyHelp() {
        val events = listOf(
            event(Persona.KID, 5.0, "엽록체"),
            event(Persona.TEACHER, 5.0, "엽록체", EventType.NO_EVIDENCE),
            event(Persona.KID, 40.0, "캘빈 회로"),
            event(Persona.CLASSMATE, 39.0, "명반응, 암반응, 캘빈 회로", EventType.DENSE_SLIDE),
            event(Persona.TEACHER, 41.0, "캘빈 회로를 거쳐서", EventType.NO_EVIDENCE),
            event(Persona.KID, 55.0, "숲"),
        )
        val ranked = FixRanking.rank(events)

        assertEquals(3, ranked.size)
        assertEquals(39.0, ranked[0].t)
        assertEquals(setOf(Persona.KID, Persona.CLASSMATE, Persona.TEACHER), ranked[0].personas)
        assertEquals(5.0, ranked[1].t)
        assertEquals(55.0, ranked[2].t)
    }

    @Test
    fun eventOverlappingTwoSpotsMergesThem() {
        val ranked = FixRanking.rank(
            listOf(
                event(Persona.CLASSMATE, 39.0, "명반응, 암반응, 캘빈 회로", EventType.DENSE_SLIDE),
                event(Persona.CLASSMATE, 39.5, "캘빈 회로를 거쳐서", EventType.SKIPPED_STEP),
                event(Persona.KID, 39.5, "캘빈 회로"),
            )
        )
        assertEquals(1, ranked.size)
        assertEquals(3, ranked.single().events.size)
    }

    @Test
    fun spotsFarApartInTimeStaySeparate() {
        val ranked = FixRanking.rank(listOf(event(Persona.KID, 5.0, "엽록체"), event(Persona.TEACHER, 30.0, "엽록체")))
        assertEquals(2, ranked.size)
    }

    @Test
    fun heatmapCountsEventsPerBin() {
        val events = listOf(event(Persona.KID, 0.0, "a"), event(Persona.KID, 59.0, "b"), event(Persona.KID, 60.0, "c"))
        assertEquals(listOf(1, 0, 0, 2), FixRanking.heatmap(events, durationSeconds = 60.0, bins = 4))
    }
}

class PaceAnalyzerTest {
    @Test
    fun measuresPaceFillersAndPauses() {
        val strip = PaceAnalyzer.analyze(
            listOf(
                Segment(0.0, 2.0, "음, 광합성은"),   // 음 + 광합성은 = 5 syllables in 2 s
                Segment(4.0, 5.0, "어 그 과정이에요"), // 어 counts; 그 does not
            )
        )
        assertEquals(2.5, strip.samples[0].syllablesPerSecond)
        assertEquals(listOf(1, 1), strip.samples.map { it.fillers })
        assertEquals(listOf(Pause(t = 2.0, seconds = 2.0)), strip.pauses)
        // 5 + 7 syllables over 3 s of speech; the 2 s pause does not count.
        assertEquals(4.0, strip.averageSyllablesPerSecond)
    }

    @Test
    fun countsEnglishWordsBySyllableNotLetter() {
        assertEquals(4 + 5, PaceAnalyzer.countSyllables("광합성은 photosynthesis")) // not 4 + 14
        assertEquals(3 + 1, PaceAnalyzer.countSyllables("DNA 는")) // all caps is read letter by letter
        assertEquals(1, PaceAnalyzer.countSyllables("rhythm")) // a word with only "y" still counts once
        assertEquals(3, PaceAnalyzer.countSyllables("2학년")) // digits count one each
    }

    @Test
    fun averageIsZeroWithoutTimedSpeech() {
        assertEquals(0.0, PaceAnalyzer.analyze(listOf(Segment(3.0, 3.0, "음"))).averageSyllablesPerSecond)
        assertEquals(0.0, PaceAnalyzer.analyze(emptyList()).averageSyllablesPerSecond)
    }
}
