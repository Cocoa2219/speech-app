package dev.cocoa.speech.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlideTextTest {
    // Trimmed from a real slide: a title shape and a body shape with two paragraphs, one split into runs.
    private val slide = """
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree>
        <p:sp><p:txBody><a:bodyPr/><a:p><a:r><a:rPr lang="ko-KR"/><a:t>광합성 과정</a:t></a:r></a:p></p:txBody></p:sp>
        <p:sp><p:txBody><a:p><a:pPr lvl="1"/><a:r><a:t>명반응 </a:t></a:r><a:r><a:rPr b="1"/><a:t>→ 암반응</a:t></a:r></a:p>
        <a:p/><a:p><a:r><a:t xml:space="preserve">CO&#8322; + H&#x2082;O &amp; 빛</a:t></a:r></a:p></p:txBody></p:sp>
        </p:spTree></p:cSld></p:sld>
    """.trimIndent()

    @Test
    fun readsParagraphsAndJoinsRuns() {
        assertEquals(listOf("광합성 과정", "명반응 → 암반응", "CO₂ + H₂O & 빛"), SlideText.lines(slide))
    }

    @Test
    fun recognizesSlideEntriesOnly() {
        assertEquals(12, SlideText.slideNumber("ppt/slides/slide12.xml"))
        assertNull(SlideText.slideNumber("ppt/slides/_rels/slide1.xml.rels"))
        assertNull(SlideText.slideNumber("ppt/notesSlides/notesSlide1.xml"))
        assertNull(SlideText.slideNumber("ppt/slideLayouts/slideLayout1.xml"))
    }

    @Test
    fun formatsInSlideOrderAndSkipsEmptySlides() {
        val text = SlideText.format(mapOf(10 to listOf("끝"), 2 to listOf("제목", "본문"), 3 to emptyList()))
        assertEquals("[슬라이드 2]\n제목\n본문\n\n[슬라이드 10]\n끝", text)
    }
}
