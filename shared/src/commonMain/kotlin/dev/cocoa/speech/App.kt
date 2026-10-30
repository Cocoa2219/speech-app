package dev.cocoa.speech

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.cocoa.speech.history.HistoryClock
import dev.cocoa.speech.history.HistoryEntry
import dev.cocoa.speech.history.HistoryStore
import dev.cocoa.speech.history.LocalStamp
import dev.cocoa.speech.home.HomeScreen
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.onboarding.OnboardingScreen
import dev.cocoa.speech.onboarding.OnboardingStore
import dev.cocoa.speech.pipeline.LivePipeline
import dev.cocoa.speech.pipeline.SampleCheck
import dev.cocoa.speech.pipeline.Stage
import dev.cocoa.speech.processing.ProcessingRoute
import dev.cocoa.speech.record.Capture
import dev.cocoa.speech.record.Permission
import dev.cocoa.speech.record.RecordRoute
import dev.cocoa.speech.record.Recording
import dev.cocoa.speech.record.ReplayMedia
import dev.cocoa.speech.results.ReplayPlayer
import dev.cocoa.speech.results.ResultsRoute
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.SetupRoute
import dev.cocoa.speech.setup.TalkSetup
import dev.cocoa.speech.setup.MaterialPicker
import dev.cocoa.speech.theme.AppTheme
import dev.cocoa.speech.theme.motionSchemeEffects
import dev.cocoa.speech.theme.motionSchemeSpatial
import kotlinx.coroutines.launch
import kotlin.math.sign

// A camera record screen supplied by the platform (Android: CameraX in androidMain). onVoiceOnly switches to a
// microphone-only recording when the camera is refused.
typealias VideoRecordScreen = @Composable (onFinished: (Recording) -> Unit, onCancel: () -> Unit, onVoiceOnly: () -> Unit) -> Unit

// Everything the live path needs. Null when there is no API key, so only the example (mock mode) is offered.
// Debug builds only: debugSample loads a pushed audio file in place of the microphone, and debugLast reloads the kept
// latest recording for re-analysis (answered from the answer cache unless a prompt changed). Both return null if none.
class LiveDeps(
    val recorder: Capture,
    val micPermission: Permission,
    val pipeline: LivePipeline,
    val videoRecord: VideoRecordScreen? = null,
    val replayPlayer: ReplayPlayer? = null,
    val pickMaterial: MaterialPicker? = null,
    val debugSample: (() -> Recording?)? = null,
    val debugLast: (() -> Recording?)? = null,
)

// Back stack keys. Recording bytes and reports are not saveable, so the stack is plain remembered state (the activity
// handles configuration changes itself); entries' own rememberSaveable state survives while they are on the stack.
private sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Setup : Screen
    data class Record(val setup: TalkSetup) : Screen
    data class VideoRecord(val setup: TalkSetup) : Screen
    class Processing(val setup: TalkSetup, val recording: Recording, val fromSample: Boolean = false) : Screen

    // setup is set for a run that just finished (record again keeps its grade and mode); topic names the run.
    class Results(
        val grade: String,
        val live: ResultsReport?,
        val replay: ReplayMedia? = null,
        val topic: String? = null,
        val setup: TalkSetup? = null,
    ) : Screen
}

// How deep a screen sits in the flow. Carried in each entry's metadata so the transition knows which way to move.
private val Screen.depth: Int
    get() = when (this) {
        Screen.Onboarding -> 0
        Screen.Home -> 1
        Screen.Setup -> 2
        is Screen.Record, is Screen.VideoRecord -> 3
        is Screen.Processing -> 4
        is Screen.Results -> 5
    }

private const val DEPTH = "depth"

private fun Scene<Screen>.depth(): Int = entries.lastOrNull()?.metadata?.get(DEPTH) as? Int ?: 0

// Shared axis: a deeper screen slides in from the right and fades in, a shallower one comes from the left, and a swap
// at the same depth (video -> voice only) just fades. Decided by depth rather than by push vs pop, because finishing a
// run replaces the stack ([home, setup, processing] -> [home, results]) and that is still a step forward.
private fun AnimatedContentTransitionScope<Scene<Screen>>.sharedAxis(
    slide: FiniteAnimationSpec<IntOffset>,
    fade: FiniteAnimationSpec<Float>,
    forceBack: Boolean = false,
): ContentTransform {
    val dir = if (forceBack) -1 else (targetState.depth() - initialState.depth()).sign
    return (slideInHorizontally(slide) { w -> dir * w / 5 } + fadeIn(fade)) togetherWith
        (slideOutHorizontally(slide) { w -> -dir * w / 5 } + fadeOut(fade))
}

