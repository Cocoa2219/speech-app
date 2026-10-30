package dev.cocoa.speech.results

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cocoa.speech.listeners.AnimatedListenerAvatar
import dev.cocoa.speech.listeners.ListenerAvatar
import dev.cocoa.speech.listeners.ListenerColors
import dev.cocoa.speech.logic.PaceStrip
import dev.cocoa.speech.logic.RankedFix
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.logic.ResultsReport.Companion.formatTime
import dev.cocoa.speech.logic.VideoStatus
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import dev.cocoa.speech.theme.motionSchemeEffects
import dev.cocoa.speech.theme.motionSchemeFast
import dev.cocoa.speech.theme.motionSchemeSpatial
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.persona_classmate
import speech_app.shared.generated.resources.persona_kid
import speech_app.shared.generated.resources.persona_teacher
import speech_app.shared.generated.resources.results_back
import speech_app.shared.generated.resources.results_error
import speech_app.shared.generated.resources.results_fix_how
import speech_app.shared.generated.resources.results_fix_page
import speech_app.shared.generated.resources.results_pace_average
import speech_app.shared.generated.resources.results_pace_hint
import speech_app.shared.generated.resources.results_fix_title
import speech_app.shared.generated.resources.results_grade_basis
import speech_app.shared.generated.resources.results_mock_badge
import speech_app.shared.generated.resources.results_no_fixes
import speech_app.shared.generated.resources.results_retry
import speech_app.shared.generated.resources.results_scrubber
import speech_app.shared.generated.resources.results_title
import speech_app.shared.generated.resources.results_video_failed
import speech_app.shared.generated.resources.results_video_failed_title
import speech_app.shared.generated.resources.results_dismiss
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import kotlin.math.abs
import kotlin.math.roundToInt

// Results, as designed on the canvas (board D, 2026-10-29): replay on top with a draggable scrubber, then the listener
// stage, their speech bubbles, and a pager of the top fixes. Playback position drives everything: hands go up and
// bubbles appear while the position is at a listener's moment; choosing a fix seeks to it.

// A listener's hand stays up from shortly before its moment until a few seconds after. The lead also lets every hand
// in a fix's cluster (e.g. 39.0 s and 39.5 s) be up when the fix is selected.
private const val RAISE_LEAD_S = 1.0
private const val RAISE_HOLD_S = 3.0

// While dragging, hand-raises within this distance of the handle count as "here".
private const val NEAR_S = 1.5

private fun PersonaEvent.activeAt(t: Double) = t >= this.t - RAISE_LEAD_S && t <= this.t + RAISE_HOLD_S

