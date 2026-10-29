package dev.cocoa.speech.replay

import android.content.Context
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.cocoa.speech.record.ReplayMedia
import dev.cocoa.speech.results.SeekRequest
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.results_pause
import speech_app.shared.generated.resources.results_play

// Replays the latest recording on the results screen with platform players (no extra dependency): VideoView for
// camera recordings, MediaPlayer for audio. Reports the position every 100 ms while playing; seeks on each new request.
@Composable
fun AndroidReplayPlayer(media: ReplayMedia, seek: SeekRequest?, onPosition: (Double) -> Unit) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val player = remember(media.path) {
        if (media.isVideo) VideoPlayer(context, media.path) { playing = false } else AudioPlayer(media.path) { playing = false }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(player, seek) {
        seek?.let { player.seekTo((it.t * 1000).toInt()) }
    }
    // Report the position only while playing, so opening the screen (which seeks to the first fix) is not reset to 0.
    LaunchedEffect(player, playing) {
        while (playing) {
            onPosition(player.positionMs() / 1000.0)
            delay(100)
        }
    }

    // Fills the results screen's dark replay area; a small play/pause button sits in the corner.
    Box(Modifier.fillMaxSize()) {
        if (player is VideoPlayer) {
            AndroidView(factory = { player.view }, modifier = Modifier.fillMaxSize())
        }
        val label = stringResource(if (playing) Res.string.results_pause else Res.string.results_play)
        FilledIconButton(
            onClick = {
                if (playing) player.pause() else player.play()
                playing = !playing
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp).semantics { contentDescription = label },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.92f)),
        ) {
            PlayPauseGlyph(playing)
        }
    }
}

@Composable
private fun PlayPauseGlyph(playing: Boolean) {
    val ink = Color(0xFF1E2329)
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        if (playing) {
            drawRoundRect(ink, Offset(w * 0.18f, 0f), Size(w * 0.22f, w), CornerRadius(w * 0.06f))
            drawRoundRect(ink, Offset(w * 0.60f, 0f), Size(w * 0.22f, w), CornerRadius(w * 0.06f))
        } else {
            drawPath(Path().apply { moveTo(w * 0.2f, 0f); lineTo(w, w / 2); lineTo(w * 0.2f, w); close() }, ink)
        }
    }
}

private interface Player {
    fun play()
    fun pause()
    fun seekTo(ms: Int)
    fun positionMs(): Int
    fun release()
}

private class VideoPlayer(context: Context, path: String, onEnd: () -> Unit) : Player {
    val view = VideoView(context).apply {
        setVideoPath(path)
        setOnCompletionListener { onEnd() }
        // Shows the first frame instead of a black box before playback starts.
        setOnPreparedListener { if (currentPosition == 0) seekTo(1) }
    }

    override fun play() = view.start()
    override fun pause() = view.pause()
    override fun seekTo(ms: Int) = view.seekTo(ms)
    override fun positionMs() = view.currentPosition
    override fun release() = view.stopPlayback()
}

private class AudioPlayer(path: String, onEnd: () -> Unit) : Player {
    private val mp: MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setDataSource(path)
            prepare()
            setOnCompletionListener { onEnd() }
        }
    }.getOrNull()

    override fun play() { mp?.start() }
    override fun pause() { mp?.pause() }
    override fun seekTo(ms: Int) { mp?.seekTo(ms) }
    override fun positionMs() = mp?.currentPosition ?: 0
    override fun release() { mp?.release() }
}
