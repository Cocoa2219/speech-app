package dev.cocoa.speech.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.history.CivilDate
import dev.cocoa.speech.history.HistoryEntry
import dev.cocoa.speech.history.HistoryGroup
import dev.cocoa.speech.history.HistoryGrouping
import dev.cocoa.speech.history.LocalStamp
import dev.cocoa.speech.listeners.ListenerAvatar
import dev.cocoa.speech.listeners.ListeningAvatar
import dev.cocoa.speech.logic.ResultsReport.Companion.formatTime
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.home_all_clear
import speech_app.shared.generated.resources.home_am
import speech_app.shared.generated.resources.home_date
import speech_app.shared.generated.resources.home_empty_body
import speech_app.shared.generated.resources.home_empty_title
import speech_app.shared.generated.resources.home_example
import speech_app.shared.generated.resources.home_fixes
import speech_app.shared.generated.resources.home_history
import speech_app.shared.generated.resources.home_last_week
import speech_app.shared.generated.resources.home_month
import speech_app.shared.generated.resources.home_new
import speech_app.shared.generated.resources.home_new_desc
import speech_app.shared.generated.resources.home_no_key
import speech_app.shared.generated.resources.home_pm
import speech_app.shared.generated.resources.home_replay
import speech_app.shared.generated.resources.home_this_week
import speech_app.shared.generated.resources.home_title
import speech_app.shared.generated.resources.home_today
import speech_app.shared.generated.resources.home_weekdays
import speech_app.shared.generated.resources.home_year_month
import speech_app.shared.generated.resources.home_yesterday

private val AllClear = Color(0xFF136F63)

// Canvas board J: past runs grouped by day, newest first, and a FAB that starts a new talk. Only the newest run can
// still have its recording, so only it is tagged "다시 보기". onNew is null without an API key.
@Composable
fun HomeScreen(
    entries: List<HistoryEntry>,
    today: LocalStamp,
    onNew: (() -> Unit)?,
    onOpen: (HistoryEntry) -> Unit,
    onShowExample: () -> Unit,
) {
    val groups = remember(entries, today) { HistoryGrouping.group(entries, today) }
    val newest = remember(entries) { entries.maxByOrNull { it.stamp } }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 120.dp)) {
            item { Header() }
            item { HistoryHeader(entries.size, onShowExample) }
            if (entries.isEmpty()) {
                item { Empty(onNew != null) }
            }
            for ((group, rows) in groups) {
                item(key = group.toString()) { GroupLabel(group, today) }
                items(rows.size, key = { rows[it].id }) { i ->
                    val entry = rows[i]
                    HistoryRow(
                        entry = entry,
                        group = group,
                        replay = entry.replayPath != null && entry.id == newest?.id,
                        shape = segment(i, rows.size),
                        onClick = { onOpen(entry) },
                    )
                    if (i < rows.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
        }
        // The list scrolls under a short fade so the FAB never sits on top of a row's text.
        val bg = MaterialTheme.colorScheme.background
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.7f to bg)),
        )
        if (onNew != null) {
            val desc = stringResource(Res.string.home_new_desc)
            ExtendedFloatingActionButton(
                onClick = onNew,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp).height(64.dp)
                    .semantics { contentDescription = desc },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                GlyphIcon(Glyph.Plus, MaterialTheme.colorScheme.onPrimary, size = 24.dp)
                Text(
                    stringResource(Res.string.home_new),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                stringResource(Res.string.home_no_key),
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.home_title),
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            for (persona in Persona.entries) ListeningAvatar(persona, listening = true, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun HistoryHeader(count: Int, onShowExample: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.home_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Text(
                " $count",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Mock mode (Layer 0): fixture results, no network. Also the demo safety net.
        FilledTonalButton(onClick = onShowExample, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text(stringResource(Res.string.home_example), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Empty(canRecord: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.home_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (canRecord) {
            Text(
                stringResource(Res.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GroupLabel(group: HistoryGroup, today: LocalStamp) {
    val label = when (group) {
        HistoryGroup.Today -> stringResource(Res.string.home_today)
        HistoryGroup.Yesterday -> stringResource(Res.string.home_yesterday)
        HistoryGroup.ThisWeek -> stringResource(Res.string.home_this_week)
        HistoryGroup.LastWeek -> stringResource(Res.string.home_last_week)
        is HistoryGroup.Month ->
            if (group.year == CivilDate.yearMonth(today.epochDay).first) stringResource(Res.string.home_month, group.month.toString())
            else stringResource(Res.string.home_year_month, group.year.toString(), group.month.toString())
    }
    Text(
        label,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Rows in a group join into one block: round outer corners, tight inner ones.
private fun segment(i: Int, n: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return when {
        n == 1 -> RoundedCornerShape(big)
        i == 0 -> RoundedCornerShape(big, big, small, small)
        i == n - 1 -> RoundedCornerShape(small, small, big, big)
        else -> RoundedCornerShape(small)
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, group: HistoryGroup, replay: Boolean, shape: RoundedCornerShape, onClick: () -> Unit) {
    val whenText = if (group == HistoryGroup.Today || group == HistoryGroup.Yesterday) clock(entry.stamp.minuteOfDay) else date(entry.stamp)
    val meta = "$whenText · ${formatTime(entry.durationSeconds)} · ${entry.grade}"
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, color = Color.White) {
        Row(Modifier.padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.topic,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (replay) {
                        Text(
                            stringResource(Res.string.home_replay),
                            modifier = Modifier.background(Color(0xFFECEEF0), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    for (persona in entry.raised) ListenerAvatar(persona, raise = { 1f }, modifier = Modifier.size(28.dp))
                }
                val fixes = entry.fixCount
                Text(
                    if (fixes == 0) stringResource(Res.string.home_all_clear) else stringResource(Res.string.home_fixes, fixes.toString()),
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (fixes == 0) AllClear else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun clock(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = (minuteOfDay % 60).toString().padStart(2, '0')
    val h12 = if (h % 12 == 0) 12 else h % 12
    return stringResource(if (h < 12) Res.string.home_am else Res.string.home_pm, "$h12:$m")
}

@Composable
private fun date(stamp: LocalStamp): String {
    val (_, month, day) = CivilDate.toCivil(stamp.epochDay)
    val weekday = stringResource(Res.string.home_weekdays)[CivilDate.weekday(stamp.epochDay)].toString()
    return stringResource(Res.string.home_date, month.toString(), day.toString(), weekday)
}
