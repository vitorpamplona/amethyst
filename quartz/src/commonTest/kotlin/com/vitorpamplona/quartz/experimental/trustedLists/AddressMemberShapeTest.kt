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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.addressables.tags.AddressMemberTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AddressMemberShapeTest {
    private val pk = "1".repeat(64)

    @Test
    fun onlyCoordinatesAreMembers() {
        val coordinate = "30023:$pk:post"
        assertEquals(coordinate, AddressMemberTag.parseAddressId(arrayOf("a", coordinate)))

        // An naddr would decode, but its raw bech32 must not become the member key.
        val naddr = NAddress.create(30023, pk, "post", null)
        for (value in listOf(naddr, "abc:$pk:d", "not-an-address", "30023:short:d")) {
            assertNull(AddressMemberTag.parse(arrayOf("a", value)), value)
            assertNull(AddressMemberTag.parseAddressId(arrayOf("a", value)), value)
            assertNull(AddressMemberTag.parseAddress(arrayOf("a", value)), value)
            assertFalse(AddressMemberTag.isTag(arrayOf("a", value)), value)
        }
        assertEquals(Address(30023, pk, "post"), AddressMemberTag.parseAddress(arrayOf("a", coordinate)))
    }
}
