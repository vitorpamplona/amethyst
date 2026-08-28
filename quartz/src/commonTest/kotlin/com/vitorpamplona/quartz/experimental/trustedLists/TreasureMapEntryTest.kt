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

import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.TrustedListProviderTag
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.publicTrustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.publicTrustedListProviders
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.removeTrustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.replaceTrustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.trustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Trusted List entry in a NIP-85 Treasure Map (kind 10040):
 * `["30392", <publisher>, <relay>]`, per Tapestry ADR `tl-treasure-map/0001`.
 */
class TreasureMapEntryTest {
    private val publisher = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val otherPublisher = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val scores = "wss://scores.brainstorm.world"
    private val nip85 = "wss://nip85.brainstorm.world"

    private fun map(vararg tags: Array<String>): TrustProviderListEvent {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = TrustProviderListEvent.KIND,
                tags = arrayOf(*tags),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<TrustProviderListEvent>(event)
        return event
    }

    @Test
    fun readsTheGenericBareKindEntry() {
        val entry = assertNotNull(map(arrayOf("30392", publisher, nip85)).publicTrustedListProvider(UserTrustedListEvent.KIND))

        assertEquals(UserTrustedListEvent.KIND, entry.kind)
        assertEquals(publisher, entry.pubkey)
        assertEquals("$nip85/", entry.relayUrl?.url)
        assertTrue(entry.isGeneric)
        assertNull(entry.name)
    }

    @Test
    fun oneEntryDelegatesEveryListOfItsKindAndOnlyThatKind() {
        // list names are never enumerated -- the entry is per kind, which is
        // what keeps the Map a fixed size however many lists get computed
        val treasureMap = map(arrayOf("30392", publisher, nip85))

        assertEquals(publisher, treasureMap.publicTrustedListProvider(UserTrustedListEvent.KIND)?.pubkey)
        assertNull(treasureMap.publicTrustedListProvider(AddressableTrustedListEvent.KIND))
    }

    @Test
    fun anUnconfiguredRelayHintDoesNotTakeTheDelegationWithIt() {
        // the spec keeps the three-element shape with an empty relay slot. The
        // pubkey is the part a consumer cannot do without, so the entry stands
        val entry = map(arrayOf("30392", publisher, "")).publicTrustedListProvider(UserTrustedListEvent.KIND)

        assertEquals(publisher, entry?.pubkey)
        assertNull(entry?.relayUrl)

        // and a missing third element is the same story
        val short = map(arrayOf("30392", publisher)).publicTrustedListProvider(UserTrustedListEvent.KIND)
        assertEquals(publisher, short?.pubkey)
        assertNull(short?.relayUrl)
    }

    @Test
    fun theFirstOfDuplicateGenericEntriesWins() {
        // at most one per kind is the writer's invariant; readers still need a
        // fixed rule so two of them resolve the same publisher
        val entry =
            map(
                arrayOf("30392", publisher, nip85),
                arrayOf("30392", otherPublisher, scores),
            ).publicTrustedListProvider(UserTrustedListEvent.KIND)

        assertEquals(publisher, entry?.pubkey)
    }

    @Test
    fun aReservedNamedEntryIsReadableButNeverDrivesTheDelegation() {
        val treasureMap = map(arrayOf("30392:podcaster", publisher, nip85))

        // display it as a Trusted List entry...
        val named = treasureMap.publicTrustedListProviders().single()
        assertEquals(UserTrustedListEvent.KIND, named.kind)
        assertEquals("podcaster", named.name)
        assertFalse(named.isGeneric)

        // ...but drive nothing from it: it is not the kind's delegation
        assertNull(treasureMap.publicTrustedListProvider(UserTrustedListEvent.KIND))
    }

    @Test
    fun aNamedEntryIsNotHandedToNip85Consumers() {
        // it splits on `:` into two segments exactly like `30382:rank` does.
        // Without a kind bound it would land in the NIP-85 provider set and be
        // offered to code looking for a rank or follower-count service
        val treasureMap =
            map(
                arrayOf("30382:rank", publisher, scores),
                arrayOf("30392:podcaster", publisher, nip85),
                arrayOf("30392", publisher, nip85),
            )

        assertEquals(listOf(ProviderTypes.rank), treasureMap.serviceProviders().map { it.service })
    }

    @Test
    fun foreignTagsInTheMapFallOutRatherThanParse() {
        // a 10040 is an open tag set: `["client", "nostria"]` lives there too
        val treasureMap =
            map(
                arrayOf("client", "nostria"),
                arrayOf("30382:rank", publisher, scores),
                arrayOf("30392"),
                arrayOf("30392", "not-a-pubkey", nip85),
                arrayOf("30392:", publisher, nip85),
                arrayOf("30396", publisher, nip85),
                arrayOf("alt", "a trust provider list"),
            )

        assertEquals(emptyList(), treasureMap.publicTrustedListProviders())
    }

