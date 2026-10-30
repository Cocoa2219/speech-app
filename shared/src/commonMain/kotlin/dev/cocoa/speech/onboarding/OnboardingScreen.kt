package dev.cocoa.speech.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.listeners.AnimatedListenerAvatar
import dev.cocoa.speech.listeners.ListenerColors
import dev.cocoa.speech.listeners.ListeningAvatar
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.results.personaLabel
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import dev.cocoa.speech.theme.motionSchemeSpatial
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.onboarding_cast_body
import speech_app.shared.generated.resources.onboarding_cast_title
import speech_app.shared.generated.resources.onboarding_classmate
import speech_app.shared.generated.resources.onboarding_delete_body
import speech_app.shared.generated.resources.onboarding_delete_title
import speech_app.shared.generated.resources.onboarding_hand_body
import speech_app.shared.generated.resources.onboarding_hand_question
import speech_app.shared.generated.resources.onboarding_hand_quote
import speech_app.shared.generated.resources.onboarding_hand_title
import speech_app.shared.generated.resources.onboarding_kid
import speech_app.shared.generated.resources.onboarding_local_body
import speech_app.shared.generated.resources.onboarding_local_title
import speech_app.shared.generated.resources.onboarding_next
import speech_app.shared.generated.resources.onboarding_promise_title
import speech_app.shared.generated.resources.onboarding_skip
import speech_app.shared.generated.resources.onboarding_start
import speech_app.shared.generated.resources.onboarding_teacher
import speech_app.shared.generated.resources.onboarding_truth_body
import speech_app.shared.generated.resources.onboarding_truth_title

// Whether the first-run introduction was seen. Android: the setup preferences file.
interface OnboardingStore {
    fun seen(): Boolean
    fun markSeen()
}

private const val PAGES = 3

// Canvas board I, shown once on first run: who listens, what a hand-raise looks like, and the promises (clarity, not
// truth; privacy). Swipe or "다음"; "건너뛰기" jumps to the last page so the promises are always seen.
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
            if (!last) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } }) {
                    Text(stringResource(Res.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
                when (page) {
                    0 -> CastPage()
                    1 -> HandPage(visible = pager.currentPage == 1)
                    else -> PromisePage()
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGES) { i ->
                    val width by animateDpAsState(if (i == pager.currentPage) 24.dp else 8.dp, MaterialTheme.motionSchemeSpatial(), label = "dot")
                    Box(
                        Modifier.size(width, 8.dp).background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(4.dp),
                        ),
                    )
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(
                    stringResource(if (last) Res.string.onboarding_start else Res.string.onboarding_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PageTitle(title: StringResource, body: StringResource? = null) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    if (body != null) {
        Text(
            stringResource(body),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CastPage() {
    PageTitle(Res.string.onboarding_cast_title, Res.string.onboarding_cast_body)
    Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val lines = listOf(
            Persona.CLASSMATE to Res.string.onboarding_classmate,
            Persona.TEACHER to Res.string.onboarding_teacher,
            Persona.KID to Res.string.onboarding_kid,
        )
        for ((persona, line) in lines) {
            Row(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ListeningAvatar(persona, listening = true, modifier = Modifier.size(72.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(personaLabel(persona), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// The kid raises a hand once the page is on screen, then asks; the hand stays up.
@Composable
private fun HandPage(visible: Boolean) {
    var raised by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(500)
            raised = true
        } else {
            raised = false
        }
    }
    PageTitle(Res.string.onboarding_hand_title, Res.string.onboarding_hand_body)
    Column(
        Modifier.padding(top = 32.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedListenerAvatar(Persona.KID, raised, Modifier.size(120.dp))
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(raised, enter = fadeIn() + slideInVertically { it / 3 }) {
                Text(
                    stringResource(Res.string.onboarding_hand_question),
                    modifier = Modifier.background(ListenerColors.tint(Persona.KID), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HorizontalDivider(color = Color(0xFFECEEF0))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "0:39",
                modifier = Modifier.background(Color(0xFFECEEF0), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(Res.string.onboarding_hand_quote), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A404A))
        }
    }
}

@Composable
private fun PromisePage() {
    PageTitle(Res.string.onboarding_promise_title)
    Column(Modifier.padding(top = 28.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 4.dp)) {
        val promises = listOf(
            Res.string.onboarding_truth_title to Res.string.onboarding_truth_body,
            Res.string.onboarding_local_title to Res.string.onboarding_local_body,
            Res.string.onboarding_delete_title to Res.string.onboarding_delete_body,
        )
        promises.forEachIndexed { i, (title, body) ->
            if (i > 0) HorizontalDivider(color = Color(0xFFECEEF0))
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).background(Color(0xFFECEEF0), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Check, MaterialTheme.colorScheme.onSurface, size = 18.dp)
                }
                Column(Modifier.width(0.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
