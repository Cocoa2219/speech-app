package dev.cocoa.speech.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.setup_back
import speech_app.shared.generated.resources.setup_debug_last
import speech_app.shared.generated.resources.setup_debug_sample
import speech_app.shared.generated.resources.setup_grade_hint
import speech_app.shared.generated.resources.setup_grade_label
import speech_app.shared.generated.resources.setup_memo
import speech_app.shared.generated.resources.setup_memo_count
import speech_app.shared.generated.resources.setup_memo_file
import speech_app.shared.generated.resources.setup_memo_loaded
import speech_app.shared.generated.resources.setup_memo_truncated
import speech_app.shared.generated.resources.setup_memo_hint
import speech_app.shared.generated.resources.setup_optional
import speech_app.shared.generated.resources.setup_record
import speech_app.shared.generated.resources.setup_record_video
import speech_app.shared.generated.resources.setup_school_adult
import speech_app.shared.generated.resources.setup_school_elementary
import speech_app.shared.generated.resources.setup_school_group
import speech_app.shared.generated.resources.setup_school_high
import speech_app.shared.generated.resources.setup_school_middle
import speech_app.shared.generated.resources.setup_title
import speech_app.shared.generated.resources.setup_topic
import speech_app.shared.generated.resources.setup_topic_hint
import speech_app.shared.generated.resources.setup_year
import speech_app.shared.generated.resources.setup_year_group

// What the student set up before recording. The topic names the history entry; the memo is kept with the run.
// TODO: pass memo to the analyzer and persona prompts (CLAUDE.md open question "자료/메모"); not wired yet.
data class TalkSetup(val grade: String, val topic: String = "", val memo: String = "")

const val MEMO_MAX_CHARS = 2000

// Opens the platform file picker and hands back the file's name and its text: a PowerPoint deck's slide text
// (SlideText) or a plain text file. Nothing happens if the user cancels or the file cannot be read.
typealias TextFilePicker = (onPicked: (name: String, text: String) -> Unit) -> Unit

// A loaded file is added after what is already in the memo, and the whole memo is cut at the limit.
internal fun mergeMemo(memo: String, text: String): String =
    (if (memo.isBlank()) text.trim() else memo.trimEnd() + "\n\n" + text.trim()).take(MEMO_MAX_CHARS)

// Owns the grade (persisted), topic and memo; the screen below stays stateless.
// onRecord is null when there is no API key; home does not open setup then.
@Composable
fun SetupRoute(
    store: GradeStore,
    onBack: () -> Unit,
    onRecord: ((TalkSetup) -> Unit)?,
    onRecordVideo: ((TalkSetup) -> Unit)? = null,
    pickTextFile: TextFilePicker? = null,
    onDebugSample: ((TalkSetup) -> Unit)? = null,
    onDebugLast: ((TalkSetup) -> Unit)? = null,
) {
    // Saveable, so coming back from the record screen keeps what was typed.
    var grade by rememberSaveable { mutableStateOf(Grade.resolve(store.load())) }
    var topic by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var loadedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var truncated by rememberSaveable { mutableStateOf(false) }
    val setup = { TalkSetup(grade, topic.trim(), memo.trim()) }
    SetupScreen(
        grade = grade,
        topic = topic,
        memo = memo,
        onGradeSelected = {
            grade = it
            store.save(it)
        },
        onTopicChange = { topic = it },
        onMemoChange = { memo = it.take(MEMO_MAX_CHARS) },
        loadedFile = loadedFile,
        truncated = truncated,
        onPickFile = pickTextFile?.let { pick ->
            {
                pick { name, text ->
                    truncated = (memo.length + text.length) > MEMO_MAX_CHARS
                    memo = mergeMemo(memo, text)
                    loadedFile = name
                }
            }
        },
        onBack = onBack,
        onRecord = onRecord?.let { record -> { record(setup()) } },
        onRecordVideo = onRecordVideo?.let { record -> { record(setup()) } },
        onDebugSample = onDebugSample?.let { sample -> { sample(setup()) } },
        onDebugLast = onDebugLast?.let { last -> { last(setup()) } },
    )
}

// Canvas board K: topic, then the grade as a school (joined buttons) and a year (pills), then optional notes that
// open from a dashed button. The actions stay at the bottom; everything above scrolls, and moves up with the keyboard.
@Composable
fun SetupScreen(
    grade: String,
    topic: String,
    memo: String,
    onGradeSelected: (String) -> Unit,
    onTopicChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onBack: () -> Unit,
    loadedFile: String? = null,
    truncated: Boolean = false,
    onPickFile: (() -> Unit)? = null,
    onRecord: (() -> Unit)?,
    onRecordVideo: (() -> Unit)? = null,
    onDebugSample: (() -> Unit)? = null,
    onDebugLast: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(Res.string.setup_back)
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                GlyphIcon(Glyph.Back, MaterialTheme.colorScheme.onSurface, size = 24.dp)
            }
            Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            TopicField(topic, onTopicChange)
            GradePicker(grade, onGradeSelected)
            MemoField(memo, onMemoChange, loadedFile, truncated, onPickFile)
        }
        Actions(onRecord, onRecordVideo, onDebugSample, onDebugLast)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
    unfocusedBorderColor = Color.Transparent,
)

