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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The getaddrinfo check behind iOS's public-only fetches. Numeric hosts and localhost need no network. */
class IosNappletHttpTest {
    @Test
    fun numericHostsResolveToThemselves() {
        assertContentEquals(byteArrayOf(1, 1, 1, 1), assertNotNull(resolvedAddresses("1.1.1.1")).single())
        assertContentEquals(ByteArray(15) + byteArrayOf(1), assertNotNull(resolvedAddresses("::1")).single())
    }

    @Test
    fun localNamesAreNotPublic() =
        runTest {
            assertFalse(resolvesOnlyToPublicAddresses("localhost"))
            assertFalse(resolvesOnlyToPublicAddresses("127.0.0.1"))
            assertFalse(resolvesOnlyToPublicAddresses("192.168.1.1"))
            assertTrue(resolvesOnlyToPublicAddresses("1.1.1.1"))
        }

    @Test
    fun aNameThatDoesNotResolveIsNotPublic() =
        runTest {
            assertNull(resolvedAddresses("does-not-exist.invalid"))
            assertFalse(resolvesOnlyToPublicAddresses("does-not-exist.invalid"))
        }
}
