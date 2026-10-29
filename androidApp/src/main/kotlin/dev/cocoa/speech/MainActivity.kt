package dev.cocoa.speech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.cocoa.speech.gemini.createGeminiClient
import dev.cocoa.speech.pipeline.LivePipeline
import dev.cocoa.speech.record.AndroidAudioRecorder
import dev.cocoa.speech.record.Recording
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.PrefsGradeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val gradeStore = PrefsGradeStore(applicationContext)
        // Demo only: the key is compiled into the APK from local.properties (see androidApp/build.gradle.kts).
        val apiKey = BuildConfig.GEMINI_API_KEY
        val live = if (apiKey.isBlank()) null else LiveDeps(
            recorder = AndroidAudioRecorder(applicationContext),
            permission = ActivityMicPermission(this),
            pipeline = LivePipeline(createGeminiClient(apiKey)),
            debugSample = if (BuildConfig.DEBUG) ::loadDebugSample else null,
        )
        setContent {
            App(gradeStore, live)
        }
    }

    // Debug only: a 16-bit PCM WAV copied into the app's files dir (see CLAUDE.md, Commands) replaces the microphone.
    private fun loadDebugSample(): Recording? {
        val file = File(filesDir, "sample.wav")
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSecond = header.getInt(28) // WAV byte rate
        return Recording(bytes, "audio/wav", (bytes.size - 44).toDouble() / bytesPerSecond)
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
