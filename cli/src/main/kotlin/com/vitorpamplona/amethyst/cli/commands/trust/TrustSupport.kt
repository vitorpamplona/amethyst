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
package com.vitorpamplona.amethyst.cli.commands.trust

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.commands.graperank.fetchLatestProviderList
import com.vitorpamplona.amethyst.cli.commands.graperank.providerListOf
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.DefaultMinTrustScore
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.rankProvider
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * The account's Web of Trust network, opened the way the apps open it (commons
 * [TrustNetworkState]) but one-shot: nothing syncs unless the command asks. Files live in
 * `~/.amy/<account>/wot/`, the same format the apps write.
 */
internal class TrustSession(
    val state: TrustNetworkState,
    val provider: ServiceProviderTag?,
    val providerList: TrustProviderListEvent?,
    val dir: File,
    private val scope: CoroutineScope,
) : AutoCloseable {
    override fun close() = scope.cancel()

    fun indexFile() = File(dir, TrustNetworkState.INDEX_FILE)

    fun idsFile() = File(dir, TrustNetworkState.IDS_FILE)
}

/**
 * Opens [observer]'s network (default: the account's). [refresh] fetches the freshest
 * kind 10040 from relays first (a sync wants that); otherwise the local store's copy is used,
 * with no network. [withClient] gives the state a dedicated relay client so it can sync.
 *
 * Another observer's network is read-only and built from the public entries of their 10040;
 * it is kept apart from the account's own, under `wot/observers/<hex>/`.
 */
internal suspend fun openTrustNetwork(
    ctx: Context,
    observer: HexKey = ctx.identity.pubKeyHex,
    minScore: Int = DefaultMinTrustScore,
    refresh: Boolean = false,
    withClient: Boolean = false,
    timeoutMs: Long = 8_000,
): TrustSession {
    val isSelf = observer == ctx.identity.pubKeyHex
    val list =
        if (refresh) {
            val relays = if (isSelf) ctx.outboxRelays() + ctx.bootstrapRelays() else ctx.bootstrapRelays() + Constants.eventFinderRelays
            fetchLatestProviderList(ctx, observer, relays, timeoutMs)
        } else {
            providerListOf(ctx, observer)
        }
    val provider = list?.rankProvider(if (isSelf) ctx.signer else null)
    val dir = if (isSelf) File(ctx.dataDir.root, "wot") else File(ctx.dataDir.root, "wot/observers/$observer")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state =
        TrustNetworkState(
            rankProvider = MutableStateFlow(provider),
            minTrustScore = MutableStateFlow(minScore),
            directory = dir.toOkioPath(),
            // A throwaway client, like the apps: a sync receives hundreds of thousands of cards.
            clientBuilder = if (withClient) ({ NostrClient(BasicOkHttpWebSocket.Builder { ctx.okhttp }) }) else null,
            scope = scope,
            providerGraceMs = 0,
            autoSync = false,
        )
    state.awaitReady()
    return TrustSession(state, provider, list, dir, scope)
}

/** "wss://scores.example.com/" for a provider, or null. */
internal fun ServiceProviderTag?.relayOrNull(): String? = this?.relayUrl?.url
