/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.playback

/** One caption: [lines] shown from [startMs] until [endMs]. */
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val lines: List<String>,
)

/**
 * Reads WebVTT and SubRip caption files, the formats NIP-71 `text-track` tags point at, into
 * [Cue]s sorted by start. For players that can't side-load captions themselves (AVPlayer only
 * reads them from inside an HLS stream). Styling tags (`<b>`, `<c.x>`, `{\an8}`) are dropped and
 * cue settings ignored: the shared caption overlay draws plain lines.
 */
object CaptionFileParser {
    private val timing = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")
    private val tags = Regex("""<[^>]*>|\{\\[^}]*\}""")

    fun parse(text: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
        for (block in blocks) {
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (timingIndex < 0) continue
            val match = timing.find(lines[timingIndex]) ?: continue
            val start = timestampMs(match.groupValues[1]) ?: continue
            val end = timestampMs(match.groupValues[2]) ?: continue
            val body =
                lines
                    .drop(timingIndex + 1)
                    .map { decodeEntities(it.replace(tags, "")).trim() }
                    .filter { it.isNotEmpty() }
            if (body.isNotEmpty() && end > start) cues += Cue(start, end, body)
        }
        return cues.sortedBy { it.startMs }
    }

    /** The lines on screen at [positionMs], across overlapping cues. */
    fun linesAt(
        cues: List<Cue>,
        positionMs: Long,
    ): List<String> = cues.filter { positionMs >= it.startMs && positionMs < it.endMs }.flatMap { it.lines }

    internal fun timestampMs(value: String): Long? {
        val parts = value.replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val secondsPart = parts.last().split('.')
        val seconds = secondsPart[0].toLongOrNull() ?: return null
        val millis =
            secondsPart
                .getOrNull(1)
                ?.padEnd(3, '0')
                ?.take(3)
                ?.toLongOrNull() ?: 0L
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    private fun decodeEntities(line: String): String =
        line
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
}
