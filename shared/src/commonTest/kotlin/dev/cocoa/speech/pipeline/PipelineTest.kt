package dev.cocoa.speech.pipeline

import dev.cocoa.speech.gemini.GeminiClient
import dev.cocoa.speech.gemini.GeminiException
import dev.cocoa.speech.gemini.MediaUploader
import dev.cocoa.speech.logic.VideoStatus
import dev.cocoa.speech.record.VideoInput
import dev.cocoa.speech.logic.PersonaTrigger
import dev.cocoa.speech.model.EventType
import dev.cocoa.speech.model.Persona
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriberTest {
    @Test
    fun parsesTimestampFormats() {
        assertEquals(65.0, Transcriber.parseTimestamp("01:05"))
        assertEquals(65.4, Transcriber.parseTimestamp("1:05.4"))
        assertEquals(3725.0, Transcriber.parseTimestamp("01:02:05"))
        assertEquals(7.5, Transcriber.parseTimestamp("7.5"))
        assertNull(Transcriber.parseTimestamp(""))
        assertNull(Transcriber.parseTimestamp("1:xx"))
        assertNull(Transcriber.parseTimestamp("1::2"))
        assertNull(Transcriber.parseTimestamp("-0:05"))
    }

    @Test
    fun toSegmentsDropsBadRowsAndSorts() {
        val segments = Transcriber.toSegments(
            TranscriptDto(
                listOf(
                    TranscriptDto.SegmentDto("00:05", "00:09", " 그래서 식물은 자라요 "),
                    TranscriptDto.SegmentDto("bad", "00:04", "버려짐"),
                    TranscriptDto.SegmentDto("00:00", "00:04", "음 광합성은"),
                    TranscriptDto.SegmentDto("00:10", "00:12", "   "),
                    TranscriptDto.SegmentDto("00:13", "00:11", "끝이 시작보다 빠름"),
                ),
            ),
        )
        assertEquals(listOf(0.0, 5.0, 13.0), segments.map { it.tStart })
        assertEquals("그래서 식물은 자라요", segments[1].text)
        assertEquals("음 광합성은", segments[0].text) // fillers are kept
        assertEquals(13.0, segments[2].tEnd) // end before start is clamped to start
    }
}

class PersonaWriterTest {
    private val triggers = listOf(
        PersonaTrigger(Persona.CLASSMATE, 0.0, EventType.UNDEFINED_TERM, "광합성"),
        PersonaTrigger(Persona.TEACHER, 5.0, EventType.NO_EVIDENCE, "그래서 식물은 자라요"),
        PersonaTrigger(Persona.KID, 5.0, EventType.WHY_QUESTION, "그래서 식물은 자라요"),
    )

    @Test
    fun keepsTriggerFieldsAndDropsUnwritten() {
        val logs = mutableListOf<String>()
        val events = PersonaWriter.events(
            triggers,
            listOf(
                PersonaDto.Item(0, "뜻을 몰라요", "뜻을 먼저 말해요", question = "친구한테는 질문 없음"),
                PersonaDto.Item(1, " ", "빈 이유"),
                PersonaDto.Item(2, "왜 자라는지 몰라요", "이유를 말해요", question = "왜 자라?"),
                PersonaDto.Item(9, "없는 번호", "무시"),
            ),
            logs::add,
        )
        assertEquals(listOf(Persona.CLASSMATE, Persona.KID), events.map { it.persona })
        assertEquals("광합성", events[0].quote) // quote, time, and type always come from the trigger
        assertNull(events[0].question) // only the kid asks questions
        assertEquals("왜 자라?", events[1].question)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("trigger 1"))
        assertTrue("그래서" !in logs.single()) // logs never carry transcript text
    }

    @Test
    fun inputNumbersTriggersAndUsesContractNames() {
        val kidOnly = triggers.withIndex().filter { it.value.persona == Persona.KID }
        val input = Json.parseToJsonElement(PersonaWriter.input(emptyList(), kidOnly)).jsonObject
        val first = input.getValue("triggers").jsonArray.single().jsonObject
        assertEquals(2, first.getValue("index").jsonPrimitive.content.toInt()) // global index, not position in group
        assertEquals("kid", first.getValue("persona").jsonPrimitive.content)
        assertEquals("why_question", first.getValue("type").jsonPrimitive.content)
    }
}

class SchemasTest {
    @Test
    fun analyzerSchemaRequiresEveryFieldButSegmentsAndVisual() {
        val required = Schemas.analyzer.getValue("required").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("terms", "claims", "mismatches", "steps"), required)
    }
}

