/**
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.metadata.AccountMetadataEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromInboxRelaysManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromRandomRelaysManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountFeedContentStates
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

// This allows multiple screen to be listening to logged-in accounts.
class AccountQueryState(
    val account: Account,
    val feedContentStates: AccountFeedContentStates,
    val otherAccounts: Set<HexKey>,
)

/**
 * This assembler loads everything eech account needs.
 */
class AccountFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<AccountQueryState>() {
    val group =
        listOf(
            AccountMetadataEoseManager(client, ::allKeys),
            AccountGiftWrapsEoseManager(client, ::allKeys),
            AccountNotificationsEoseFromInboxRelaysManager(client, ::allKeys),
            AccountNotificationsEoseFromRandomRelaysManager(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
