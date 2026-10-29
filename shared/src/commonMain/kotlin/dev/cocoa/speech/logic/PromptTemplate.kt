package dev.cocoa.speech.logic

import dev.cocoa.speech.setup.Grade

object PromptTemplate {
    // Fills {{grade}} and {{duration_seconds}} in prompts/analyzer.md. A blank or unknown grade falls back to the
    // default, so the analyzer never runs without one.
    fun analyzer(template: String, grade: String?, durationSeconds: Double, mockMode: Boolean = false): String =
        template
            .replace("{{grade}}", Grade.resolve(grade, mockMode))
            .replace("{{duration_seconds}}", durationSeconds.toInt().toString())
}
