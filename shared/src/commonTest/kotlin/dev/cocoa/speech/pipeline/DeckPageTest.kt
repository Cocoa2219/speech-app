package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.JsonGenerator
import dev.cocoa.speech.gemini.MediaUploader
import dev.cocoa.speech.logic.PersonaTrigger
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckPageTest {
    private fun item(t: String, onScreen: String?, page: Int?, text: String?, partial: Boolean? = null) =
        SceneDto.Item(t, "Slide", onScreenText = onScreen, deckPage = page, deckText = text, deckPartial = partial)

    @Test
    fun keepsAPageWhenAReadableWordIsOnIt() {
        val scene = SceneDescriber.toScenes(SceneDto(listOf(item("00:05", "Introduction", 2, "Introduction / 광합성은 …"))), deckAttached = true).single()
        assertEquals(2, scene.deckPage)
        assertEquals("Introduction / 광합성은 …", scene.deckText)
    }

    @Test
    fun dropsAPageWhenNoReadableWordIsOnIt() {
        val logs = mutableListOf<String>()
        val scene = SceneDescriber.toScenes(
            SceneDto(listOf(item("00:05", "Results", 2, "Introduction / 광합성은 …"))), deckAttached = true, log = logs::add,
        ).single()
        assertNull(scene.deckPage)
        assertNull(scene.deckText)
        assertEquals("Results", scene.onScreenText) // the frame's own text stays
        assertTrue(logs.single().contains("5.0s"))
        assertTrue("Introduction" !in logs.single()) // logs never carry slide text
    }

    @Test
    fun aPictureOnlySlideKeepsTheModelsMatch() {
        val scene = SceneDescriber.toScenes(SceneDto(listOf(item("00:05", null, 3, "Figure 1"))), deckAttached = true).single()
        assertEquals(3, scene.deckPage)
    }

    @Test
    fun noPageWithoutADeckOrWithoutText() {
        val scenes = SceneDescriber.toScenes(
            SceneDto(listOf(item("00:05", null, 3, "Figure 1"), item("00:09", null, 4, " "))), deckAttached = false,
        )
        assertTrue(scenes.all { it.deckPage == null && it.deckText == null })
        val noText = SceneDescriber.toScenes(SceneDto(listOf(item("00:09", null, 4, " "))), deckAttached = true).single()
        assertNull(noText.deckPage)
    }

    @Test
    fun partialIsKeptOnlyWithAMatchedPage() {
        val scenes = SceneDescriber.toScenes(
            SceneDto(listOf(item("00:05", "TABLE OF CONTENTS", 2, "TABLE OF CONTENTS / INTRODUCTION", partial = true),
                item("00:09", "Results", 2, "TABLE OF CONTENTS", partial = true))),
            deckAttached = true,
        )
        assertTrue(scenes[0].deckPartial)
        assertFalse(scenes[1].deckPartial) // its page was dropped, so it cannot be partial either
    }

    @Test
    fun denseSlideIsDroppedWhileTheSlideBuildsUp() {
        val scenes = listOf(Scene(0.0, "a", deckPage = 2, deckText = "x", deckPartial = true), Scene(8.0, "b", deckPage = 2, deckText = "x"))
        val triggers = listOf(
            PersonaTrigger(Persona.CLASSMATE, 3.0, EventType.DENSE_SLIDE, "x"),
            PersonaTrigger(Persona.CLASSMATE, 9.0, EventType.DENSE_SLIDE, "x"),
            PersonaTrigger(Persona.KID, 3.0, EventType.WHATS_THAT, "x"),
        )
        assertEquals(listOf(9.0, 3.0), Scene.withoutDenseOnPartialSlides(triggers, scenes).map { it.t })
    }

    @Test
    fun highResolutionFilePartsAskForHighMediaResolution() {
        fun part(high: Boolean) = GeminiClient.requestBody("s", listOf(GeminiClient.Part.FileRef("video/mp4", "u", high)), JsonObject(emptyMap()), GeminiClient.Thinking.LOW)
            .getValue("contents").jsonArray[0].jsonObject.getValue("parts").jsonArray[0].jsonObject
        assertEquals("MEDIA_RESOLUTION_HIGH", part(true).getValue("mediaResolution").jsonObject.getValue("level").jsonPrimitive.content)
        assertFalse("mediaResolution" in part(false))
    }

    @Test
    fun visualCarriesThePageTextSoSlideQuotesCanBeGrounded() {
        val visual = Scene(5.0, "Slide", onScreenText = "Introduction", deckPage = 2, deckText = "Introduction / 본문").toVisual()
        assertEquals("Slide On screen: \"Introduction\" PDF page 2: \"Introduction / 본문\"", visual.summary)
    }

    @Test
    fun pageAtATimeIsTheLatestSceneStartedBeforeIt() {
        val scenes = listOf(Scene(0.0, "a", deckPage = 1), Scene(10.0, "b"), Scene(20.0, "c", deckPage = 3))
        assertNull(Scene.deckPageAt(scenes, -1.0))
        assertEquals(1, Scene.deckPageAt(scenes, 5.0))
        assertNull(Scene.deckPageAt(scenes, 15.0)) // a scene without a page in between
        assertEquals(3, Scene.deckPageAt(scenes, 20.0))
    }

    @Test
    fun personaInputGivesEachTriggerThePageOnScreen() {
        val triggers = listOf(
            PersonaTrigger(Persona.KID, 5.0, EventType.WHATS_THAT, "이거"),
            PersonaTrigger(Persona.TEACHER, 15.0, EventType.NO_EVIDENCE, "그래서"),
        ).withIndex().toList()
        val scenes = listOf(Scene(0.0, "a", deckPage = 1), Scene(10.0, "b"))
        val items = Json.parseToJsonElement(PersonaWriter.input(emptyList(), triggers, scenes = scenes)).jsonObject
            .getValue("triggers").jsonArray.map { it.jsonObject }
        assertEquals("1", items[0].getValue("deck_page").jsonPrimitive.content)
        assertFalse("deck_page" in items[1])
    }

    @Test
    fun videoCallSendsTheDeckAfterTheVideo() = runTest {
        val sent = mutableListOf<List<GeminiClient.Part>>()
        val client = object : JsonGenerator {
            override suspend fun generateJson(
                model: String,
                systemInstruction: String,
                parts: List<GeminiClient.Part>,
                schema: JsonObject,
                thinking: GeminiClient.Thinking,
            ): String {
                sent += parts
                return """{"scenes":[{"t":"00:01","summary":"s","on_screen_text":null,"slide_index":1,"pointing_at":null,"deck_page":1,"deck_text":"Intro"}]}"""
            }
        }
        val uploader = object : MediaUploader {
            override suspend fun <T> withUploaded(bytes: ByteArray, mimeType: String, block: suspend (GeminiClient.Part.FileRef) -> T): T =
                block(GeminiClient.Part.FileRef(mimeType, "files/fake"))
        }
        val scenes = SceneDescriber(client, uploader, "V", deck = byteArrayOf(7), log = {}).describe(byteArrayOf(9), "video/mp4")

        val parts = sent.single()
        assertTrue(assertIs<GeminiClient.Part.FileRef>(parts[0]).highResolution)
        assertEquals("application/pdf", assertIs<GeminiClient.Part.Media>(parts[1]).mimeType)
        assertIs<GeminiClient.Part.Text>(parts[2])
        assertEquals(1, scenes.single().deckPage)
    }
}
