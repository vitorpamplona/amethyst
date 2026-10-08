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
package com.vitorpamplona.quartz.experimental.profileTheme.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.ensure

/** How a theme's background media fills the page. */
enum class BackgroundMode(
    val code: String,
) {
    COVER("cover"),
    TILE("tile"),
    ;

    companion object {
        fun parse(code: String): BackgroundMode? =
            when (code) {
                COVER.code -> COVER
                TILE.code -> TILE
                else -> null
            }
    }
}

/**
 * `["bg", "url <url>", "mode <mode>", "m <mime>", "dim <w>x<h>", "blurhash <hash>"]` — a theme's
 * background image or video, in NIP-92 `imeta` style: each entry after the name is a
 * space-delimited key/value pair.
 *
 * The spec requires `url`, `mode` and `m`; only `url` is required here, because live events omit
 * `m` (`["bg", "url …", "mode cover"]`). A missing or unknown [mode] is null — callers default to
 * cover — and unknown keys are ignored "for forward compatibility". The first value of a repeated
 * key wins. [url] is not validated: it is a value to load, which may fail.
 */
@Immutable
data class BackgroundTag(
    val url: String,
    val mode: BackgroundMode? = null,
    val mimeType: String? = null,
    val dimension: DimensionTag? = null,
    val blurhash: String? = null,
) {
    /** "Clients MAY choose not to render video backgrounds for performance or bandwidth reasons." */
    fun isVideo() = mimeType?.startsWith("video/") == true

    fun toTagArray() = assemble(this)

    companion object {
        const val TAG_NAME = "bg"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): BackgroundTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            var url: String? = null
            var mode: String? = null
            var mimeType: String? = null
            var dim: String? = null
            var blurhash: String? = null

            for (i in 1 until tag.size) {
                val entry = tag[i]
                val space = entry.indexOf(' ')
                if (space <= 0) continue
                val value = entry.substring(space + 1).trim()
                if (value.isEmpty()) continue
                when (entry.substring(0, space)) {
                    "url" -> if (url == null) url = value
                    "mode" -> if (mode == null) mode = value
                    "m" -> if (mimeType == null) mimeType = value
                    "dim" -> if (dim == null) dim = value
                    "blurhash" -> if (blurhash == null) blurhash = value
                }
            }

            if (url == null) return null

            return BackgroundTag(
                url = url,
                mode = mode?.let(BackgroundMode::parse),
                mimeType = mimeType,
                dimension = dim?.let(DimensionTag::parse),
                blurhash = blurhash,
            )
        }

        fun assemble(background: BackgroundTag): Array<String> =
            listOfNotNull(
                TAG_NAME,
                "url ${background.url}",
                background.mode?.let { "mode ${it.code}" },
                background.mimeType?.let { "m $it" },
                background.dimension?.let { "dim $it" },
                background.blurhash?.let { "blurhash $it" },
            ).toTypedArray()
    }
}
