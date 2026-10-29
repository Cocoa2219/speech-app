package dev.cocoa.speech

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.cocoa.speech.results.ResultsRoute
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.SetupRoute

@Composable
fun App(gradeStore: GradeStore) {
    MaterialTheme {
        Surface {
            // Two screens for now; a navigation library can wait until there are more.
            var showResults by rememberSaveable { mutableStateOf(false) }
            if (showResults) {
                ResultsRoute(grade = Grade.resolve(null, mockMode = true), onBack = { showResults = false })
            } else {
                SetupRoute(store = gradeStore, onShowExample = { showResults = true })
            }
        }
    }
}
