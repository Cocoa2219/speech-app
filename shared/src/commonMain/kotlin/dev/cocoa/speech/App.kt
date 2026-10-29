package dev.cocoa.speech

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.pipeline.LivePipeline
import dev.cocoa.speech.processing.ProcessingRoute
import dev.cocoa.speech.record.AudioRecorder
import dev.cocoa.speech.record.MicPermission
import dev.cocoa.speech.record.RecordRoute
import dev.cocoa.speech.record.Recording
import dev.cocoa.speech.results.ResultsRoute
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.SetupRoute

// Everything the live path needs. Null when there is no API key, so only the example (mock mode) is offered.
class LiveDeps(val recorder: AudioRecorder, val permission: MicPermission, val pipeline: LivePipeline)

private sealed interface Screen {
    data object Setup : Screen
    data class Record(val grade: String) : Screen
    class Processing(val grade: String, val recording: Recording) : Screen
    class Results(val grade: String, val live: ResultsReport?) : Screen
}

@Composable
fun App(gradeStore: GradeStore, live: LiveDeps?) {
    MaterialTheme {
        Surface {
            // A handful of screens; a navigation library can wait. Recording bytes and reports are not saveable,
            // so this is plain remember (the activity handles configuration changes itself).
            var screen by remember { mutableStateOf<Screen>(Screen.Setup) }
            val home = { screen = Screen.Setup }
            when (val s = screen) {
                Screen.Setup -> SetupRoute(
                    store = gradeStore,
                    onRecord = live?.let { { grade -> screen = Screen.Record(grade) } },
                    onShowExample = { screen = Screen.Results(Grade.resolve(null, mockMode = true), live = null) },
                )
                is Screen.Record -> RecordRoute(
                    recorder = live!!.recorder,
                    permission = live.permission,
                    onFinished = { screen = Screen.Processing(s.grade, it) },
                    onCancel = home,
                )
                is Screen.Processing -> ProcessingRoute(
                    createRun = {
                        live!!.pipeline.newRun(s.recording.audio, s.recording.mimeType, s.recording.durationSeconds, s.grade)
                    },
                    onDone = { screen = Screen.Results(s.grade, it) },
                    onRerecord = { screen = Screen.Record(s.grade) },
                    onHome = home,
                )
                is Screen.Results -> ResultsRoute(grade = s.grade, live = s.live, onBack = home)
            }
        }
    }
}
