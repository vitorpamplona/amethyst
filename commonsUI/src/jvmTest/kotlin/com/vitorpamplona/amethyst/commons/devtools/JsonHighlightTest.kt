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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.JsonColors
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.highlightJson
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonHighlightTest {
    private val colors = JsonColors(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Gray)

    private fun colorOf(
        json: String,
        token: String,
    ): Color? {
        val start = json.indexOf(token)
        return highlightJson(json, colors)
            .spanStyles
            .single { it.start == start && it.end == start + token.length }
            .item.color
    }

    @Test
    fun keysValuesNumbersAndLiteralsGetTheirOwnColour() {
        val json = """{"kind": 1, "content": "a \"quoted\": b", "ok": true, "x": null, "n": -1.5e3}"""
        assertEquals(json, highlightJson(json, colors).text)
        assertEquals(Color.Red, colorOf(json, "\"kind\""))
        assertEquals(Color.Blue, colorOf(json, "1"))
        assertEquals(Color.Green, colorOf(json, "\"a \\\"quoted\\\": b\""))
        assertEquals(Color.Cyan, colorOf(json, "true"))
        assertEquals(Color.Cyan, colorOf(json, "null"))
        assertEquals(Color.Blue, colorOf(json, "-1.5e3"))
    }

    @Test
    fun brokenJsonDoesNotThrow() {
        assertEquals("{\"open", highlightJson("{\"open", colors).text)
    }
}