class SceneDescriberTest {
    @Test
    fun toScenesParsesTimesDropsBadRowsAndSorts() {
        val scenes = SceneDescriber.toScenes(
            SceneDto(
                listOf(
                    SceneDto.Item("00:20.5", "Slide 2", onScreenText = " 명반응 / 암반응 ", slideIndex = 2),
                    SceneDto.Item("??", "dropped"),
                    SceneDto.Item("00:03", "Title slide", onScreenText = "", slideIndex = 1, pointingAt = " "),
                ),
            ),
        )
        assertEquals(listOf(3.0, 20.5), scenes.map { it.t })
        assertNull(scenes[0].onScreenText) // blank text becomes null
        assertNull(scenes[0].pointingAt)
        assertEquals("명반응 / 암반응", scenes[1].onScreenText)
    }

    @Test
    fun visualSummaryCarriesOnScreenTextSoScreenQuotesCanBeGrounded() {
        val visual = Scene(20.5, "Slide 2", onScreenText = "명반응 / 암반응", slideIndex = 2, pointingAt = "diagram").toVisual()
        assertEquals("Slide 2 On screen: \"명반응 / 암반응\" Pointing at: diagram", visual.summary)
        assertEquals(2, visual.slideIndex)
    }

    @Test
    fun analyzerInputIncludesScenesOnlyWhenThereAreAny() {
        assertFalse("scenes" in Json.parseToJsonElement(Analyzer.input(emptyList())).jsonObject)
        val withScenes = Json.parseToJsonElement(Analyzer.input(emptyList(), listOf(Scene(1.0, "s", "글자")))).jsonObject
        assertEquals("글자", withScenes.getValue("scenes").jsonArray[0].jsonObject.getValue("on_screen_text").jsonPrimitive.content)
    }
}

class AnalysisRunTest {
    private val transcript = """{"segments":[
        {"start":"00:00","end":"00:04","text":"광합성은 빛으로 양분을 만들어요"},
        {"start":"00:05","end":"00:09","text":"그래서 식물은 자라요"}]}"""
    private val analysis = """{"terms":[{"term":"광합성","t":0.0,"level":"school","defined_at":null}],
        "claims":[{"t":5.0,"text":"그래서 식물은 자라요","has_reason":false,"evidence_on_screen":null}],
        "mismatches":[{"t":5.0,"type":"shown_not_said","quote":"광합성 공식"}],"steps":[]}"""
    private val writing = """{"items":[
        {"index":0,"reason":"광합성이 뭔지 몰라요","fix":"광합성의 뜻을 먼저 말해 주세요","question":null},
        {"index":1,"reason":"근거가 없어요","fix":"왜 자라는지 덧붙여요","question":null},
        {"index":2,"reason":"이유가 궁금해요","fix":"이유를 말해 주세요","question":"왜 자라?"}]}"""

    private val sceneJson = """{"scenes":[{"t":"00:01","summary":"Title slide","on_screen_text":"광합성 공식","slide_index":1,"pointing_at":null}]}"""

    // Hands the bytes back as a file reference; records whether the upload was used and released.
    private class FakeUploader(val fail: Boolean = false) : MediaUploader {
        var uploads = 0
        override suspend fun <T> withUploaded(bytes: ByteArray, mimeType: String, block: suspend (GeminiClient.Part.FileRef) -> T): T {
            uploads++
            if (fail) throw GeminiException(200, "FAILED", "File processing failed")
            return block(GeminiClient.Part.FileRef(mimeType, "files/fake"))
        }
    }

