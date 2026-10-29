package dev.cocoa.speech

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.cocoa.speech.record.MicPermission
import kotlinx.coroutines.CompletableDeferred

// Must be created before the activity is STARTED (registerForActivityResult rule), so construct it in onCreate.
class ActivityMicPermission(private val activity: ComponentActivity) : MicPermission {
    private var pending: CompletableDeferred<Boolean>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.complete(granted)
        pending = null
    }

    override suspend fun request(): Boolean {
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        val result = CompletableDeferred<Boolean>().also { pending = it }
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        return result.await()
    }
}
