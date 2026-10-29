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
