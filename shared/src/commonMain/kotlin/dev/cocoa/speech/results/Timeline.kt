package dev.cocoa.speech.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.logic.PaceStrip
import dev.cocoa.speech.logic.ResultsReport.Companion.formatTime
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.results_pace_hint
import speech_app.shared.generated.resources.results_pace_label
import speech_app.shared.generated.resources.results_timeline_desc
import speech_app.shared.generated.resources.results_timeline_label
import kotlin.math.abs

// Heatmap band with one pin row per listener above it. Tapping seeks; a tap near a pin snaps to that pin.
// The playhead reads `position` inside the draw phase, so moving it redraws only this canvas.
@Composable
internal fun ConfusionTimeline(
    heatmap: List<Int>,
    events: List<PersonaEvent>,
    durationSeconds: Double,
    position: () -> Double,
    onSeek: (Double) -> Unit,
) {
    val heat = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceVariant
    val playhead = MaterialTheme.colorScheme.onSurface
    val pinColors = Persona.entries.associateWith { personaColor(it) }
    val description = stringResource(Res.string.results_timeline_desc, events.size.toString(), formatTime(durationSeconds))
    val maxCount = (heatmap.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.results_timeline_label), style = MaterialTheme.typography.titleSmall)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = description }
                .pointerInput(events, durationSeconds) {
                    if (durationSeconds <= 0.0) return@pointerInput
                    val snapPx = 16.dp.toPx()
                    detectTapGestures { tap ->
                        val pin = events.minByOrNull { abs(it.t / durationSeconds * size.width - tap.x) }
                        val pinX = pin?.let { it.t / durationSeconds * size.width }
                        onSeek(
                            if (pin != null && pinX != null && abs(pinX - tap.x) <= snapPx) pin.t
                            else (tap.x / size.width * durationSeconds).coerceIn(0.0, durationSeconds)
                        )
                    }
                },
        ) {
            if (durationSeconds <= 0.0) return@Canvas
            val bandTop = size.height * 0.5f
            val bandSize = Size(size.width, size.height - bandTop)
            drawRect(track, topLeft = Offset(0f, bandTop), size = bandSize)

            val binWidth = size.width / heatmap.size.coerceAtLeast(1)
            heatmap.forEachIndexed { i, count ->
                if (count > 0) {
                    drawRect(
                        heat.copy(alpha = 0.25f + 0.75f * count / maxCount),
                        topLeft = Offset(i * binWidth, bandTop),
                        size = Size(binWidth, bandSize.height),
                    )
                }
            }

            val radius = 3.5.dp.toPx()
            val rowGap = (bandTop - 2 * radius) / Persona.entries.size
            for (event in events) {
                val x = (event.t / durationSeconds).toFloat() * size.width
                val y = radius + rowGap * (event.persona.ordinal + 0.5f)
                val color = pinColors.getValue(event.persona)
                drawLine(color, Offset(x, y), Offset(x, bandTop), strokeWidth = 1.5.dp.toPx())
                drawCircle(color, radius, Offset(x, y))
            }

            val px = (position() / durationSeconds).toFloat().coerceIn(0f, 1f) * size.width
            drawLine(playhead, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

// Descriptive only: bar height is syllables per second, dots are fillers, gaps are pauses. Never a score.
@Composable
internal fun PaceStripView(pace: PaceStrip, durationSeconds: Double) {
    val bar = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val dot = MaterialTheme.colorScheme.tertiary
    val maxRate = (pace.samples.maxOfOrNull { it.syllablesPerSecond } ?: 0.0).coerceAtLeast(1.0)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.results_pace_label), style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            if (durationSeconds <= 0.0) return@Canvas
            val dotRadius = 2.5.dp.toPx()
            val barArea = size.height - 4 * dotRadius
            for (s in pace.samples) {
                val left = (s.tStart / durationSeconds).toFloat() * size.width
                val right = (s.tEnd / durationSeconds).toFloat() * size.width
                val h = (s.syllablesPerSecond / maxRate).toFloat() * barArea
                drawRect(bar, topLeft = Offset(left + 1f, size.height - h), size = Size((right - left - 2f).coerceAtLeast(1f), h))
                repeat(s.fillers) { k ->
                    drawCircle(dot, dotRadius, Offset(left + dotRadius * (2 + 3 * k), dotRadius * 1.5f))
                }
            }
        }
        Text(
            stringResource(Res.string.results_pace_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
