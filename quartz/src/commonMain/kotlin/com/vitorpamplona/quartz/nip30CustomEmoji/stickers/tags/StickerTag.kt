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
package com.vitorpamplona.quartz.nip30CustomEmoji.stickers.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * One sticker of a kind 30031 sticker pack: `["sticker", "<shortcode>", "<image-url>", …]`.
 *
 * There is no spec yet. The shortcode and URL slots mirror the NIP-30 `emoji` tag, and two layouts
 * were seen on relays for what follows:
 * - DEN Chat: `["sticker", code, url, "sfw" | "nsfw"]`, a content rating in slot 3.
 * - Sonar (Signal/Telegram imports): `["sticker", code, url, <sha256>, <mime>, "", <description>, <emoji>]`.
 *
 * Both are read; a slot that does not look like its field is left null. A sticker needs a
 * shortcode and a URL, so a tag missing either is dropped.
 */
@Immutable
data class StickerTag(
    val code: String,
    val url: String,
    val rating: String? = null,
    val sha256: HexKey? = null,
    val mimeType: String? = null,
    val description: String? = null,
    val emoji: String? = null,
) {
    /** True when the publisher rated this sticker not safe for work (DEN Chat's `nsfw`). */
    fun isNsfw() = rating == RATING_NSFW

    fun toTagArray() = assemble(this)

    companion object {
        const val TAG_NAME = "sticker"
        const val RATING_SFW = "sfw"
        const val RATING_NSFW = "nsfw"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[2].isNotEmpty()

        fun parse(tag: Array<String>): StickerTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val slot3 = tag.getOrNull(3)
            val rating = slot3?.takeIf { it == RATING_SFW || it == RATING_NSFW }
            val sha256 = slot3?.takeIf { it.length == 64 && Hex.isHex64(it) }

            return StickerTag(
                code = tag[1],
                url = tag[2],
                rating = rating,
                sha256 = sha256,
                mimeType = if (sha256 != null) tag.getOrNull(4)?.takeIf { it.contains('/') } else null,
                description = if (sha256 != null) tag.getOrNull(6)?.ifBlank { null } else null,
                emoji = if (sha256 != null) tag.getOrNull(7)?.ifBlank { null } else null,
            )
        }

        /**
         * Writes the DEN Chat layout, `["sticker", code, url, rating?]`: the one most packs on relays
         * use. Hash, MIME type, description and emoji are Sonar-only and are not written.
         */
        fun assemble(
            code: String,
            url: String,
            rating: String? = null,
        ) = if (rating != null) arrayOf(TAG_NAME, code, url, rating) else arrayOf(TAG_NAME, code, url)

        /**
         * Round-trips what [parse] read: a sticker that came in the Sonar layout (it has a [sha256])
         * goes back out in that layout, keeping its hash, MIME type, description and emoji; any
         * other sticker is written in the DEN Chat layout. Sonar's slot 5 has no known meaning and
         * was always empty on relays, so it is written empty.
         */
        fun assemble(sticker: StickerTag): Array<String> =
            if (sticker.sha256 != null) {
                arrayOf(
                    TAG_NAME,
                    sticker.code,
                    sticker.url,
                    sticker.sha256,
                    sticker.mimeType ?: "",
                    "",
                    sticker.description ?: "",
                    sticker.emoji ?: "",
                )
            } else {
                assemble(sticker.code, sticker.url, sticker.rating)
            }
    }
}
