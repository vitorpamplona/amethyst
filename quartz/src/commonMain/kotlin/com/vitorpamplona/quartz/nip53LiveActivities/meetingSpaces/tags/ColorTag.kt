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
 * `["c", "<hex6>", "<target>"]` on a kind-30312 audio-room event,
 * where `target` is one of [Target.BACKGROUND], [Target.TEXT],
 * or [Target.PRIMARY]. Used by the room author to tint the room
 * UI; clients without theming support fall back to default theming.
 *
 * Strict 6-char hex parser — `#abc` shorthand and named colors are
 * REJECTED so a typo in the room event can't crash the renderer.
 */
data class ColorTag(
    /** 6-char uppercase hex without `#`. */
    val hex: String,
    val target: Target,
) {
    enum class Target(
        val code: String,
    ) {
        BACKGROUND("background"),
        TEXT("text"),
        PRIMARY("primary"),
        ;

        companion object {
            fun fromCode(code: String): Target? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }

    companion object {
        const val TAG_NAME = "c"

        private val hexPattern = Regex("^#?[0-9a-fA-F]{6}$")

        fun parse(tag: Array<String>): ColorTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(hexPattern.matches(tag[1])) { return null }
            val target = Target.fromCode(tag[2]) ?: return null
            val hex = tag[1].removePrefix("#").uppercase()
            return ColorTag(hex = hex, target = target)
        }

        fun assemble(
            hex: String,
            target: Target,
        ): Array<String> {
            require(hexPattern.matches(hex)) { "ColorTag: hex must be #?[0-9a-fA-F]{6}, got '$hex'" }
            return arrayOf(TAG_NAME, hex.removePrefix("#").uppercase(), target.code)
        }
    }
}
