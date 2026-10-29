package dev.cocoa.speech

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.pipeline.LivePipeline
import dev.cocoa.speech.pipeline.SampleCheck
import dev.cocoa.speech.pipeline.Stage
import dev.cocoa.speech.processing.ProcessingRoute
import dev.cocoa.speech.record.Capture
import dev.cocoa.speech.record.Permission
import dev.cocoa.speech.record.RecordRoute
import dev.cocoa.speech.record.Recording
import dev.cocoa.speech.results.ResultsRoute
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.SetupRoute
import kotlinx.coroutines.launch

// A camera record screen supplied by the platform (Android: CameraX in androidMain).
typealias VideoRecordScreen = @Composable (onFinished: (Recording) -> Unit, onCancel: () -> Unit) -> Unit

// Everything the live path needs. Null when there is no API key, so only the example (mock mode) is offered.
// debugSample (debug builds only) loads a pushed audio file in place of the microphone; it returns null if none.
class LiveDeps(
    val recorder: Capture,
    val micPermission: Permission,
    val pipeline: LivePipeline,
    val videoRecord: VideoRecordScreen? = null,
    val debugSample: (() -> Recording?)? = null,
)

// The platform back handler (Android: androidx.activity.compose.BackHandler).
typealias BackHandlerSlot = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

private sealed interface Screen {
    data object Setup : Screen
    data class Record(val grade: String) : Screen
    data class VideoRecord(val grade: String) : Screen
    class Processing(val grade: String, val recording: Recording, val fromSample: Boolean = false) : Screen
    class Results(val grade: String, val live: ResultsReport?) : Screen
}

@Composable
fun App(gradeStore: GradeStore, live: LiveDeps?, backHandler: BackHandlerSlot = { _, _ -> }) {
    MaterialTheme {
        Surface {
            // A handful of screens; a navigation library can wait. Recording bytes and reports are not saveable,
            // so this is plain remember (the activity handles configuration changes itself).
            var screen by remember { mutableStateOf<Screen>(Screen.Setup) }
            val scope = rememberCoroutineScope()
            val home = { screen = Screen.Setup }
            // Back from any screen returns to setup; on setup the system default (leave the app) applies.
            // Leaving the record screens discards the recording; leaving processing abandons the run.
            backHandler(screen != Screen.Setup, home)

            when (val s = screen) {
                Screen.Setup -> SetupRoute(
                    store = gradeStore,
                    onRecord = live?.let { { grade -> screen = Screen.Record(grade) } },
                    onRecordVideo = live?.videoRecord?.let { { grade -> screen = Screen.VideoRecord(grade) } },
                    onShowExample = { screen = Screen.Results(Grade.resolve(null, mockMode = true), live = null) },
                    onDebugSample = live?.debugSample?.let { load ->
                        { grade -> load()?.let { screen = Screen.Processing(grade, it, fromSample = true) } }
                    },
                )
                is Screen.Record -> RecordRoute(
                    capture = live!!.recorder,
                    permission = live.micPermission,
                    onFinished = { screen = Screen.Processing(s.grade, it) },
                    onCancel = home,
                )
                is Screen.VideoRecord -> live!!.videoRecord!!(
                    { screen = Screen.Processing(s.grade, it) },
                    home,
                )
                is Screen.Processing -> {
                    // The local video lives only as long as its run (CLAUDE.md privacy rules).
                    DisposableEffect(s) { onDispose { s.recording.video?.discard() } }
                    ProcessingRoute(
                        stages = if (s.recording.video != null) Stage.entries else Stage.entries - Stage.VIDEO,
                        createRun = { live!!.pipeline.newRun(s.recording, s.grade) },
                        onDone = { report ->
                            if (s.fromSample) scope.launch { runCatching { SampleCheck.log(report) } }
                            screen = Screen.Results(s.grade, report)
                        },
                        onRerecord = {
                            screen = if (s.recording.video != null) Screen.VideoRecord(s.grade) else Screen.Record(s.grade)
                        },
                        onHome = home,
                    )
                }
                is Screen.Results -> ResultsRoute(grade = s.grade, live = s.live, onBack = home)
            }
        }
    }
}
