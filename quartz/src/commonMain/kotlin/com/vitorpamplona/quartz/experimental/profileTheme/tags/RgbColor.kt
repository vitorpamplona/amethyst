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

import kotlin.jvm.JvmInline

/**
 * An opaque 24-bit sRGB color, as a theme's `c` tag carries it (`#rrggbb`).
 *
 * [rgb] holds `0xRRGGBB`; [toArgb] adds a full alpha channel for UI toolkits that take ARGB ints.
 */
@JvmInline
value class RgbColor(
    val rgb: Int,
) {
    val red: Int get() = (rgb shr 16) and 0xFF
    val green: Int get() = (rgb shr 8) and 0xFF
    val blue: Int get() = rgb and 0xFF

    /** Opaque ARGB (`0xFFRRGGBB`). */
    fun toArgb(): Int = OPAQUE or (rgb and RGB_MASK)

    /** The spec's form: `#` plus six lowercase hex digits. */
    fun toHex(): String = "#" + (rgb and RGB_MASK).toString(16).padStart(6, '0')

    override fun toString() = toHex()

    companion object {
        private const val RGB_MASK = 0xFFFFFF
        private const val OPAQUE = -0x1000000 // 0xFF000000

        fun of(
            red: Int,
            green: Int,
            blue: Int,
        ) = RgbColor(((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF))

        /**
         * Ditto's shared tag definitions require "lowercase 6-digit hex color code including the `#`
         * sign". Readers are lenient on case and on a missing `#`, and strict on everything else:
         * anything but exactly six hex digits (CSS shorthands, alpha, names, `rgb(...)`) is null,
         * never an exception.
         */
        fun parse(hex: String): RgbColor? {
            val start = if (hex.startsWith('#')) 1 else 0
            if (hex.length - start != 6) return null
            var value = 0
            for (i in start until hex.length) {
                val digit = hex[i].digitToIntOrNull(16) ?: return null
                value = (value shl 4) or digit
            }
            return RgbColor(value)
        }
    }
}
