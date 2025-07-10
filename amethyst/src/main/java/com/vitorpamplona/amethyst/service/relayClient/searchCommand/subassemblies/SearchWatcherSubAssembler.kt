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
package com.vitorpamplona.amethyst.service.relayClient.searchCommand.subassemblies

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex

/**
 * Creates a new sub for each Search Screen. The StateFlow of the text field
 * that has the search ter is the id of the sub.
 */
class SearchWatcherSubAssembler(
    val cache: LocalCache,
    client: NostrClient,
    allKeys: () -> Set<SearchQueryState>,
) : PerUniqueIdEoseManager<SearchQueryState, Int>(client, allKeys) {
    override fun updateFilter(
        key: SearchQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val mySearchString = key.searchQuery.value

        if (mySearchString.isBlank()) return null

        val defaultRelays = key.account.followPlusAllMine.flow.value

        val directFilters =
            runCatching {
                if (Hex.isHex(mySearchString)) {
                    val hexKey = Hex.decode(mySearchString).toHexKey()
                    filterByAuthor(hexKey, defaultRelays) + filterByEvent(hexKey, defaultRelays)
                } else {
                    val parsed = Nip19Parser.uriToRoute(mySearchString)?.entity
                    if (parsed != null) {
                        cache.consume(parsed)

                        when (parsed) {
                            is NSec -> filterByAuthor(parsed.toPubKeyHex(), defaultRelays)
                            is NPub -> filterByAuthor(parsed.hex, defaultRelays)
                            is NProfile -> filterByAuthor(parsed.hex, defaultRelays)
                            is NNote -> filterByEvent(parsed.hex, defaultRelays)
                            is NEvent -> filterByEvent(parsed.hex, defaultRelays)
                            is NEmbed -> emptyList()
                            is NRelay -> emptyList()
                            is NAddress -> filterByAddress(parsed, defaultRelays)
                            else -> emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            }.getOrDefault(emptyList())

        val searchFilters =
            key.account.searchRelayList.flow.value.flatMap {
                searchPeopleByName(mySearchString, it)
            } +
                key.account.searchRelayList.flow.value.flatMap {
                    searchPostsByText(mySearchString, it)
                }

        return directFilters + searchFilters
    }

    override fun id(key: SearchQueryState) = key.searchQuery.hashCode()
}
