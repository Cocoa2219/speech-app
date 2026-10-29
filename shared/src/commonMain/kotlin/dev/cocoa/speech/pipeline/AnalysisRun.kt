package dev.cocoa.speech.pipeline

import dev.cocoa.speech.logic.PersonaRules
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

enum class Stage { TRANSCRIBE, ANALYZE, WRITE }

class StageFailure(val stage: Stage, val noSpeech: Boolean = false, cause: Throwable? = null) :
    Exception("${stage.name} failed", cause)

// One recording through the audio-only pipeline. Each stage's result is kept, so a retry after a failure resumes at
// the failed stage instead of re-uploading the audio. Logs carry stage names and timings only, never transcript text.
class AnalysisRun(
    private val audio: ByteArray,
    private val mimeType: String,
    private val transcriber: Transcriber,
    private val analyzer: Analyzer,
    private val writer: PersonaWriter,
    private val log: (String) -> Unit = ::println,
) {
    private var segments: List<Segment>? = null
    private var analysis: AnalyzerOutput? = null
    private var events: List<PersonaEvent>? = null

    // Runs every stage that has no result yet. onStage is called as each one starts.
    suspend fun run(onStage: (Stage) -> Unit): ResultsReport {
        val segments = segments ?: stage(Stage.TRANSCRIBE, onStage) {
            transcriber.transcribe(audio, mimeType).also {
                log("AnalysisRun: ${it.size} segments")
                if (it.isEmpty()) throw StageFailure(Stage.TRANSCRIBE, noSpeech = true)
            }
        }.also { segments = it }

        val analysis = analysis ?: stage(Stage.ANALYZE, onStage) { analyzer.analyze(segments) }.also { analysis = it }

        val events = events ?: stage(Stage.WRITE, onStage) {
            val triggers = PersonaRules.triggers(analysis)
            log("AnalysisRun: ${triggers.size} triggers")
            writer.write(segments, triggers)
        }.also { events = it }

        return ResultsReport.build(analysis, events)
    }

    private suspend fun <T> stage(stage: Stage, onStage: (Stage) -> Unit, block: suspend () -> T): T {
        onStage(stage)
        val mark = TimeSource.Monotonic.markNow()
        return try {
            block().also { log("AnalysisRun: ${stage.name} done in ${mark.elapsedNow()}") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StageFailure) {
            log("AnalysisRun: ${stage.name} failed after ${mark.elapsedNow()} (no speech)")
            throw e
        } catch (e: Exception) {
            log("AnalysisRun: ${stage.name} failed after ${mark.elapsedNow()}: ${e::class.simpleName}")
            throw StageFailure(stage, cause = e)
        }
    }
}
