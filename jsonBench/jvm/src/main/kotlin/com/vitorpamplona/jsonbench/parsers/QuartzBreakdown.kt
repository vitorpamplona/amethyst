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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

/**
 * Quartz's EventDeserializer loop (hash-dispatched field names, TagArrayDeserializer's
 * tag loop) copied onto a bare jackson-core parser, with each of its extra costs behind a
 * switch. Together with `quartz-jackson` these rows split the production path's time into
 * databind `readValue` overhead, `String.intern()` and `EventFactory` building the kind's
 * subclass; their retained heap shows what interning saves in return.
 */
class QuartzEventLoop(
    private val intern: Boolean,
    private val factory: Boolean,
) {
    private val json = JsonFactory()
    private val emptyTags = emptyArray<Array<String>>()

    fun parse(input: String): QuartzEvent? = json.createParser(input).use { read(it) }

    fun parseToBench(input: String): Event = json.createParser(input).use { readBench(it) }

    private fun str(p: JsonParser): String = if (intern) p.text.intern() else p.text

    private inline fun <T> readFields(
        p: JsonParser,
        build: (String, String, Long, Int, Array<Array<String>>, String, String) -> T,
    ): T {
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tags = emptyTags
        var content = ""
        var sig = ""
        check(p.nextToken() == JsonToken.START_OBJECT) { "expected an object" }
        while (true) {
            val token = p.nextToken() ?: error("truncated object")
            if (token == JsonToken.END_OBJECT) break
            val fieldName = p.currentName()
            p.nextToken()
            when (fieldName.hashCode()) {
                3355 -> id = str(p)
                -977424830 -> pubKey = str(p)
                1369680106 -> createdAt = p.longValue
                3292052 -> kind = p.intValue
                3552281 -> tags = readTags(p)
                951530617 -> content = p.text
                113873 -> sig = p.text
                else -> p.skipChildren()
            }
        }
        return build(id, pubKey, createdAt, kind, tags, content, sig)
    }

    private fun readTags(p: JsonParser): Array<Array<String>> {
        val outerList = ArrayList<Array<String>>()
        while (p.nextToken() != JsonToken.END_ARRAY) {
            val innerList = ArrayList<String>(5)
            while (p.nextToken() != JsonToken.END_ARRAY) {
                innerList.add(if (p.currentToken == JsonToken.VALUE_NULL) "" else str(p))
            }
            outerList.add(innerList.toArray(arrayOfNulls<String>(innerList.size)))
        }
        return outerList.toArray(arrayOfNulls<Array<String>>(outerList.size))
    }

    private fun read(p: JsonParser): QuartzEvent? =
        readFields(p) { id, pubKey, createdAt, kind, tags, content, sig ->
            if (factory) EventFactory.create<QuartzEvent>(id, pubKey, createdAt, kind, tags, content, sig) else null
        }

    private fun readBench(p: JsonParser): Event = readFields(p) { id, pubKey, createdAt, kind, tags, content, sig -> Event(id, pubKey, createdAt, kind, tags, content, sig) }

    fun sinkOf(input: String): Long =
        if (factory) {
            val e = parse(input)!!
            (e.kind + e.tags.size + e.content.length).toLong()
        } else {
            val e = parseToBench(input)
            (e.kind + e.tags.size + e.content.length).toLong()
        }
}

/** Quartz's EventDeserializer loop as shipped (intern + EventFactory), minus jackson-databind's readValue. */
class QuartzLoop : StringInputParser() {
    private val loop = QuartzEventLoop(intern = true, factory = true)

    override fun decode(input: String): Event = loop.parse(input)!!.let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }

    override fun keep(input: String): Any = loop.parse(input)!!

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) sink += loop.sinkOf(s)
        return sink
    }
}

/** Quartz's loop + EventFactory, without String.intern(). */
class QuartzLoopNoIntern : StringInputParser() {
    private val loop = QuartzEventLoop(intern = false, factory = true)

    override fun decode(input: String): Event = loop.parse(input)!!.let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }

    override fun keep(input: String): Any = loop.parse(input)!!

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) sink += loop.sinkOf(s)
        return sink
    }
}

/** Quartz's loop alone: no intern, no EventFactory, a plain Event. */
class QuartzLoopPlain : StringInputParser() {
    private val loop = QuartzEventLoop(intern = false, factory = false)

    override fun decode(input: String): Event = loop.parseToBench(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) sink += loop.sinkOf(s)
        return sink
    }
}
