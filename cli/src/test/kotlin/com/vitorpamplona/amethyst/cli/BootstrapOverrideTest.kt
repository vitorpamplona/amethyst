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
package com.vitorpamplona.amethyst.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** `AMY_BOOTSTRAP_RELAYS` either names relays or is unset: never an empty set `amy create` signs. */
class BootstrapOverrideTest {
    @Test
    fun unsetOrBlankMeansNoOverride() {
        assertNull(Context.parseBootstrapOverride(null))
        assertNull(Context.parseBootstrapOverride(""))
        assertNull(Context.parseBootstrapOverride("  "))
    }

    @Test
    fun validRelaysAreKeptInOrder() {
        assertEquals(
            listOf("ws://127.0.0.1:7777/", "ws://127.0.0.1:7778/"),
            Context.parseBootstrapOverride("ws://127.0.0.1:7777, not a relay ,ws://127.0.0.1:7778")!!.map { it.url },
        )
    }

    @Test
    fun aValueWithNoValidRelayIsABadArgument() {
        assertFailsWith<IllegalArgumentException> { Context.parseBootstrapOverride(",") }
        assertFailsWith<IllegalArgumentException> { Context.parseBootstrapOverride("not a relay") }
    }
}
