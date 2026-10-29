package dev.cocoa.speech.setup

// The listener's grade. The label goes into the analyzer prompt's {{grade}} verbatim, so it is data, not UI copy.
object Grade {
    const val DEFAULT = "중학교 2학년"
    const val ADULT_DEMO = "일반 성인 (demo)"

    val options: List<String> =
        (1..6).map { "초등학교 ${it}학년" } +
            (1..3).map { "중학교 ${it}학년" } +
            (1..3).map { "고등학교 ${it}학년" } +
            ADULT_DEMO

    // Never empty. Mock mode always uses DEFAULT because the fixtures were written for it.
    fun resolve(selected: String?, mockMode: Boolean = false): String =
        if (mockMode || selected.isNullOrBlank() || selected !in options) DEFAULT else selected
}

// Remembers the last grade picked. Android: SharedPreferences.
interface GradeStore {
    fun load(): String?
    fun save(grade: String)
}
