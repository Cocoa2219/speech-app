package dev.cocoa.speech.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoTest {
    @Test
    fun aLoadedFileFillsAnEmptyMemo() {
        assertEquals("캘빈 회로: 포도당을 만드는 과정", mergeMemo("  ", "\n캘빈 회로: 포도당을 만드는 과정\n"))
    }

    @Test
    fun aLoadedFileGoesAfterWhatIsAlreadyTyped() {
        assertEquals("엽록체\n\n명반응", mergeMemo("엽록체 \n", "명반응"))
    }

    @Test
    fun theMergedMemoIsCutAtTheLimit() {
        val merged = mergeMemo("가".repeat(1990), "나".repeat(50))
        assertEquals(MEMO_MAX_CHARS, merged.length)
        assertEquals("나".repeat(8), merged.takeLast(8))
    }
}
