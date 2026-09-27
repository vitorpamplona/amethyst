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
package com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FavoriteFollowSetsListEventTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    private fun followSet(
        pubkey: String,
        dTag: String = "friends",
    ) = AddressBookmark(Address(30000, pubkey, dTag))

    @Test
    fun kindMatchesSpec() {
        assertEquals(10021, FavoriteFollowSetsListEvent.KIND)
        assertEquals(10021, FavoriteFollowSetsListEvent.createAddress("a".repeat(64)).kind)
        assertEquals("", FavoriteFollowSetsListEvent.createAddress("a".repeat(64)).dTag)
    }

    @Test
    fun eventFactoryBuildsTheTypedEvent() {
        val event = EventFactory.create<Event>("0".repeat(64), "1".repeat(64), 1L, 10021, emptyArray(), "", "0".repeat(128))
        assertIs<FavoriteFollowSetsListEvent>(event)
    }

    @Test
    fun addsAndRemovesPublicFollowSets() =
        runTest {
            val mine = followSet(signer.pubKey)
            val theirs = followSet("b".repeat(64), "devs")

            val first = FavoriteFollowSetsListEvent.create(mine, isPrivate = false, signer = signer, createdAt = 1740669816)
            val second = FavoriteFollowSetsListEvent.add(first, theirs, isPrivate = false, signer = signer, createdAt = 1740669817)
            val dupe = FavoriteFollowSetsListEvent.add(second, theirs, isPrivate = false, signer = signer, createdAt = 1740669818)

            assertEquals(listOf(mine.address, theirs.address), dupe.publicFavoriteFollowSets().map { it.address })

            val removed = FavoriteFollowSetsListEvent.remove(dupe, mine.address, signer, 1740669819)
            assertEquals(listOf(theirs.address), removed.publicFavoriteFollowSets().map { it.address })
        }

    @Test
    fun keepsPrivateFollowSetsEncrypted() =
        runTest {
            val secret = followSet("c".repeat(64), "secret")
            val event = FavoriteFollowSetsListEvent.create(secret, isPrivate = true, signer = signer, createdAt = 1740669816)

            assertTrue(event.publicFavoriteFollowSets().isEmpty())
            assertEquals(listOf(secret.address), event.privateFavoriteFollowSets(signer)?.map { it.address })

            val removed = FavoriteFollowSetsListEvent.remove(event, secret.address, signer, 1740669817)
            assertEquals(emptyList(), removed.privateFavoriteFollowSets(signer)?.map { it.address })
        }

    @Test
    fun ignoresPointersThatAreNotFollowSets() {
        val tags =
            arrayOf(
                arrayOf("a", "30000:" + "a".repeat(64) + ":friends"),
                arrayOf("a", "30002:" + "a".repeat(64) + ":relays"),
                arrayOf("a", "39089:" + "a".repeat(64) + ":pack"),
            )
        assertEquals(listOf("friends"), tags.favoriteFollowSetBookmarks().map { it.address.dTag })
        assertEquals(setOf(Address(30000, "a".repeat(64), "friends")), tags.favoriteFollowSetsSet())
    }
}
