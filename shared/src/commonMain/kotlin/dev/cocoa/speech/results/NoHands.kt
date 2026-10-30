package dev.cocoa.speech.results

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.listeners.ListenerAvatar
import dev.cocoa.speech.listeners.ListeningAvatar
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.results_no_hands_classmate
import speech_app.shared.generated.resources.results_no_hands_desc
import speech_app.shared.generated.resources.results_no_hands_kid
import speech_app.shared.generated.resources.results_no_hands_teacher
import speech_app.shared.generated.resources.results_no_hands_title
import speech_app.shared.generated.resources.results_record_again

// Canvas board H: no hand went up. Each check line is true by construction (that listener raised no hand), so it is
// not praise the model made up, and it says nothing about whether the content is correct. Scrolls when a notice above
// (video failed) leaves less room.
@Composable
internal fun NoHands(grade: String, onRecordAgain: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                for (persona in Persona.entries) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ListeningAvatar(persona, listening = true, modifier = Modifier.size(84.dp))
                        Text(personaLabel(persona), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.results_no_hands_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(Res.string.results_no_hands_desc, grade),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                val lines = listOf(
                    Persona.CLASSMATE to Res.string.results_no_hands_classmate,
                    Persona.TEACHER to Res.string.results_no_hands_teacher,
                    Persona.KID to Res.string.results_no_hands_kid,
                )
                lines.forEachIndexed { i, (persona, text) ->
                    if (i > 0) HorizontalDivider(color = Color(0xFFECEEF0))
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        ListenerAvatar(persona, raise = { 0f }, modifier = Modifier.size(34.dp))
                        Text(
                            stringResource(text),
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        GlyphIcon(Glyph.Check, MaterialTheme.colorScheme.onSurface, size = 20.dp)
                    }
                }
            }
        }
        if (onRecordAgain != null) {
            Button(onClick = onRecordAgain, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp)) {
                Text(stringResource(Res.string.results_record_again), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
