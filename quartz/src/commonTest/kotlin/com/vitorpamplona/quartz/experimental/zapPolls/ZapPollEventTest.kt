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
package com.vitorpamplona.quartz.experimental.zapPolls

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZapPollEventTest {
    @Test
    fun detectsZaplessPollsWithZeroValueBounds() {
        val event =
            zapPollEvent(
                arrayOf("poll_option", "0", "Yes"),
                arrayOf("poll_option", "1", "No"),
                arrayOf("value_minimum", "0"),
                arrayOf("value_maximum", "0"),
            )

        assertTrue(event.isZapless())
    }

    @Test
    fun rejectsPaidZapPolls() {
        val event =
            zapPollEvent(
                arrayOf("poll_option", "0", "Yes"),
                arrayOf("value_minimum", "1"),
                arrayOf("value_maximum", "100"),
            )

        assertFalse(event.isZapless())
    }

    @Test
    fun rejectsPollsWithoutExplicitZeroBounds() {
        val event =
            zapPollEvent(
                arrayOf("poll_option", "0", "Yes"),
            )

        assertFalse(event.isZapless())
    }

    private fun zapPollEvent(vararg tags: Array<String>) =
        ZapPollEvent(
            id = "6ff9bc13d27490f6e3953325260bd996901a143de89886a0608c39e7d0160a72",
            pubKey = "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
            createdAt = 1729186078,
            tags = arrayOf(*tags),
            content = "Testing polls",
            sig = "540101837a8826e2ae28401ee5f4fd8606def8501bec92a74a9e05264bb2c67558b927edf23bb085f5b5f0d91a61c65a25c37a3b92075bf10c9be03dbbe8e94e",
        )
}