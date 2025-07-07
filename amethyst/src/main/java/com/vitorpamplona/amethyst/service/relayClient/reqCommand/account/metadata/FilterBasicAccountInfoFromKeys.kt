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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.metadata

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

val BasicAccountInfoKinds =
    listOf(
        MetadataEvent.KIND,
        ContactListEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        ChatMessageRelayListEvent.KIND,
        SearchRelayListEvent.KIND,
        FileServersEvent.KIND,
        BlossomServersEvent.KIND,
        BlockedRelayListEvent.KIND,
        TrustedRelayListEvent.KIND,
    )

fun filterBasicAccountInfoFromKeys(
    relay: NormalizedRelayUrl,
    otherAccounts: List<HexKey>?,
    since: Long?,
): List<RelayBasedFilter> {
    if (otherAccounts == null || otherAccounts.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = BasicAccountInfoKinds,
                    authors = otherAccounts.toList(),
                    limit = otherAccounts.size * 8,
                    since = since,
                ),
        ),
    )
}
