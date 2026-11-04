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

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.commands.graperank.fetchLatestProviderList
import com.vitorpamplona.amethyst.cli.commands.graperank.providerListOf
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.DefaultMinTrustScore
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.knownProviders
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.rankChoice
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.rankProvider
import com.vitorpamplona.amethyst.commons.wot.network.RelayTrustNetworkSource
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkState
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
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
    /** A list was found but its private part could not be read: it may hold the provider. */
    val undecryptable: Boolean = false,
) : AutoCloseable {
    override fun close() = scope.cancel()

    fun indexFile() = File(dir, TrustNetworkStore.INDEX_FILE)

    fun idsFile() = File(dir, TrustNetworkStore.IDS_FILE)
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
    // The account's own list may name its provider only in the encrypted part, which a bunker
    // can decrypt only once its response subscription is open.
    if (isSelf && list != null && list.content.isNotBlank()) ctx.prepare()
    // The apps' rule (knownProviders). Someone else's private rows are encrypted to them, so for
    // an observer only the public ones count. A list not found stays unknown rather than absent,
    // which would delete the files: a relay that did not answer is not a removed provider.
    val resolved = MutableStateFlow(list?.knownProviders { if (isSelf) it.privateTags(ctx.signer) else emptyArray() }?.rankChoice())
    val provider = resolved.value?.provider
    val dir = if (isSelf) File(ctx.dataDir.root, "wot") else File(ctx.dataDir.root, "wot/observers/$observer")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state =
        TrustNetworkState(
            rankProvider = resolved,
            minTrustScore = MutableStateFlow(minScore),
            store = TrustNetworkStore(dir.toOkioPath()),
            // A throwaway client, like the apps: a sync receives hundreds of thousands of cards.
            source = if (withClient) RelayTrustNetworkSource { NostrClient(BasicOkHttpWebSocket.Builder { ctx.okhttp }) } else null,
            scope = scope,
            autoSync = false,
        )
    if (resolved.value != null) state.awaitReady() else state.awaitLoaded()
    return TrustSession(state, provider, list, dir, scope, undecryptable = list != null && resolved.value == null)
}

/**
 * The account's kind 10040 as it is before a rewrite, from its outbox and the bootstrap relays
 * (then the local store). Null when no relay answered and nothing is stored: the list is then
 * unknown, not absent, and a rewrite would drop every row the user keeps there.
 */
internal suspend fun readOwnProviderList(
    ctx: Context,
    timeoutMs: Long,
): OwnProviderList? {
    val me = ctx.identity.pubKeyHex
    val relays = ctx.outboxRelays() + ctx.bootstrapRelays()
    val filter = Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(me), limit = 1)
    val served = relays.isNotEmpty() && ctx.drainResult(relays.associateWith { listOf(filter) }, timeoutMs).anyRelayServed
    val stored = providerListOf(ctx, me)
    return if (stored != null || served) OwnProviderList(stored) else null
}

internal class OwnProviderList(
    val event: TrustProviderListEvent?,
)

/** The error for a rewrite whose current list could not be read (see [readOwnProviderList]). */
internal fun ownListUnreachable(): Int = Output.error("timeout", "no relay answered for the account's kind 10040; not rewriting a list that cannot be read")

/** `--min-score`, 0..100 like the apps' setting (default [DefaultMinTrustScore]). */
internal fun Args.minScore(): Int = intFlag("min-score", DefaultMinTrustScore).coerceIn(0, 100)

/** "wss://scores.example.com/" for a provider, or null. */
internal fun ServiceProviderTag?.relayOrNull(): String? = this?.relayUrl?.url
