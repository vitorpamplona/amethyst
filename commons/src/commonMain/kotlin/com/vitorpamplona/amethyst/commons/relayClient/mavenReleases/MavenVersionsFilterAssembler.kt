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
package com.vitorpamplona.amethyst.commons.relayClient.mavenReleases

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.KeyHeldCursors
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.RelayCursors
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule
import com.vitorpamplona.quartz.nipFCMavenRepositories.release.MavenReleaseEvent

/** One NIP-FC card on screen asking for the other releases of its library by the same signer. */
class MavenVersionsQueryState(
    val note: Note,
    val signer: HexKey,
    val module: MavenModule,
) : KeyHeldCursors {
    override val relayCursors = RelayCursors()
}

/**
 * Loads the releases of a library a NIP-FC card shows, so the card can say when a newer version
 * exists. It asks NIP-FC's "all versions" filter, by the card's signer only, on the relays the
 * card's event came from and the signer's outbox, where publishers send releases.
 */
class MavenVersionsFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<MavenVersionsQueryState>() {
    val group = listOf(MavenVersionsSubAssembler(cache, client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class MavenVersionsSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<MavenVersionsQueryState>,
) : PerUniqueIdEoseManager<MavenVersionsQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: MavenVersionsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val wanted = MavenReleaseEvent.filter(listOf(key.signer), key.module)
        val relays = (key.note.relayUrls() + cache.getUserIfExists(key.signer)?.outboxRelays().orEmpty()).toSet()
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.ADD_ONS,
                        kinds = wanted.kinds,
                        authors = wanted.authors,
                        tags = wanted.tags,
                        limit = 50,
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: MavenVersionsQueryState) = key.signer + ":" + key.module.key
}
