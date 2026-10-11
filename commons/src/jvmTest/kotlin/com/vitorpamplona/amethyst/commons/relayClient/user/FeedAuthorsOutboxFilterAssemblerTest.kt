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
package com.vitorpamplona.amethyst.commons.relayClient.user

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A people-list member nobody follows has no loader asking for their kind:10002, so a list feed
 * only looked for their notes on fallback relays. The feed now hands its authors to
 * [FeedAuthorsOutboxFilterAssembler], which must ask for the relay lists that are still missing.
 */
class FeedAuthorsOutboxFilterAssemblerTest {
    private val indexRelay = NormalizedRelayUrl("wss://index.test/")
    private val outboxRelay = NormalizedRelayUrl("wss://outbox.test/")
    private val blocked = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    private val client = EmptyNostrClient()
    private val cache = EventCache()
    private val assembler = FeedAuthorsOutboxFilterAssembler(client, cache, RelayOfflineTracker(client))

    private val account =
        object : UserFinderAccount {
            override val userFinderPubkeyHex: HexKey = "00".repeat(32)

            override fun indexRelays() = setOf(indexRelay)

            override fun outboxHomeRelays() = emptySet<NormalizedRelayUrl>()

            override fun searchRelays() = emptySet<NormalizedRelayUrl>()

            override fun searchOnlyRelays() = emptySet<NormalizedRelayUrl>()

            override fun followPlusAllMineWithSearchRelays() = emptySet<NormalizedRelayUrl>()

            override fun commonRelays() = emptySet<NormalizedRelayUrl>()

            override fun cardHomeRelays() = emptySet<NormalizedRelayUrl>()

            override fun trustProvider(): ServiceProviderTag? = null

            override fun followerCountProvider(): ServiceProviderTag? = null

            override fun declaredFollowsByOutboxRelay() = emptyMap<NormalizedRelayUrl, Set<HexKey>>()
        }

    @AfterTest
    fun tearDown() = assembler.destroy()

    @Test
    fun listFeedsExposeTheirAuthors() {
        val authors = setOf("aa".repeat(32), "bb".repeat(32))
        val relays = MutableStateFlow(emptySet<NormalizedRelayUrl>())

        assertEquals(authors, AuthorsByOutboxTopNavFilter(authors, blocked).outboxAuthors())
        assertEquals(authors, MutedAuthorsByOutboxTopNavFilter(authors, blocked).outboxAuthors())

        // A proxy feed reads from the proxy relays, never from the authors' own.
        assertEquals(emptySet(), AuthorsByProxyTopNavFilter(authors, relays.value).outboxAuthors())

        // The follows feeds stay out: their kind:3 part already loads at startup, and handing every
        // follow to the finder would keep a standing REQ for the ones that never published a list.
        assertEquals(emptySet(), AllUserFollowsByOutboxTopNavFilter(authors, relays, blocked).outboxAuthors())
        assertEquals(emptySet(), AllFollowsByOutboxTopNavFilter(authors, defaultRelays = relays, blockedRelays = blocked).outboxAuthors())
    }

    @Test
    fun asksForTheRelayListOnlyOfAuthorsMissingOne() {
        val listOnly = NostrSignerSync(KeyPair())
        val withRelayList = NostrSignerSync(KeyPair())

        val relayList = AdvertisedRelayListEvent.create(listOf(AdvertisedRelayInfo(outboxRelay, AdvertisedRelayType.BOTH)), withRelayList)
        assertTrue(cache.justConsume(relayList, null, true))

        val keys =
            listOf(listOnly, withRelayList).map {
                UserFinderQueryState(cache.getOrCreateUser(it.pubKey), account)
            }

        val filters =
            assembler.group
                .single()
                .updateFilter(keys)
                .orEmpty()

        val requested = filters.flatMap { it.filter.authors.orEmpty() }.toSet()
        assertEquals(setOf(listOnly.pubKey), requested)
        filters.forEach {
            assertEquals(listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND), it.filter.kinds)
        }
        assertTrue(filters.any { it.relay == indexRelay }, "the index relays are asked: ${filters.map { it.relay }}")
    }
}
