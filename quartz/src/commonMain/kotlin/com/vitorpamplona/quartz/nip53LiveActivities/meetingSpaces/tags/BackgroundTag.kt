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
 * `["bg", "<image-url>", "<mode>"]` on a kind-30312 audio-room
 * event. Mode is one of [Mode.COVER] (default — fill behind the
 * UI) or [Mode.TILE] (repeat as a pattern). Clients without
 * background support fall back to the theme's solid background
 * color (or default).
 */
data class BackgroundTag(
    val url: String,
    val mode: Mode,
) {
    enum class Mode(
        val code: String,
    ) {
        COVER("cover"),
        TILE("tile"),
        ;

        companion object {
            fun fromCode(code: String?): Mode? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }

    companion object {
        const val TAG_NAME = "bg"

        fun parse(tag: Array<String>): BackgroundTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            // mode is optional; default to COVER when missing.
            val mode = (tag.getOrNull(2)?.let(Mode::fromCode)) ?: Mode.COVER
            return BackgroundTag(url = tag[1], mode = mode)
        }

        fun assemble(
            url: String,
            mode: Mode = Mode.COVER,
        ): Array<String> = arrayOf(TAG_NAME, url, mode.code)
    }
}
