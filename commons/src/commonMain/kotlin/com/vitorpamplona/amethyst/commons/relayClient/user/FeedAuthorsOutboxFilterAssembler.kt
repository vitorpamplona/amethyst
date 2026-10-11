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
 * [UserFinderFilterAssembler] only covers users drawn on screen, and the account loaders only cover
 * the kind:3 follows. A list member nobody follows is neither until one of their notes shows up,
 * and their notes don't show up without their relay list: the feed falls back to generic relays.
 *
 * Only the outbox finder runs here, not the user finder's reports and contact cards: a list can
 * have hundreds of members and those load anyway once a member's note is drawn. The finder asks
 * only for users still missing a relay list, so authors that already have one cost nothing.
 */
@Stable
class FeedAuthorsOutboxFilterAssembler(
    client: INostrClient,
    cache: ICacheProvider,
    failureTracker: RelayOfflineTracker,
) : ComposeSubscriptionManager<UserFinderQueryState>() {
    // Sized for big lists: members without a kind:10002 stay in the set after every relay was
    // tried, and evicting them would ask for them again on every pass.
    val group = listOf(UserOutboxFinderSubAssembler(client, cache, failureTracker, ::allKeys, triedCacheSize = 2000))

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = group.forEach { it.destroy() }
}
