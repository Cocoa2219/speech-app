package dev.cocoa.speech.video

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.cocoa.speech.record.Permission
import dev.cocoa.speech.record.RecordRoute
import dev.cocoa.speech.record.Recording
import kotlin.coroutines.cancellation.CancellationException

// Camera recording: the shared record screen with a live rear-camera preview filling the screen.
@Composable
fun VideoRecordRoute(permission: Permission, onFinished: (Recording) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val capture = remember { CameraCapture(context) }
    DisposableEffect(capture) { onDispose { capture.unbind() } }

    RecordRoute(
        capture = capture,
        permission = permission,
        onFinished = onFinished,
        onCancel = onCancel,
        preview = {
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(previewView) {
                try {
                    capture.bind(owner, Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No usable rear camera (for example an emulator). Starting will then fail with a visible message.
                    println("CameraCapture: bind failed: ${e::class.simpleName}")
                }
            }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        },
    )
}
