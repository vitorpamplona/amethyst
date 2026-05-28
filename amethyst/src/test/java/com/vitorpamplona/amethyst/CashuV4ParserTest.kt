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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.service.cashu.v4.V4Parser
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CashuV4ParserTest {
    @Test
    fun decodesMinibitsTestToken() {
        val token =
            "cashuBv2FteCJodHRwczovL21pbnQubWluaWJpdHMuY2FzaC9CaXRjb2luYXVjc2F0YWRkVEVTVGF0n79haUgAEHk32wzI" +
                "ZWFwn79hYQJhc3hAZWM1YWI3Yjc1NjViYjBjZTZhNzg2NzBkMDA0OGExMjVlZGQzMjJhYmVjMTEzYWMwZTBmZGVkZmE3NTQ4Mzg3OWFj" +
                "WCED0-ops8Ta6NjKChNJPe_jgIbXLlyxg2KSy2WaSTADo5D_v2FhCGFzeEBkNDZlODU5MDExNjU0NmNjZjAwNTE3ZTQ1NmU0MTY0N2Fm" +
                "ZWUxOWNlMzY2N2IzYTcxODZkMzEwZDY1MjM3OTM4YWNYIQMTDGTY943O4ojhKopoYdemsUE2rSLfzwNBODL8WgOX0v______"

        val outcome = V4Parser.parseCashuB(token)
        when (outcome) {
            is GenericLoadable.Loaded -> {
                val list = outcome.loaded
                assertTrue("Expected at least one CashuToken", list.isNotEmpty())
                val first = list.first()
                println("mint=${first.mint} totalAmount=${first.totalAmount} proofs=${first.proofs.size}")
            }
            is GenericLoadable.Error -> fail("V4Parser failed: ${outcome.errorMessage}")
            else -> fail("V4Parser returned $outcome")
        }
    }
}
