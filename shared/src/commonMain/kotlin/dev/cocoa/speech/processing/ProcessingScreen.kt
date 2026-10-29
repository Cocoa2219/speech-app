package dev.cocoa.speech.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.pipeline.AnalysisRun
import dev.cocoa.speech.pipeline.Stage
import dev.cocoa.speech.pipeline.StageFailure
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.processing_failed
import speech_app.shared.generated.resources.processing_hint
import speech_app.shared.generated.resources.processing_home
import speech_app.shared.generated.resources.processing_no_speech
import speech_app.shared.generated.resources.processing_rerecord
import speech_app.shared.generated.resources.processing_title
import speech_app.shared.generated.resources.results_retry
import speech_app.shared.generated.resources.stage_analyze
import speech_app.shared.generated.resources.stage_done
import speech_app.shared.generated.resources.stage_failed
import speech_app.shared.generated.resources.stage_transcribe
import speech_app.shared.generated.resources.stage_video
import speech_app.shared.generated.resources.stage_write
import kotlin.coroutines.cancellation.CancellationException

// Runs the pipeline for one recording. The run is created once and kept across retries, so a retry resumes at the
// failed stage. createRun reads the prompts; if that fails it is reported as a transcription failure and retried.
@Composable
fun ProcessingRoute(
    stages: List<Stage>,
    createRun: suspend () -> AnalysisRun,
    onDone: (ResultsReport) -> Unit,
    onRerecord: () -> Unit,
    onHome: () -> Unit,
) {
    var run by remember { mutableStateOf<AnalysisRun?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(Stage.TRANSCRIBE) }
    var failure by remember { mutableStateOf<StageFailure?>(null) }

    LaunchedEffect(attempt) {
        failure = null
        try {
            val r = run ?: createRun().also { run = it }
            onDone(r.run { current = it })
        } catch (e: CancellationException) {
            throw e
        } catch (e: StageFailure) {
            failure = e
        } catch (e: Exception) {
            failure = StageFailure(current, cause = e)
        }
    }

    ProcessingScreen(
        stages = stages,
        current = current,
        failure = failure,
        onRetry = { attempt++ },
        onRerecord = onRerecord,
        onHome = onHome,
    )
}

@Composable
fun ProcessingScreen(
    stages: List<Stage>,
    current: Stage,
    failure: StageFailure?,
    onRetry: () -> Unit,
    onRerecord: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.processing_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(Res.string.processing_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (stage in stages) {
            StageRow(stage, current, failed = failure != null && stage == current)
        }
        if (failure != null) {
            Text(
                stringResource(if (failure.noSpeech) Res.string.processing_no_speech else Res.string.processing_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.weight(1f))
        if (failure != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.processing_home))
                }
                if (failure.noSpeech) {
                    Button(onClick = onRerecord, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.processing_rerecord))
                    }
                } else {
                    Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.results_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(stage: Stage, current: Stage, failed: Boolean) {
    val done = stage.ordinal < current.ordinal
    val active = stage == current && !failed
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (active) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(
            stringResource(stageLabel(stage)),
            style = MaterialTheme.typography.titleMedium,
            color = if (done || active || failed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        when {
            done -> Text(stringResource(Res.string.stage_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            failed -> Text(stringResource(Res.string.stage_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun stageLabel(stage: Stage) = when (stage) {
    Stage.TRANSCRIBE -> Res.string.stage_transcribe
    Stage.VIDEO -> Res.string.stage_video
    Stage.ANALYZE -> Res.string.stage_analyze
    Stage.WRITE -> Res.string.stage_write
}