    @Test
    fun switchingPublishersReplacesInPlaceAndPreservesEveryOtherTag() {
        // 10040 is replaceable: the update republishes the whole tag set, so a
        // tag dropped here is gone from the Map for good
        val before =
            map(
                arrayOf("30382:rank", publisher, scores),
                arrayOf("30392", publisher, nip85),
                arrayOf("client", "nostria"),
            ).tags

        val after =
            before.replaceTrustedListProvider(
                TrustedListProviderTag(UserTrustedListEvent.KIND, null, otherPublisher, RelayUrlNormalizer.normalizeOrNull(scores)),
            )

        assertEquals(
            listOf(
                listOf("30382:rank", publisher, scores),
                listOf("30392", otherPublisher, "$scores/"),
                listOf("client", "nostria"),
            ),
            after.map { it.toList() },
            "the entry keeps its position and its neighbours survive verbatim",
        )
    }

    @Test
    fun replacingCollapsesDuplicateGenericEntriesButLeavesNamedOnesAlone() {
        val before =
            map(
                arrayOf("30392", publisher, nip85),
                arrayOf("30392:podcaster", publisher, nip85),
                arrayOf("30392", otherPublisher, scores),
                arrayOf("30393", publisher, nip85),
            ).tags

        val after = before.replaceTrustedListProvider(TrustedListProviderTag(UserTrustedListEvent.KIND, null, otherPublisher, null))

        assertEquals(
            listOf(
                listOf("30392", otherPublisher, ""),
                listOf("30392:podcaster", publisher, nip85),
                listOf("30393", publisher, nip85),
            ),
            after.map { it.toList() },
        )
    }

    @Test
    fun replacingAddsTheEntryWhenTheMapHasNoneForThatKind() {
        val before = map(arrayOf("30382:rank", publisher, scores)).tags

        val after = before.replaceTrustedListProvider(TrustedListProviderTag(UserTrustedListEvent.KIND, null, publisher, null))

        assertEquals(publisher, after.trustedListProvider(UserTrustedListEvent.KIND)?.pubkey)
        assertEquals(2, after.size)
    }

    @Test
    fun removingDropsOnlyTheGenericEntryForThatKind() {
        val before =
            map(
                arrayOf("30382:rank", publisher, scores),
                arrayOf("30392", publisher, nip85),
                arrayOf("30393", publisher, nip85),
            ).tags

        val after: TagArray = before.removeTrustedListProvider(UserTrustedListEvent.KIND)

        assertNull(after.trustedListProvider(UserTrustedListEvent.KIND))
        assertEquals(2, after.size)
    }

    @Test
    fun aNamedWriteNeverDeletesTheKindsGenericDelegation() {
        // the generic entry is a live delegation and 10040 is replaceable, so
        // a write that matched on kind alone would drop it irrecoverably --
        // and, never having found its own entry, duplicate on every later call
        val before =
            map(
                arrayOf("30392", publisher, nip85),
                arrayOf("30392:podcaster", publisher, nip85),
            ).tags

        val named = TrustedListProviderTag(UserTrustedListEvent.KIND, "podcaster", otherPublisher, null)
        val after = before.replaceTrustedListProvider(named)

        assertEquals(
            listOf(
                listOf("30392", publisher, nip85),
                listOf("30392:podcaster", otherPublisher, ""),
            ),
            after.map { it.toList() },
        )

        // and it is idempotent: a second write finds its own entry
        assertEquals(after.map { it.toList() }, after.replaceTrustedListProvider(named).map { it.toList() })
    }

    @Test
    fun removingTheGenericEntryLeavesTheNamedOneAndViceVersa() {
        val before =
            map(
                arrayOf("30392", publisher, nip85),
                arrayOf("30392:podcaster", publisher, nip85),
            ).tags

        assertEquals(
            listOf(listOf("30392:podcaster", publisher, nip85)),
            before.removeTrustedListProvider(UserTrustedListEvent.KIND).map { it.toList() },
        )
        assertEquals(
            listOf(listOf("30392", publisher, nip85)),
            before.removeTrustedListProvider(UserTrustedListEvent.KIND, "podcaster").map { it.toList() },
        )
    }

    @Test
    fun aKindOutsideTheFamilyCannotBeConstructed() {
        // parse refuses it, so a constructed one would write a tag that can
        // never be read back -- and therefore never replaced or removed, since
        // both address an entry through the parser. It would accumulate
        assertFailsWith<IllegalArgumentException> {
            TrustedListProviderTag(30382, null, publisher, null)
        }
        assertFailsWith<IllegalArgumentException> {
            TrustedListProviderTag(30396, null, publisher, null)
        }
    }

    @Test
    fun entriesRoundTripThroughTheirWireShape() {
        assertEquals(
            listOf("30392", publisher, "$nip85/"),
            TrustedListProviderTag(UserTrustedListEvent.KIND, null, publisher, RelayUrlNormalizer.normalizeOrNull(nip85)).toTagArray().toList(),
        )
        // three elements even with no hint, so readers index a stable shape
        assertEquals(
            listOf("30392", publisher, ""),
            TrustedListProviderTag(UserTrustedListEvent.KIND, null, publisher, null).toTagArray().toList(),
        )
        assertEquals(
            listOf("30392:podcaster", publisher, ""),
            TrustedListProviderTag(UserTrustedListEvent.KIND, "podcaster", publisher, null).toTagArray().toList(),
        )
    }
}
