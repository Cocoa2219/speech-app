package dev.cocoa.speech.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.theme.Glyph
import dev.cocoa.speech.theme.GlyphIcon
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.permission_camera_body
import speech_app.shared.generated.resources.permission_camera_steps
import speech_app.shared.generated.resources.permission_camera_title
import speech_app.shared.generated.resources.permission_how
import speech_app.shared.generated.resources.permission_mic_body
import speech_app.shared.generated.resources.permission_mic_steps
import speech_app.shared.generated.resources.permission_mic_title
import speech_app.shared.generated.resources.permission_open_settings
import speech_app.shared.generated.resources.permission_voice_only
import speech_app.shared.generated.resources.record_cancel
import speech_app.shared.generated.resources.record_title

// Canvas board L, in place of the record screen when a permission was refused. Camera refused (microphone allowed):
// voice only is offered. Microphone refused: nothing can be recorded, so settings is the only way on.
@Composable
fun PermissionScreen(camera: Boolean, onOpenSettings: () -> Unit, onVoiceOnly: (() -> Unit)?, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val close = stringResource(Res.string.record_cancel)
            IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = close }) {
                GlyphIcon(Glyph.Close, MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(Res.string.record_title),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(132.dp).background(Color.White, RoundedCornerShape(44.dp)), contentAlignment = Alignment.Center) {
                GlyphIcon(if (camera) Glyph.Camera else Glyph.Mic, MaterialTheme.colorScheme.onSurface, size = 64.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(44.dp)
                        .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (camera) Res.string.permission_camera_title else Res.string.permission_mic_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(if (camera) Res.string.permission_camera_body else Res.string.permission_mic_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(Res.string.permission_how),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(if (camera) Res.string.permission_camera_steps else Res.string.permission_mic_steps),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(stringResource(Res.string.permission_open_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (onVoiceOnly != null) {
                FilledTonalButton(onClick = onVoiceOnly, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    GlyphIcon(Glyph.Mic, MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        stringResource(Res.string.permission_voice_only),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
