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
package com.vitorpamplona.quartz.nip71Video.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-71 `text-track`: supplementary timed text for a video (captions, subtitles, chapters,
 * metadata).
 *
 * The spec is inconsistent about the payload — its prose calls [ref] a "link to WebVTT file"
 * while its example writes an encoded event — and publishers use both. divine.video emits a
 * Blossom URL and an addressable `39307:<pubkey>:subtitles:<d>` coordinate for the same track,
 * so [ref] is deliberately untyped: whatever identifies the track. Positions 3 and 4 carry the
 * kind of information and its language code, per the prose.
 */
data class TextTrackTag(
    val ref: String,
    val relay: String? = null,
    val type: String? = null,
    val language: String? = null,
) {
    // Positions are meaningful, so a gap before a field that IS set has to be written as an
    // empty string rather than dropped — otherwise `language` would be read as `type`.
    fun toTagArray(): Array<String> =
        when {
            language != null -> arrayOf(TAG_NAME, ref, relay ?: "", type ?: "", language)
            type != null -> arrayOf(TAG_NAME, ref, relay ?: "", type)
            relay != null -> arrayOf(TAG_NAME, ref, relay)
            else -> arrayOf(TAG_NAME, ref)
        }

    companion object {
        const val TAG_NAME = "text-track"

        fun parse(tag: Array<String>): TextTrackTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return TextTrackTag(
                ref = tag[1],
                relay = tag.getOrNull(2)?.ifBlank { null },
                type = tag.getOrNull(3)?.ifBlank { null },
                language = tag.getOrNull(4)?.ifBlank { null },
            )
        }

        fun assemble(
            ref: String,
            relay: String?,
            type: String? = null,
            language: String? = null,
        ) = TextTrackTag(ref, relay, type, language).toTagArray()
    }
}
