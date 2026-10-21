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
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor

/**
 * Quartz's own JSON writer for what it puts on a WebSocket (events, tags, rumors, filters,
 * every relay message and client command) and for the NIP-01 id hash, the twin of
 * [NostrJsonReader].
 *
 * The output is byte-identical to the Jackson serializers it replaces, including their
 * escaping, which is wider than NIP-01's: `"`, `\` and the short forms `\b \f \n \r \t`, every
 * other char below 0x20 as `\u00XX` in uppercase hex, everything else (DEL, U+2028, `/`,
 * non-ASCII) as is. Two modes share that rule:
 *
 * - **Text** ([toJson]) builds a `String`. A lone surrogate is written as is, like Jackson's
 *   string generator.
 * - **UTF-8** (the id hash) writes the bytes SHA-256 reads, like Jackson's UTF-8 generator
 *   with `COMBINE_UNICODE_SURROGATES_IN_UTF8`: a surrogate pair becomes one 4-byte sequence and a
 *   lone surrogate is escaped as `\uXXXX`, so every event hashes to the same id it always did.
 *
 * Each string costs a check and one bulk copy in the common case where it has nothing to
 * escape: in text mode `String.indexOf` (an intrinsic on the JVM) looks for `"` and `\` and a
 * loop for control chars; in UTF-8 mode each run of printable ASCII goes through
 * [copyAsciiInto] in one call and only the chars between runs are encoded one by one.
 *
 * Not thread-safe. On the JVM and Android each thread reuses its own (see
 * [threadNostrJsonWriter]), so the buffers are allocated once per thread, not once per write;
 * a nested write on the same thread, and every write on native, gets a fresh one.
 */
class NostrJsonWriter internal constructor() {
    private var inUse = false

    internal var sb = StringBuilder(INITIAL_CHARS)

    private var buf = ByteArray(INITIAL_BYTES)
    private var pos = 0

    /** Set when the next object field needs a comma before it; reset by [objectStart]. */
    private var needsComma = false

    private inline fun <T> writing(write: NostrJsonWriter.() -> T): T {
        inUse = true
        try {
            return write()
        } finally {
            inUse = false
            if (sb.length > MAX_KEPT_CHARS) sb = StringBuilder(INITIAL_CHARS) else sb.clear()
            if (buf.size > MAX_KEPT_BYTES) buf = ByteArray(INITIAL_BYTES)
            pos = 0
        }
    }

    private inline fun text(write: NostrJsonWriter.() -> Unit): String =
        writing {
            write()
            sb.toString()
        }

    // ---------------------------------------------------------------------------------------
    // Text mode
    // ---------------------------------------------------------------------------------------

    internal fun raw(c: Char) {
        sb.append(c)
    }

    internal fun raw(s: String) {
        sb.append(s)
    }

    internal fun number(v: Long) {
        sb.append(v)
    }

    internal fun number(v: Int) {
        sb.append(v)
    }

    internal fun bool(v: Boolean) {
        sb.append(if (v) "true" else "false")
    }

    internal fun string(s: String) {
        val sb = sb
        sb.append('"')
        stringBody(s)
        sb.append('"')
    }

    /** [s] escaped, without the quotes. */
    internal fun stringBody(s: String) {
        if (s.indexOf('"') < 0 && s.indexOf('\\') < 0 && hasNoControlChar(s)) {
            sb.append(s)
        } else {
            escapedBody(s)
        }
    }

    private fun escapedBody(s: String) {
        val sb = sb
        var start = 0
        for (k in s.indices) {
            val c = s[k]
            if (c < ' ' || c == '"' || c == '\\') {
                if (k > start) sb.appendRange(s, start, k)
                sb.append('\\')
                when (c) {
                    '"' -> {
                        sb.append('"')
                    }

                    '\\' -> {
                        sb.append('\\')
                    }

                    '\b' -> {
                        sb.append('b')
                    }

                    '\u000C' -> {
                        sb.append('f')
                    }

                    '\n' -> {
                        sb.append('n')
                    }

                    '\r' -> {
                        sb.append('r')
                    }

                    '\t' -> {
                        sb.append('t')
                    }

                    else -> {
                        sb.append("u00")
                        sb.append(HEX[c.code shr 4])
                        sb.append(HEX[c.code and 0xF])
                    }
                }
                start = k + 1
            }
        }
        if (start < s.length) sb.appendRange(s, start, s.length)
    }

