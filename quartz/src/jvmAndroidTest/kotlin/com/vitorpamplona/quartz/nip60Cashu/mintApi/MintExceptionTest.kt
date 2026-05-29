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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MintExceptionTest {
    @Test
    fun httpExceptionPreservesDetail() {
        val e =
            MintHttpException(
                httpStatus = 400,
                detail = "amount too small",
                code = 11000,
                message = "amount too small",
            )
        assertEquals(400, e.httpStatus)
        assertEquals("amount too small", e.detail)
        assertEquals(11000, e.code)
        assertEquals("amount too small", e.message)
    }

    @Test
    fun httpExceptionAllowsNullDetail() {
        val e = MintHttpException(httpStatus = 500, detail = null, code = null, message = "HTTP 500")
        assertNull(e.detail)
        assertNull(e.code)
        assertEquals("HTTP 500", e.message)
    }

    @Test
    fun protocolExceptionCarriesMessage() {
        val e = MintProtocolException("Melt not completed (state=UNPAID)")
        assertEquals("Melt not completed (state=UNPAID)", e.message)
    }

    @Test
    fun bothAreRuntimeExceptions() {
        // describeMintError lives in amethyst-layer, but at the quartz level we
        // can at least confirm both exceptions are runtime — callers don't need
        // to declare them.
        assertTrue(MintHttpException(200, null, null, "m") is RuntimeException)
        assertTrue(MintProtocolException("m") is RuntimeException)
    }
}
