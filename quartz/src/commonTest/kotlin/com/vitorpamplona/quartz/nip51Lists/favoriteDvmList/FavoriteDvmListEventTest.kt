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
package com.vitorpamplona.quartz.nip51Lists.favoriteDvmList

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoriteDvmListEventTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    private fun dvm(
        pubkey: String,
        dTag: String = "content-discovery",
    ) = AddressBookmark(Address(31990, pubkey, dTag))

    @Test
    fun kindMatchesSpec() {
        assertEquals(10090, FavoriteDvmListEvent.KIND)
    }

    @Test
    fun addressesAreReplaceableWithFixedDTag() {
        val address = FavoriteDvmListEvent.createAddress("a".repeat(64))
        assertEquals(10090, address.kind)
        assertEquals("", address.dTag)
    }

    @Test
    fun createStoresDvmAsATag() =
        runTest {
            val dvm = dvm("a".repeat(64))

            val event =
                FavoriteDvmListEvent.create(
                    dvm = dvm,
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1740669816,
                )

            assertEquals(10090, event.kind)
            assertTrue(
                event.tags.any { it.size >= 2 && it[0] == "a" && it[1] == dvm.address.toValue() },
                "public a tag for the favourited DVM should be present",
            )
            val favorites = event.publicFavoriteDvms()
            assertEquals(1, favorites.size)
            assertEquals(dvm.address, favorites.first().address)
        }

    @Test
    fun addAppendsWithoutDuplicatingExistingEntry() =
        runTest {
            val dvm = dvm("a".repeat(64))

            val initial =
                FavoriteDvmListEvent.create(
                    dvm = dvm,
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1740669816,
                )

            val afterDupeAdd =
                FavoriteDvmListEvent.add(
                    earlierVersion = initial,
                    dvm = dvm,
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertEquals(
                1,
                afterDupeAdd.publicFavoriteDvms().count { it.address == dvm.address },
                "re-adding the same DVM must not produce a duplicate tag",
            )
        }

    @Test
    fun addPreservesOtherFavorites() =
        runTest {
            val first = dvm("a".repeat(64))
            val second = dvm("b".repeat(64))

            val initial =
                FavoriteDvmListEvent.create(
                    dvm = first,
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1740669816,
                )

            val after =
                FavoriteDvmListEvent.add(
                    earlierVersion = initial,
                    dvm = second,
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1740669817,
                )

            val addresses = after.publicFavoriteDvms().map { it.address }.toSet()
            assertTrue(first.address in addresses)
            assertTrue(second.address in addresses)
        }

    @Test
    fun removeDropsTheRequestedDvmOnly() =
        runTest {
            val first = dvm("a".repeat(64))
            val second = dvm("b".repeat(64))

            val initial =
                FavoriteDvmListEvent.create(
                    publicDvms = listOf(first, second),
                    privateDvms = emptyList(),
                    signer = signer,
                    createdAt = 1740669816,
                )

            val after =
                FavoriteDvmListEvent.remove(
                    earlierVersion = initial,
                    dvm = first.address,
                    signer = signer,
                    createdAt = 1740669817,
                )

            val addresses = after.publicFavoriteDvms().map { it.address }.toSet()
            assertFalse(first.address in addresses, "removed DVM should not survive")
            assertTrue(second.address in addresses, "other DVMs should be preserved")
        }
}
