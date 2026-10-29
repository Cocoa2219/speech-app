package dev.cocoa.speech.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.logic.RankedFix
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.logic.VideoStatus
import dev.cocoa.speech.listeners.AnimatedListenerAvatar
import dev.cocoa.speech.listeners.ListenerAvatar
import dev.cocoa.speech.listeners.ListenerColors
import dev.cocoa.speech.logic.ResultsReport.Companion.formatTime
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.persona_classmate
import speech_app.shared.generated.resources.persona_kid
import speech_app.shared.generated.resources.persona_teacher
import speech_app.shared.generated.resources.results_back
import speech_app.shared.generated.resources.results_error
import speech_app.shared.generated.resources.results_grade_basis
import speech_app.shared.generated.resources.results_listeners_count
import speech_app.shared.generated.resources.results_mock_badge
import speech_app.shared.generated.resources.results_no_fixes
import speech_app.shared.generated.resources.results_no_questions
import speech_app.shared.generated.resources.results_no_recording
import speech_app.shared.generated.resources.results_no_replay
import speech_app.shared.generated.resources.results_video_failed
import speech_app.shared.generated.resources.results_retry
import speech_app.shared.generated.resources.results_tab_fixes
import speech_app.shared.generated.resources.results_tab_questions
import speech_app.shared.generated.resources.results_title

@Composable
fun ResultsScreen(
    state: ResultsUiState,
    grade: String,
    isExample: Boolean,
    position: () -> Double,
    onSeek: (Double) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    replayPlayer: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ResultsHeader(grade = grade, isExample = isExample, onBack = onBack)
        when (state) {
            ResultsUiState.Loading -> ResultsSkeleton()
            ResultsUiState.Failed -> ResultsError(onRetry = onRetry)
            is ResultsUiState.Loaded -> ResultsContent(state.report, isExample, position, onSeek, replayPlayer)
        }
    }
}

@Composable
private fun ResultsHeader(grade: String, isExample: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.results_back)) }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
            Text(stringResource(Res.string.results_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.results_grade_basis, grade) +
                    if (isExample) " · " + stringResource(Res.string.results_mock_badge) else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsContent(
    report: ResultsReport,
    isExample: Boolean,
    position: () -> Double,
    onSeek: (Double) -> Unit,
    replayPlayer: (@Composable () -> Unit)?,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Replay and timeline stay pinned so tapping a card visibly moves the playhead.
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report.video == VideoStatus.FAILED) {
            Text(
                stringResource(Res.string.results_video_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ReplayPanel(report.segments, report.events, isExample, position, replayPlayer)
        ConfusionTimeline(report.heatmap, report.events, report.durationSeconds, position, onSeek)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "pace", contentType = "pace") { PaceStripView(report.pace, report.durationSeconds) }
        item(key = "tabs", contentType = "tabs") {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(Res.string.results_tab_fixes)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(Res.string.results_tab_questions)) })
            }
        }
        if (tab == 0) {
            if (report.topFixes.isEmpty()) {
                item(key = "no-fixes", contentType = "empty") { EmptyHint(stringResource(Res.string.results_no_fixes)) }
            }
            items(report.topFixes, key = { "fix-${it.t}-${it.quote}" }, contentType = { "fix" }) { fix ->
                FixCard(fix, onSeek)
            }
        } else {
            if (report.questions.isEmpty()) {
                item(key = "no-questions", contentType = "empty") { EmptyHint(stringResource(Res.string.results_no_questions)) }
            }
            items(report.questions, key = { "q-${it.t}-${it.quote}" }, contentType = { "question" }) { event ->
                QuestionCard(event, onSeek)
            }
        }
    }
}

// The player (when there is a recording to replay), the playback time, and the sentence being said at that moment.
// Mock mode has no recording, so it shows only the time and sentence.
@Composable
private fun ReplayPanel(
    segments: List<Segment>,
    events: List<PersonaEvent>,
    isExample: Boolean,
    position: () -> Double,
    replayPlayer: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (replayPlayer != null) {
            replayPlayer()
        } else {
            Text(
                stringResource(if (isExample) Res.string.results_no_recording else Res.string.results_no_replay),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlaybackCaption(segments, position)
        ListenerRow(events, position)
    }
}

// The three listeners; each raises a hand while playback is at one of their moments. Reads the position itself so
// only this row re-runs as playback moves, and each avatar animates only when its raised state flips.
@Composable
private fun ListenerRow(events: List<PersonaEvent>, position: () -> Double) {
    val t = position()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (persona in Persona.entries) {
            val raised = events.any { it.persona == persona && t >= it.t - RAISE_LEAD_S && t <= it.t + RAISE_HOLD_S }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedListenerAvatar(persona, raised, Modifier.size(56.dp))
                Text(personaLabel(persona), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// A hand goes up just before the quoted moment and stays up long enough to notice.
private const val RAISE_LEAD_S = 0.3
private const val RAISE_HOLD_S = 3.0

// Reads the position itself so the 10 Hz playback tick re-runs only this caption, not the player above it.
@Composable
private fun PlaybackCaption(segments: List<Segment>, position: () -> Double) {
    val t = position()
    val current = segments.lastOrNull { it.tStart <= t } ?: segments.firstOrNull()
    Text(formatTime(t), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(current?.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FixCard(fix: RankedFix, onSeek: (Double) -> Unit) {
    Card(onClick = { onSeek(fix.t) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(fix.t), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(Res.string.results_listeners_count, fix.personas.size.toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("“${fix.quote}”", style = MaterialTheme.typography.bodyLarge)
            Text(fix.events.first().fix, style = MaterialTheme.typography.titleSmall)
            for (event in fix.events) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    PersonaTag(event.persona)
                    Text(event.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(event: PersonaEvent, onSeek: (Double) -> Unit) {
    Card(onClick = { onSeek(event.t) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonaTag(event.persona)
                Text(formatTime(event.t), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(event.question.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(
                "“${event.quote}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Every card is a hand that went up, so the listener is shown with a raised hand.
@Composable
private fun PersonaTag(persona: Persona) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ListenerAvatar(persona, raise = { 1f }, modifier = Modifier.size(28.dp))
        Text(personaLabel(persona), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Cold load only; same shapes as the loaded content so nothing jumps when it arrives.
@Composable
private fun ResultsSkeleton() {
    val shade = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (h in listOf(112, 56, 40, 48, 140, 140)) {
            Box(Modifier.fillMaxWidth().height(h.dp).background(shade, RoundedCornerShape(12.dp)))
        }
    }
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

// The approved listener colors (fixed, not themed), so a pin's color names the same listener as its avatar.
internal fun personaColor(persona: Persona): Color = ListenerColors.main(persona)

@Composable
internal fun personaLabel(persona: Persona): String = stringResource(
    when (persona) {
        Persona.CLASSMATE -> Res.string.persona_classmate
        Persona.TEACHER -> Res.string.persona_teacher
        Persona.KID -> Res.string.persona_kid
    }
)
