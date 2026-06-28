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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/**
 * Keyspace for the connected-apps manifest subscription. Carries the account (for relay selection)
 * and the specific authors whose napplet manifests should be fetched — the set of pubkeys that have
 * been granted permissions in the user's ledger.
 */
class ConnectedAppsQueryState(
    val account: Account,
    val authors: Set<HexKey>,
)

/**
 * Live subscription for NIP-5D napplet manifests (kinds 15129/35129) while
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.ConnectedAppsScreen] is open.
 * Unlike [NappletsFilterAssembler] (which follows the global follow list), this assembler
 * only fetches manifests for the specific authors that have entries in the permission ledger.
 */
@Stable
class ConnectedAppsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<ConnectedAppsQueryState>() {
    val group =
        listOf(
            ConnectedAppsSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
