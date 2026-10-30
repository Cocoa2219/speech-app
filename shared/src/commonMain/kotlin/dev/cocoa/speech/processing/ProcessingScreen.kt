package dev.cocoa.speech.processing

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.listeners.ListeningAvatar
import dev.cocoa.speech.logic.ResultsReport
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.pipeline.AnalysisRun
import dev.cocoa.speech.pipeline.Stage
import dev.cocoa.speech.pipeline.StageFailure
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import dev.cocoa.speech.theme.motionSchemeEffects
import dev.cocoa.speech.theme.motionSchemeSpatial
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.persona_classmate
import speech_app.shared.generated.resources.persona_kid
import speech_app.shared.generated.resources.persona_teacher
import speech_app.shared.generated.resources.processing_failed_title
import speech_app.shared.generated.resources.processing_hint
import speech_app.shared.generated.resources.processing_home
import speech_app.shared.generated.resources.processing_line_analyze
import speech_app.shared.generated.resources.processing_line_transcribe
import speech_app.shared.generated.resources.processing_line_video
import speech_app.shared.generated.resources.processing_line_write
import speech_app.shared.generated.resources.processing_no_speech_title
import speech_app.shared.generated.resources.processing_rerecord
import speech_app.shared.generated.resources.processing_stages
import speech_app.shared.generated.resources.processing_title
import speech_app.shared.generated.resources.results_retry
import speech_app.shared.generated.resources.stage_analyze
import speech_app.shared.generated.resources.stage_done
import speech_app.shared.generated.resources.stage_failed
import speech_app.shared.generated.resources.stage_transcribe
import speech_app.shared.generated.resources.stage_video
import speech_app.shared.generated.resources.stage_write
import kotlin.coroutines.cancellation.CancellationException

// Runs the pipeline for one recording. The run is created once and kept across retries, so a retry resumes at the
// failed stage. createRun reads the prompts; if that fails it is reported as a transcription failure and retried.
@Composable
fun ProcessingRoute(
    stages: List<Stage>,
    createRun: suspend () -> AnalysisRun,
    onDone: (ResultsReport) -> Unit,
    onRerecord: () -> Unit,
    onHome: () -> Unit,
) {
    var run by remember { mutableStateOf<AnalysisRun?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(Stage.TRANSCRIBE) }
    var failure by remember { mutableStateOf<StageFailure?>(null) }

    LaunchedEffect(attempt) {
        failure = null
        try {
            val r = run ?: createRun().also { run = it }
            onDone(r.run { current = it })
        } catch (e: CancellationException) {
            throw e
        } catch (e: StageFailure) {
            failure = e
        } catch (e: Exception) {
            failure = StageFailure(current, cause = e)
        }
    }

    ProcessingScreen(
        stages = stages,
        current = current,
        failure = failure,
        onRetry = { attempt++ },
        onRerecord = onRerecord,
        onHome = onHome,
    )
}

// Designed on the "Listener characters" canvas, board G: an expressive loading shape, a line for the current stage,
// the listeners waking one by one as stages finish, and the stages on a card. A failure keeps finished stages done.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProcessingScreen(
    stages: List<Stage>,
    current: Stage,
    failure: StageFailure?,
    onRetry: () -> Unit,
    onRerecord: () -> Unit,
    onHome: () -> Unit,
) {
    val index = stages.indexOf(current).coerceAtLeast(0)
    // Listeners awake: one more each time a share of the stages is done.
    val awake = (index * Persona.entries.size + stages.size / 2) / stages.size.coerceAtLeast(1)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(Res.string.processing_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().padding(top = 20.dp).height(104.dp), contentAlignment = Alignment.Center) {
                if (failure == null) {
                    LoadingIndicator(Modifier.size(96.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Box(
                        Modifier.size(88.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            Text(
                stringResource(
                    when {
                        failure?.noSpeech == true -> Res.string.processing_no_speech_title
                        failure != null -> Res.string.processing_failed_title
                        else -> stageLine(current)
                    },
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.processing_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                for (persona in Persona.entries) {
                    val on = persona.ordinal < awake
                    val alpha by animateFloatAsState(if (on) 1f else 0.4f, MaterialTheme.motionSchemeEffects(), label = "awake")
                    Column(
                        Modifier.weight(1f).graphicsLayer { this.alpha = alpha },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ListeningAvatar(persona, listening = on && failure == null, modifier = Modifier.size(68.dp))
                        Text(
                            stringResource(personaName(persona)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            StageCard(stages, index, failed = failure != null, modifier = Modifier.padding(top = 24.dp))
        }
        if (failure != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onHome, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(Res.string.processing_home), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = if (failure.noSpeech) onRerecord else onRetry, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(
                        stringResource(if (failure.noSpeech) Res.string.processing_rerecord else Res.string.results_retry),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StageCard(stages: List<Stage>, index: Int, failed: Boolean, modifier: Modifier) {
    val label = stringResource(Res.string.processing_stages)
    Surface(
        modifier.fillMaxWidth().semantics { contentDescription = label },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            stages.forEachIndexed { i, stage ->
                val done = i < index
                val active = i == index && !failed
                val stageFailed = i == index && failed
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Done stages settle into a squarer mark; the current one spins.
                    val corner by animateDpAsState(if (done) 9.dp else 14.dp, MaterialTheme.motionSchemeSpatial(), label = "mark")
                    Box(
                        Modifier
                            .size(28.dp)
                            .then(
                                when {
                                    done -> Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(corner))
                                    stageFailed -> Modifier.background(MaterialTheme.colorScheme.error, CircleShape)
                                    active -> Modifier
                                    else -> Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            done -> GlyphIcon(Glyph.Check, MaterialTheme.colorScheme.onPrimary, size = 16.dp)
                            stageFailed -> Text("!", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                            active -> LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        stringResource(stageLabel(stage)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (done || active || stageFailed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        done -> Text(stringResource(Res.string.stage_done), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        stageFailed -> Text(stringResource(Res.string.stage_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun stageLabel(stage: Stage) = when (stage) {
    Stage.TRANSCRIBE -> Res.string.stage_transcribe
    Stage.VIDEO -> Res.string.stage_video
    Stage.ANALYZE -> Res.string.stage_analyze
    Stage.WRITE -> Res.string.stage_write
}

private fun stageLine(stage: Stage) = when (stage) {
    Stage.TRANSCRIBE -> Res.string.processing_line_transcribe
    Stage.VIDEO -> Res.string.processing_line_video
    Stage.ANALYZE -> Res.string.processing_line_analyze
    Stage.WRITE -> Res.string.processing_line_write
}

private fun personaName(persona: Persona) = when (persona) {
    Persona.CLASSMATE -> Res.string.persona_classmate
    Persona.TEACHER -> Res.string.persona_teacher
    Persona.KID -> Res.string.persona_kid
}
