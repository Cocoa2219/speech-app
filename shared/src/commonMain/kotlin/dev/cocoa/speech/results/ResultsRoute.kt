package dev.cocoa.speech.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.model.AnalyzerOutput
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.PersonaEvent
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

@Composable
fun ResultsRoute(grade: String, onBack: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<ResultsUiState>(ResultsUiState.Loading, attempt) {
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

    ResultsScreen(
        state = state,
        grade = grade,
        position = { position },
        onSeek = { position = it },
        onRetry = { attempt++ },
        onBack = onBack,
    )
}
