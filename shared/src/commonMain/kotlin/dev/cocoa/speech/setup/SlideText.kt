package dev.cocoa.speech.setup

// Text of a PowerPoint (.pptx) deck for the setup memo. A .pptx is a zip; each slide is ppt/slides/slideN.xml and its
// visible text sits in <a:t> runs inside <a:p> paragraphs. The platform unzips; this turns slide XML into lines.
// Pictures, charts and speaker notes are not read.
object SlideText {
    private val slideEntry = Regex("""^ppt/slides/slide(\d+)\.xml$""")
    private val paragraph = Regex("""<a:p[ >].*?</a:p>|<a:p/>""", RegexOption.DOT_MATCHES_ALL)
    private val run = Regex("""<a:t(?:\s[^>]*)?>(.*?)</a:t>""", RegexOption.DOT_MATCHES_ALL)
    private val numericEntity = Regex("""&#(x?)([0-9a-fA-F]+);""")

    // The slide number for a zip entry name, or null if the entry is not a slide.
    fun slideNumber(entryName: String): Int? = slideEntry.find(entryName)?.groupValues?.get(1)?.toIntOrNull()

    // One line per non-empty paragraph, runs joined as written.
    fun lines(slideXml: String): List<String> =
        paragraph.findAll(slideXml).map { p ->
            run.findAll(p.value).joinToString("") { decode(it.groupValues[1]) }.trim()
        }.filter { it.isNotEmpty() }.toList()

    // "[슬라이드 n]" headers in slide order; slides without text are left out.
    fun format(slides: Map<Int, List<String>>): String =
        slides.toSortedMap().filterValues { it.isNotEmpty() }.entries.joinToString("\n\n") { (n, lines) ->
            "[슬라이드 $n]\n" + lines.joinToString("\n")
        }

    private fun decode(text: String): String =
        numericEntity.replace(text) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.let { codePointToString(it) } ?: m.value
        }
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun codePointToString(code: Int): String =
        if (code < 0x10000) code.toChar().toString()
        else {
            val c = code - 0x10000
            charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
        }
}
