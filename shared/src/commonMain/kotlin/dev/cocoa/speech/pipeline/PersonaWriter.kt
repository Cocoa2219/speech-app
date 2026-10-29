package dev.cocoa.speech.pipeline

import dev.cocoa.speech.GeminiModels
import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.logic.PersonaTrigger
import dev.cocoa.speech.model.AppJson
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import dev.cocoa.speech.model.PersonaEvent
import dev.cocoa.speech.model.Segment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// Writes reason, fix, and the kid's question for triggers that PersonaRules already placed. The model cannot add or
// move a hand-raise: persona, time, type, and quote always come from the trigger.
// parallel: one call per persona, run at once. Output length drives latency (one call for 17 triggers took ~22 s),
// but it costs 3 requests instead of 1 against the daily request limit. See PipelineConfig.
class PersonaWriter(
    private val client: JsonGenerator,
    private val prompt: String,
    private val parallel: Boolean = PipelineConfig.PARALLEL_PERSONA_CALLS,
    private val log: (String) -> Unit = ::println,
) {
    suspend fun write(segments: List<Segment>, triggers: List<PersonaTrigger>): List<PersonaEvent> {
        val indexed = triggers.withIndex().toList()
        val groups = if (parallel) indexed.groupBy { it.value.persona }.values else listOf(indexed).filter { it.isNotEmpty() }
        val items = coroutineScope {
            groups.map { group -> async { writeGroup(segments, group) } }.awaitAll().flatten()
        }
        return events(triggers, items, log)
    }

    // Indexes stay global so events() can pair text with triggers; items for indexes outside the group are ignored.
    private suspend fun writeGroup(segments: List<Segment>, group: List<IndexedValue<PersonaTrigger>>): List<PersonaDto.Item> {
        val json = client.generateJson(
            model = GeminiModels.PERSONA,
            systemInstruction = prompt,
            parts = listOf(GeminiClient.Part.Text(input(segments, group))),
            schema = Schemas.persona,
        )
        val indexes = group.map { it.index }.toSet()
        return AppJson.decodeFromString<PersonaDto>(json).items.filter { it.index in indexes }
    }

    companion object {
        fun input(segments: List<Segment>, triggers: List<IndexedValue<PersonaTrigger>>): String = buildJsonObject {
            put("segments", Analyzer.segmentsJson(segments))
            putJsonArray("triggers") {
                for ((i, trigger) in triggers) {
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
