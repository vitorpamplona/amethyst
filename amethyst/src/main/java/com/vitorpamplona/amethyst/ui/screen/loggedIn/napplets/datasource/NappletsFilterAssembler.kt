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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.datasource

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import kotlinx.coroutines.CoroutineScope

/**
 * Keyspace for the napplet-discovery subscription. Unlike a feed datasource it carries no
 * `FeedContentState` — [com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.NappletsScreen]
 * reads the manifests straight out of `LocalCache`, so this only needs the account (for relay
 * selection) and a scope.
 */
class NappletsQueryState(
    val account: Account,
    val scope: CoroutineScope,
)

/**
 * Subscribes to NIP-5D napplet manifests (kinds 15129/35129) while a napplet screen is open,
 * honoring the top-nav follow-list selection at the relay level (see [NappletsFilterSubAssembler])
 * and dumping the results into `LocalCache` for the screen to observe. Registered as an app-lifetime
 * singleton in `RelaySubscriptionsCoordinator` (the underlying EOSE manager opens its relay
 * subscription at construction, so it must not be created per screen).
 */
@Stable
class NappletsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NappletsQueryState>() {
    val group =
        listOf(
            NappletsFilterSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
