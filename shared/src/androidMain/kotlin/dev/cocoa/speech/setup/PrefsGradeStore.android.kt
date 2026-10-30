package dev.cocoa.speech.setup

import android.content.Context
import dev.cocoa.speech.onboarding.OnboardingStore

// The grade picked last and whether the first-run introduction was seen, in one small preferences file.
class PrefsGradeStore(context: Context) : GradeStore, OnboardingStore {
    private val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    override fun save(grade: String) {
        prefs.edit().putString(KEY, grade).apply()
    }

    override fun seen(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    override fun markSeen() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    private companion object {
        const val KEY = "grade"
        const val KEY_ONBOARDED = "onboarded"
    }
}
