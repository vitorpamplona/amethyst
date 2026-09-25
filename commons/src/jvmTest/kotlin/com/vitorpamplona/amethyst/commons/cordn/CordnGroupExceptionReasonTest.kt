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
package com.vitorpamplona.amethyst.commons.cordn

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reason a screen branches on.
 *
 * `CordnGroupException`'s message is written for a log: it names pubkeys and
 * gids in full, which is what you want when reading one and never what you
 * want in a chat. A UI that rendered it answered somebody who had just tapped
 * a face with 64 hex characters. [CordnGroupException.Reason] is how the
 * screen says it in its own words instead, so it has to survive as a value
 * rather than as English anyone is free to reword.
 */
class CordnGroupExceptionReasonTest {
    @Test
    fun `the reason travels with the exception`() {
        val e = CordnGroupException("the coordinator holds no KeyPackage for abc", CordnGroupException.Reason.NO_KEY_PACKAGE)

        assertEquals(CordnGroupException.Reason.NO_KEY_PACKAGE, e.reason)
    }

    @Test
    fun `an exception raised without one is OTHER, not a crash`() {
        // Most throw sites have no better wording than their own message, and
        // must keep compiling and keep rendering that message.
        val e = CordnGroupException("something else went wrong")

        assertEquals(CordnGroupException.Reason.OTHER, e.reason)
    }

    @Test
    fun `the message is left alone for the log`() {
        // The reason is additive. Whatever a maintainer reads in logcat must
        // still be the precise thing, pubkey and all.
        val e = CordnGroupException("abc is not a member of gid-1", CordnGroupException.Reason.NOT_A_MEMBER)

        assertEquals("abc is not a member of gid-1", e.message)
    }
}
