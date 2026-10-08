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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeTagsTest {
    @Test
    fun parsesHexColors() {
        assertEquals(0x0d9488, RgbColor.parse("#0d9488")?.rgb)
        // Lenient on case and on a missing '#'.
        assertEquals(0xFF0044, RgbColor.parse("#FF0044")?.rgb)
        assertEquals(0xFF0044, RgbColor.parse("ff0044")?.rgb)
        assertEquals("#00e5ff", RgbColor.parse("#00E5FF")?.toHex())
        assertEquals("#000000", RgbColor(0).toHex())
        assertEquals(-0x1000000 or 0x123456, RgbColor(0x123456).toArgb())

        val c = RgbColor.of(0x12, 0x34, 0x56)
        assertEquals(0x12, c.red)
        assertEquals(0x34, c.green)
        assertEquals(0x56, c.blue)
    }

    @Test
    fun rejectsMalformedHexColorsWithoutThrowing() {
        listOf("", "#", "#fff", "#ff00440", "#ff004", "#gg0044", "red", "rgb(1,2,3)", "#+12345", "# 12345", "##12345").forEach {
            assertNull(RgbColor.parse(it), "'$it' should not parse")
        }
    }

    @Test
    fun colorTag() {
        assertEquals(ColorTag(RgbColor(0xf8fafc), ColorRole.BACKGROUND), ColorTag.parse(arrayOf("c", "#f8fafc", "background")))
        assertNull(ColorTag.parse(arrayOf("c", "#f8fafc")), "role is required")
        assertNull(ColorTag.parse(arrayOf("c", "#f8fafc", "accent")), "unknown role")
        assertNull(ColorTag.parse(arrayOf("c", "not-a-color", "text")))
        assertNull(ColorTag.parse(arrayOf("x", "#f8fafc", "text")))
        assertNull(ColorTag.parse(arrayOf("c")))
        assertContentEquals(arrayOf("c", "#0f172a", "text"), ColorTag.assemble(RgbColor(0x0f172a), ColorRole.TEXT))
    }

    @Test
    fun fontTag() {
        val url = "https://cdn.jsdelivr.net/fontsource/fonts/lora:vf@latest/latin-wght-normal.woff2"
        assertEquals(FontTag("Lora", url, FontRole.TITLE), FontTag.parse(arrayOf("f", "Lora", url, "title")))
        // Legacy 3-element tags are the body font.
        assertEquals(FontTag("Lora", url, FontRole.BODY), FontTag.parse(arrayOf("f", "Lora", url)))
        // Ditto writes an empty url for some families.
        assertEquals(FontTag("Rubik Maps", null, FontRole.BODY), FontTag.parse(arrayOf("f", "Rubik Maps", "", "body")))
        // Unknown roles are ignored, as the spec asks.
        assertNull(FontTag.parse(arrayOf("f", "Lora", url, "caption")))
        assertNull(FontTag.parse(arrayOf("f", "", url, "body")))
        assertNull(FontTag.parse(arrayOf("f")))

        assertContentEquals(arrayOf("f", "Rubik Maps", "", "title"), FontTag.assemble("Rubik Maps", null, FontRole.TITLE))
        val roundTrip = FontTag("Inter", url, FontRole.TITLE)
        assertEquals(roundTrip, FontTag.parse(roundTrip.toTagArray()))
    }

    @Test
    fun backgroundTag() {
        val bg =
            BackgroundTag.parse(
                arrayOf("bg", "url https://example.com/bg.png", "mode tile", "m image/png", "dim 1080x1098", "blurhash LEHV6n", "future thing"),
            )!!
        assertEquals("https://example.com/bg.png", bg.url)
        assertEquals(BackgroundMode.TILE, bg.mode)
        assertEquals("image/png", bg.mimeType)
        assertEquals("1080x1098", bg.dimension.toString())
        assertEquals("LEHV6n", bg.blurhash)

        // Live events omit `m`; an unknown mode is null rather than a failure.
        val partial = BackgroundTag.parse(arrayOf("bg", "url https://example.com/v.mp4", "mode stretch"))!!
        assertNull(partial.mode)
        assertNull(partial.mimeType)
        assertTrue(BackgroundTag("https://example.com/v.mp4", mimeType = "video/mp4").isVideo())

        assertNull(BackgroundTag.parse(arrayOf("bg", "mode cover", "m image/png")), "url is required")
        assertNull(BackgroundTag.parse(arrayOf("bg", "url ")), "url is required")
        assertNull(BackgroundTag.parse(arrayOf("bg")))
        assertNull(BackgroundTag.parse(arrayOf("imeta", "url https://example.com/bg.png")))
        assertEquals(null, BackgroundTag.parse(arrayOf("bg", "url https://x.com/a.png", "dim garbage"))?.dimension)
    }

    @Test
    fun backgroundTagRoundTrip() {
        val tag = arrayOf("bg", "url https://example.com/bg.jpg", "mode cover", "m image/jpeg", "dim 1920x1080")
        assertContentEquals(tag, BackgroundTag.parse(tag)!!.toTagArray())
        assertContentEquals(arrayOf("bg", "url https://example.com/bg.jpg"), BackgroundTag("https://example.com/bg.jpg").toTagArray())
    }
}
