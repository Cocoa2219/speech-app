package dev.cocoa.speech.pipeline

import dev.cocoa.speech.logic.ExpectationCheck
import dev.cocoa.speech.logic.Expectations
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.model.AppJson
import speech_app.shared.generated.resources.Res

// Debug: after a run on the bundled seasons sample (tools/make-sample.ps1), logs which hand-written expectations were
// met, by id only.
object SampleCheck {
    private const val EXPECTED = "files/fixtures/sample-seasons/expected.json"

    suspend fun log(report: ResultsReport, log: (String) -> Unit = ::println) {
        val expectations = AppJson.decodeFromString<Expectations>(Res.readBytes(EXPECTED).decodeToString())
        log(ExpectationCheck.check(expectations, report.events).summary())
    }
}