    private fun envelope(text: String) =
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}]},"finishReason":"STOP"}]}"""

    // Answers by system prompt; fails the analyzer on the attempts listed in failAnalyze.
    private fun run(
        calls: MutableList<String>,
        failAnalyze: Set<Int> = emptySet(),
        transcriptBody: String = transcript,
        parallel: Boolean = false,
        uploader: FakeUploader? = null,
        analyzerInputs: MutableList<String> = mutableListOf(),
    ): AnalysisRun {
        var analyzeAttempts = 0
        val engine = MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val prompt = body.getValue("systemInstruction").jsonObject.getValue("parts").jsonArray[0]
                .jsonObject.getValue("text").jsonPrimitive.content
            calls += prompt
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when (prompt) {
                "T" -> respond(envelope(transcriptBody), HttpStatusCode.OK, headers)
                "V" -> respond(envelope(sceneJson), HttpStatusCode.OK, headers)
                "A" -> {
                    val parts = body.getValue("contents").jsonArray[0].jsonObject.getValue("parts").jsonArray
                    analyzerInputs += parts[0].jsonObject.getValue("text").jsonPrimitive.content
                    if (analyzeAttempts++ in failAnalyze) {
                        respond("""{"error":{"code":400,"message":"bad","status":"INVALID_ARGUMENT"}}""", HttpStatusCode.BadRequest, headers)
                    } else {
                        respond(envelope(analysis), HttpStatusCode.OK, headers)
                    }
                }
                else -> respond(envelope(writing), HttpStatusCode.OK, headers)
            }
        }
        val client = GeminiClient("k", HttpClient(engine))
        return AnalysisRun(
            audio = byteArrayOf(1, 2, 3),
            mimeType = "audio/aac",
            transcriber = Transcriber(client, "T"),
            analyzer = Analyzer(client, "A"),
            writer = PersonaWriter(client, "W", parallel = parallel, log = {}),
            video = uploader?.let { VideoInput("video/mp4", read = { byteArrayOf(9) }) },
            describer = uploader?.let { SceneDescriber(client, it, "V") },
            log = {},
        )
    }

    @Test
    fun runsAllStagesIntoAReport() = runTest {
        val calls = mutableListOf<String>()
        val stages = mutableListOf<Stage>()
        val report = run(calls, parallel = true).run(stages::add)

        assertEquals(listOf(Stage.TRANSCRIBE, Stage.ANALYZE, Stage.WRITE), stages)
        assertEquals(3, calls.count { it == "W" }) // one writer call per persona
        assertEquals(2, report.segments.size)
        // The fake writer answers every call with all three items; each call keeps only its own, so no duplicates.
        assertEquals(3, report.events.size)
        assertEquals(listOf("광합성이 뭔지 몰라요", "근거가 없어요", "이유가 궁금해요"), report.events.map { it.reason })
        assertEquals(listOf("왜 자라?"), report.questions.map { it.question })
        // Teacher and kid flag the same claim, so it ranks first as the fix that helps the most listeners.
        assertEquals("그래서 식물은 자라요", report.topFixes.first().quote)
    }

    @Test
    fun singleWriterCallGivesTheSameEvents() = runTest {
        val calls = mutableListOf<String>()
        val report = run(calls, parallel = false).run {}
        assertEquals(1, calls.count { it == "W" })
        assertEquals(listOf("광합성이 뭔지 몰라요", "근거가 없어요", "이유가 궁금해요"), report.events.map { it.reason })
    }

    @Test
    fun retryResumesAtTheFailedStage() = runTest {
        val calls = mutableListOf<String>()
        val pipeline = run(calls, failAnalyze = setOf(0))

        val failure = assertFailsWith<StageFailure> { pipeline.run {} }
        assertEquals(Stage.ANALYZE, failure.stage)

        val stages = mutableListOf<Stage>()
        pipeline.run(stages::add)
        assertEquals(listOf(Stage.ANALYZE, Stage.WRITE), stages)
        assertEquals(1, calls.count { it == "T" }) // the audio is sent once
    }

    @Test
    fun emptyTranscriptIsANoSpeechFailure() = runTest {
        val failure = assertFailsWith<StageFailure> { run(mutableListOf(), transcriptBody = """{"segments":[]}""").run {} }
        assertEquals(Stage.TRANSCRIBE, failure.stage)
        assertTrue(failure.noSpeech)
    }

    @Test
    fun audioOnlyRunHasNoVideoStageOrMismatches() = runTest {
        val pipeline = run(mutableListOf())
        assertEquals(listOf(Stage.TRANSCRIBE, Stage.ANALYZE, Stage.WRITE), pipeline.stages)
        val report = pipeline.run {}
        assertEquals(VideoStatus.NONE, report.video)
        assertTrue(report.visual.isEmpty())
    }

    @Test
    fun videoScenesReachTheAnalyzerAndTheReport() = runTest {
        val calls = mutableListOf<String>()
        val inputs = mutableListOf<String>()
        val uploader = FakeUploader()
        val pipeline = run(calls, uploader = uploader, analyzerInputs = inputs)
        val stages = mutableListOf<Stage>()
        val report = pipeline.run(stages::add)

        assertEquals(listOf(Stage.TRANSCRIBE, Stage.VIDEO, Stage.ANALYZE, Stage.WRITE), stages)
        assertEquals(1, uploader.uploads)
        assertTrue("광합성 공식" in inputs.single()) // the scene timeline is in the analyzer's input
        assertEquals(VideoStatus.USED, report.video)
        assertEquals(1, report.visual.size)
        assertTrue("광합성 공식" in report.visual.single().summary)
    }

    @Test
    fun videoFailureFallsBackToAudioOnlyAndIsNotRetried() = runTest {
        val calls = mutableListOf<String>()
        val inputs = mutableListOf<String>()
        val uploader = FakeUploader(fail = true)
        val pipeline = run(calls, failAnalyze = setOf(0), uploader = uploader, analyzerInputs = inputs)

        assertFailsWith<StageFailure> { pipeline.run {} } // the analyzer fails once, unrelated to video
        val report = pipeline.run {}

        assertEquals(VideoStatus.FAILED, report.video)
        assertEquals(1, uploader.uploads) // a failed video is not uploaded again on retry
        assertTrue(inputs.none { "scenes" in it }) // the analyzer ran audio-only
        assertTrue(report.visual.isEmpty())
    }
}
