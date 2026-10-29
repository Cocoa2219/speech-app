package dev.cocoa.speech

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.cocoa.speech.gemini.createGemini
import dev.cocoa.speech.pipeline.LivePipeline
import dev.cocoa.speech.record.AndroidAudioRecorder
import dev.cocoa.speech.record.Recording
import dev.cocoa.speech.record.ReplayMedia
import dev.cocoa.speech.replay.AndroidReplayPlayer
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.PrefsGradeStore
import dev.cocoa.speech.video.VideoRecordRoute
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val gradeStore = PrefsGradeStore(applicationContext)
        // Both launchers must be registered here, before the activity starts.
        val micPermission = ActivityPermission(this, arrayOf(Manifest.permission.RECORD_AUDIO))
        val cameraPermission = ActivityPermission(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        // Demo only: the key is compiled into the APK from local.properties (see androidApp/build.gradle.kts).
        val apiKey = BuildConfig.GEMINI_API_KEY
        val gemini = if (apiKey.isBlank()) null else {
            createGemini(apiKey, cacheDir = if (BuildConfig.DEBUG) File(filesDir, "gemini-cache") else null)
        }
        val live = if (gemini == null) null else LiveDeps(
            recorder = AndroidAudioRecorder(applicationContext),
            micPermission = micPermission,
            pipeline = LivePipeline(gemini.generator, uploader = gemini.files),
            videoRecord = { onFinished, onCancel -> VideoRecordRoute(cameraPermission, onFinished, onCancel) },
            replayPlayer = { media, seek, onPosition -> AndroidReplayPlayer(media, seek, onPosition) },
            debugSample = if (BuildConfig.DEBUG) ::loadDebugSample else null,
        )
        setContent {
            App(gradeStore, live, backHandler = { enabled, onBack -> BackHandler(enabled, onBack) })
        }
    }

    // Debug only: a 16-bit PCM WAV copied into the app's files dir (tools/make-sample.ps1 -Push) replaces the microphone.
    private fun loadDebugSample(): Recording? {
        val file = File(filesDir, "sample.wav")
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSecond = header.getInt(28) // WAV byte rate
        return Recording(
            bytes,
            "audio/wav",
            (bytes.size - 44).toDouble() / bytesPerSecond,
            replay = ReplayMedia(file.absolutePath, isVideo = false),
        )
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        object : GradeStore {
            override fun load() = Grade.DEFAULT
            override fun save(grade: String) {}
        },
        live = null,
    )
}
