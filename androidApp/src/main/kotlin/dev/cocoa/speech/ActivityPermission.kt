package dev.cocoa.speech

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.cocoa.speech.record.Permission
import kotlinx.coroutines.CompletableDeferred

// Requests all of `permissions` at once; granted only if every one is. Must be created before the activity is
// STARTED (registerForActivityResult rule), so construct it in onCreate.
class ActivityPermission(private val activity: ComponentActivity, private val permissions: Array<String>) : Permission {
    private var pending: CompletableDeferred<Boolean>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pending?.complete(permissions.all { result[it] == true || isGranted(it) })
        pending = null
    }

    override suspend fun request(): Boolean {
        if (permissions.all(::isGranted)) return true
        val result = CompletableDeferred<Boolean>().also { pending = it }
        launcher.launch(permissions)
        return result.await()
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
}
