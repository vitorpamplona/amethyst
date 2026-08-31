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
package com.vitorpamplona.quartz.nip01Core.tags.references

import kotlin.test.Test
import kotlin.test.assertEquals

class HttpUrlFormatterTest {
    @Test
    fun addSchemeIfNeededLeavesIpfsUriAlone() {
        val uri = "ipfs://QmYhhHT8DtLdwm82oPhJFf4Rm9acod5KuNY53T5u3VPFJD"
        assertEquals(uri, HttpUrlFormatter.addSchemeIfNeeded(uri))
        assertEquals(uri, HttpUrlFormatter.addSchemeIfNeeded("  $uri  "))
        assertEquals(
            "ipfs:QmYhhHT8DtLdwm82oPhJFf4Rm9acod5KuNY53T5u3VPFJD",
            HttpUrlFormatter.addSchemeIfNeeded("ipfs:QmYhhHT8DtLdwm82oPhJFf4Rm9acod5KuNY53T5u3VPFJD"),
        )
    }

    @Test
    fun normalizePreservesIpfsCidV0() {
        val uri = "ipfs://QmYhhHT8DtLdwm82oPhJFf4Rm9acod5KuNY53T5u3VPFJD"
        assertEquals(uri, HttpUrlFormatter.normalize(uri))
        assertEquals(uri, HttpUrlFormatter.normalize("IPFS://QmYhhHT8DtLdwm82oPhJFf4Rm9acod5KuNY53T5u3VPFJD"))
    }

    @Test
    fun normalizeStillAddsHttpsAndLowercasesHttpHosts() {
        assertEquals("https://example.com/", HttpUrlFormatter.normalize("example.com"))
        assertEquals("https://example.com/", HttpUrlFormatter.normalize("https://Example.COM"))
    }
}
