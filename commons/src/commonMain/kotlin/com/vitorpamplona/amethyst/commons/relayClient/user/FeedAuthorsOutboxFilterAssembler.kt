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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.user.loaders.UserOutboxFinderSubAssembler
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker

/**
 * Loads the profile and relay list (kind:0 + kind:10002) of every author a visible feed reads by
 * the outbox model, such as the members of the people list picked in the top bar.
 *
 * [UserFinderFilterAssembler] only covers users drawn on screen. An author of someone else's list
 * is not on screen until one of their notes shows up, and their notes don't show up without their
 * relay list: the feed falls back to generic relays.
 *
 * Only the outbox finder runs here, not the user finder's reports and contact cards: a list can
 * have hundreds of members and those load anyway once a member's note is drawn. The finder asks
 * only for users still missing a relay list, so authors that already have one cost nothing.
 *
 * The kind:3 follows and the members of the user's own lists are loaded at startup by
 * `AccountFollowsLoaderSubAssembler`; this covers what the user picks beyond them: other people's
 * lists, follow packs, custom feeds and community moderators.
 */
@Stable
class FeedAuthorsOutboxFilterAssembler(
    client: INostrClient,
    cache: ICacheProvider,
    failureTracker: RelayOfflineTracker,
) : ComposeSubscriptionManager<UserFinderQueryState>() {
    // The tried-set must hold every member still missing a relay list: an evicted member is asked
    // for again on the next pass, and each answer evicts another, so a cap below the list's size
    // loops for as long as the feed is open. Members that no relay has a list for are dropped
    // after one round instead of being kept in a standing REQ.
    val outboxFinder =
        UserOutboxFinderSubAssembler(
            client,
            cache,
            failureTracker,
            ::allKeys,
            triedCacheSize = 10_000,
            keepAskingForMissingRelayLists = false,
        )

    override fun invalidateFilters() = outboxFinder.invalidateFilters()

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = outboxFinder.destroy()
}
