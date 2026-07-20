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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.drafts.AccountDraftsEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.marmot.MarmotGroupEventsEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.metadata.AccountMetadataEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromInboxRelaysManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsHistoryEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountFeedContentStates
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

// This allows multiple screen to be listening to logged-in accounts.
@Stable
class AccountQueryState(
    val account: Account,
    val feedContentStates: AccountFeedContentStates,
    val otherAccounts: Set<HexKey>,
)

/**
 * Always-on account loaders: metadata, gift wraps, drafts, inbox-relay
 * notifications, marmot group events. Foreground-only loaders live in
 * [AccountForegroundFilterAssembler].
 */
@Stable
class AccountFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<AccountQueryState>() {
    // Live tail: the recent week of gift wraps, always open at the top for new messages.
    val giftWraps = AccountGiftWrapsEoseManager(client, ::allKeys)

    // History: older gift wraps, loaded on demand in bounded one-shot slices.
    val giftWrapsHistory = AccountGiftWrapsHistoryEoseManager(client, ::allKeys)

    // Live tail: the recent week of notifications from the inbox + group host relays.
    val notifications = AccountNotificationsEoseFromInboxRelaysManager(client, ::allKeys)

    // History: older notifications, paged backward by until+limit per relay, driven by the feed's markers.
    val notificationsHistory = AccountNotificationsHistoryEoseManager(client, ::allKeys)

    val group =
        listOf(
            AccountMetadataEoseManager(client, ::allKeys),
            giftWraps,
            giftWrapsHistory,
            AccountDraftsEoseManager(client, ::allKeys),
            notifications,
            notificationsHistory,
            MarmotGroupEventsEoseManager(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
