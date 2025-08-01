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
package com.vitorpamplona.amethyst.model.topNavFeeds

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class OutboxRelayLoader {
    companion object {
        private fun authorsPerRelay(
            outboxRelayNotes: Array<NoteState>,
            cache: LocalCache,
        ): Map<NormalizedRelayUrl, Set<HexKey>> =
            mapOfSet {
                outboxRelayNotes.forEach { outboxNote ->
                    val note = outboxNote.note

                    val authorHex =
                        if (note is AddressableNote) {
                            note.address.pubKeyHex
                        } else {
                            note.author?.pubkeyHex
                        }

                    if (authorHex != null) {
                        val relays =
                            (outboxNote.note.event as? AdvertisedRelayListEvent)?.writeRelaysNorm()?.ifEmpty { null }
                                ?: cache.relayHints.hintsForKey(authorHex)

                        relays.forEach {
                            add(it, authorHex)
                        }
                    }
                }
            }

        fun <T> authorsPerRelaySnapshot(
            authors: Set<HexKey>,
            cache: LocalCache,
            transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
        ): T {
            val noteMetadata =
                authors
                    .map { pubkeyHex ->
                        cache
                            .getOrCreateAddressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
                            .flow()
                            .metadata.stateFlow.value
                    }.toTypedArray()
            return transformation(authorsPerRelay(noteMetadata, cache))
        }

        fun <T> toAuthorsPerRelayFlow(
            authors: Set<HexKey>,
            cache: LocalCache,
            transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
        ): Flow<T> {
            val noteMetadataFlows =
                authors.map { pubkeyHex ->
                    val note = cache.getOrCreateAddressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
                    note.flow().metadata.stateFlow
                }

            return combine(noteMetadataFlows) { outboxRelays ->
                transformation(authorsPerRelay(outboxRelays, cache))
            }
        }
    }
}
