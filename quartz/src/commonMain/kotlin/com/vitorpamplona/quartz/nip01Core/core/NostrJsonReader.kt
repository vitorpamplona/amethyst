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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.reflect.KClass

/**
 * Reads what travels over a Nostr WebSocket straight off the frame's `String`: relay messages
 * ([readMessage]), client commands ([readCommand]), and the events, rumors and event lists inside
 * them. About twice as fast as Jackson on events, with ~40% less garbage.
 *
 * It finds the end of every string value with `String.indexOf('"')`, an intrinsic on HotSpot
 * and ART, and tracks the next backslash in the frame (also found with `indexOf`), so a value
 * without escapes is one `substring`. Event keys are matched by length and characters, never
 * built. Only values with escapes go through a `StringBuilder`.
 *
 * Every type follows what Quartz's former Jackson deserializers accepted and built (`EventDeserializer`,
 * `RumorDeserializer`, `MessageDeserializer`, `CommandDeserializer`, `ManualFilterDeserializer`,
 * the COUNT and LIMITS readers): the same optional elements, the same defaults and the same
 * lenient fields. It follows the JSON grammar everywhere, including values it skips, and never
 * accepts a frame Jackson rejects. It is stricter only where Jackson builds garbage or depends
 * on token order: a non-string `id`, `pubkey`, `content` or `sig`; an array or object inside a
 * tag, or where a frame element should be a string or number; a missing closing bracket is
 * rejected by both. Keys are matched by their text, where `EventDeserializer` matched hashes.
 * Every failure is an [IllegalArgumentException].
 *
 * A reader holds the cursor and scratch buffers, so one parse at a time uses it. On JVM and
 * Android each thread reuses its own (see [threadNostrJsonReader]), so the decode buffer and
 * tag scratch are allocated once per thread, not once per event; a nested parse on the same
 * thread, and every parse on native, gets a fresh one.
 */
internal class NostrJsonReader {
    internal var s = ""
    internal var n = 0

    /** The cursor. */
    internal var i = 0

    /**
     * Position of the next backslash at or after the cursor, or [n] when there is none. -1 until
     * the first string asks, so a frame that is not an EVENT is never scanned.
     */
    private var nextEscape = -1

    /** Whether the frame being read reached its closing bracket. */
    private var frameClosed = false

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
        frameClosed = false
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

    internal fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at offset $i")

