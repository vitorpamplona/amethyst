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
import com.vitorpamplona.quartz.utils.ensure

/** The role marker in slot 2 of a theme's `c` tag. */
enum class ColorRole(
    val code: String,
) {
    BACKGROUND("background"),
    TEXT("text"),
    PRIMARY("primary"),
    ;

    companion object {
        fun parse(code: String): ColorRole? =
            when (code) {
                BACKGROUND.code -> BACKGROUND
                TEXT.code -> TEXT
                PRIMARY.code -> PRIMARY
                else -> null
            }
    }
}

/**
 * `["c", "#rrggbb", "<role>"]` — one color of a profile theme (Ditto kinds 16767 and 36767).
 *
 * Ditto's shared tag definitions: all three markers MUST be present and only one `c` per marker is
 * allowed. Readers take the first tag per role (see [ThemeColors]); a tag whose color is not a
 * six-digit hex or whose role is not one of the three is dropped.
 */
@Immutable
data class ColorTag(
    val color: RgbColor,
    val role: ColorRole,
) {
    fun toTagArray() = assemble(color, role)

    companion object {
        const val TAG_NAME = "c"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): ColorTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val color = RgbColor.parse(tag[1]) ?: return null
            val role = ColorRole.parse(tag[2]) ?: return null
            return ColorTag(color, role)
        }

        fun assemble(
            color: RgbColor,
            role: ColorRole,
        ) = arrayOf(TAG_NAME, color.toHex(), role.code)
    }
}

/**
 * The three colors every theme MUST define. A theme missing any of them has no
 * [ThemeColors]: a renderer cannot pick a sensible text color for an unknown background.
 */
@Immutable
data class ThemeColors(
    val background: RgbColor,
    val text: RgbColor,
    val primary: RgbColor,
) {
    fun toTags() =
        listOf(
            ColorTag(background, ColorRole.BACKGROUND),
            ColorTag(text, ColorRole.TEXT),
            ColorTag(primary, ColorRole.PRIMARY),
        )
}
