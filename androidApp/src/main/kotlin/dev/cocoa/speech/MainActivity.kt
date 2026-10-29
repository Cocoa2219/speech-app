package dev.cocoa.speech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.cocoa.speech.setup.Grade
import dev.cocoa.speech.setup.GradeStore
import dev.cocoa.speech.setup.PrefsGradeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val gradeStore = PrefsGradeStore(applicationContext)
        setContent {
            App(gradeStore)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(object : GradeStore {
        override fun load() = Grade.DEFAULT
        override fun save(grade: String) {}
    })
}
