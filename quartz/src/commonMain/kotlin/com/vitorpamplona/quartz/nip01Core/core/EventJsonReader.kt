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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.utils.EventFactory

/**
 * Reads one Nostr event, or one `["EVENT", subId, event]` relay frame, straight off the
 * frame's `String`, about twice as fast as Jackson and with ~40% less garbage.
 *
 * It finds the end of every string value with `String.indexOf('"')`, an intrinsic on HotSpot
 * and ART, and tracks the next backslash in the frame (also found with `indexOf`), so a value
 * without escapes is one `substring`. Keys are matched by length and characters, never
 * built. Only values with escapes go through a `StringBuilder`.
 *
 * It accepts what Jackson accepts with Quartz's mapper settings (raw control characters in
 * strings, unknown fields of any shape, `null` tag values as "", trailing data after the
 * event), follows the JSON grammar for numbers and skipped values, and fails the way
 * `EventDeserializer` does on a missing pubkey. It is stricter in three places, all on events
 * that could never verify: a non-string `id`, `pubkey`, `content` or `sig`, an array or object
 * inside a tag, and a non-array inside `tags` throw instead of producing garbage. Every
 * failure is an [IllegalArgumentException].
 *
 * A reader holds the cursor and scratch buffers, so one parse at a time uses it. On JVM and
 * Android each thread reuses its own (see [threadEventJsonReader]), so the decode buffer and
 * tag scratch are allocated once per thread, not once per event; a nested parse on the same
 * thread, and every parse on native, gets a fresh one.
 */
internal class EventJsonReader {
    private var s = ""
    private var n = 0
    private var i = 0

    /**
     * Position of the next backslash at or after the cursor, or [n] when there is none. -1 until
     * the first string asks, so a frame that is not an EVENT is never scanned.
     */
    private var nextEscape = -1

    /** Nesting depth of the value being read: 1 inside the event object, 2 inside an EVENT frame's event. */
    private var baseDepth = 1

    private var inUse = false
    private var sb: StringBuilder? = null
    private var tagScratch: Array<String?>? = null
    private var tagsScratch: Array<Array<String>?>? = null
    private var skipStack: BooleanArray? = null

    private inline fun <T> parse(
        json: String,
        read: () -> T,
    ): T {
        s = json
        n = json.length
        i = 0
        nextEscape = -1
        baseDepth = 1
        inUse = true
        var done = false
        try {
            val result = read()
            done = true
            return result
        } finally {
            s = ""
            inUse = false
            if (!done) {
                // A parse that failed inside the tags left references in the scratch.
                tagsScratch?.fill(null)
                tagScratch?.fill(null)
            }
            // Don't let one huge event pin its buffers to the thread.
            if ((tagsScratch?.size ?: 0) > MAX_KEPT_TAGS) tagsScratch = null
            if ((tagScratch?.size ?: 0) > MAX_KEPT_TAGS) tagScratch = null
        }
    }

    private fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at offset $i")

