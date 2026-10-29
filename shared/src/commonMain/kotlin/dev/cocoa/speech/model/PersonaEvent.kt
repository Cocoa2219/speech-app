package dev.cocoa.speech.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// One hand-raise from a listener. Rejected unless `quote` is found in the transcript or on-screen text.
@Serializable
data class PersonaEvent(
    val persona: Persona,
    val t: Double,
    val type: EventType,
    val quote: String,
    val reason: String,
    val fix: String,
    // Kid only: the question in the kid's own words.
    val question: String? = null,
)

@Serializable
enum class Persona {
    @SerialName("classmate") CLASSMATE,
    @SerialName("teacher") TEACHER,
    @SerialName("kid") KID,
}

@Serializable
enum class EventType {
    @SerialName("undefined_term") UNDEFINED_TERM,
    @SerialName("skipped_step") SKIPPED_STEP,
    @SerialName("no_evidence") NO_EVIDENCE,
    @SerialName("why_question") WHY_QUESTION,
    @SerialName("whats_that") WHATS_THAT,
    @SerialName("dense_slide") DENSE_SLIDE,
}