    internal fun ws() {
        while (i < n) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++ else return
        }
    }

    internal fun expect(c: Char) {
        ws()
        if (i >= n || s[i] != c) fail("expected '$c'")
        i++
    }

    /** The next character after whitespace; fails at the end of input. */
    internal fun peek(): Char {
        ws()
        if (i >= n) fail("unexpected end of input")
        return s[i]
    }

    // ---- frames: ["LABEL", element, element, …]

    /**
     * Opens a frame and reads its label, or returns null when its first element is not a
     * string (Jackson reads that as an unsupported type).
     */
    internal fun openFrame(): String? {
        expect('[')
        if (peek() != '"') return null
        i++
        // Most frames are EVENTs: skip decoding their label.
        if (s.startsWith(EVENT_LABEL_QUOTE, i)) {
            i += EVENT_LABEL_QUOTE.length
            return EVENT_LABEL
        }
        return readStringBody()
    }

    /**
     * Steps to the frame's next element and returns true, with the cursor on it, or returns false
     * at the frame's closing bracket (and on every call after). A frame must close: one cut short
     * fails, as in Jackson.
     */
    internal fun nextElement(): Boolean {
        if (frameClosed) return false
        val c = peek()
        i++
        if (c == ',') {
            peek()
            return true
        }
        if (c == ']') {
            frameClosed = true
            return false
        }
        i--
        fail("expected ',' or ']'")
    }

    /** Skips the frame's remaining elements, checking them, through its closing bracket. */
    internal fun finishFrame() {
        while (nextElement()) skipValue(1)
    }

    /** The next element, which must be a string (Jackson's `nextTextValue()` into a non-null field). */
    internal fun requiredString(): String {
        if (!nextElement()) fail("missing element")
        return readString()
    }

    /** The next element, which must be an object; the cursor ends past it. */
    internal fun requiredObjectStart() {
        if (!nextElement()) fail("missing element")
        if (s[i] != '{') fail("expected an object")
    }

    // ---- events, rumors

    private var fId: String? = null
    private var fPubKey: String? = null
    private var fCreatedAt = 0L
    private var fHasCreatedAt = false
    private var fKind = 0
    private var fHasKind = false
    private var fTags: TagArray? = null
    private var fContent: String? = null
    private var fSig: String? = null

    /**
     * Reads an event-shaped object into the f* fields. [base] is its nesting depth (1 at the top
     * level, 2 inside a frame or a list), for Jackson's nesting limit on skipped values.
     */
    private fun readEventFields(
        base: Int,
        withSig: Boolean,
        withIdentity: Boolean = true,
    ) {
        fId = null
        fPubKey = null
        fHasCreatedAt = false
        fHasKind = false
        fTags = null
        fContent = null
        fSig = null

        expect('{')
        ws()
        if (i < n && s[i] == '}') {
            i++
            return
        }
        while (true) {
            ws()
            if (i >= n || s[i] != '"') fail("expected a field name")
            i++
            val field = readKey()
            expect(':')
            ws()
            if (i >= n) fail("unexpected end of input")
            when (field) {
                F_ID -> if (withIdentity) fId = readString() else skipValue(base)
                F_PUBKEY -> if (withIdentity) fPubKey = readString() else skipValue(base)
                F_CREATED_AT -> {
                    fCreatedAt = readLong()
                    fHasCreatedAt = true
                }
                F_KIND -> {
                    fKind = readInt()
                    fHasKind = true
                }
                F_TAGS -> fTags = readTags()
                F_CONTENT -> fContent = readString()
                F_SIG -> if (withSig) fSig = readString() else skipValue(base)
                else -> skipValue(base)
            }
            ws()
            if (i >= n) fail("unexpected end of input inside an object")
            val c = s[i++]
            if (c == ',') continue
            if (c == '}') return
            fail("expected ',' or '}'")
        }
    }

    /** One event object, like `EventDeserializer`: absent fields read as empty, a missing pubkey fails. */
    internal fun readEventObject(base: Int): Event {
        readEventFields(base, withSig = true)
        val pubKey = fPubKey
        if (pubKey.isNullOrEmpty()) throw IllegalArgumentException("Event not found")
        val event =
            EventFactory.create<Event>(
                fId ?: "",
                pubKey,
                if (fHasCreatedAt) fCreatedAt else 0L,
                if (fHasKind) fKind else 0,
                fTags ?: EMPTY_TAGS,
                fContent ?: "",
                fSig ?: "",
            )
        clearFields()
        return event
    }

    /** One rumor object, like `RumorDeserializer`: every field optional, `sig` ignored. */
    internal fun readRumorObject(base: Int): Rumor {
        readEventFields(base, withSig = false)
        val rumor =
            Rumor(
                fId,
                fPubKey,
                if (fHasCreatedAt) fCreatedAt else null,
                if (fHasKind) fKind else null,
                fTags,
                fContent,
            )
        clearFields()
        return rumor
    }

    /**
     * An event template (`created_at`, `kind`, `tags`, `content`), like `EventTemplateDeserializer`:
     * absent fields read as zero or empty, and anything else (an `id`, a `pubkey`) is skipped.
     */
    internal fun readEventTemplateObject(base: Int): EventTemplate<Event> {
        readEventFields(base, withSig = false, withIdentity = false)
        val template =
            EventTemplate<Event>(
                if (fHasCreatedAt) fCreatedAt else 0L,
                if (fHasKind) fKind else 0,
                fTags ?: EMPTY_TAGS,
                fContent ?: "",
            )
        clearFields()
        return template
    }

    /** A bare tag array, `[["e","…"],["p","…"]]`, read like `TagArrayDeserializer`. */
    internal fun readTagArray(): TagArray {
        ws()
        if (i >= n) fail("unexpected end of input")
        return readTags()
    }

    private fun clearFields() {
        fId = null
        fPubKey = null
        fTags = null
        fContent = null
        fSig = null
    }

    /**
     * A JSON array of events, or one event object (Quartz's Jackson mapper accepts a single
     * value as an array).
     */
    internal fun readEventList(): List<Event> {
        if (peek() == '{') return listOf(readEventObject(1))
        expect('[')
        if (peek() == ']') {
            i++
            return emptyList()
        }
        val list = ArrayList<Event>()
        while (true) {
            if (peek() != '{') fail("expected an event object")
            list.add(readEventObject(2))
            val c = peek()
            i++
            if (c == ',') continue
            if (c == ']') return list
            i--
            fail("expected ',' or ']'")
        }
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

    internal fun readString(): String {
        if (s[i] != '"') fail("expected a string")
        i++
        return readStringBody()
    }

    /** The cursor is just past the opening quote; returns the decoded value and leaves the cursor past the closing one. */
    internal fun readStringBody(): String {
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
    internal fun scanNumber(): Boolean {
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
    internal fun readLong(): Long {
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
    internal fun readInt(): Int {
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

    internal fun literal(
        word: String,
        value: String,
    ): String {
        if (!s.startsWith(word, i)) fail("expected $word")
        i += word.length
        return value
    }

    internal fun numberText(): String {
        val start = i
        scanNumber()
        return s.substring(start, i)
    }

    /**
     * Skips one value of any shape, checking its grammar, without building anything. Iterative:
     * a hostile relay's deep nesting costs a counter, not stack frames. Fails past Jackson's
     * nesting limit, counted from [base] (the depth of the value itself) the way Jackson counts it.
     */
    internal fun skipValue(base: Int) {
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
    internal fun skipStringBody() {
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

        private const val EVENT_LABEL = "EVENT"
        private const val EVENT_LABEL_QUOTE = "EVENT\""

        /** Scratch kept on a thread between parses; bigger buffers are dropped after use. */
        private const val MAX_KEPT_CHARS = 16 * 1024
        private const val MAX_KEPT_TAGS = 1024

        private val EMPTY_TAGS: TagArray = emptyArray()
        private val EMPTY_TAG = emptyArray<String>()

        private fun acquire(): NostrJsonReader {
            val local = threadNostrJsonReader()
            return if (local == null || local.inUse) NostrJsonReader() else local
        }

        /** One event object; throws [IllegalArgumentException] on malformed input or a missing pubkey. */
        fun readEvent(json: String): Event = acquire().run { parse(json) { readEventObject(1) } }

        /** One rumor (an unsigned event, NIP-59); every field optional. */
        fun readRumor(json: String): Rumor = acquire().run { parse(json) { readRumorObject(1) } }

        /** A JSON array of events, or one event object. */
        fun readEventList(json: String): List<Event> = acquire().run { parse(json) { readEventList() } }

        /** One relay-to-client frame; throws [IllegalArgumentException] on malformed or unsupported frames. */
        fun readMessage(json: String): Message = acquire().run { parse(json) { readMessageFrame() } }

        /** One client-to-relay frame; throws [IllegalArgumentException] on malformed or unsupported frames. */
        fun readCommand(json: String): Command = acquire().run { parse(json) { readCommandFrame() } }

        /** An unsigned event template, as NIP-46 `sign_event` and NIP-55 carry it. */
        fun readEventTemplate(json: String): EventTemplate<Event> =
            acquire().run {
                parse(json) {
                    ws()
                    readEventTemplateObject(1)
                }
            }

        /** A NIP-46 request or response; see [readBunkerObject]. */
        fun readBunkerMessage(json: String): BunkerMessage = acquire().run { parse(json) { readBunkerObject() } }

        fun readBunkerRequest(json: String): BunkerRequest = readBunkerMessage(json) as? BunkerRequest ?: throw IllegalArgumentException("not a NIP-46 request")

        fun readBunkerResponse(json: String): BunkerResponse = readBunkerMessage(json) as? BunkerResponse ?: throw IllegalArgumentException("not a NIP-46 response")

        /** Runs [read] on [json] with this thread's reader, for formats read outside this package. */
        internal fun <T> read(
            json: String,
            read: NostrJsonReader.() -> T,
        ): T = acquire().run { parse(json) { read() } }

        /** A tag array on its own. */
        fun readTagArray(json: String): TagArray = acquire().run { parse(json) { readTagArray() } }

        /** One filter object, read like `ManualFilterDeserializer`. */
        fun readFilter(json: String): Filter =
            acquire().run {
                parse(json) {
                    ws()
                    if (i >= n || s[i] != '{') fail("expected a filter object")
                    readFilterObject(1)
                }
            }
    }
}

/** This thread's reusable reader, or null where readers are not kept per thread (native). */
internal expect fun threadNostrJsonReader(): NostrJsonReader?

/**
 * [json] read as [type] when it is one of the types [NostrJsonReader] knows, else null so the
 * caller can hand it to another reader. Exact classes, like the Jackson registrations it replaced.
 */
@PublishedApi
internal fun readKnownType(
    type: KClass<*>,
    json: String,
): OptimizedSerializable? =
    when (type) {
        Event::class -> NostrJsonReader.readEvent(json)
        Filter::class -> NostrJsonReader.readFilter(json)
        Message::class -> NostrJsonReader.readMessage(json)
        Command::class -> NostrJsonReader.readCommand(json)
        Rumor::class -> NostrJsonReader.readRumor(json)
        EventTemplate::class -> NostrJsonReader.readEventTemplate(json)
        BunkerMessage::class -> NostrJsonReader.readBunkerMessage(json)
        BunkerRequest::class -> NostrJsonReader.readBunkerRequest(json)
        BunkerResponse::class -> NostrJsonReader.readBunkerResponse(json)
        else -> null
    }
