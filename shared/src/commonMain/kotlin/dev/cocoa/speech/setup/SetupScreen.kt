package dev.cocoa.speech.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.setup_debug_sample
import speech_app.shared.generated.resources.setup_grade_change
import speech_app.shared.generated.resources.setup_grade_hint
import speech_app.shared.generated.resources.setup_grade_label
import speech_app.shared.generated.resources.setup_no_key
import speech_app.shared.generated.resources.setup_record
import speech_app.shared.generated.resources.setup_show_example
import speech_app.shared.generated.resources.setup_title

// Owns the grade state and persistence; the screen below stays stateless.
// onRecord is null when there is no API key; only the example can be shown then.
@Composable
fun SetupRoute(
    store: GradeStore,
    onRecord: ((grade: String) -> Unit)?,
    onShowExample: () -> Unit,
    onDebugSample: ((grade: String) -> Unit)? = null,
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
        onDebugSample = onDebugSample?.let { sample -> { sample(grade) } },
    )
}

@Composable
fun SetupScreen(
    grade: String,
    onGradeSelected: (String) -> Unit,
    onRecord: (() -> Unit)?,
    onShowExample: () -> Unit,
    onDebugSample: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        GradePicker(grade = grade, onGradeSelected = onGradeSelected)
        Spacer(Modifier.weight(1f))
        if (onRecord != null) {
            Button(onClick = onRecord, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.setup_record))
            }
        } else {
            Text(
                stringResource(Res.string.setup_no_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Mock mode (Layer 0): fixture results, no network. Also the demo safety net.
        OutlinedButton(onClick = onShowExample, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.setup_show_example))
        }
        if (onDebugSample != null) {
            TextButton(onClick = onDebugSample, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.setup_debug_sample))
            }
        }
    }
}

@Composable
private fun GradePicker(grade: String, onGradeSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val changeLabel = stringResource(Res.string.setup_grade_change)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.setup_grade_label), style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$changeLabel: $grade" },
            ) {
                Text(grade)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (option in Grade.options) {
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onGradeSelected(option)
                        },
                    )
                }
            }
        }
        Text(
            stringResource(Res.string.setup_grade_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
