package dev.cocoa.speech.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.logic.ResultsReport.Companion.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.record_cancel
import speech_app.shared.generated.resources.record_failed
import speech_app.shared.generated.resources.record_finish
import speech_app.shared.generated.resources.record_hint_idle
import speech_app.shared.generated.resources.record_min_hint
import speech_app.shared.generated.resources.record_pause
import speech_app.shared.generated.resources.record_paused
import speech_app.shared.generated.resources.record_permission_denied
import speech_app.shared.generated.resources.record_recording
import speech_app.shared.generated.resources.record_resume
import speech_app.shared.generated.resources.record_start
import speech_app.shared.generated.resources.record_time_desc
import speech_app.shared.generated.resources.record_title
import speech_app.shared.generated.resources.record_warn
import kotlin.coroutines.cancellation.CancellationException

enum class RecordPhase { IDLE, RECORDING, PAUSED }

// Owns the recorder and the timer. Leaving this screen any way other than finishing discards the recording.
@Composable
fun RecordRoute(
    recorder: AudioRecorder,
    permission: MicPermission,
    onFinished: (Recording) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(RecordPhase.IDLE) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var problem by remember { mutableStateOf<StringResource?>(null) }

    fun finish() {
        val seconds = recorder.elapsedSeconds()
        val audio = recorder.stop()
        phase = RecordPhase.IDLE
        onFinished(Recording(audio, recorder.mimeType, seconds))
    }

    DisposableEffect(recorder) { onDispose { recorder.cancel() } }

    // Ticks the timer while recording and auto-stops at the limit.
    LaunchedEffect(phase) {
        while (phase == RecordPhase.RECORDING) {
            elapsed = recorder.elapsedSeconds()
            if (elapsed >= RecordLimits.MAX_SECONDS) {
                finish()
                break
            }
            delay(100)
        }
    }

    RecordScreen(
        phase = phase,
        elapsed = { elapsed },
        problem = problem,
        onStart = {
            scope.launch {
                problem = null
                if (!permission.request()) {
                    problem = Res.string.record_permission_denied
                    return@launch
                }
                try {
                    recorder.start()
                    elapsed = 0.0
                    phase = RecordPhase.RECORDING
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    recorder.cancel()
                    problem = Res.string.record_failed
                }
            }
        },
        onPauseToggle = {
            if (phase == RecordPhase.RECORDING) {
                recorder.pause()
                elapsed = recorder.elapsedSeconds()
                phase = RecordPhase.PAUSED
            } else {
                recorder.resume()
                phase = RecordPhase.RECORDING
            }
        },
        onFinish = ::finish,
        onCancel = {
            recorder.cancel()
            onCancel()
        },
    )
}

@Composable
fun RecordScreen(
    phase: RecordPhase,
    elapsed: () -> Double,
    problem: StringResource?,
    onStart: () -> Unit,
    onPauseToggle: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.record_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.record_cancel)) }
        }
        if (phase == RecordPhase.IDLE) {
            Text(
                stringResource(Res.string.record_hint_idle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RecordTimer(phase, elapsed)
        }
        if (problem != null) {
            Text(stringResource(problem), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.weight(1f))
        if (phase == RecordPhase.IDLE) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.record_start)) }
        } else {
            RecordControls(phase, elapsed, onPauseToggle, onFinish)
        }
    }
}

// Reads elapsed itself so the 10 Hz tick only re-runs the timer and the controls, not the whole screen.
@Composable
private fun RecordTimer(phase: RecordPhase, elapsed: () -> Double) {
    val seconds = elapsed()
    val paused = phase == RecordPhase.PAUSED
    val timeDesc = stringResource(Res.string.record_time_desc, formatTime(seconds), formatTime(RecordLimits.MAX_SECONDS))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(if (paused) Res.string.record_paused else Res.string.record_recording),
            style = MaterialTheme.typography.labelLarge,
            color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Text(
            "${formatTime(seconds)} / ${formatTime(RecordLimits.MAX_SECONDS)}",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.clearAndSetSemantics { contentDescription = timeDesc },
        )
        LinearProgressIndicator(
            progress = { (seconds / RecordLimits.MAX_SECONDS).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (seconds < RecordLimits.MIN_SECONDS) {
            Text(
                stringResource(Res.string.record_min_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (seconds >= RecordLimits.WARN_SECONDS) {
            Text(
                stringResource(Res.string.record_warn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun RecordControls(phase: RecordPhase, elapsed: () -> Double, onPauseToggle: () -> Unit, onFinish: () -> Unit) {
    val canFinish = elapsed() >= RecordLimits.MIN_SECONDS
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPauseToggle, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (phase == RecordPhase.PAUSED) Res.string.record_resume else Res.string.record_pause))
        }
        Button(onClick = onFinish, enabled = canFinish, modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.record_finish))
        }
    }
}
