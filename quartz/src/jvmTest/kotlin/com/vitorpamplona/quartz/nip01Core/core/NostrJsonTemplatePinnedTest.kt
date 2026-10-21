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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Event templates, bare tag arrays and filters read and written by Quartz's reader and writer:
 * every captured event as a template and as its tags, hand-picked odd inputs, and every prefix
 * of a sample. The pins were recorded while every outcome was also compared with the Jackson
 * (de)serializers they replaced; the two agreed wherever Jackson accepted the input.
 */
class NostrJsonTemplatePinnedTest {
    private fun <T> run(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun describe(t: EventTemplate<*>) = "${t.createdAt}|${t.kind}|${t.tags.joinToString("|") { it.joinToString(",") }}|${t.content}"

    private fun describe(tags: TagArray) = tags.joinToString("|") { it.joinToString(",") }

    private fun describe(f: Filter): String = "Filter(${f.ids},${f.authors},${f.kinds},${f.tags},${f.tagsAll},${f.since},${f.until},${f.limit},${f.search})"

    private class Tally {
        val pin = Pin("")
        var accepted = 0
        var rejected = 0
    }

    private fun compare(
        input: String,
        reader: (String) -> String,
        t: Tally,
    ) {
        val r = run { reader(input) }
        t.pin.add(r.getOrNull())
        if (r.isSuccess) t.accepted++ else t.rejected++
    }

    private fun assertPinned(
        name: String,
        t: Tally,
    ) {
        t.pin.name = name
        println("$name: accepted ${t.accepted}, rejected ${t.rejected}")
        t.pin.check(PINS.getValue(name))
    }

    private val templateReader = { s: String -> describe(NostrJsonReader.readEventTemplate(s)) }
    private val tagsReader = { s: String -> describe(NostrJsonReader.readTagArray(s)) }
    private val filterReader = { s: String -> describe(NostrJsonReader.readFilter(s)) }

    private fun capturedEvents(limit: Int): List<String> {
        val out = ArrayList<String>()
        val stream = javaClass.classLoader!!.getResourceAsStream("nostr_vitor_startup_data.json.gz")!!
        GZIPInputStream(stream).bufferedReader().use { reader ->
            val buf = CharArray(1 shl 16)
            val sb = StringBuilder()
            var depth = 0
            var inStr = false
            var esc = false
            var n = reader.read(buf)
            while (n > 0 && out.size < limit) {
                for (i in 0 until n) {
                    val c = buf[i]
                    if (depth > 0) sb.append(c)
                    when {
                        esc -> {
                            esc = false
                        }

                        c == '\\' && inStr -> {
                            esc = true
                        }

                        c == '"' -> {
                            inStr = !inStr
                        }

                        inStr -> {}

                        c == '{' -> {
                            if (depth == 0) sb.append(c)
                            depth++
                        }

                        c == '}' -> {
                            depth--
                            if (depth == 0) {
                                out += sb.toString()
                                sb.setLength(0)
                            }
                        }
                    }
                }
                n = reader.read(buf)
            }
        }
        return out
    }

    @Test
    fun capturedEventsAsTemplatesAndTags() {
        val templates = Tally()
        val tags = Tally()
        val written = Tally()
        for (json in capturedEvents(Int.MAX_VALUE)) {
            compare(json, templateReader, templates)
            val e = NostrJsonReader.readEvent(json)
            val tagsJson = NostrJsonWriter.toJson(e.tags)
            compare(tagsJson, tagsReader, tags)
            val template = EventTemplate<Event>(e.createdAt, e.kind, e.tags, e.content)
            compare("", { NostrJsonWriter.toJson(template) }, written)
        }
        assertPinned("templates", templates)
        assertPinned("tag arrays", tags)
        assertPinned("written templates", written)
        assertTrue(templates.accepted > 200_000)
    }

    private val oddTemplates =
        listOf(
            "{}",
            """{"kind":1}""",
            """{"created_at":5,"kind":7,"tags":[],"content":"x"}""",
            """{"id":null,"pubkey":{"a":[1]},"sig":5,"created_at":5,"kind":7,"tags":[["a",null,1,true,1.5]],"content":"x"}""",
            """{"content":5}""",
            """{"content":null}""",
            """{"content":true}""",
            """{"content":{}}""",
            """{"content":[]}""",
            """{"tags":[[]]}""",
            """{"tags":[["a"],[]]}""",
            """{"created_at":"5"}""",
            """{"created_at":5.9,"kind":7.2}""",
            """{"created_at":99999999999999999999}""",
            """{"kind":3000000000}""",
            """{"kind":-1}""",
            """{"extra":{"deep":[[[[{}]]]]},"kind":1}""",
            """{"content":"A\n😀"}""",
            """ { "kind" : 1 } trailing""",
            """[]""",
            """"string"""",
            "",
            "{",
            """{"kind":}""",
            """{"kind":1,}""",
        )

    @Test
    fun oddTemplatesTagsAndFilters() {
        val t = Tally()
        for (s in oddTemplates) compare(s, templateReader, t)
        assertPinned("odd templates", t)

        // Tags of the wrong shape, and every prefix of a real template, are rejected. These were
        // always checked against the reader alone: the replaced deserializer was no oracle for them.
        for (bad in listOf("""{"tags":{}}""", """{"tags":null}""", """{"tags":[{"a":1}]}""", """{"tags":["a"]}""", """{"tags":[[{}]]}""", """{"tags":[[["nested"]]]}""")) {
            if (run { NostrJsonReader.readEventTemplate(bad) }.isSuccess) fail("accepted malformed tags: $bad")
        }
        for (json in capturedEvents(40)) {
            for (k in json.indices) {
                if (run { NostrJsonReader.readEventTemplate(json.substring(0, k)) }.isSuccess) fail("accepted a truncated template: ${json.substring(0, k)}")
            }
        }

        val tags = Tally()
        val oddTags =
            listOf("[]", "[[]]", """[["a","b"],["c"]]""", """[["a",null,1,true,-2.5e3]]""", "", """ [ [ "a" ] ] x""")
        for (s in oddTags) compare(s, tagsReader, tags)
        assertPinned("odd tag arrays", tags)
        for (bad in listOf("[", """[["a"],""", """[["a""", """[["a",""", "[[[]]]", """[{"a":1}]""", """["a"]""", "{}")) {
            if (run { NostrJsonReader.readTagArray(bad) }.isSuccess) fail("accepted a malformed tag array: $bad")
        }

        val filters = Tally()
        val oddFilters =
            listOf(
                "{}",
                """{"kinds":[1,2],"authors":["a"],"#e":["x"],"&t":["y"],"since":1,"until":2,"limit":3,"search":"q"}""",
                """{"kinds":"1","ids":{},"since":null,"limit":"5"}""",
                """{"unknown":[{"deep":1}],"kinds":[1.7,"x",null]}""",
                "[]",
                "",
                """{"kinds":[1}""",
            )
        for (s in oddFilters) compare(s, filterReader, filters)
        assertPinned("filters", filters)
    }

    companion object {
        /** See [Pin]: recorded while every outcome was also compared with Jackson. */
        private val PINS =
            mapOf(
                "templates" to "9768c546eec3ec796c9559ed3c38bd2dad90928e7b68e6e932f6332aadd58d64",
                "tag arrays" to "903900905972551d6c983e609fd1bd5fdcf00677a6e7ca7e101e7a1ff7b6c9c3",
                "written templates" to "610e602d0c8da268854f1cee50626d844151c54e5a072fdebbf4f233a24aa5ec",
                "odd templates" to "a9e3bcced953d3576eccbfb3057132fa966173f9e47325bdbf21ce7239684eca",
                "odd tag arrays" to "5343507e47666a3ea93f1862122fcbe70e98b5b005283fb18662e1026bf5bf5c",
                "filters" to "67668e6943165af16a0d9e0d99bbb42006087f9efda6053145768f41e6b719f6",
            )
    }
}
