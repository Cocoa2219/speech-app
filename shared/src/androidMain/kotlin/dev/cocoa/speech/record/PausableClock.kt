package dev.cocoa.speech.record

import android.os.SystemClock

// Recorded time that stops while paused. Shared by the microphone and camera captures.
internal class PausableClock {
    private var accumulatedMs = 0L
    private var runningSinceMs: Long? = null

    val running get() = runningSinceMs != null

    fun start() {
        accumulatedMs = 0
        runningSinceMs = SystemClock.elapsedRealtime()
    }

    fun pause() {
        val since = runningSinceMs ?: return
        accumulatedMs += SystemClock.elapsedRealtime() - since
        runningSinceMs = null
    }

    fun resume() {
        if (runningSinceMs == null) runningSinceMs = SystemClock.elapsedRealtime()
    }

    fun stop() = pause()

    fun seconds(): Double {
        val running = runningSinceMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L
        return (accumulatedMs + running) / 1000.0
    }
}
