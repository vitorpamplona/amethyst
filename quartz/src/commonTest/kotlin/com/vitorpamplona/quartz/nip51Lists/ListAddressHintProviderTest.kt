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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** NIP-51 standard lists whose public `a` / `e` pointers were parsed but never surfaced as hints. */
class ListAddressHintProviderTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val c = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private val followSet = "30000:$b:friends"
    private val relaySet = "30002:$b:fast"
    private val interestSet = "30015:$c:cooking"
    private val feed = "31990:$c:feed"

    private fun algo(
        vararg tags: Array<String>,
        content: String = "",
    ) = FavoriteAlgoFeedsListEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun follows(
        vararg tags: Array<String>,
        content: String = "",
    ) = FavoriteFollowSetsListEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun interests(
        vararg tags: Array<String>,
        content: String = "",
    ) = InterestListEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun relays(
        vararg tags: Array<String>,
        content: String = "",
    ) = FavoriteRelayListEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun pictures(
        vararg tags: Array<String>,
        content: String = "",
    ) = PictureCurationSetEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun mutes(
        vararg tags: Array<String>,
        content: String = "",
    ) = MuteListEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    @Test
    fun standardListsExposeTheirSetPointers() {
        val cases =
            listOf(
                algo(arrayOf("a", feed, relay), arrayOf("a", followSet)) to listOf(feed, followSet),
                follows(arrayOf("a", followSet, relay), arrayOf("a", relaySet)) to listOf(followSet, relaySet),
                interests(arrayOf("t", "food"), arrayOf("a", interestSet, relay), arrayOf("a", followSet)) to listOf(interestSet, followSet),
                relays(arrayOf("relay", "wss://nos.lol/"), arrayOf("a", relaySet, relay), arrayOf("a", followSet)) to listOf(relaySet, followSet),
            )
        cases.forEach { (event, ids) ->
            event as com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
            assertEquals(ids, event.linkedAddressIds())
            assertEquals(listOf(ids.first()), event.addressHints().map { it.addressId })
            assertEquals(listOf(relay), event.addressHints().map { it.relay.url })
        }
    }

    @Test
    fun pictureCurationSetExposesBothEventAndAddressItems() {
        val event = pictures(arrayOf("d", "pics"), arrayOf("e", eventId, relay), arrayOf("a", "20:$b:pic"), arrayOf("a", "30023:$b:art", relay))
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals(listOf("20:$b:pic", "30023:$b:art"), event.linkedAddressIds())
        assertEquals(listOf("30023:$b:art"), event.addressHints().map { it.addressId })
    }

    @Test
    fun muteListExposesPublicMutedThreads() {
        val event = mutes(arrayOf("p", b), arrayOf("e", eventId, relay), arrayOf("e", "11".repeat(32)), arrayOf("e", "short"), arrayOf("word", "spam"))
        assertEquals(listOf(eventId, "11".repeat(32)), event.linkedEventIds())
        assertEquals(listOf(eventId), event.eventHints().map { it.eventId })
        assertEquals(listOf(relay), event.eventHints().map { it.relay.url })
        assertEquals(listOf(b), event.linkedPubKeys())
    }

    @Test
    fun listsWithoutPointersAreEmpty() {
        assertTrue(algo().linkedAddressIds().isEmpty())
        assertTrue(mutes().linkedEventIds().isEmpty())
    }
}
