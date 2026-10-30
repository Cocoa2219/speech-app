package dev.cocoa.speech.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.listeners.AnimatedListenerAvatar
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import dev.cocoa.speech.theme.motionSchemeSpatial
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.persona_classmate
import speech_app.shared.generated.resources.persona_kid
import speech_app.shared.generated.resources.persona_teacher
import speech_app.shared.generated.resources.role_classmate
import speech_app.shared.generated.resources.role_kid
import speech_app.shared.generated.resources.role_teacher
import speech_app.shared.generated.resources.setup_debug_last
import speech_app.shared.generated.resources.setup_debug_sample
import speech_app.shared.generated.resources.setup_grade_hint
import speech_app.shared.generated.resources.setup_grade_label
import speech_app.shared.generated.resources.setup_no_key
import speech_app.shared.generated.resources.setup_record
import speech_app.shared.generated.resources.setup_record_video
import speech_app.shared.generated.resources.setup_school_adult
import speech_app.shared.generated.resources.setup_school_elementary
import speech_app.shared.generated.resources.setup_school_group
import speech_app.shared.generated.resources.setup_school_high
import speech_app.shared.generated.resources.setup_school_middle
import speech_app.shared.generated.resources.setup_show_example
import speech_app.shared.generated.resources.setup_title
import speech_app.shared.generated.resources.setup_year
import speech_app.shared.generated.resources.setup_year_group

// Owns the grade state and persistence; the screen below stays stateless.
// onRecord is null when there is no API key; only the example can be shown then.
@Composable
fun SetupRoute(
    store: GradeStore,
    onRecord: ((grade: String) -> Unit)?,
    onShowExample: () -> Unit,
    onRecordVideo: ((grade: String) -> Unit)? = null,
    onDebugSample: ((grade: String) -> Unit)? = null,
    onDebugLast: ((grade: String) -> Unit)? = null,
) {
    var grade by remember { mutableStateOf(Grade.resolve(store.load())) }
    SetupScreen(
        grade = grade,
        onGradeSelected = {
            grade = it
            store.save(it)
        },
        onRecord = onRecord?.let { record -> { record(grade) } },
        onShowExample = onShowExample,
        onRecordVideo = onRecordVideo?.let { record -> { record(grade) } },
        onDebugSample = onDebugSample?.let { sample -> { sample(grade) } },
        onDebugLast = onDebugLast?.let { last -> { last(grade) } },
    )
}

// Designed on the "Listener characters" canvas, board E: the three listeners greet, the grade is a school (joined
// buttons) then a year (pills), and the actions sit at the bottom. Everything above the actions scrolls on short screens.
@Composable
fun SetupScreen(
    grade: String,
    onGradeSelected: (String) -> Unit,
    onRecord: (() -> Unit)?,
    onShowExample: () -> Unit,
    onRecordVideo: (() -> Unit)? = null,
    onDebugSample: (() -> Unit)? = null,
    onDebugLast: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 16.dp)) {
            Text(
                stringResource(Res.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            ListenerCast(Modifier.padding(top = 24.dp))
            GradePicker(grade, onGradeSelected, Modifier.padding(top = 28.dp))
        }
        Actions(onRecord, onShowExample, onRecordVideo, onDebugSample, onDebugLast)
    }
}

// Tapping a listener makes them raise a hand, the same morph as on the results screen.
@Composable
private fun ListenerCast(modifier: Modifier) {
    val waving = remember { mutableStateMapOf<Persona, Boolean>() }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 16.dp)) {
            for (persona in Persona.entries) {
                val up = waving[persona] == true
                val scale by animateFloatAsState(if (up) 1.08f else 1f, MaterialTheme.motionSchemeSpatial(), label = "wave")
                val name = stringResource(personaName(persona))
                val role = stringResource(personaRole(persona))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) { waving[persona] = !up }
                        .semantics { contentDescription = "$name, $role" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedListenerAvatar(
                        persona,
                        up,
                        Modifier.size(84.dp).graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GradePicker(grade: String, onGradeSelected: (String) -> Unit, modifier: Modifier) {
    val school = Grade.schoolOf(grade)
    val year = Grade.yearOf(grade)
    val levels: List<Pair<Grade.School?, StringResource>> = Grade.schools.zip(
        listOf(Res.string.setup_school_elementary, Res.string.setup_school_middle, Res.string.setup_school_high),
    ) + (null to Res.string.setup_school_adult)
    val schoolGroup = stringResource(Res.string.setup_school_group)
    val yearGroup = stringResource(Res.string.setup_year_group)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.setup_grade_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    onShowExample: () -> Unit,
    onRecordVideo: (() -> Unit)?,
    onDebugSample: (() -> Unit)?,
    onDebugLast: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onRecord != null) {
            Button(onClick = onRecord, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                GlyphIcon(Glyph.Mic, MaterialTheme.colorScheme.onPrimary)
                Text(
                    stringResource(Res.string.setup_record),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (onRecordVideo != null) {
                FilledTonalButton(onClick = onRecordVideo, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    GlyphIcon(Glyph.Camera, MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        stringResource(Res.string.setup_record_video),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Text(
                stringResource(Res.string.setup_no_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Mock mode (Layer 0): fixture results, no network. Also the demo safety net.
        TextButton(onClick = onShowExample, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(Res.string.setup_show_example), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

private fun personaName(persona: Persona) = when (persona) {
    Persona.CLASSMATE -> Res.string.persona_classmate
    Persona.TEACHER -> Res.string.persona_teacher
    Persona.KID -> Res.string.persona_kid
}

private fun personaRole(persona: Persona) = when (persona) {
    Persona.CLASSMATE -> Res.string.role_classmate
    Persona.TEACHER -> Res.string.role_teacher
    Persona.KID -> Res.string.role_kid
}
