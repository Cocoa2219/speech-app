package dev.cocoa.speech.record

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.listeners.ListenerColors
import dev.cocoa.speech.listeners.ListeningAvatar
import dev.cocoa.speech.logic.ResultsReport.Companion.formatTime
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import dev.cocoa.speech.theme.RecordRed
import dev.cocoa.speech.theme.motionSchemeEffects
import dev.cocoa.speech.theme.motionSchemeSpatial
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.persona_classmate
import speech_app.shared.generated.resources.persona_kid
import speech_app.shared.generated.resources.persona_teacher
import speech_app.shared.generated.resources.record_cancel
import speech_app.shared.generated.resources.record_failed
import speech_app.shared.generated.resources.record_finish
import speech_app.shared.generated.resources.record_finishing
import speech_app.shared.generated.resources.record_hint_idle
import speech_app.shared.generated.resources.record_listen_idle
import speech_app.shared.generated.resources.record_listen_paused
import speech_app.shared.generated.resources.record_listen_recording
import speech_app.shared.generated.resources.record_min_hint
import speech_app.shared.generated.resources.record_pause
import speech_app.shared.generated.resources.record_paused
import speech_app.shared.generated.resources.record_permission_denied
import speech_app.shared.generated.resources.record_ready
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

// Designed on the "Listener characters" canvas, board F: the listeners listen (bobbing while recording), a big timer
// over a wavy progress line, and a record button that changes shape. With a camera preview the listeners sit as small
// faces over it.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val listening = phase == RecordPhase.RECORDING
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(phase, onCancel)
        if (preview != null) {
            Box(
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp).weight(1f, fill = false).clip(RoundedCornerShape(28.dp))
                    .background(ListenerColors.Ink),
                contentAlignment = Alignment.BottomCenter,
            ) {
                preview()
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (persona in Persona.entries) {
                        Box(
                            Modifier.size(52.dp).background(Color.White.copy(alpha = 0.92f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            ListeningAvatar(persona, listening, Modifier.size(46.dp))
                        }
                    }
                }
            }
        } else {
            ListeningStage(phase, Modifier.padding(top = 28.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            when (phase) {
                RecordPhase.IDLE -> {
                    TimerText(phase) { 0.0 }
                    TimeLine(phase) { 0.0 }
                    Hint(stringResource(Res.string.record_hint_idle))
                }
                RecordPhase.FINISHING -> TimerText(phase, elapsed)
                else -> {
                    TimerText(phase, elapsed)
                    TimeLine(phase, elapsed)
                    LimitHint(elapsed)
                }
            }
            if (problem != null) {
                Text(
                    stringResource(problem),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            when (phase) {
                RecordPhase.IDLE -> StartButton(canStart, onStart)
                RecordPhase.FINISHING -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingIndicator(Modifier.size(64.dp))
                    Text(
                        stringResource(Res.string.record_finishing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> RecordControls(phase, elapsed, onPauseToggle, onFinish)
            }
        }
    }
}

@Composable
private fun TopBar(phase: RecordPhase, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel, enabled = phase != RecordPhase.FINISHING) {
            val label = stringResource(Res.string.record_cancel)
            GlyphIcon(Glyph.Close, MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { contentDescription = label })
        }
        Text(
            stringResource(Res.string.record_title),
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusChip(phase, Modifier.padding(end = 12.dp))
    }
}

@Composable
private fun StatusChip(phase: RecordPhase, modifier: Modifier) {
    val recording = phase == RecordPhase.RECORDING
    val label = when (phase) {
        RecordPhase.RECORDING -> Res.string.record_recording
        RecordPhase.PAUSED -> Res.string.record_paused
        else -> Res.string.record_ready
    }
    val blink = rememberInfiniteTransition(label = "chip").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "blink",
    )
    Row(
        modifier
            .height(28.dp)
            .background(
                if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (recording) blink.value else 1f }
                .background(if (recording) RecordRed else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (recording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Dimmed before the start and while paused; bobbing while recording.
@Composable
private fun ListeningStage(phase: RecordPhase, modifier: Modifier) {
    val listening = phase == RecordPhase.RECORDING
    val alpha by animateFloatAsState(if (listening || phase == RecordPhase.FINISHING) 1f else 0.55f, MaterialTheme.motionSchemeEffects(), label = "listen")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).graphicsLayer { this.alpha = alpha }) {
            for (persona in Persona.entries) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ListeningAvatar(persona, listening, Modifier.size(92.dp))
                    Text(stringResource(personaName(persona)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            stringResource(
                when (phase) {
                    RecordPhase.RECORDING -> Res.string.record_listen_recording
                    RecordPhase.PAUSED -> Res.string.record_listen_paused
                    else -> Res.string.record_listen_idle
                },
            ),
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Reads elapsed itself so the 10 Hz tick only re-runs the timer, the line, and the hints, not the whole screen.
@Composable
private fun TimerText(phase: RecordPhase, elapsed: () -> Double) {
    val seconds = elapsed()
    val timeDesc = stringResource(Res.string.record_time_desc, formatTime(seconds), formatTime(RecordLimits.MAX_SECONDS))
    val blink = rememberInfiniteTransition(label = "paused").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "blink",
    )
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = timeDesc },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            formatTime(seconds),
            modifier = Modifier.alignByBaseline().graphicsLayer { alpha = if (phase == RecordPhase.PAUSED) blink.value else 1f },
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
        )
        Text(
            " / ${formatTime(RecordLimits.MAX_SECONDS)}",
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Wavy while recording, flat when paused (the indicator animates the change). The 20 s minimum and the 2:30 warning
// are told by the hint text and the finish button, not by marks on the line.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimeLine(phase: RecordPhase, elapsed: () -> Double) {
    val waving = phase == RecordPhase.RECORDING
    LinearWavyProgressIndicator(
        progress = { (elapsed() / RecordLimits.MAX_SECONDS).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurface,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        amplitude = { if (waving) 1f else 0f },
    )
}

@Composable
private fun LimitHint(elapsed: () -> Double) {
    val seconds = elapsed()
    when {
        seconds < RecordLimits.MIN_SECONDS -> Hint(stringResource(Res.string.record_min_hint))
        seconds >= RecordLimits.WARN_SECONDS -> Text(
            stringResource(Res.string.record_warn),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = WarnText,
            textAlign = TextAlign.Center,
        )
        else -> Spacer(Modifier.height(44.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StartButton(enabled: Boolean, onStart: () -> Unit) {
    val label = stringResource(Res.string.record_start)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier.size(96.dp).semantics { contentDescription = label },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).background(if (enabled) RecordRed else MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// The big button changes shape: a rounded square with pause bars while recording, a circle with the record dot when
// paused. Finishing unlocks at the 20 s minimum.
@Composable
private fun RecordControls(phase: RecordPhase, elapsed: () -> Double, onPauseToggle: () -> Unit, onFinish: () -> Unit) {
    val recording = phase == RecordPhase.RECORDING
    val canFinish = elapsed() >= RecordLimits.MIN_SECONDS
    val corner by animateDpAsState(if (recording) 28.dp else 44.dp, MaterialTheme.motionSchemeSpatial(), label = "morph")
    val toggleLabel = stringResource(if (recording) Res.string.record_pause else Res.string.record_resume)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            onClick = onPauseToggle,
            modifier = Modifier.size(88.dp).semantics { contentDescription = toggleLabel },
            shape = RoundedCornerShape(corner),
            color = if (recording) MaterialTheme.colorScheme.primary else PausedButton,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (recording) GlyphIcon(Glyph.Pause, Color.White, size = 30.dp)
                else Box(Modifier.size(30.dp).background(RecordRed, CircleShape))
            }
        }
        Button(
            onClick = onFinish,
            enabled = canFinish,
            modifier = Modifier.weight(1f).height(64.dp),
            colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            val tint = if (canFinish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
            GlyphIcon(Glyph.Check, tint, size = 20.dp)
            Text(
                stringResource(Res.string.record_finish),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val PausedButton = Color(0xFF3A404A)
private val WarnText = Color(0xFFA0590A)

private fun personaName(persona: Persona) = when (persona) {
    Persona.CLASSMATE -> Res.string.persona_classmate
    Persona.TEACHER -> Res.string.persona_teacher
    Persona.KID -> Res.string.persona_kid
}