@Composable
fun ResultsScreen(
    state: ResultsUiState,
    grade: String,
    isExample: Boolean,
    position: () -> Double,
    onSeek: (Double) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onRecordAgain: (() -> Unit)? = null,
    replayPlayer: (@Composable () -> Unit)? = null,
    topic: String? = null,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        ResultsHeader(grade, isExample, (state as? ResultsUiState.Loaded)?.report, topic, onBack)
        when (state) {
            ResultsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
            ResultsUiState.Failed -> ResultsError(onRetry)
            is ResultsUiState.Loaded -> ResultsContent(state.report, grade, position, onSeek, onRecordAgain, replayPlayer)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLoading() = LoadingIndicator()

@Composable
private fun ResultsHeader(grade: String, isExample: Boolean, report: ResultsReport?, topic: String?, onBack: () -> Unit) {
    val basis = stringResource(Res.string.results_grade_basis, grade)
    val subtitle = buildString {
        if (!topic.isNullOrBlank()) append(topic).append(" · ")
        append(basis)
        if (report != null) {
            append(" · ").append(formatTime(report.durationSeconds))
        }
        if (isExample) append(" · ").append(stringResource(Res.string.results_mock_badge))
    }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            BackChevron(stringResource(Res.string.results_back))
        }
        Column {
            Text(
                stringResource(Res.string.results_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackChevron(label: String) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp).semantics { contentDescription = label }) {
        val w = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.62f, w * 0.2f)
            lineTo(w * 0.33f, w * 0.5f)
            lineTo(w * 0.62f, w * 0.8f)
        }
        drawPath(path, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ColumnScope.ResultsContent(
    report: ResultsReport,
    grade: String,
    position: () -> Double,
    onSeek: (Double) -> Unit,
    onRecordAgain: (() -> Unit)?,
    replayPlayer: (@Composable () -> Unit)?,
) {
    val fixes = report.topFixes
    val pager = rememberPagerState { fixes.size }
    val scope = rememberCoroutineScope()

    // Open at the first fix, ready to play. Turning the pager seeks to that fix as soon as the target page is known
    // (on release, or when a drag passes halfway), so the replay and stage move with the card, not after it settles.
    LaunchedEffect(report) { fixes.firstOrNull()?.let { onSeek(it.t) } }
    LaunchedEffect(pager, fixes) {
        snapshotFlow { pager.targetPage }.distinctUntilChanged().drop(1).collect { page ->
            fixes.getOrNull(page)?.let { onSeek(it.t) }
        }
    }

    ReplayArea(report.segments, position, replayPlayer)
    if (report.video == VideoStatus.FAILED) VideoFailedNotice()
    Scrubber(
        events = report.events,
        pace = report.pace,
        duration = report.durationSeconds,
        position = position,
        onScrub = onSeek,
        // On release, snap to the nearest hand-raise; if it belongs to a top fix, turn the pager to it.
        onRelease = { t ->
            val hit = report.events.filter { abs(it.t - t) <= NEAR_S }.minByOrNull { abs(it.t - t) }
            val target = hit?.t ?: t
            onSeek(target)
            val page = fixes.indexOfFirst { fix -> fix.events.any { abs(it.t - target) < 0.01 } }
            if (page >= 0) scope.launch { pager.animateScrollToPage(page) }
        },
    )
    if (report.events.isEmpty()) {
        NoHands(grade, onRecordAgain, Modifier.weight(1f))
        return
    }
    // The stage, bubbles and cards scroll under the fixed scrubber; once scrolled, a short fade softens the edge.
    val scroll = rememberScrollState()
    val bg = MaterialTheme.colorScheme.background
    val fade by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, MaterialTheme.motionSchemeEffects(), label = "fade")
    Box {
        Column(Modifier.verticalScroll(scroll).padding(bottom = 24.dp)) {
            ListenerStage(report.events, position)
            Bubbles(report.events, position)
            if (fixes.isEmpty()) {
                Text(
                    stringResource(Res.string.results_no_fixes),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FixPager(fixes, report.segments, pager, onPick = { scope.launch { pager.animateScrollToPage(it) } })
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .graphicsLayer { alpha = fade }
                .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))),
        )
    }
}

// Canvas board M: video was recorded but its analysis failed, so the run went on audio only (CLAUDE.md failure rule).
// The results are complete for what was said; one dismissible notice says the screen was not seen. Not an error.
@Composable
private fun VideoFailedNotice() {
    var shown by remember { mutableStateOf(true) }
    AnimatedVisibility(shown, exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth()
                .background(NoticeContainer, RoundedCornerShape(20.dp))
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(Glyph.CameraOff, NoticeText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.results_video_failed_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NoticeTitle)
                Text(stringResource(Res.string.results_video_failed), style = MaterialTheme.typography.bodySmall, color = NoticeText)
            }
            val dismiss = stringResource(Res.string.results_dismiss)
            IconButton(onClick = { shown = false }, modifier = Modifier.semantics { contentDescription = dismiss }) {
                GlyphIcon(Glyph.Close, NoticeText, size = 16.dp)
            }
        }
    }
}

private val NoticeContainer = Color(0xFFFCEFD9)
private val NoticeTitle = Color(0xFF5C3804)
private val NoticeText = Color(0xFF7A4A06)

// Dark replay area: the player when there is a recording, with the sentence being said shown as a subtitle.
@Composable
private fun ReplayArea(segments: List<Segment>, position: () -> Double, replayPlayer: (@Composable () -> Unit)?) {
    Box(Modifier.fillMaxWidth().height(232.dp).background(ListenerColors.Ink)) {
        replayPlayer?.invoke()
        Subtitle(segments, position, Modifier.align(if (replayPlayer == null) Alignment.Center else Alignment.BottomCenter))
    }
}

