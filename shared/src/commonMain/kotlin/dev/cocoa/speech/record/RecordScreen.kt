package dev.cocoa.speech.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import speech_app.shared.generated.resources.record_finishing
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

enum class RecordPhase { IDLE, RECORDING, PAUSED, FINISHING }

// Owns the capture and the timer. Permission is asked on entry so a camera preview can show before recording starts.
// Leaving this screen any way other than finishing discards the recording.
@Composable
fun RecordRoute(
    capture: Capture,
    permission: Permission,
    onFinished: (Recording) -> Unit,
    onCancel: () -> Unit,
    preview: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(RecordPhase.IDLE) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var problem by remember { mutableStateOf<StringResource?>(null) }

    LaunchedEffect(permission) {
        granted = permission.request()
        if (!granted) problem = Res.string.record_permission_denied
    }
    DisposableEffect(capture) { onDispose { capture.cancel() } }

    fun finish() {
        if (phase == RecordPhase.FINISHING) return
        phase = RecordPhase.FINISHING
        scope.launch {
            try {
                onFinished(capture.finish())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                capture.cancel()
                problem = Res.string.record_failed
                phase = RecordPhase.IDLE
            }
        }
    }

    // Ticks the timer while recording and auto-stops at the limit.
    LaunchedEffect(phase) {
        while (phase == RecordPhase.RECORDING) {
            elapsed = capture.elapsedSeconds()
            if (elapsed >= RecordLimits.MAX_SECONDS) finish()
            delay(100)
        }
    }

    RecordScreen(
        phase = phase,
        canStart = granted,
        elapsed = { elapsed },
        problem = problem,
        preview = if (granted) preview else null,
        onStart = {
            problem = null
            try {
                capture.start()
                elapsed = 0.0
                phase = RecordPhase.RECORDING
            } catch (e: Exception) {
                capture.cancel()
                problem = Res.string.record_failed
            }
        },
        onPauseToggle = {
            if (phase == RecordPhase.RECORDING) {
                capture.pause()
                elapsed = capture.elapsedSeconds()
                phase = RecordPhase.PAUSED
            } else if (phase == RecordPhase.PAUSED) {
                capture.resume()
                phase = RecordPhase.RECORDING
            }
        },
        onFinish = ::finish,
        onCancel = {
            capture.cancel()
            onCancel()
        },
    )
}

@Composable
fun RecordScreen(
    phase: RecordPhase,
    canStart: Boolean,
    elapsed: () -> Double,
    problem: StringResource?,
    preview: (@Composable () -> Unit)?,
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
            TextButton(onClick = onCancel, enabled = phase != RecordPhase.FINISHING) {
                Text(stringResource(Res.string.record_cancel))
            }
        }
        if (preview != null) {
            Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) { preview() }
        }
        when (phase) {
            RecordPhase.IDLE -> Text(
                stringResource(Res.string.record_hint_idle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RecordPhase.FINISHING -> Text(stringResource(Res.string.record_finishing), style = MaterialTheme.typography.bodyLarge)
            else -> RecordTimer(phase, elapsed)
        }
        if (problem != null) {
            Text(stringResource(problem), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (preview == null) Spacer(Modifier.weight(1f))
        when (phase) {
            RecordPhase.IDLE -> Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.record_start))
            }
            RecordPhase.FINISHING -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> RecordControls(phase, elapsed, onPauseToggle, onFinish)
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
