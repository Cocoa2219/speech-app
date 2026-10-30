package dev.cocoa.speech.setup

// The listener's grade. The label goes into the analyzer prompt's {{grade}} verbatim, so it is data, not UI copy.
object Grade {
    const val DEFAULT = "중학교 2학년"
    const val ADULT_DEMO = "일반 성인 (demo)"

    // The setup screen picks a school, then a year; no school means the adult demo grade.
    class School(val name: String, val years: Int)

    val schools = listOf(School("초등학교", 6), School("중학교", 3), School("고등학교", 3))

    val options: List<String> = schools.flatMap { school -> (1..school.years).map { of(school, it) } } + ADULT_DEMO

    fun schoolOf(grade: String): School? = schools.firstOrNull { grade.startsWith(it.name + " ") }

    fun yearOf(grade: String): Int = Regex("""(\d+)학년""").find(grade)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    // Switching school keeps the year when the new school has it (중학교 3학년 -> 고등학교 3학년), else clamps it.
    fun of(school: School?, year: Int): String =
        if (school == null) ADULT_DEMO else "${school.name} ${year.coerceIn(1, school.years)}학년"

    // Never empty. Mock mode always uses DEFAULT because the fixtures were written for it.
    fun resolve(selected: String?, mockMode: Boolean = false): String =
        if (mockMode || selected.isNullOrBlank() || selected !in options) DEFAULT else selected
}

// Remembers the last grade picked. Android: SharedPreferences.
interface GradeStore {
    fun load(): String?
    fun save(grade: String)
}
