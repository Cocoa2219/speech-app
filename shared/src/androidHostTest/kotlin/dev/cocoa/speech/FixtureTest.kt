package dev.cocoa.speech

import dev.cocoa.speech.logic.PersonaRules
import dev.cocoa.speech.logic.QuoteValidator
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.PersonaEvent
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val root = File("src/commonMain/composeResources/files/fixtures")

    private fun analyzer(name: String) =
        json.decodeFromString<AnalyzerOutput>(File(root, "$name/analyzer.json").readText())

    @Test
    fun photosynthesisFixtureParsesAndEveryQuoteIsGrounded() {
        val analyzer = analyzer("photosynthesis")
        val events = json.decodeFromString<List<PersonaEvent>>(File(root, "photosynthesis/events.json").readText())
        val sources = analyzer.segments.map { it.text } + analyzer.visual.map { it.summary }

        assertTrue(events.isNotEmpty())
        for (event in events) {
            assertTrue(sources.any { event.quote in it }, "Quote not grounded: ${event.quote}")
        }
        for (mismatch in analyzer.mismatches) {
            assertTrue(sources.any { mismatch.quote in it }, "Mismatch quote not grounded: ${mismatch.quote}")
        }
    }

    @Test
    fun stepQuotesInEveryFixtureAreInTheTranscript() {
        for (name in listOf("photosynthesis", "seasons")) {
            val analyzer = analyzer(name)
            assertEquals(analyzer.steps, QuoteValidator(analyzer).groundSteps(analyzer.steps), "Ungrounded step in $name")
        }
    }

    @Test
    fun seasonsFixtureFlagsNeverStatedAndStatedLateButNotStatedBefore() {
        val skipped = PersonaRules.triggers(analyzer("seasons")).filter { it.type == EventType.SKIPPED_STEP }
        assertEquals(
            listOf("여름에는 덥고 겨울에는 추워요", "적도 근처는 계절 변화가 거의 없어요"),
            skipped.map { it.quote },
        )
    }
}
