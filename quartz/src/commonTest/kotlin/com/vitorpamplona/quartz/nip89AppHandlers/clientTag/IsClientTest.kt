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
package com.vitorpamplona.quartz.nip89AppHandlers.clientTag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsClientTest {
    @Test
    fun matchesClientTagByName() {
        val tags = arrayOf(arrayOf("client", "Amethyst"))
        assertTrue(tags.isClient("Amethyst"))
    }

    @Test
    fun matchesIgnoringCase() {
        val tags = arrayOf(arrayOf("client", "amethyst"))
        assertTrue(tags.isClient("Amethyst"))
    }

    @Test
    fun matchesWithAddressAndRelayHint() {
        val tags = arrayOf(arrayOf("client", "Amethyst", "31990:abc123:amethyst", "wss://relay.example.com"))
        assertTrue(tags.isClient("Amethyst"))
    }

    @Test
    fun doesNotMatchDifferentClient() {
        val tags = arrayOf(arrayOf("client", "OtherClient"))
        assertFalse(tags.isClient("Amethyst"))
    }

    @Test
    fun doesNotMatchWhenNoClientTag() {
        val tags = arrayOf(arrayOf("e", "abc"), arrayOf("p", "def"))
        assertFalse(tags.isClient("Amethyst"))
    }

    @Test
    fun doesNotMatchEmptyClientName() {
        val tags = arrayOf(arrayOf("client", ""))
        assertFalse(tags.isClient("Amethyst"))
    }
}
