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
package com.vitorpamplona.amethyst.commons.wot.network

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkCheckpoint
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIds
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkPartial
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkProgress
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkSyncResult
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.catchUpTrustNetwork
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.downloadTrustNetwork
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.reconcileTrustNetwork

/**
 * Where a sync gets the provider's cards. The apps use [RelayTrustNetworkSource]; tests pass a
 * fake, so the sync scheduling in `TrustNetworkState` runs without a relay.
 */
interface TrustNetworkSource {
    /** Opens a connection for one sync and closes it after [block]. */
    suspend fun <T> connect(block: suspend (TrustNetworkConnection) -> T): T
}

/** One sync's connection to the [ServiceProviderTag]'s relay. See `TrustNetworkSync.kt`. */
interface TrustNetworkConnection {
    /** A cold download, resumed from [resumeFrom] when given; [checkpoint] saves its progress. */
    suspend fun download(
        provider: ServiceProviderTag,
        progress: TrustNetworkProgress,
        checkpoint: TrustNetworkCheckpoint? = null,
        resumeFrom: TrustNetworkPartial? = null,
    ): TrustNetworkSyncResult

    /**
     * The hourly catch-up: a REQ for what is newer than [header]'s cursor. Null when nothing is;
     * [ids] (the id column, MBs) is read only when something is.
     */
    suspend fun catchUp(
        provider: ServiceProviderTag,
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
        progress: TrustNetworkProgress,
        ids: suspend () -> TrustNetworkIds,
    ): TrustNetworkSyncResult?

    /** Null when the relay cannot reconcile (no NIP-77). */
    suspend fun reconcile(
        provider: ServiceProviderTag,
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
        ids: TrustNetworkIds,
        progress: TrustNetworkProgress,
    ): TrustNetworkSyncResult?
}

/**
 * Syncs over a dedicated client from [clientBuilder]: an app client files everything it receives
 * into `LocalCache`, which must not receive hundreds of thousands of cards.
 */
class RelayTrustNetworkSource(
    private val clientBuilder: () -> INostrClient,
) : TrustNetworkSource {
    override suspend fun <T> connect(block: suspend (TrustNetworkConnection) -> T): T {
        val client = clientBuilder()
        try {
            client.connect()
            return block(RelayConnection(client))
        } finally {
            client.close()
        }
    }

    private class RelayConnection(
        val client: INostrClient,
    ) : TrustNetworkConnection {
        override suspend fun download(
            provider: ServiceProviderTag,
            progress: TrustNetworkProgress,
            checkpoint: TrustNetworkCheckpoint?,
            resumeFrom: TrustNetworkPartial?,
        ) = client.downloadTrustNetwork(provider.pubkey, provider.relayUrl, resumeFrom = resumeFrom, checkpoint = checkpoint, progress = progress)

        override suspend fun catchUp(
            provider: ServiceProviderTag,
            header: TrustNetworkHeader,
            index: TrustNetworkIndex,
            progress: TrustNetworkProgress,
            ids: suspend () -> TrustNetworkIds,
        ) = client.catchUpTrustNetwork(header, index, provider.relayUrl, progress, ids)

        override suspend fun reconcile(
            provider: ServiceProviderTag,
            header: TrustNetworkHeader,
            index: TrustNetworkIndex,
            ids: TrustNetworkIds,
            progress: TrustNetworkProgress,
        ) = client.reconcileTrustNetwork(header, index, ids, provider.relayUrl, progress)
    }
}
