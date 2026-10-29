package dev.cocoa.speech.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Neutral analyzer output (no role-play). All times are seconds from the start of the recording.
@Serializable
data class AnalyzerOutput(
    val segments: List<Segment> = emptyList(),
    val terms: List<Term> = emptyList(),
    val claims: List<Claim> = emptyList(),
    val visual: List<Visual> = emptyList(),
    val mismatches: List<Mismatch> = emptyList(),
    val steps: List<Step> = emptyList(),
)

@Serializable
data class Segment(
    @SerialName("t_start") val tStart: Double,
    @SerialName("t_end") val tEnd: Double,
    val text: String,
)

@Serializable
data class Term(
    val term: String,
    val t: Double,
    val level: TermLevel,
    @SerialName("defined_at") val definedAt: Double? = null,
)

@Serializable
enum class TermLevel {
    @SerialName("everyday") EVERYDAY,
    @SerialName("school") SCHOOL,
    @SerialName("specialist") SPECIALIST,
}

@Serializable
data class Claim(
    val t: Double,
    val text: String,
    @SerialName("has_reason") val hasReason: Boolean,
    @SerialName("evidence_on_screen") val evidenceOnScreen: String? = null,
)

@Serializable
data class Visual(
    val t: Double,
    val summary: String,
    @SerialName("slide_index") val slideIndex: Int? = null,
)

@Serializable
data class Mismatch(
    val t: Double,
    val type: MismatchType,
    val quote: String,
)

// A reasoning step and the premises a listener at the selected grade would need for it.
@Serializable
data class Step(
    val id: String,
    val t: Double,
    @SerialName("claim_quote") val claimQuote: String,
    val requires: List<Premise> = emptyList(),
)

@Serializable
data class Premise(
    val text: String,
    // When the speaker stated it; null if never.
    @SerialName("stated_at") val statedAt: Double? = null,
    @SerialName("stated_quote") val statedQuote: String? = null,
)

@Serializable
enum class MismatchType {
    @SerialName("shown_not_said") SHOWN_NOT_SAID,
    @SerialName("said_not_shown") SAID_NOT_SHOWN,
    @SerialName("dense_slide") DENSE_SLIDE,
    @SerialName("pointing_unnamed") POINTING_UNNAMED,
}
