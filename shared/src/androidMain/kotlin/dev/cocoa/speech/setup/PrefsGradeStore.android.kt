package dev.cocoa.speech.setup

import android.content.Context

class PrefsGradeStore(context: Context) : GradeStore {
    private val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    override fun save(grade: String) {
        prefs.edit().putString(KEY, grade).apply()
    }

    private companion object {
        const val KEY = "grade"
    }
}
