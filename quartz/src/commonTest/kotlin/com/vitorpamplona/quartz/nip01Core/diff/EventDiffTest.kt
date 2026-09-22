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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
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

    private inline fun <reified T : Event> sign(
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>>,
        content: String = "",
    ): T = assertIs<T>(signer.sign<Event>(createdAt, kind, tags, content))

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    @Test
    fun followListDiffsContactTags() {
        val older = sign<ContactListEvent>(ContactListEvent.KIND, 100, arrayOf(arrayOf("p", alice), arrayOf("p", bob, "wss://a.com/", "bobby")))
        val newer = sign<ContactListEvent>(ContactListEvent.KIND, 200, arrayOf(arrayOf("p", bob, "wss://b.com/"), arrayOf("p", carol)), "")
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf(alice), diff.follows.removed.map { it.pubKey })
        assertEquals(listOf(carol), diff.follows.added.map { it.pubKey })
        val change = diff.follows.changed.single()
        assertEquals("bobby", change.before.petname)
        assertEquals("wss://b.com/", change.after.relayUri?.url)
        assertTrue(diff.removesData())
    }

    @Test
    fun followListOnlyAddingDoesNotRemove() {
        val older = sign<ContactListEvent>(ContactListEvent.KIND, 100, arrayOf(arrayOf("p", alice)), "{\"wss://a.com\":{}}")
        val newer = sign<ContactListEvent>(ContactListEvent.KIND, 200, arrayOf(arrayOf("p", alice), arrayOf("p", bob)), "")
        val diff = assertNotNull(newer.diffFrom(older))
        assertFalse(diff.removesData())
    }

    @Test
    fun muteListDiffsMuteTagsAndPrivateItems() {
        val older =
            sign<MuteListEvent>(
                MuteListEvent.KIND,
                100,
                arrayOf(arrayOf("p", alice), arrayOf("t", "spam"), arrayOf("word", "crypto"), arrayOf("e", bob)),
                "encrypted-private-items",
            )
        val newer = sign<MuteListEvent>(MuteListEvent.KIND, 200, arrayOf(arrayOf("word", "crypto")), "")
        val diff = assertNotNull(newer.diffFrom(older))
        val removed = diff.publicMutes.removed
        assertEquals(3, removed.size)
        assertEquals(alice, assertIs<UserTag>(removed[0]).pubKey)
        assertEquals("spam", assertIs<HashtagTag>(removed[1]).hashtag)
        assertEquals(bob, assertIs<EventTag>(removed[2]).eventId)
        assertTrue(diff.publicMutes.added.none { it is WordTag })
        assertEquals(ContentChange.CLEARED, diff.privateItems)
    }

    @Test
    fun rewrittenPrivateItemsAreChangedNotRemoved() {
        val older = sign<MuteListEvent>(MuteListEvent.KIND, 100, arrayOf(), "cipher-a")
        val newer = sign<MuteListEvent>(MuteListEvent.KIND, 200, arrayOf(), "cipher-b")
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(ContentChange.CHANGED, diff.privateItems)
        assertFalse(diff.removesData())
    }

    @Test
    fun nip65DiffsRelayTypes() {
        val older =
            sign<AdvertisedRelayListEvent>(
                AdvertisedRelayListEvent.KIND,
                100,
                arrayOf(arrayOf("r", "wss://a.com/"), arrayOf("r", "wss://b.com/", "read"), arrayOf("r", "wss://c.com/")),
            )
        val newer =
            sign<AdvertisedRelayListEvent>(
                AdvertisedRelayListEvent.KIND,
                200,
                arrayOf(arrayOf("r", "wss://a.com/", "write"), arrayOf("r", "wss://b.com/", "read"), arrayOf("client", "x")),
            )
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf("wss://c.com/"), diff.relays.removed.map { it.relayUrl.url })
        val change = diff.relays.changed.single()
        assertEquals(AdvertisedRelayType.BOTH, change.before.type)
        assertEquals(AdvertisedRelayType.WRITE, change.after.type)
    }

    @Test
    fun relayListsDiffNormalizedUrls() {
        val older = sign<BlockedRelayListEvent>(BlockedRelayListEvent.KIND, 100, arrayOf(arrayOf("relay", "wss://spam.com/")))
        val newer = sign<BlockedRelayListEvent>(BlockedRelayListEvent.KIND, 200, arrayOf())
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf(NormalizedRelayUrl("wss://spam.com/")), diff.relays.removed)
    }

    @Test
    fun profileDiffsTypedFields() {
        val older = sign<MetadataEvent>(MetadataEvent.KIND, 100, arrayOf(), """{"name":"vitor","about":"hi","lud16":"v@x.com","banner":"","twitter":"vp"}""")
        val newer = sign<MetadataEvent>(MetadataEvent.KIND, 200, arrayOf(), """{"name":"vitor2","about":"hi","banner":"https://b"}""")
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals("vitor", diff.name?.before)
        assertEquals("vitor2", diff.name?.after)
        assertNull(diff.about)
        assertTrue(assertNotNull(diff.lud16).isRemoval())
        assertEquals("https://b", diff.banner?.after)
        assertNull(diff.banner?.before)
        assertEquals(listOf("twitter" to "vp"), diff.otherFields.removed)
        assertTrue(diff.removesData())
    }

    @Test
    fun groupsDiffGroupTagsIncludingRenames() {
        val older = sign<SimpleGroupListEvent>(SimpleGroupListEvent.KIND, 100, arrayOf(arrayOf("group", "abc", "wss://g.com", "Friends"), arrayOf("group", "xyz", "wss://g.com")))
        val newer = sign<SimpleGroupListEvent>(SimpleGroupListEvent.KIND, 200, arrayOf(arrayOf("group", "abc", "wss://g.com", "Family")))
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(listOf("xyz"), diff.groups.removed.map { it.groupId })
        assertEquals(
            "Family",
            diff.groups.changed
                .single()
                .after.name,
        )
    }

    @Test
    fun trustProvidersDiffServiceProviderTags() {
        val older = sign<TrustProviderListEvent>(TrustProviderListEvent.KIND, 100, arrayOf(arrayOf("30382:rank", alice, "wss://a.com")))
        val newer = sign<TrustProviderListEvent>(TrustProviderListEvent.KIND, 200, arrayOf())
        val removed = assertNotNull(newer.diffFrom(older)).providers.removed.single()
        assertEquals("rank", removed.service.type)
        assertEquals(alice, removed.pubkey)
    }

    @Test
    fun nutzapInfoDiffsMintsRelaysAndKey() {
        val older = sign<NutzapInfoEvent>(NutzapInfoEvent.KIND, 100, arrayOf(arrayOf("mint", "https://mint.com", "sat"), arrayOf("pubkey", alice)))
        val newer = sign<NutzapInfoEvent>(NutzapInfoEvent.KIND, 200, arrayOf(arrayOf("mint", "https://mint.com", "sat", "usd"), arrayOf("pubkey", bob)))
        val diff = assertNotNull(newer.diffFrom(older))
        assertEquals(
            listOf("sat", "usd"),
            diff.mints.changed
                .single()
                .after.units,
        )
        assertEquals(alice, diff.p2pkPubkey?.before)
        assertEquals(bob, diff.p2pkPubkey?.after)
        assertFalse(diff.removesData())
    }

    @Test
    fun otherEventsAreNotComparable() {
        val mutes = sign<MuteListEvent>(MuteListEvent.KIND, 100, arrayOf())
        val follows = sign<ContactListEvent>(ContactListEvent.KIND, 200, arrayOf())
        val someoneElse = assertIs<ContactListEvent>(NostrSignerSync(KeyPair()).sign<Event>(300, ContactListEvent.KIND, arrayOf(), ""))
        assertNull(follows.diffFrom(mutes))
        assertNull(someoneElse.diffFrom(follows))
    }

    @Test
    fun listDiffMatchesByKey() {
        val diff = ListDiff.of(listOf(1 to "a", 2 to "b"), listOf(2 to "B", 3 to "c"), { it.first })
        assertEquals(listOf(1 to "a"), diff.removed)
        assertEquals(listOf(3 to "c"), diff.added)
        assertEquals(2 to "B", diff.changed.single().after)
    }
}
