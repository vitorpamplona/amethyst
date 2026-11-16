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
package com.vitorpamplona.amethyst.commons.relayClient.communities

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.HeldNotes
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.RelayCursorHolder
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import kotlinx.coroutines.CoroutineScope

// This allows multiple screen to be listening to tags, even the same tag
class CommunityQueryState(
    var community: AddressableNote,
    /** The community's feeds (posts, moderation), which hold what its posts REQ loads. */
    val feeds: List<RelayCursorHolder> = emptyList(),
    /**
     * What holds the community's rules (see [communityRulesHolder]) while the screen is open:
     * nothing else keeps them, and the feeds read them back from the cache on every rebuild.
     */
    val rules: RelayCursorHolder,
)

/** The community's rules events, held for as long as [scope] (the screen showing it) runs. */
fun communityRulesHolder(
    community: AddressableNote,
    scope: CoroutineScope,
): HeldNotes {
    val address = community.idHex
    return HeldNotes(LocalCache, scope) { note -> (note.event as? CommunityRulesEvent)?.communityAddress() == address }
}

class CommunityFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<CommunityQueryState>() {
    val group =
        listOf(
            CommunityFeedFilterSubAssembler(cache, client, ::allKeys),
            CommunityRulesFilterSubAssembler(cache, client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