    /** `,` unless this is the object's first field, then `"name":`. [name] needs no escaping. */
    internal fun field(name: String) {
        val sb = sb
        if (needsComma) sb.append(',') else needsComma = true
        sb.append('"')
        sb.append(name)
        sb.append('"')
        sb.append(':')
    }

    /** Like [field] for `"<prefix><name>":`, with [name] escaped. */
    internal fun fieldPrefixed(
        prefix: Char,
        name: String,
    ) {
        val sb = sb
        if (needsComma) sb.append(',') else needsComma = true
        sb.append('"')
        sb.append(prefix)
        stringBody(name)
        sb.append('"')
        sb.append(':')
    }

    internal fun objectStart() {
        sb.append('{')
        needsComma = false
    }

    internal fun objectEnd() {
        sb.append('}')
        needsComma = true
    }

    internal fun strings(list: List<String>) {
        val sb = sb
        sb.append('[')
        for (k in list.indices) {
            if (k > 0) sb.append(',')
            string(list[k])
        }
        sb.append(']')
    }

    internal fun numbers(list: List<Int>) {
        val sb = sb
        sb.append('[')
        for (k in list.indices) {
            if (k > 0) sb.append(',')
            sb.append(list[k])
        }
        sb.append(']')
    }

    internal fun tagArray(tags: TagArray) {
        val sb = sb
        sb.append('[')
        for (t in tags.indices) {
            if (t > 0) sb.append(',')
            val tag = tags[t]
            sb.append('[')
            for (k in tag.indices) {
                if (k > 0) sb.append(',')
                string(tag[k])
            }
            sb.append(']')
        }
        sb.append(']')
    }

    /** `{"id":…,"pubkey":…,"created_at":…,"kind":…,"tags":…,"content":…,"sig":…}`, as `EventSerializer` writes it. */
    internal fun eventObject(
        id: HexKey,
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: TagArray,
        content: String,
        sig: String,
    ) {
        val sb = sb
        sb.append("{\"id\":")
        string(id)
        sb.append(",\"pubkey\":")
        string(pubKey)
        sb.append(",\"created_at\":")
        sb.append(createdAt)
        sb.append(",\"kind\":")
        sb.append(kind)
        sb.append(",\"tags\":")
        tagArray(tags)
        sb.append(",\"content\":")
        string(content)
        sb.append(",\"sig\":")
        string(sig)
        sb.append('}')
        needsComma = true
    }

    internal fun eventObject(event: Event) = eventObject(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content, event.sig)

    // ---------------------------------------------------------------------------------------
    // UTF-8 mode, for the id hash
    // ---------------------------------------------------------------------------------------

