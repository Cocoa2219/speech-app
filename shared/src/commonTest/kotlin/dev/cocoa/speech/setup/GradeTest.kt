package dev.cocoa.speech.setup

import dev.cocoa.speech.logic.PromptTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradeTest {
    @Test
    fun defaultsToMiddleSchoolSecondYearAndOffersAdultDemo() {
        assertEquals("중학교 2학년", Grade.DEFAULT)
        assertTrue(Grade.DEFAULT in Grade.options)
        assertEquals("일반 성인 (demo)", Grade.options.last())
    }

    @Test
    fun resolveFallsBackToDefaultForMissingBlankOrUnknown() {
        assertEquals(Grade.DEFAULT, Grade.resolve(null))
        assertEquals(Grade.DEFAULT, Grade.resolve("  "))
        assertEquals(Grade.DEFAULT, Grade.resolve("대학원생"))
        assertEquals("고등학교 1학년", Grade.resolve("고등학교 1학년"))
    }

    @Test
    fun optionsKeepTheirOrderAndLabels() {
        assertEquals(13, Grade.options.size)
        assertEquals("초등학교 1학년", Grade.options.first())
        assertEquals("고등학교 3학년", Grade.options[11])
    }

    @Test
    fun schoolAndYearRoundTripEveryOption() {
        for (option in Grade.options) {
            assertEquals(option, Grade.of(Grade.schoolOf(option), Grade.yearOf(option)))
        }
        assertEquals(null, Grade.schoolOf(Grade.ADULT_DEMO))
    }

    @Test
    fun switchingSchoolKeepsOrClampsTheYear() {
        val (elementary, middle, high) = Grade.schools
        assertEquals("고등학교 3학년", Grade.of(high, Grade.yearOf("중학교 3학년")))
        assertEquals("중학교 3학년", Grade.of(middle, Grade.yearOf("초등학교 5학년")))
        assertEquals("초등학교 1학년", Grade.of(elementary, Grade.yearOf(Grade.ADULT_DEMO)))
    }

    @Test
    fun mockModeAlwaysUsesDefault() {
        assertEquals(Grade.DEFAULT, Grade.resolve("고등학교 1학년", mockMode = true))
    }

    @Test
    fun analyzerPromptNeverHasAnEmptyGrade() {
        val template = "audience: {{grade}}; length: {{duration_seconds}}s; again {{grade}}"
        assertEquals(
            "audience: 중학교 2학년; length: 60s; again 중학교 2학년",
            PromptTemplate.analyzer(template, grade = "", durationSeconds = 60.4),
        )
        assertEquals(
            "audience: 일반 성인 (demo); length: 180s; again 일반 성인 (demo)",
            PromptTemplate.analyzer(template, grade = Grade.ADULT_DEMO, durationSeconds = 180.0),
        )
    }
}
