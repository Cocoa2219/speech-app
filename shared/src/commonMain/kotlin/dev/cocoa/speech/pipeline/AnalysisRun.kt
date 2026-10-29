package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.GeminiException
import dev.cocoa.speech.logic.PersonaRules
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.logic.VideoStatus
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import dev.cocoa.speech.record.VideoInput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

// VIDEO runs alongside TRANSCRIBE; it is reported as a stage only while the run waits for it after transcription.
enum class Stage { TRANSCRIBE, VIDEO, ANALYZE, WRITE }

class StageFailure(val stage: Stage, val noSpeech: Boolean = false, cause: Throwable? = null) :
    Exception("${stage.name} failed", cause)

// One recording through the pipeline. Each stage's result is kept, so a retry after a failure resumes at the failed
// stage instead of re-sending the audio. Video is optional and never fails the run: if it fails, the run falls back to
// audio-only results (CLAUDE.md failure handling) and the report says so. Logs carry stage names, timings, and
// counts only, never transcript text.
class AnalysisRun(
    private val audio: ByteArray,
    private val mimeType: String,
    private val transcriber: Transcriber,
    private val analyzer: Analyzer,
    private val writer: PersonaWriter,
    private val video: VideoInput? = null,
    private val describer: SceneDescriber? = null,
    private val log: (String) -> Unit = ::println,
) {
    val stages: List<Stage> = if (video != null && describer != null) Stage.entries else Stage.entries - Stage.VIDEO

    private var segments: List<Segment>? = null
    private var scenes: List<Scene>? = null
    private var videoStatus = if (video != null && describer != null) null else VideoStatus.NONE
    private var analysis: AnalyzerOutput? = null
    private var events: List<PersonaEvent>? = null

    // Runs every stage that has no result yet. onStage is called as each one starts.
    suspend fun run(onStage: (Stage) -> Unit): ResultsReport = coroutineScope {
        // If transcription fails, leaving this scope cancels the video job; its upload is deleted on the way out.
        val videoJob = if (videoStatus == null) async { describeVideo() } else null

        val segments = segments ?: stage(Stage.TRANSCRIBE, onStage) {
            transcriber.transcribe(audio, mimeType).also {
                log("AnalysisRun: ${it.size} segments")
                if (it.isEmpty()) throw StageFailure(Stage.TRANSCRIBE, noSpeech = true)
            }
        }.also { segments = it }

        if (videoJob != null) {
            onStage(Stage.VIDEO)
            videoJob.await()
        }

        val analysis = analysis ?: stage(Stage.ANALYZE, onStage) {
            analyzer.analyze(segments, scenes.orEmpty())
        }.also { analysis = it }

        val events = events ?: stage(Stage.WRITE, onStage) {
            val triggers = PersonaRules.triggers(analysis)
            log("AnalysisRun: ${triggers.size} triggers")
            writer.write(segments, triggers)
        }.also { events = it }

        ResultsReport.build(analysis, events, videoStatus ?: VideoStatus.NONE)
    }

    private suspend fun describeVideo() {
        val mark = TimeSource.Monotonic.markNow()
        try {
            scenes = describer!!.describe(video!!.read(), video.mimeType)
            videoStatus = VideoStatus.USED
            log("AnalysisRun: VIDEO done in ${mark.elapsedNow()}, ${scenes!!.size} scenes")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            videoStatus = VideoStatus.FAILED
            log("AnalysisRun: VIDEO failed after ${mark.elapsedNow()}${detail(e)}; continuing audio-only")
        }
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
            log("AnalysisRun: ${stage.name} failed after ${mark.elapsedNow()}${detail(e)}")
            throw StageFailure(stage, cause = e)
        }
    }

    // Gemini's status and reason codes help diagnose; its free-text message is left out on purpose.
    private fun detail(e: Exception): String =
        ": ${e::class.simpleName}" + (e as? GeminiException)?.let { " (HTTP ${it.httpStatus}, ${it.reason})" }.orEmpty()
}
