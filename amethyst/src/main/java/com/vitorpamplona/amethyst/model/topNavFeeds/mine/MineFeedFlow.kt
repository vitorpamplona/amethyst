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
package com.vitorpamplona.amethyst.model.topNavFeeds.mine

import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Resolves the "Mine" top-nav selection to an author filter scoped to the logged-in user's own
 * pubkey. Mirrors [com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsFeedFlow]'s
 * outbox/proxy split: in proxy mode it pins the author to the configured proxy relays, otherwise it
 * resolves the user's own NIP-65 outbox via OutboxRelayLoader.
 *
 * This is the single source of truth for "Mine". Both the relay sub-assemblers (through each screen's
 * `liveXFollowListsPerRelay`) and the local DAL filters (through `liveXFollowLists`) consume the
 * produced [AuthorsByOutboxTopNavFilter] / [AuthorsByProxyTopNavFilter], so screens no longer need a
 * dedicated `TopFilter.Mine` branch: the generic author path already narrows to the user. It also
 * means outbox changes re-invalidate automatically — the per-relay flow is an `OutboxLoaderState`
 * over the user's own outbox, so a NIP-65 update re-emits without a screen-specific trigger.
 */
class MineFeedFlow(
    val myPubkey: HexKey,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedFlowsType {
    fun convert(proxyRelays: Set<NormalizedRelayUrl>): IFeedTopNavFilter =
        if (proxyRelays.isEmpty()) {
            AuthorsByOutboxTopNavFilter(
                authors = setOf(myPubkey),
                blockedRelays = blockedRelays,
            )
        } else {
            AuthorsByProxyTopNavFilter(
                authors = setOf(myPubkey),
                proxyRelays = proxyRelays,
            )
        }

    override fun flow(): Flow<IFeedTopNavFilter> = proxyRelays.map(::convert)

    override fun startValue(): IFeedTopNavFilter = convert(proxyRelays.value)

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        collector.emit(startValue())
    }
}
