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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.utils.EventFactory

/**
 * An event as it sits in storage: fields as plain strings, `tags` still in
 * its serialized-JSON form. This is the zero-decode currency of the relay
 * read path — a REQ that only needs to put the event back on the wire can
 * splice these strings straight into the outgoing frame via
 * [appendJsonObjectTo], skipping the tags parse, the [EventFactory]
 * dispatch to a kind-specific class, and the full re-serialization that
 * materializing an [Event] would cost per row.
 */
class RawEvent(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Kind,
    val jsonTags: String,
    val content: String,
    val sig: HexKey,
) {
    fun <T : Event> toEvent() =
        EventFactory.create<T>(
            id,
            pubKey,
            createdAt,
            kind,
            OptimizedJsonMapper.fromJsonToTagArray(jsonTags),
            content,
            sig,
        )

    /**
     * Appends this event as a NIP-01 JSON object, reusing the stored
     * strings verbatim: `id`/`pubkey`/`sig` are validated hex, [jsonTags]
     * is already JSON; only [content] needs string escaping.
     */
    fun appendJsonObjectTo(builder: StringBuilder) {
        builder
            .append("{\"id\":\"")
            .append(id)
            .append("\",\"pubkey\":\"")
            .append(pubKey)
            .append("\",\"created_at\":")
            .append(createdAt)
            .append(",\"kind\":")
            .append(kind)
            .append(",\"tags\":")
            .append(jsonTags)
            .append(",\"content\":")
        appendJsonQuoted(builder, content)
        builder
            .append(",\"sig\":\"")
            .append(sig)
            .append("\"}")
    }

    companion object {
        fun fromEvent(event: Event) =
            RawEvent(
                id = event.id,
                pubKey = event.pubKey,
                createdAt = event.createdAt,
                kind = event.kind,
                jsonTags = OptimizedJsonMapper.toJson(event.tags),
                content = event.content,
                sig = event.sig,
            )

        /**
         * Appends [value] as a JSON string literal, quotes included.
         * Minimal spec-compliant escaping (RFC 8259 §7): `"`, `\`, the
         * short control escapes, and `\u00XX` for the rest of C0.
         * Everything else — including non-ASCII — passes through raw,
         * which is valid JSON and preserves the exact code points the
         * event id was hashed over.
         */
        fun appendJsonQuoted(
            builder: StringBuilder,
            value: String,
        ) {
            builder.append('"')
            for (ch in value) {
                when {
                    ch == '"' -> builder.append("\\\"")
                    ch == '\\' -> builder.append("\\\\")
                    ch == '\n' -> builder.append("\\n")
                    ch == '\r' -> builder.append("\\r")
                    ch == '\t' -> builder.append("\\t")
                    ch == '\b' -> builder.append("\\b")
                    ch == '\u000C' -> builder.append("\\f")
                    ch < ' ' -> {
                        builder.append("\\u")
                        val hex = ch.code.toString(16)
                        repeat(4 - hex.length) { builder.append('0') }
                        builder.append(hex)
                    }
                    else -> builder.append(ch)
                }
            }
            builder.append('"')
        }
    }
}
