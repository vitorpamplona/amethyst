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
package com.vitorpamplona.quartz.nip01Core.diff

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventDiffTest {
    private val signer = NostrSignerSync(KeyPair())

    private fun sign(
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>>,
        content: String = "",
    ): Event = signer.sign<Event>(createdAt, kind, tags, content)

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    @Test
    fun followListReportsRemovedAndAddedPeople() {
        val older = sign(ContactListEvent.KIND, 100, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))
        val newer = sign(ContactListEvent.KIND, 200, arrayOf(arrayOf("p", alice), arrayOf("p", carol)))
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf(DiffEntry.Person(bob)), diff.removed)
        assertEquals(listOf(DiffEntry.Person(carol)), diff.added)
        assertTrue(diff.removesData())
    }

    @Test
    fun onlyAddingIsNotARemoval() {
        val older = sign(ContactListEvent.KIND, 100, arrayOf(arrayOf("p", alice)))
        val newer = sign(ContactListEvent.KIND, 200, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))
        assertFalse(assertNotNull(newer.diffFrom(older)).removesData())
    }

    @Test
    fun newRelayHintOrPetnameIsAChange() {
        val older = sign(ContactListEvent.KIND, 100, arrayOf(arrayOf("p", alice, "wss://a.com", "al")))
        val newer = sign(ContactListEvent.KIND, 200, arrayOf(arrayOf("p", alice, "wss://b.com")))
        val diff = assertNotNull(newer.diffFrom(older))
        assertTrue(diff.removed.isEmpty())
        assertEquals(DiffChange(DiffEntry.Person(alice, "wss://a.com", "al"), DiffEntry.Person(alice, "wss://b.com")), diff.changed.single())
    }

    @Test
    fun contactListContentIsIgnored() {
        val older = sign(ContactListEvent.KIND, 100, arrayOf(arrayOf("p", alice)), "{\"wss://a.com\":{}}")
        val newer = sign(ContactListEvent.KIND, 200, arrayOf(arrayOf("p", alice)), "")
        assertTrue(assertNotNull(newer.diffFrom(older)).isEmpty())
    }

    @Test
    fun bookkeepingTagsAreIgnored() {
        val older = sign(AdvertisedRelayListEvent.KIND, 100, arrayOf(arrayOf("r", "wss://a.com"), arrayOf("client", "Amethyst"), arrayOf("alt", "x")))
        val newer = sign(AdvertisedRelayListEvent.KIND, 200, arrayOf(arrayOf("r", "wss://a.com")))
        assertTrue(assertNotNull(newer.diffFrom(older)).isEmpty())
    }

    @Test
    fun relayMarkersAreTyped() {
        val older =
            sign(
                AdvertisedRelayListEvent.KIND,
                100,
                arrayOf(arrayOf("r", "wss://a.com"), arrayOf("r", "wss://b.com", "read"), arrayOf("r", "wss://c.com")),
            )
        val newer = sign(AdvertisedRelayListEvent.KIND, 200, arrayOf(arrayOf("r", "wss://a.com", "write"), arrayOf("r", "wss://b.com", "read")))
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf(DiffEntry.Relay("wss://c.com")), diff.removed)
        assertEquals(DiffChange(DiffEntry.Relay("wss://a.com"), DiffEntry.Relay("wss://a.com", read = false, write = true)), diff.changed.single())
    }

    @Test
    fun muteListEntriesAndPrivateItems() {
        val older =
            sign(
                MuteListEvent.KIND,
                100,
                arrayOf(arrayOf("p", alice), arrayOf("t", "spam"), arrayOf("word", "crypto"), arrayOf("e", bob)),
                "encrypted-private-items",
            )
        val newer = sign(MuteListEvent.KIND, 200, arrayOf(), "")
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(
            listOf(DiffEntry.Person(alice), DiffEntry.Hashtag("spam"), DiffEntry.Word("crypto"), DiffEntry.EventRef(bob)),
            diff.removed,
        )
        assertEquals(ContentChange.CLEARED, diff.content)
        assertTrue(diff.contentEncrypted)
    }

    @Test
    fun privateItemsRewrittenAreChangedNotCleared() {
        val older = sign(MuteListEvent.KIND, 100, arrayOf(), "cipher-a")
        val newer = sign(MuteListEvent.KIND, 200, arrayOf(), "cipher-b")
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(ContentChange.CHANGED, diff.content)
        assertFalse(diff.removesData())
    }

    @Test
    fun profileFieldsComeFromTheContent() {
        val older = sign(MetadataEvent.KIND, 100, arrayOf(), """{"name":"vitor","about":"hi","lud16":"v@x.com","banner":""}""")
        val newer = sign(MetadataEvent.KIND, 200, arrayOf(), """{"name":"vitor2","about":"hi","banner":"https://b"}""")
        assertIs<MetadataEvent>(newer)
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf(DiffEntry.ProfileField("lud16", "v@x.com")), diff.removed)
        assertEquals(listOf(DiffEntry.ProfileField("banner", "https://b")), diff.added)
        assertEquals(DiffChange(DiffEntry.ProfileField("name", "vitor"), DiffEntry.ProfileField("name", "vitor2")), diff.changed.single())
        assertEquals(ContentChange.NONE, diff.content)
    }

    @Test
    fun groupsAreTyped() {
        val older = sign(SimpleGroupListEvent.KIND, 100, arrayOf(arrayOf("group", "abc", "wss://groups.com", "Friends")))
        val newer = sign(SimpleGroupListEvent.KIND, 200, arrayOf())
        assertEquals(listOf(DiffEntry.RelayGroup("abc", "wss://groups.com", "Friends")), assertNotNull(newer.diffFrom(older)).removed)
    }

    @Test
    fun trustProvidersAreTyped() {
        val older = sign(TrustProviderListEvent.KIND, 100, arrayOf(arrayOf("30382:rank", alice, "wss://a.com")))
        val newer = sign(TrustProviderListEvent.KIND, 200, arrayOf())
        val entry = assertNotNull(newer.diffFrom(older)).removed.single()
        assertIs<DiffEntry.TrustProvider>(entry)
        assertEquals("30382:rank", entry.service)
        assertEquals(alice, entry.pubKey)
    }

    @Test
    fun nutzapMintsAndKeyAreTyped() {
        val older = sign(NutzapInfoEvent.KIND, 100, arrayOf(arrayOf("mint", "https://mint.com", "sat"), arrayOf("pubkey", alice)))
        val newer = sign(NutzapInfoEvent.KIND, 200, arrayOf(arrayOf("pubkey", bob)))
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf(DiffEntry.Mint("https://mint.com", listOf("sat")), DiffEntry.NutzapKey(alice)), diff.removed)
        assertEquals(listOf(DiffEntry.NutzapKey(bob)), diff.added)
    }

    @Test
    fun differentKindsOrAuthorsAreNotComparable() {
        val a = sign(ContactListEvent.KIND, 100, arrayOf())
        val b = sign(MuteListEvent.KIND, 200, arrayOf())
        val c = NostrSignerSync(KeyPair()).sign<Event>(300, ContactListEvent.KIND, arrayOf(), "")
        assertNull(b.diffFrom(a))
        assertNull(c.diffFrom(a))
    }
}
