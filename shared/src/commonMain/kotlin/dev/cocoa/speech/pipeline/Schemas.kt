package dev.cocoa.speech.pipeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// JSON Schemas for responseFormat.text.schema. Nullable fields use a type array (["string", "null"]), which the
// Gemini JSON Schema subset supports. Keys match the data contracts in CLAUDE.md.
object Schemas {
    val transcript: JsonObject = obj(
        "segments" to array(obj("start" to string(), "end" to string(), "text" to string())),
    )

    // AnalyzerOutput without `segments` and `visual`: code keeps the transcript and scene timeline; the model does not
    // echo them.
    val analyzer: JsonObject = obj(
        "terms" to array(
            obj(
                "term" to string(),
                "t" to number(),
                "level" to enum("everyday", "school", "specialist"),
                "defined_at" to number(nullable = true),
            ),
        ),
        "claims" to array(
            obj(
                "t" to number(),
                "text" to string(),
                "has_reason" to boolean(),
                "evidence_on_screen" to string(nullable = true),
            ),
        ),
        "mismatches" to array(
            obj(
                "t" to number(),
                "type" to enum("shown_not_said", "said_not_shown", "dense_slide", "pointing_unnamed"),
                "quote" to string(),
            ),
        ),
        "steps" to array(
            obj(
                "id" to string(),
                "t" to number(),
                "claim_quote" to string(),
                "requires" to array(
                    obj(
                        "text" to string(),
                        "stated_at" to number(nullable = true),
                        "stated_quote" to string(nullable = true),
                    ),
                ),
            ),
        ),
    )

    val video: JsonObject = obj(
        "scenes" to array(
            obj(
                "t" to string(),
                "summary" to string(),
                "on_screen_text" to string(nullable = true),
                "slide_index" to integer(nullable = true),
                "pointing_at" to string(nullable = true),
            ),
        ),
    )

    val persona: JsonObject = obj(
        "items" to array(
            obj(
                "index" to integer(),
                "reason" to string(),
                "fix" to string(),
                "question" to string(nullable = true),
            ),
        ),
    )

    private fun type(name: String, nullable: Boolean): JsonObjectBuilder.() -> Unit = {
        if (nullable) putJsonArray("type") { add(name); add("null") } else put("type", name)
    }

    private fun string(nullable: Boolean = false) = buildJsonObject(type("string", nullable))
    private fun number(nullable: Boolean = false) = buildJsonObject(type("number", nullable))
    private fun integer(nullable: Boolean = false) = buildJsonObject(type("integer", nullable))
    private fun boolean() = buildJsonObject(type("boolean", false))

    private fun enum(vararg values: String) = buildJsonObject {
        put("type", "string")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    private fun array(items: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    // Every property is required; optional values are expressed as nullable instead.
    private fun obj(vararg properties: Pair<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        putJsonArray("required") { properties.forEach { add(it.first) } }
    }
}
