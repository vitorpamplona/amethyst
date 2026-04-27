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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `["f", family, optionalUrl]` on a kind-30312 audio-room event.
 * The host suggests a font family for the room screen; clients
 * with theming support load it (URL → fetch font file; bare
 * family → match against a built-in / system font name with the
 * client's platform fallback). Clients without theming support
 * ignore the tag.
 *
 * The URL component is optional and is the only spec-defined way
 * to request a non-system font (e.g. a Google-Fonts WOFF). When
 * present, the URL must be HTTPS — the parser does not enforce
 * this (a renderer is free to decline `http://` for security
 * reasons), but invalid / blank family values are rejected so
 * the renderer can trust the parsed shape.
 */
class FontTag(
    val family: String,
    val url: String?,
) {
    companion object {
        const val TAG_NAME = "f"

        fun parse(tag: Array<String>): FontTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val family = tag[1]
            ensure(family.isNotBlank()) { return null }
            val url = tag.getOrNull(2)?.takeIf { it.isNotBlank() }
            return FontTag(family, url)
        }

        fun assemble(
            family: String,
            url: String? = null,
        ): Array<String> {
            require(family.isNotBlank()) { "font: family must not be blank" }
            return if (url != null) arrayOf(TAG_NAME, family, url) else arrayOf(TAG_NAME, family)
        }
    }
}
