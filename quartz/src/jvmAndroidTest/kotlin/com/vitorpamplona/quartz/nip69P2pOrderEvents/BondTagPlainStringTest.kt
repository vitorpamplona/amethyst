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
package com.vitorpamplona.quartz.nip69P2pOrderEvents

import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.BondTag
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BondTagPlainStringTest {
    @Test
    fun aBondInScientificFormIsWrittenAsAPlainNumber() {
        // java.math.BigDecimal.toString() prints "3E+2" for this value; no Nostr client parses that.
        assertEquals("300", BondTag.assemble(BigDecimal("3E+2"))[1])
        assertEquals("0.0000001", BondTag.assemble(BigDecimal("1E-7"))[1])
        assertEquals(BigDecimal("1E-7").compareTo(BondTag.parse(BondTag.assemble(BigDecimal("1E-7")))!!), 0)
    }
}