@Composable
private fun SectionLabel(label: StringResource, optional: Boolean = false, trailing: String? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            stringResource(label),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (optional) {
            Text(
                " " + stringResource(Res.string.setup_optional),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopicField(topic: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(Res.string.setup_topic)
        OutlinedTextField(
            value = topic,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.setup_topic_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
    }
}

// Always open: a text box with a character count, and the file button under it.
@Composable
private fun MemoField(
    memo: String,
    onChange: (String) -> Unit,
    loadedFile: String?,
    truncated: Boolean,
    onPickFile: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(
            Res.string.setup_memo,
            optional = true,
            trailing = stringResource(Res.string.setup_memo_count, memo.length.toString(), "2,000"),
        )
        OutlinedTextField(
            value = memo,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text(stringResource(Res.string.setup_memo_hint)) },
            minLines = 4,
            maxLines = 10,
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors(),
        )
        if (onPickFile != null) FileRow(loadedFile, truncated, onPickFile)
    }
}

// "발표 자료 불러오기" under the memo; after a load it names the file, and says so when the memo hit the limit.
@Composable
private fun FileRow(loadedFile: String?, truncated: Boolean, onPickFile: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPickFile, contentPadding = PaddingValues(horizontal = 8.dp)) {
            GlyphIcon(Glyph.File, MaterialTheme.colorScheme.onSurface, size = 20.dp)
            Text(
                stringResource(Res.string.setup_memo_file),
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (loadedFile != null) {
            Text(
                if (truncated) stringResource(Res.string.setup_memo_truncated, loadedFile, "2,000")
                else stringResource(Res.string.setup_memo_loaded, loadedFile),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GradePicker(grade: String, onGradeSelected: (String) -> Unit) {
    val school = Grade.schoolOf(grade)
    val year = Grade.yearOf(grade)
    val levels: List<Pair<Grade.School?, StringResource>> = Grade.schools.zip(
        listOf(Res.string.setup_school_elementary, Res.string.setup_school_middle, Res.string.setup_school_high),
    ) + (null to Res.string.setup_school_adult)
    val schoolGroup = stringResource(Res.string.setup_school_group)
    val yearGroup = stringResource(Res.string.setup_year_group)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(Res.string.setup_grade_label)
        // Joined buttons: outer corners round, inner ones tight; the selected one turns fully round.
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = schoolGroup },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            levels.forEachIndexed { i, (option, label) ->
                val checked = option?.name == school?.name
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onGradeSelected(Grade.of(option, year)) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        levels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(stringResource(label), fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
        if (school != null) {
            // Year pills, three to a row: one row for 중학교/고등학교, two for 초등학교.
            Column(
                Modifier.semantics { contentDescription = yearGroup },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..school.years).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { n ->
                            val checked = n == year
                            ToggleButton(
                                checked = checked,
                                onCheckedChange = { onGradeSelected(Grade.of(school, n)) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shapes = ToggleButtonDefaults.shapes(
                                    shape = RoundedCornerShape(22.dp),
                                    pressedShape = RoundedCornerShape(10.dp),
                                    checkedShape = RoundedCornerShape(14.dp),
                                ),
                                colors = ToggleButtonDefaults.toggleButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                border = if (checked) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    stringResource(Res.string.setup_year, n.toString()),
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                        // Keep pill widths equal when the last row is short.
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        val hint = stringResource(Res.string.setup_grade_hint, grade)
        val emphasis = MaterialTheme.colorScheme.onSurface
        Text(
            buildAnnotatedString {
                val at = hint.indexOf(grade)
                if (at < 0) {
                    append(hint)
                } else {
                    append(hint.substring(0, at))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = emphasis)) { append(grade) }
                    append(hint.substring(at + grade.length))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Actions(
    onRecord: (() -> Unit)?,
    onRecordVideo: (() -> Unit)?,
    onDebugSample: (() -> Unit)?,
    onDebugLast: (() -> Unit)?,
) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Video is the main path (listeners react to what is shown); voice only is the fallback.
        if (onRecordVideo != null) {
            PrimaryAction(Glyph.Camera, Res.string.setup_record_video, onRecordVideo)
            onRecord?.let { SecondaryAction(Glyph.Mic, Res.string.setup_record, it) }
        } else if (onRecord != null) {
            PrimaryAction(Glyph.Mic, Res.string.setup_record, onRecord)
        }
        // Debug builds only.
        if (onDebugSample != null || onDebugLast != null) {
            Row(Modifier.fillMaxWidth()) {
                onDebugSample?.let { DebugButton(Res.string.setup_debug_sample, it, Modifier.weight(1f)) }
                onDebugLast?.let { DebugButton(Res.string.setup_debug_last, it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PrimaryAction(glyph: Glyph, label: StringResource, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        GlyphIcon(glyph, MaterialTheme.colorScheme.onPrimary)
        Text(
            stringResource(label),
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SecondaryAction(glyph: Glyph, label: StringResource, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        GlyphIcon(glyph, MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            stringResource(label),
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DebugButton(label: StringResource, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
