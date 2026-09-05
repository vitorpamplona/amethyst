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
package com.vitorpamplona.amethyst.commons.relayClient.search

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A live search box. The [searchQuery] flow is the subscription id: every edit re-issues the
 * REQs, so the sub-assemblers read the relay lists as snapshots at filter-build time.
 *
 * @param searchRelays NIP-50 search relays (kind 10007), for full-text people/post search.
 * @param indexerRelays indexer relays, tried first when the query is a bare pubkey / npub / nprofile.
 * @param followPlusAllMineWithSearchRelays follows' outboxes + every relay of mine + the search
 *   relays; the fallback set for a direct id lookup that no hint resolves.
 */
@Stable
class SearchQueryState(
    val searchQuery: MutableStateFlow<String>,
    override val account: IAccount,
    val searchRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val indexerRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val followPlusAllMineWithSearchRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : MutableQueryState,
    AccountScopedQuery {
    override fun flow(): Flow<String> = searchQuery
}