// history and clock: past runs on the device (null in previews). onboarding: the first-run flag.
@Composable
fun App(
    gradeStore: GradeStore,
    live: LiveDeps?,
    history: HistoryStore? = null,
    clock: HistoryClock? = null,
    onboarding: OnboardingStore? = null,
) {
    AppTheme {
        Surface {
            val backStack = remember {
                mutableStateListOf<Screen>(if (onboarding?.seen() == false) Screen.Onboarding else Screen.Home)
            }
            val scope = rememberCoroutineScope()

            fun push(screen: Screen) {
                backStack.add(screen)
            }
            fun replaceTop(screen: Screen) {
                backStack[backStack.lastIndex] = screen
            }
            fun resetTo(vararg screens: Screen) {
                Snapshot.withMutableSnapshot {
                    backStack.clear()
                    backStack.addAll(screens)
                }
            }
            val home = { resetTo(Screen.Home) }

            // Back never lands on a screen that would redo work: from results it goes home, and from processing it
            // skips the finished recording and returns to setup. Leaving a record screen discards the recording.
            // On home and onboarding the stack has one entry, so the system default (leave the app) applies.
            fun back() {
                when (backStack.last()) {
                    is Screen.Results -> home()
                    is Screen.Processing -> Snapshot.withMutableSnapshot {
                        backStack.removeAt(backStack.lastIndex)
                        while (backStack.size > 1 && backStack.last().let { it is Screen.Record || it is Screen.VideoRecord }) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    }
                    else -> if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }
            }

            fun record(setup: TalkSetup, video: Boolean) =
                if (video && live?.videoRecord != null) Screen.VideoRecord(setup) else Screen.Record(setup)

            val slide = MaterialTheme.motionSchemeSpatial<IntOffset>()
            val fade = MaterialTheme.motionSchemeEffects<Float>()
            NavDisplay(
                backStack = backStack,
                onBack = ::back,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = { sharedAxis(slide, fade) },
                popTransitionSpec = { sharedAxis(slide, fade) },
                // Predictive back always reveals the previous screen, so it always moves backwards.
                predictivePopTransitionSpec = { sharedAxis(slide, fade, forceBack = true) },
                entryProvider = entryProvider {
                    entry<Screen.Onboarding>(metadata = { mapOf(DEPTH to it.depth) }) {
                        OnboardingScreen(onDone = {
                            onboarding?.markSeen()
                            replaceTop(Screen.Home)
                        })
                    }
                    entry<Screen.Home>(metadata = { mapOf(DEPTH to it.depth) }) {
                        // Re-read on every visit, so a run that just finished is listed.
                        val entries = remember { history?.list().orEmpty() }
                        val today = remember { clock?.now() ?: LocalStamp("1970-01-01", 0) }
                        HomeScreen(
                            entries = entries,
                            today = today,
                            onNew = live?.let { { push(Screen.Setup) } },
                            onOpen = { entry ->
                                // Only the newest run can still have its recording (ReplayFiles keeps one).
                                val newest = entries.maxByOrNull { it.stamp }
                                push(
                                    Screen.Results(
                                        grade = entry.grade,
                                        live = entry.report(),
                                        replay = if (entry.id == newest?.id) entry.replay else null,
                                        topic = entry.topic,
                                    ),
                                )
                            },
                            onShowExample = { push(Screen.Results(Grade.resolve(null, mockMode = true), live = null)) },
                        )
                    }
                    entry<Screen.Setup>(metadata = { mapOf(DEPTH to it.depth) }) {
                        SetupRoute(
                            store = gradeStore,
                            onBack = ::back,
                            onRecord = live?.let { { setup -> push(record(setup, video = false)) } },
                            onRecordVideo = live?.videoRecord?.let { { setup -> push(record(setup, video = true)) } },
                            pickMaterial = live?.pickMaterial,
                            onDebugSample = live?.debugSample?.let { load ->
                                { setup -> load()?.let { push(Screen.Processing(setup, it, fromSample = true)) } }
                            },
                            onDebugLast = live?.debugLast?.let { load ->
                                { setup -> load()?.let { push(Screen.Processing(setup, it)) } }
                            },
                        )
                    }
                    entry<Screen.Record>(metadata = { mapOf(DEPTH to it.depth) }) { s ->
                        RecordRoute(
                            capture = live!!.recorder,
                            permission = live.micPermission,
                            onFinished = { replaceTop(Screen.Processing(s.setup, it)) },
                            onCancel = ::back,
                        )
                    }
                    entry<Screen.VideoRecord>(metadata = { mapOf(DEPTH to it.depth) }) { s ->
                        live!!.videoRecord!!(
                            { replaceTop(Screen.Processing(s.setup, it)) },
                            ::back,
                            { replaceTop(Screen.Record(s.setup)) },
                        )
                    }
                    entry<Screen.Processing>(metadata = { mapOf(DEPTH to it.depth) }) { s ->
                        ProcessingRoute(
                            stages = if (s.recording.video != null) Stage.entries else Stage.entries - Stage.VIDEO,
                            createRun = { live!!.pipeline.newRun(s.recording, s.setup) },
                            onDone = { report ->
                                if (s.fromSample) scope.launch { runCatching { SampleCheck.log(report) } }
                                if (history != null && clock != null) {
                                    val entry = HistoryEntry.of(
                                        clock.newId(), s.setup.topic, s.setup.grade, s.setup.memo, clock.now(), report, s.recording.replay,
                                    )
                                    runCatching { history.save(entry) }.onFailure { println("History: save failed: ${it::class.simpleName}") }
                                }
                                // The finished run's screens leave the stack, so back from results goes home.
                                resetTo(
                                    Screen.Home,
                                    Screen.Results(s.setup.grade, report, s.recording.replay, s.setup.topic.ifBlank { null }, s.setup),
                                )
                            },
                            onRerecord = { replaceTop(record(s.setup, video = s.recording.video != null)) },
                            onHome = home,
                        )
                    }
                    entry<Screen.Results>(metadata = { mapOf(DEPTH to it.depth) }) { s ->
                        ResultsRoute(
                            grade = s.grade,
                            live = s.live,
                            onBack = ::back,
                            // "다른 주제로 녹음하기": a run that just finished goes straight to recording in the same mode and
                            // grade, with the topic cleared; a reopened one goes through setup. Not offered for the example.
                            onRecordAgain = when {
                                s.live == null || live == null -> null
                                s.setup != null -> {
                                    { resetTo(Screen.Home, record(s.setup.copy(topic = "", memo = ""), video = s.replay?.isVideo == true)) }
                                }
                                else -> { { resetTo(Screen.Home, Screen.Setup) } }
                            },
                            replay = s.replay,
                            player = live?.replayPlayer,
                            topic = s.topic,
                        )
                    }
                },
            )
        }
    }
}
