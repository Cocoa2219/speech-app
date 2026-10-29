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
import speech_app.shared.generated.resources.setup_grade_change
import speech_app.shared.generated.resources.setup_grade_hint
import speech_app.shared.generated.resources.setup_grade_label
import speech_app.shared.generated.resources.setup_show_example
import speech_app.shared.generated.resources.setup_title

// Owns the grade state and persistence; the screen below stays stateless.
@Composable
fun SetupRoute(store: GradeStore, onShowExample: () -> Unit) {
    var grade by remember { mutableStateOf(Grade.resolve(store.load())) }
    SetupScreen(
        grade = grade,
        onGradeSelected = {
            grade = it
            store.save(it)
        },
        onShowExample = onShowExample,
    )
}

@Composable
fun SetupScreen(grade: String, onGradeSelected: (String) -> Unit, onShowExample: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        GradePicker(grade = grade, onGradeSelected = onGradeSelected)
        Spacer(Modifier.weight(1f))
        // Mock mode (Layer 0) until recording exists.
        Button(onClick = onShowExample, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.setup_show_example))
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
