package dev.cocoa.speech.pipeline

import dev.cocoa.speech.GeminiModels
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.logic.PersonaTrigger
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// Writes reason, fix, and the kid's question for triggers that PersonaRules already placed. The model cannot add or
// move a hand-raise: persona, time, type, and quote always come from the trigger.
class PersonaWriter(
    private val client: GeminiClient,
    private val prompt: String,
    private val log: (String) -> Unit = ::println,
) {
    suspend fun write(segments: List<Segment>, triggers: List<PersonaTrigger>): List<PersonaEvent> {
        if (triggers.isEmpty()) return emptyList()
        val json = client.generateJson(
            model = GeminiModels.PERSONA,
            systemInstruction = prompt,
            parts = listOf(GeminiClient.Part.Text(input(segments, triggers))),
            schema = Schemas.persona,
        )
        return events(triggers, AppJson.decodeFromString<PersonaDto>(json).items, log)
    }

    companion object {
        fun input(segments: List<Segment>, triggers: List<PersonaTrigger>): String = buildJsonObject {
            put("segments", Analyzer.segmentsJson(segments))
            putJsonArray("triggers") {
                triggers.forEachIndexed { i, trigger ->
                    addJsonObject {
                        put("index", i)
                        put("persona", trigger.persona.name.lowercase())
                        put("t", trigger.t)
                        put("type", AppJson.encodeToJsonElement(EventType.serializer(), trigger.type))
                        put("quote", trigger.quote)
                    }
                }
            }
        }.toString()

        // Pairs written text with triggers by index. A trigger with no usable text is dropped and logged by index only.
        fun events(triggers: List<PersonaTrigger>, items: List<PersonaDto.Item>, log: (String) -> Unit): List<PersonaEvent> {
            val byIndex = items.associateBy { it.index }
            return triggers.mapIndexedNotNull { i, trigger ->
                val item = byIndex[i]
                if (item == null || item.reason.isBlank() || item.fix.isBlank()) {
                    log("PersonaWriter: dropped trigger $i (${trigger.persona.name.lowercase()} at ${trigger.t}s): no text written")
                    return@mapIndexedNotNull null
                }
                PersonaEvent(
                    persona = trigger.persona,
                    t = trigger.t,
                    type = trigger.type,
                    quote = trigger.quote,
                    reason = item.reason.trim(),
                    fix = item.fix.trim(),
                    question = item.question?.trim()?.takeIf { trigger.persona == Persona.KID && it.isNotEmpty() },
                )
            }
        }
    }
}

@Serializable
data class PersonaDto(val items: List<Item> = emptyList()) {
    @Serializable
    data class Item(val index: Int, val reason: String = "", val fix: String = "", val question: String? = null)
}
