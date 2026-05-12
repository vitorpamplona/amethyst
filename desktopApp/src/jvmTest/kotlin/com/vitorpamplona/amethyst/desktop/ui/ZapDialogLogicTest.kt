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
package com.vitorpamplona.amethyst.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ZapDialogLogicTest {
    // -- formatSats --

    @Test
    fun `formatSats formats thousands with k suffix`() {
        assertEquals("1k", formatSats(1000))
        assertEquals("5k", formatSats(5000))
        assertEquals("10k", formatSats(10000))
    }

    @Test
    fun `formatSats preserves small amounts`() {
        assertEquals("21", formatSats(21))
        assertEquals("100", formatSats(100))
        assertEquals("500", formatSats(500))
        assertEquals("999", formatSats(999))
    }

    @Test
    fun `formatSats handles zero`() {
        assertEquals("0", formatSats(0))
    }

    @Test
    fun `formatSats handles large amounts`() {
        assertEquals("100k", formatSats(100000))
        assertEquals("1000k", formatSats(1000000))
    }

    // -- DEFAULT_ZAP_AMOUNTS --

    @Test
    fun `DEFAULT_ZAP_AMOUNTS contains expected presets`() {
        assertEquals(listOf(21L, 100L, 500L, 1000L, 5000L, 10000L), DEFAULT_ZAP_AMOUNTS)
    }

    @Test
    fun `DEFAULT_ZAP_AMOUNTS has six entries`() {
        assertEquals(6, DEFAULT_ZAP_AMOUNTS.size)
    }

    @Test
    fun `DEFAULT_ZAP_AMOUNTS first entry is 21 sats`() {
        assertEquals(21L, DEFAULT_ZAP_AMOUNTS.first())
    }

    // -- ZapType --

    @Test
    fun `ZapType PUBLIC has correct label and description`() {
        assertEquals("Public", ZapType.PUBLIC.label)
        assertEquals("Everyone sees your zap", ZapType.PUBLIC.description)
    }

    @Test
    fun `ZapType PRIVATE has correct label and description`() {
        assertEquals("Private", ZapType.PRIVATE.label)
        assertEquals("Only recipient sees your identity", ZapType.PRIVATE.description)
    }

    @Test
    fun `ZapType ANONYMOUS has correct label and description`() {
        assertEquals("Anonymous", ZapType.ANONYMOUS.label)
        assertEquals("No identity attached", ZapType.ANONYMOUS.description)
    }

    @Test
    fun `ZapType has exactly three entries`() {
        assertEquals(3, ZapType.entries.size)
    }
}