    private fun ws() {
        while (i < n) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++ else return
        }
    }

    private fun expect(c: Char) {
        ws()
        if (i >= n || s[i] != c) fail("expected '$c'")
        i++
    }

    /** `["EVENT", subId, event, …]`, or null when the frame is another message type. */
    private fun readEventMessage(): EventMessage? {
        expect('[')
        ws()
        if (i >= n || s[i] != '"') return null
        i++
        if (s.startsWith(EVENT_LABEL_QUOTE, i)) {
            i += EVENT_LABEL_QUOTE.length
        } else {
            // Another message type, unless its label spells EVENT with escapes.
            val q = quoteAfter(i)
            if (q < 0) return null
            var escaped = false
            for (k in i until q) if (s[k] == '\\') escaped = true
            if (!escaped || readStringBody() != EventMessage.LABEL) return null
        }
        expect(',')
        ws()
        if (i >= n) fail("unexpected end of input")
        val subId = readString()
        expect(',')
        baseDepth = 2
        val event = readEvent()
        baseDepth = 1
        // Like MessageDeserializer: extra elements are skipped, and a frame that ends here
        // without its closing bracket is still taken.
        while (true) {
            ws()
            if (i >= n) break
            val c = s[i++]
            if (c == ']') break
            if (c != ',') fail("expected ',' or ']'")
            ws()
            if (i >= n) fail("unexpected end of input")
            skipValue(1)
        }
        return EventMessage(subId, event)
    }

    private fun readEvent(): Event {
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tags = EMPTY_TAGS
        var content = ""
        var sig = ""

        expect('{')
        ws()
        if (i < n && s[i] == '}') {
            i++
        } else {
            while (true) {
                ws()
                if (i >= n || s[i] != '"') fail("expected a field name")
                i++
                val field = readKey()
                expect(':')
                ws()
                if (i >= n) fail("unexpected end of input")
                when (field) {
                    F_ID -> id = readString()
                    F_PUBKEY -> pubKey = readString()
                    F_CREATED_AT -> createdAt = readLong()
                    F_KIND -> kind = readInt()
                    F_TAGS -> tags = readTags()
                    F_CONTENT -> content = readString()
                    F_SIG -> sig = readString()
                    else -> skipValue(baseDepth)
                }
                ws()
                if (i >= n) fail("unexpected end of input inside an object")
                val c = s[i++]
                if (c == ',') continue
                if (c == '}') break
                fail("expected ',' or '}'")
            }
        }

        if (pubKey.isEmpty()) throw IllegalArgumentException("Event not found")
        return EventFactory.create(id, pubKey, createdAt, kind, tags, content, sig)
    }

    /** Closing quote of the string body starting at [from], or -1 when the input ends first. */
    private fun quoteAfter(from: Int): Int = s.indexOf('"', from)

    /** Keeps [nextEscape] at or after [from]; it only falls behind on input that later fails. */
    private fun escapeAtOrAfter(from: Int): Int {
        if (nextEscape < from) nextEscape = s.indexOf('\\', from).let { if (it < 0) n else it }
        return nextEscape
    }

    /** Reads a field name (the cursor is just past its opening quote) and returns which field it is. */
    private fun readKey(): Int {
        val start = i
        val q = quoteAfter(start)
        if (q < 0) fail("unexpected end of input in a field name")
        if (escapeAtOrAfter(start) < q) return fieldOf(readStringBody())
        i = q + 1
        return when (q - start) {
            2 -> if (s[start] == 'i' && s[start + 1] == 'd') F_ID else F_OTHER
            3 -> if (s.startsWith("sig", start)) F_SIG else F_OTHER
            4 ->
                when {
                    s.startsWith("kind", start) -> F_KIND
                    s.startsWith("tags", start) -> F_TAGS
                    else -> F_OTHER
                }
            6 -> if (s.startsWith("pubkey", start)) F_PUBKEY else F_OTHER
            7 -> if (s.startsWith("content", start)) F_CONTENT else F_OTHER
            10 -> if (s.startsWith("created_at", start)) F_CREATED_AT else F_OTHER
            else -> F_OTHER
        }
    }

    private fun fieldOf(key: String): Int =
        when (key) {
            "id" -> F_ID
            "pubkey" -> F_PUBKEY
            "created_at" -> F_CREATED_AT
            "kind" -> F_KIND
            "tags" -> F_TAGS
            "content" -> F_CONTENT
            "sig" -> F_SIG
            else -> F_OTHER
        }

    private fun readString(): String {
        if (s[i] != '"') fail("expected a string")
        i++
        return readStringBody()
    }

    /** The cursor is just past the opening quote; returns the decoded value and leaves the cursor past the closing one. */
    private fun readStringBody(): String {
        val start = i
        val q = quoteAfter(start)
        if (q < 0) fail("unexpected end of input in a string")
        val esc0 = escapeAtOrAfter(start)
        if (q < esc0) {
            i = q + 1
            return s.substring(start, q)
        }

        // Escapes: decode run by run. [quote] and [esc] are the next quote and backslash at or
        // after the cursor, each searched again only once the cursor passes it, so a value with
        // thousands of escapes is still read in one pass.
        val sb = sb ?: StringBuilder(256).also { sb = it }
        sb.setLength(0)
        var quote = q
        var esc = esc0
        var from = start
        while (esc < quote) {
            sb.appendRange(s, from, esc)
            i = esc
            sb.append(readEscape())
            from = i
            if (quote < i) quote = quoteAfter(i).also { if (it < 0) fail("unexpected end of input in a string") }
            esc = s.indexOf('\\', i).let { if (it < 0) n else it }
        }
        sb.appendRange(s, from, quote)
        i = quote + 1
        nextEscape = esc
        val value = sb.toString()
        // Don't let one huge value pin its buffer to the thread.
        if (sb.length > MAX_KEPT_CHARS) this.sb = null
        return value
    }

    /** Decodes the escape at the cursor (a backslash) and moves past it. */
    private fun readEscape(): Char {
        if (i + 1 >= n) fail("unexpected end of input in an escape")
        val c =
            when (s[i + 1]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (i + 6 > n) fail("unexpected end of input in a \\u escape")
                    val v = (hex(s[i + 2]) shl 12) or (hex(s[i + 3]) shl 8) or (hex(s[i + 4]) shl 4) or hex(s[i + 5])
                    i += 4
                    v.toChar()
                }
                else -> fail("bad escape")
            }
        i += 2
        return c
    }

    private fun hex(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> fail("bad hex digit in a \\u escape")
        }

    /** Scans one JSON number with the exact grammar; returns true when it has a fraction or an exponent. */
    private fun scanNumber(): Boolean {
        if (i < n && s[i] == '-') i++
        if (i >= n) fail("expected a number")
        val first = s[i]
        if (first == '0') {
            i++
            if (i < n && s[i] in '0'..'9') fail("leading zeros are not allowed")
        } else if (first in '1'..'9') {
            i++
            while (i < n && s[i] in '0'..'9') i++
        } else {
            fail("expected a number")
        }
        var real = false
        if (i < n && s[i] == '.') {
            i++
            real = true
            if (i >= n || s[i] !in '0'..'9') fail("expected a digit after the decimal point")
            while (i < n && s[i] in '0'..'9') i++
        }
        if (i < n && (s[i] == 'e' || s[i] == 'E')) {
            i++
            real = true
            if (i < n && (s[i] == '+' || s[i] == '-')) i++
            if (i >= n || s[i] !in '0'..'9') fail("expected a digit in the exponent")
            while (i < n && s[i] in '0'..'9') i++
        }
        return real
    }

    /** Digits in [start, cursor) with an optional sign; at most 18 of them, so no overflow. */
    private fun digitsValue(start: Int): Long {
        val neg = s[start] == '-'
        var v = 0L
        for (k in (if (neg) start + 1 else start) until i) v = v * 10 + (s[k] - '0')
        return if (neg) -v else v
    }

    /** A JSON number as a long. Like Jackson's longValue: a fraction is truncated, out of range fails. */
    private fun readLong(): Long {
        val start = i
        if (!scanNumber()) {
            if (i - start <= 18) return digitsValue(start)
            return s.substring(start, i).toLongOrNull() ?: fail("number out of range of long")
        }
        val d = s.substring(start, i).toDouble()
        if (!(d >= Long.MIN_VALUE.toDouble() && d <= Long.MAX_VALUE.toDouble())) fail("number out of range of long")
        return d.toLong()
    }

    /** A JSON number as an int. Like Jackson's intValue: a fraction is truncated, out of range fails. */
    private fun readInt(): Int {
        val start = i
        if (!scanNumber()) {
            val v = if (i - start <= 18) digitsValue(start) else s.substring(start, i).toLongOrNull() ?: fail("number out of range of int")
            if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) fail("number out of range of int")
            return v.toInt()
        }
        val d = s.substring(start, i).toDouble()
        if (!(d >= Int.MIN_VALUE.toDouble() && d <= Int.MAX_VALUE.toDouble())) fail("number out of range of int")
        return d.toInt()
    }

    private fun readTags(): TagArray {
        if (s[i] != '[') fail("expected the tags array")
        i++
        ws()
        if (i < n && s[i] == ']') {
            i++
            return EMPTY_TAGS
        }
        var outer = tagsScratch ?: arrayOfNulls<Array<String>>(16).also { tagsScratch = it }
        var count = 0
        while (true) {
            ws()
            val tag = readTag()
            if (count == outer.size) {
                outer = outer.copyOf(count * 2)
                tagsScratch = outer
            }
            outer[count++] = tag
            ws()
            if (i >= n) fail("unexpected end of input in tags")
            val c = s[i++]
            if (c == ',') continue
            if (c == ']') break
            fail("expected ',' or ']' in tags")
        }
        @Suppress("UNCHECKED_CAST")
        val tags = outer.copyOf(count) as TagArray
        outer.fill(null, 0, count)
        return tags
    }

    private fun readTag(): Array<String> {
        if (i >= n || s[i] != '[') fail("expected a tag array")
        i++
        ws()
        if (i < n && s[i] == ']') {
            i++
            return EMPTY_TAG
        }
        var buf = tagScratch ?: arrayOfNulls<String>(8).also { tagScratch = it }
        var count = 0
        while (true) {
            ws()
            if (i >= n) fail("unexpected end of input in a tag")
            // Like TagArrayDeserializer: null is "", numbers and booleans keep their text.
            val value =
                when (s[i]) {
                    '"' -> readString()
                    'n' -> literal("null", "")
                    't' -> literal("true", "true")
                    'f' -> literal("false", "false")
                    else -> numberText()
                }
            if (count == buf.size) {
                buf = buf.copyOf(count * 2)
                tagScratch = buf
            }
            buf[count++] = value
            ws()
            if (i >= n) fail("unexpected end of input in a tag")
            val c = s[i++]
            if (c == ',') continue
            if (c == ']') break
            fail("expected ',' or ']' in a tag")
        }
        @Suppress("UNCHECKED_CAST")
        val tag = buf.copyOf(count) as Array<String>
        buf.fill(null, 0, count)
        return tag
    }

    private fun literal(
        word: String,
        value: String,
    ): String {
        if (!s.startsWith(word, i)) fail("expected $word")
        i += word.length
        return value
    }

    private fun numberText(): String {
        val start = i
        scanNumber()
        return s.substring(start, i)
    }

    /**
     * Skips one value of any shape, checking its grammar, without building anything. Iterative:
     * a hostile relay's deep nesting costs a counter, not stack frames. Fails past Jackson's
     * nesting limit, counted from [baseDepth] the way Jackson counts it.
     */
    private fun skipValue(base: Int) {
        var depth = 0
        var stack = skipStack ?: BooleanArray(16).also { skipStack = it }
        while (true) {
            // One value. A non-empty container is opened here and its first value read next turn.
            var complete = true
            when (s[i]) {
                '"' -> {
                    i++
                    skipStringBody()
                }
                '{', '[' -> {
                    val isObject = s[i] == '{'
                    if (base + depth + 1 > MAX_DEPTH) fail("nested too deeply")
                    i++
                    ws()
                    if (i < n && s[i] == (if (isObject) '}' else ']')) {
                        i++
                    } else {
                        if (depth == stack.size) {
                            stack = stack.copyOf(depth * 2)
                            skipStack = stack
                        }
                        stack[depth++] = isObject
                        if (isObject) skipKeyAndColon()
                        ws()
                        if (i >= n) fail("unexpected end of input")
                        complete = false
                    }
                }
                't' -> literal("true", "")
                'f' -> literal("false", "")
                'n' -> literal("null", "")
                else -> scanNumber()
            }
            if (!complete) continue
            // After a value: separators and closing brackets, until the next value or the end.
            while (true) {
                if (depth == 0) return
                ws()
                if (i >= n) fail("unexpected end of input")
                val c = s[i++]
                val isObject = stack[depth - 1]
                if (c == ',') {
                    if (isObject) skipKeyAndColon()
                    ws()
                    if (i >= n) fail("unexpected end of input")
                    break
                }
                if (c == (if (isObject) '}' else ']')) {
                    depth--
                    continue
                }
                fail(if (isObject) "expected ',' or '}'" else "expected ',' or ']'")
            }
        }
    }

    private fun skipKeyAndColon() {
        ws()
        if (i >= n || s[i] != '"') fail("expected a field name")
        i++
        skipStringBody()
        expect(':')
    }

    /** Like [readStringBody], checking escapes without building the value. */
    private fun skipStringBody() {
        var quote = quoteAfter(i)
        if (quote < 0) fail("unexpected end of input in a string")
        var esc = escapeAtOrAfter(i)
        while (esc < quote) {
            i = esc
            readEscape()
            if (quote < i) quote = quoteAfter(i).also { if (it < 0) fail("unexpected end of input in a string") }
            esc = s.indexOf('\\', i).let { if (it < 0) n else it }
        }
        i = quote + 1
        nextEscape = esc
    }

    companion object {
        private const val F_OTHER = 0
        private const val F_ID = 1
        private const val F_PUBKEY = 2
        private const val F_CREATED_AT = 3
        private const val F_KIND = 4
        private const val F_TAGS = 5
        private const val F_CONTENT = 6
        private const val F_SIG = 7

        /** Jackson's default nesting limit (StreamReadConstraints). */
        private const val MAX_DEPTH = 1000

        private const val EVENT_LABEL_QUOTE = "EVENT\""

        /** Scratch kept on a thread between parses; bigger buffers are dropped after use. */
        private const val MAX_KEPT_CHARS = 16 * 1024
        private const val MAX_KEPT_TAGS = 1024

        private val EMPTY_TAGS: TagArray = emptyArray()
        private val EMPTY_TAG = emptyArray<String>()

        private fun acquire(): EventJsonReader {
            val local = threadEventJsonReader()
            return if (local == null || local.inUse) EventJsonReader() else local
        }

        /** One event object; throws [IllegalArgumentException] on malformed input or a missing pubkey. */
        fun readEvent(json: String): Event = acquire().run { parse(json) { readEvent() } }

        /**
         * One relay frame when it is `["EVENT", subId, event, …]`; null when it is another
         * message type, for the caller to parse its own way. Throws [IllegalArgumentException]
         * on a malformed EVENT frame.
         */
        fun readEventMessage(json: String): EventMessage? = acquire().run { parse(json) { readEventMessage() } }
    }
}

/** This thread's reusable reader, or null where readers are not kept per thread (native). */
internal expect fun threadEventJsonReader(): EventJsonReader?