    private fun grow(more: Int) {
        if (pos + more > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, pos + more))
    }

    private fun byte(c: Char) {
        grow(1)
        buf[pos++] = c.code.toByte()
    }

    private fun number8(value: Long) {
        grow(20)
        val buf = buf
        var v = value
        if (v < 0) {
            if (v == Long.MIN_VALUE) {
                for (c in LONG_MIN) buf[pos++] = c.code.toByte()
                return
            }
            buf[pos++] = '-'.code.toByte()
            v = -v
        }
        var digits = 1
        var x = v
        while (x >= 10) {
            x /= 10
            digits++
        }
        var p = pos + digits
        pos = p
        do {
            buf[--p] = ('0'.code + (v % 10).toInt()).toByte()
            v /= 10
        } while (v != 0L)
    }

    /**
     * [s] as a quoted UTF-8 JSON string. Runs of printable ASCII, which need no escaping and are
     * their own UTF-8, are copied in bulk; only the chars between them go one at a time, so a
     * note with a newline or an emoji in it still copies most of its text in a few calls.
     */
    private fun string8(s: String) {
        byte('"')
        val n = s.length
        var k = 0
        while (k < n) {
            val start = k
            while (k < n && isPlainAscii(s[k])) k++
            if (k > start) asciiRun8(s, start, k)
            if (k < n) k = escapedChar8(s, k)
        }
        byte('"')
    }

    private fun asciiRun8(
        s: String,
        start: Int,
        end: Int,
    ) {
        val len = end - start
        grow(len)
        if (len >= BULK_COPY_MIN) {
            copyAsciiInto(s, start, end, buf, pos)
            pos += len
        } else {
            val o = buf
            var p = pos
            for (k in start until end) o[p++] = s[k].code.toByte()
            pos = p
        }
    }

    /** Writes the char at [k], which is not [isPlainAscii] (or a surrogate pair starting there); returns the next index. */
    private fun escapedChar8(
        s: String,
        k: Int,
    ): Int {
        grow(6)
        val o = buf
        var p = pos
        var next = k + 1
        val c = s[k]
        val code = c.code
        if (code < 0x80) {
            o[p++] = BACKSLASH
            when (c) {
                '"' -> {
                    o[p++] = '"'.code.toByte()
                }

                '\\' -> {
                    o[p++] = BACKSLASH
                }

                '\b' -> {
                    o[p++] = 'b'.code.toByte()
                }

                '\u000C' -> {
                    o[p++] = 'f'.code.toByte()
                }

                '\n' -> {
                    o[p++] = 'n'.code.toByte()
                }

                '\r' -> {
                    o[p++] = 'r'.code.toByte()
                }

                '\t' -> {
                    o[p++] = 't'.code.toByte()
                }

                else -> {
                    o[p++] = 'u'.code.toByte()
                    o[p++] = '0'.code.toByte()
                    o[p++] = '0'.code.toByte()
                    o[p++] = HEX[code shr 4].code.toByte()
                    o[p++] = HEX[code and 0xF].code.toByte()
                }
            }
        } else if (code < 0x800) {
            o[p++] = (0xC0 or (code shr 6)).toByte()
            o[p++] = (0x80 or (code and 0x3F)).toByte()
        } else if (c.isSurrogate()) {
            if (c.isHighSurrogate() && next < s.length && s[next].isLowSurrogate()) {
                val cp = 0x10000 + ((code - 0xD800) shl 10) + (s[next++].code - 0xDC00)
                o[p++] = (0xF0 or (cp shr 18)).toByte()
                o[p++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
                o[p++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                o[p++] = (0x80 or (cp and 0x3F)).toByte()
            } else {
                o[p++] = BACKSLASH
                o[p++] = 'u'.code.toByte()
                o[p++] = HEX[code shr 12].code.toByte()
                o[p++] = HEX[(code shr 8) and 0xF].code.toByte()
                o[p++] = HEX[(code shr 4) and 0xF].code.toByte()
                o[p++] = HEX[code and 0xF].code.toByte()
            }
        } else {
            o[p++] = (0xE0 or (code shr 12)).toByte()
            o[p++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
            o[p++] = (0x80 or (code and 0x3F)).toByte()
        }
        pos = p
        return next
    }

    /** `[0,pubkey,created_at,kind,tags,content]` as UTF-8 into [buf], NIP-01's id preimage. */
    private fun idPreimage(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: TagArray,
        content: String,
    ) {
        byte('[')
        byte('0')
        byte(',')
        string8(pubKey)
        byte(',')
        number8(createdAt)
        byte(',')
        number8(kind.toLong())
        byte(',')
        byte('[')
        for (t in tags.indices) {
            if (t > 0) byte(',')
            val tag = tags[t]
            byte('[')
            for (k in tag.indices) {
                if (k > 0) byte(',')
                string8(tag[k])
            }
            byte(']')
        }
        byte(']')
        byte(',')
        string8(content)
        byte(']')
    }

    companion object {
        private const val INITIAL_CHARS = 1024
        private const val INITIAL_BYTES = 1024
        private const val MAX_KEPT_CHARS = 16 * 1024
        private const val MAX_KEPT_BYTES = 32 * 1024

        private const val HEX = "0123456789ABCDEF"
        private const val LONG_MIN = "-9223372036854775808"
        private const val BACKSLASH = '\\'.code.toByte()

        private fun hasNoControlChar(s: String): Boolean {
            for (k in s.indices) {
                if (s[k] < ' ') return false
            }
            return true
        }

        /** Shorter ASCII runs are copied by a loop; a bulk copy's fixed cost only pays off past this. */
        private const val BULK_COPY_MIN = 16

        /** In 0x20..0x7F (DEL included, as Jackson writes it) and not `"` or `\`: its own single UTF-8 byte. */
        private fun isPlainAscii(c: Char): Boolean = (c.code - 0x20) in 0 until 0x60 && c != '"' && c != '\\'

        private fun acquire(): NostrJsonWriter {
            val local = threadNostrJsonWriter()
            return if (local == null || local.inUse) NostrJsonWriter() else local
        }

        /** The event as `EventSerializer` writes it. */
        fun toJson(event: Event): String = acquire().text { eventObject(event) }

        fun toJson(
            id: HexKey,
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: TagArray,
            content: String,
            sig: String,
        ): String = acquire().text { eventObject(id, pubKey, createdAt, kind, tags, content, sig) }

        /** A tag array as `TagArraySerializer` writes it. */
        fun toJson(tags: TagArray): String = acquire().text { tagArray(tags) }

        fun toJson(rumor: Rumor): String = acquire().text { rumor(rumor) }

        /** `{"created_at":…,"kind":…,"tags":…,"content":…}`, as `EventTemplateSerializer` writes it. */
        fun toJson(template: EventTemplate<*>): String =
            acquire().text {
                sb.append("{\"created_at\":")
                sb.append(template.createdAt)
                sb.append(",\"kind\":")
                sb.append(template.kind)
                sb.append(",\"tags\":")
                tagArray(template.tags)
                sb.append(",\"content\":")
                string(template.content)
                sb.append('}')
            }

        fun toJson(request: BunkerRequest): String = acquire().text { bunkerRequest(request) }

        fun toJson(response: BunkerResponse): String = acquire().text { bunkerResponse(response) }

        /** Runs [write] with this thread's writer and returns the text, for formats written outside this package. */
        internal fun write(write: NostrJsonWriter.() -> Unit): String = acquire().text(write)

        /** The event laid out for people to read; see [NostrJsonPretty]. */
        fun toPrettyJson(event: Event): String = NostrJsonPretty.format(toJson(event))

        /** The template laid out for people to read; see [NostrJsonPretty]. */
        fun toPrettyJson(template: EventTemplate<*>): String = NostrJsonPretty.format(toJson(template))

        fun toJson(filter: Filter): String = acquire().text { filter(filter) }

        /** The frame, or null for a [Message] type this writer does not know. */
        fun toJsonOrNull(message: Message): String? {
            val writer = acquire()
            return writer.writing { if (writer.message(message)) writer.sb.toString() else null }
        }

        /** The frame, or null for a [Command] type this writer does not know. */
        fun toJsonOrNull(command: Command): String? {
            val writer = acquire()
            return writer.writing { if (writer.command(command)) writer.sb.toString() else null }
        }

        /** Any type this writer knows, or null to hand [value] to another serializer. */
        fun toJsonOrNull(value: OptimizedSerializable): String? =
            when (value) {
                is Event -> toJson(value)
                is Filter -> toJson(value)
                is Rumor -> toJson(value)
                is EventTemplate<*> -> toJson(value)
                is BunkerRequest -> toJson(value)
                is BunkerResponse -> toJson(value)
                is Message -> toJsonOrNull(value)
                is Command -> toJsonOrNull(value)
                else -> null
            }

        /** NIP-01's id preimage as text, the string whose UTF-8 [idPreimageBytes] holds (except for lone surrogates). */
        fun idPreimageJson(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: TagArray,
            content: String,
        ): String =
            acquire().text {
                sb.append("[0,")
                string(pubKey)
                sb.append(',')
                sb.append(createdAt)
                sb.append(',')
                sb.append(kind)
                sb.append(',')
                tagArray(tags)
                sb.append(',')
                string(content)
                sb.append(']')
            }

        /** The UTF-8 bytes NIP-01 hashes into an event id. */
        fun idPreimageBytes(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: TagArray,
            content: String,
        ): ByteArray =
            acquire().writing {
                idPreimage(pubKey, createdAt, kind, tags, content)
                buf.copyOf(pos)
            }

        /** The event id: SHA-256 of [idPreimageBytes], hashed straight from the reused buffer. */
        fun idHash(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: TagArray,
            content: String,
        ): ByteArray =
            acquire().writing {
                idPreimage(pubKey, createdAt, kind, tags, content)
                sha256Of(buf, pos)
            }
    }
}

/** This thread's reusable writer, or null where there is none (native). */
internal expect fun threadNostrJsonWriter(): NostrJsonWriter?

/** SHA-256 of the first [length] bytes of [data]. */
internal expect fun sha256Of(
    data: ByteArray,
    length: Int,
): ByteArray

/** Copies `s[start until end]`, which is all ASCII, into [dest] at [at], one byte per char. */
internal expect fun copyAsciiInto(
    s: String,
    start: Int,
    end: Int,
    dest: ByteArray,
    at: Int,
)
