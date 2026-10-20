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
package com.vitorpamplona.jsonbench.parsers

import com.squareup.moshi.JsonReader
import com.vitorpamplona.jsonbench.EMPTY_TAGS
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.jsonbench.TagCollector
import okio.Buffer

/**
 * Moshi's streaming reader with pre-encoded field names ([JsonReader.Options]), the way
 * Moshi's own generated adapters read. The frame's String is written into an okio [Buffer]
 * with `writeUtf8` (timed: it is how Moshi consumes a String).
 */
class MoshiStreaming : StringInputParser() {
    private val tags = TagCollector()
    private val names = JsonReader.Options.of("id", "pubkey", "created_at", "kind", "tags", "content", "sig")

    private fun parse(input: String): Event {
        val r = JsonReader.of(Buffer().writeUtf8(input))
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tagArray = EMPTY_TAGS
        var content = ""
        var sig = ""
        r.beginObject()
        while (r.hasNext()) {
            when (r.selectName(names)) {
                0 -> id = r.nextString()
                1 -> pubKey = r.nextString()
                2 -> createdAt = r.nextLong()
                3 -> kind = r.nextInt()
                4 -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        tags.startTag()
                        r.beginArray()
                        while (r.hasNext()) tags.addValue(r.nextString())
                        r.endArray()
                        tags.endTag()
                    }
                    r.endArray()
                    tagArray = tags.finish()
                }
                5 -> content = r.nextString()
                6 -> sig = r.nextString()
                else -> {
                    r.skipName()
                    r.skipValue()
                }
            }
        }
        r.endObject()
        return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
    }

    override fun decode(input: String) = parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (b in inputs) {
            val e = parse(b)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}
