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
package com.vitorpamplona.amethyst.commons.relayClient.wiki

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipsOnNostr
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.CursorHoldingQuery
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.RelayCursorHolder
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.commons.wiki.WikiTopics
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.concurrent.Volatile

/**
 * One wiki topic kept live while its screen is open: every author's article and redirect under the
 * slug. Shared by every screen showing the same topic ([id]); the topic screen's [feed] holds what it
 * loads, so after the first EOSE a relay is only asked for what is newer.
 */
class WikiTopicQueryState(
    val slug: String,
    override val account: Account,
    val feed: FeedContentState,
) : AccountScopedQuery,
    CursorHoldingQuery {
    val id = "topic:$slug"
    override val cursorHolders: List<RelayCursorHolder> get() = listOf(feed)
}

class WikiFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<WikiTopicQueryState>() {
    val group = listOf(WikiTopicSubAssembler(client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

/**
 * Asks the account's discovery relays and the outboxes of the topic's known authors. Versions are
 * scattered across their authors' relays, so after an EOSE that brought a new author in, the
 * filters are rebuilt to ask that author's outboxes too; only then, as a rebuild moves `since`.
 */
class WikiTopicSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<WikiTopicQueryState>,
) : PerUniqueIdEoseManager<WikiTopicQueryState, String>(client, allKeys) {
    @Volatile private var askedOutboxes: Map<String, Set<NormalizedRelayUrl>> = emptyMap()

    override fun newEose(
        key: WikiTopicQueryState,
        relayUrl: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        super.newEose(key, relayUrl, time, filters)
        if (!askedOutboxes[key.slug].orEmpty().containsAll(WikiTopics.authorOutboxes(key.slug))) invalidateFilters()
    }

    override fun updateFilter(
        key: WikiTopicQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val outboxes = WikiTopics.authorOutboxes(key.slug)
        askedOutboxes = askedOutboxes + (key.slug to outboxes)
        val relays = NipsOnNostr.accountDiscoveryRelays(key.account) + outboxes
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.TOPIC_FEED,
                        kinds = WikiTopics.KINDS,
                        tags = mapOf("d" to listOf(key.slug)),
                        limit = LIMIT,
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: WikiTopicQueryState) = key.id

    /** The topic's feed, woken first: notes loaded while no feed holds them could be collected. */
    override fun cursorHoldersOf(key: WikiTopicQueryState): List<RelayCursorHolder> = key.cursorHolders.onEach { (it as? FeedContentState)?.activate() }

    companion object {
        const val LIMIT = 100
    }
}
