package dev.cocoa.speech.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.record.ReplayMedia
import speech_app.shared.generated.resources.Res
import kotlin.coroutines.cancellation.CancellationException

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Loaded(val report: ResultsReport) : ResultsUiState
    data object Failed : ResultsUiState
}

// Mock mode (Layer 0): results come from the photosynthesis fixture, never the network.
object MockResults {
    private const val DIR = "files/fixtures/photosynthesis"

    suspend fun load(): ResultsReport {
        val analyzer = AppJson.decodeFromString<AnalyzerOutput>(Res.readBytes("$DIR/analyzer.json").decodeToString())
        val events = AppJson.decodeFromString<List<PersonaEvent>>(Res.readBytes("$DIR/events.json").decodeToString())
        return ResultsReport.build(analyzer, events)
    }
}

// A seek target in seconds. seq changes on every tap, so tapping the same pin twice seeks twice.
data class SeekRequest(val t: Double, val seq: Int)

// Plays the recording and reports its position (Android: VideoView / MediaPlayer in androidMain).
typealias ReplayPlayer = @Composable (media: ReplayMedia, seek: SeekRequest?, onPosition: (Double) -> Unit) -> Unit

// live is the report from a real recording; null shows the example (mock mode). With replay and player, the recording
// plays back and the timeline playhead follows it; tapping a pin or card seeks the player.
@Composable
fun ResultsRoute(
    grade: String,
    live: ResultsReport?,
    onBack: () -> Unit,
    replay: ReplayMedia? = null,
    player: ReplayPlayer? = null,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<ResultsUiState>(live?.let { ResultsUiState.Loaded(it) } ?: ResultsUiState.Loading, attempt, live) {
        if (live != null) return@produceState
        value = ResultsUiState.Loading
        value = try {
            ResultsUiState.Loaded(MockResults.load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultsUiState.Failed
        }
    }
    // Playback position in seconds. Leaves read it through the lambda so only they re-run when it moves.
    var position by remember { mutableDoubleStateOf(0.0) }
    var seek by remember { mutableStateOf<SeekRequest?>(null) }

    ResultsScreen(
        state = state,
        grade = grade,
        isExample = live == null,
        position = { position },
        onSeek = {
            position = it
            seek = SeekRequest(it, (seek?.seq ?: 0) + 1)
        },
        replayPlayer = if (replay != null && player != null) {
            { player(replay, seek) { position = it } }
        } else {
            null
        },
        onRetry = { attempt++ },
        onBack = onBack,
    )
}
