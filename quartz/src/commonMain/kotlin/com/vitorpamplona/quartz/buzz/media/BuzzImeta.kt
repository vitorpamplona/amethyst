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
package com.vitorpamplona.quartz.buzz.media

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag

/**
 * Buzz's relay validates every `imeta` tag on ingest (`crates/buzz-relay/src/handlers/imeta.rs`)
 * and refuses the WHOLE event on the first key it does not know or a repeated singleton key.
 * Generic NIP-92 attachments carry extras Buzz never accepts (`ox`, `content-warning`, …), so a
 * message bound for a Buzz workspace keeps only the keys Buzz allows, each singleton once.
 */
object BuzzImeta {
    /** `url` is the tag's anchor; it is not part of [IMetaTag.properties]. */
    val ALLOWED_KEYS = setOf("m", "x", "size", "dim", "blurhash", "alt", "thumb", "fallback", "duration", "bitrate", "image", "filename")

    /** Every allowed key but `fallback` may appear only once. */
    private const val REPEATABLE_KEY = "fallback"

    fun sanitize(tag: IMetaTag): IMetaTag =
        IMetaTag(
            tag.url,
            tag.properties
                .filterKeys { it in ALLOWED_KEYS }
                .mapValues { (key, values) -> if (key == REPEATABLE_KEY) values else values.take(1) }
                .filterValues { it.isNotEmpty() },
        )

    fun sanitize(tags: List<IMetaTag>): List<IMetaTag> = tags.map(::sanitize)

    /**
     * Buzz's clients draw an attachment only where the body links it in markdown — `![image](url)`
     * or `![video](url)`, the form their composer writes — and show a bare URL as a plain link. So
     * each attachment URL that stands alone in [content] becomes that markdown; one already inside
     * markdown is left alone.
     */
    fun markdownMediaBody(
        content: String,
        attachments: List<IMetaTag>,
    ): String {
        var out = content
        attachments.forEach { tag ->
            val url = tag.url
            if (url.isBlank() || out.contains("]($url)")) return@forEach
            val label = if (tag.properties["m"]?.firstOrNull()?.startsWith("video/") == true) "video" else "image"
            out = out.replace(url, "![$label]($url)")
        }
        return out
    }
}