@Composable
private fun Subtitle(segments: List<Segment>, position: () -> Double, modifier: Modifier) {
    val t = position()
    val current = segments.lastOrNull { it.tStart <= t } ?: segments.firstOrNull() ?: return
    Text(
        current.text,
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

// Track with listener-colored moment marks. Dragging seeks continuously and shows a small pill above the handle with
// the time and the faces of whoever raised a hand there.
@Composable
private fun Scrubber(
    events: List<PersonaEvent>,
    pace: PaceStrip,
    duration: Double,
    position: () -> Double,
    onScrub: (Double) -> Unit,
    onRelease: (Double) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var trackWidth by remember { mutableFloatStateOf(1f) }
    val handle by animateDpAsState(if (dragging) 22.dp else 14.dp, MaterialTheme.motionSchemeFast(), label = "handle")
    val track = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val label = stringResource(Res.string.results_scrubber)
    fun timeAt(x: Float, width: Float) = (x / width).coerceIn(0f, 1f) * duration

    Column(Modifier.padding(horizontal = 20.dp).zIndex(1f)) {
        PaceGraph(pace, duration, position)
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = formatTime(position())
                }
                // The width is read live: the window can resize (split screen, desktop windows) while the screen is open.
                .onSizeChanged { trackWidth = it.width.toFloat() }
                .pointerInput(duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        var t = timeAt(down.position.x, size.width.toFloat())
                        onScrub(t)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            t = timeAt(change.position.x, size.width.toFloat())
                            onScrub(t)
                            change.consume()
                        } while (change.pressed)
                        dragging = false
                        onRelease(t)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (duration <= 0.0) return@Canvas
                val y = size.height / 2
                val px = (position() / duration).toFloat().coerceIn(0f, 1f) * size.width
                drawRoundRect(track, Offset(0f, y - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                drawRoundRect(ink, Offset(0f, y - 2.dp.toPx()), Size(px, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                for (e in events) {
                    val x = (e.t / duration).toFloat() * size.width
                    drawCircle(ListenerColors.main(e.persona), 3.5.dp.toPx(), Offset(x, y))
                }
                drawCircle(ink, handle.toPx() / 2, Offset(px, y))
                if (dragging) drawCircle(ink.copy(alpha = 0.15f), handle.toPx(), Offset(px, y))
            }
            if (dragging) ScrubPill(events, duration, position, trackWidth)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel(position)
            if (pace.averageSyllablesPerSecond > 0) {
                Text(
                    stringResource(Res.string.results_pace_average, formatRate(pace.averageSyllablesPerSecond)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Speaking pace on the same time axis as the track: one bar per sentence, taller when faster, gaps where the speaker
// paused, and a dot above a bar for each filler word. Bars up to the playhead are dark like the played track.
// Descriptive only, never a score.
@Composable
private fun PaceGraph(pace: PaceStrip, duration: Double, position: () -> Double) {
    val unplayed = MaterialTheme.colorScheme.outlineVariant
    val played = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val filler = MaterialTheme.colorScheme.tertiary
    val hint = stringResource(Res.string.results_pace_hint)
    val maxRate = (pace.samples.maxOfOrNull { it.syllablesPerSecond } ?: 0.0).coerceAtLeast(1.0)
    Canvas(Modifier.padding(top = 12.dp).fillMaxWidth().height(22.dp).semantics { contentDescription = hint }) {
        if (duration <= 0.0) return@Canvas
        val dot = 2.dp.toPx()
        val barArea = size.height - 4 * dot
        val radius = CornerRadius(1.5.dp.toPx())
        fun bars(color: Color) {
            for (s in pace.samples) {
                if (s.syllablesPerSecond <= 0) continue
                val left = (s.tStart / duration).toFloat() * size.width + 1.dp.toPx()
                val right = (s.tEnd / duration).toFloat() * size.width - 1.dp.toPx()
                if (right <= left) continue
                val h = maxOf((s.syllablesPerSecond / maxRate).toFloat() * barArea, 2.dp.toPx())
                drawRoundRect(color, Offset(left, size.height - h), Size(right - left, h), radius)
            }
        }
        bars(unplayed)
        val px = (position() / duration).toFloat().coerceIn(0f, 1f) * size.width
        clipRect(right = px) { bars(played) }
        for (s in pace.samples) {
            if (s.fillers == 0) continue
            val left = (s.tStart / duration).toFloat() * size.width
            val h = maxOf((s.syllablesPerSecond / maxRate).toFloat() * barArea, 2.dp.toPx())
            repeat(s.fillers) { k ->
                drawCircle(filler, dot, Offset(left + dot * (2 + 3 * k), size.height - h - 2 * dot))
            }
        }
    }
}

private fun formatRate(rate: Double): String = ((rate * 10).roundToInt() / 10.0).toString()

@Composable
private fun TimeLabel(position: () -> Double) {
    Text(formatTime(position()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScrubPill(events: List<PersonaEvent>, duration: Double, position: () -> Double, trackWidth: Float) {
    val t = position()
    val here = Persona.entries.filter { p -> events.any { it.persona == p && abs(it.t - t) <= NEAR_S } }
    Row(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val handleX = ((t / duration).toFloat().coerceIn(0f, 1f) * trackWidth).toInt()
                layout(0, 0) {
                    p.place((handleX - p.width / 2).coerceIn(0, (trackWidth.toInt() - p.width).coerceAtLeast(0)), -p.height + 4.dp.roundToPx())
                }
            }
            .height(28.dp)
            .background(ListenerColors.Ink, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-4).dp),
    ) {
        Text(formatTime(t), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp))
        for (p in here) ListenerAvatar(p, raise = { 1f }, modifier = Modifier.size(22.dp))
    }
}

// The three listeners; whoever is at one of their moments raises a hand, the others fade back.
@Composable
private fun ListenerStage(events: List<PersonaEvent>, position: () -> Double) {
    val t = position()
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        for (persona in Persona.entries) {
            val raised = events.any { it.persona == persona && it.activeAt(t) }
            val alpha by animateFloatAsState(if (raised) 1f else 0.4f, MaterialTheme.motionSchemeEffects(), label = "stage")
            Column(Modifier.weight(1f).alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedListenerAvatar(persona, raised, Modifier.size(72.dp))
                Text(
                    personaLabel(persona),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (raised) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// Speech bubbles for the hand-raises active now, each with a tail pointing up at its listener on the stage.
@Composable
private fun Bubbles(events: List<PersonaEvent>, position: () -> Double) {
    val t = position()
    val active = events.filter { it.activeAt(t) }.distinctBy { it.persona to it.type }.sortedBy { it.persona.ordinal }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp).animateContentSize(MaterialTheme.motionSchemeSpatial())) {
        val column = maxWidth / 3
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (e in active) {
                Box(Modifier.padding(top = 6.dp)) {
                    Box(
                        Modifier
                            .offset(x = column * (e.persona.ordinal + 0.5f) - 7.dp, y = (-6).dp)
                            .size(14.dp)
                            .rotate(45f)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(3.dp)),
                    )
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        val question = e.question
                        Text(
                            if (question != null) "“$question”" else e.reason,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            style = if (question != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (question != null) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// Fix cards centered with the neighbors peeking, and dots that stretch into a pill for the current card.
@Composable
private fun FixPager(fixes: List<RankedFix>, segments: List<Segment>, pager: PagerState, onPick: (Int) -> Unit) {
    val current = pager.currentPage
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        val cardWidth = minOf(maxWidth - 64.dp, 320.dp)
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
            pageSpacing = 12.dp,
        ) { page ->
            FixCard(fixes[page], page, segments, isCurrent = page == current, onClick = { onPick(page) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
        fixes.indices.forEach { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, MaterialTheme.motionSchemeFast(), label = "dot")
            val pageLabel = stringResource(Res.string.results_fix_page, (i + 1).toString())
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
                    .clickable(onClickLabel = pageLabel) { onPick(i) },
            )
        }
    }
}

@Composable
private fun FixCard(fix: RankedFix, index: Int, segments: List<Segment>, isCurrent: Boolean, onClick: () -> Unit) {
    // Show the hand-raise whose quote was spoken, not one quoting on-screen text, so the card reads as a sentence.
    val first = fix.events.firstOrNull { e -> segments.any { e.quote in it.text } } ?: fix.events.first()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.results_fix_title, (index + 1).toString(), formatTime(fix.t)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            HighlightedSentence(segments, first, ListenerColors.tint(first.persona))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Text(
                stringResource(Res.string.results_fix_how),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ListenerColors.main(Persona.CLASSMATE),
            )
            Text(first.fix, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// The sentence around the hand-raise, with the quoted words marked in the listener's color. The mark is drawn behind
// the text as rounded pills, one per line the quote spans (span backgrounds in Compose are square-cornered).
@Composable
private fun HighlightedSentence(segments: List<Segment>, event: PersonaEvent, mark: Color) {
    val segment = segments.firstOrNull { event.t >= it.tStart && event.t < it.tEnd && event.quote in it.text }
        ?: segments.firstOrNull { event.quote in it.text }
    val text = segment?.text ?: event.quote
    val start = text.indexOf(event.quote).coerceAtLeast(0)
    val end = (start + event.quote.length).coerceAtMost(text.length)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind {
            val l = layout ?: return@drawBehind
            if (start >= end) return@drawBehind
            val padX = 4.dp.toPx()
            val insetY = 1.dp.toPx()
            for (line in l.getLineForOffset(start)..l.getLineForOffset(end - 1)) {
                val from = maxOf(start, l.getLineStart(line))
                val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
                if (from >= to) continue
                val left = l.getBoundingBox(from).left
                val right = l.getBoundingBox(to - 1).right
                val top = l.getLineTop(line) + insetY
                val height = l.getLineBottom(line) - insetY - top
                drawRoundRect(
                    mark,
                    topLeft = Offset(left - padX, top),
                    size = Size(right - left + 2 * padX, height),
                    cornerRadius = CornerRadius(height / 2),
                )
            }
        },
    )
}

@Composable
private fun ResultsError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.results_error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text(stringResource(Res.string.results_retry)) }
    }
}

@Composable
internal fun personaLabel(persona: Persona): String = stringResource(
    when (persona) {
        Persona.CLASSMATE -> Res.string.persona_classmate
        Persona.TEACHER -> Res.string.persona_teacher
        Persona.KID -> Res.string.persona_kid
    }
)
