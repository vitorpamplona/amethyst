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
package com.vitorpamplona.amethyst.model.trustedAssertions

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserDependencies
import com.vitorpamplona.amethyst.service.relays.EOSERelayList
import com.vitorpamplona.quartz.experimental.relationshipStatus.ContactCardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class UserCardsCache : UserDependencies {
    val receivedCards = MutableStateFlow(mapOf<User, AddressableNote>())

    /**
     * This assembler saves the EOSE per user key. That EOSE includes their metadata, etc
     * and reports, but only from trusted accounts (follows of all logged in users).
     */
    var latestEOSEs: EOSERelayList = EOSERelayList()

    fun addCard(note: AddressableNote) {
        val author = note.author ?: return

        val cardBy = receivedCards.value[author]

        // if it's already there, quick exit
        if (cardBy != null && cardBy == note) return

        receivedCards.update {
            val author = note.author
            if (author == null) {
                it
            } else {
                it + (author to note)
            }
        }
    }

    fun removeCard(note: AddressableNote) {
        val author = note.author ?: return
        val cardBy = receivedCards.value[author]

        // if it's not already there, quick exit
        if (cardBy == null || cardBy != note) return

        receivedCards.update {
            val author = note.author
            if (author == null) {
                it
            } else {
                val reportsByInner = it[author]
                if (reportsByInner == null) {
                    it
                } else {
                    it - author
                }
            }
        }
    }

    fun rankFlow(trustProviderList: TrustProviderListState) =
        combineTransform(receivedCards, trustProviderList.liveUserRankProvider) { cards, provider ->
            if (provider != null) {
                val flow =
                    cards.firstNotNullOfOrNull {
                        if (it.key.pubkeyHex == provider.pubkey) {
                            it.value
                                .flow()
                                .metadata.stateFlow
                        } else {
                            null
                        }
                    }

                if (flow != null) {
                    emitAll(flow)
                } else {
                    emit(null)
                }
            } else {
                emit(null)
            }
        }.map {
            (it?.note?.event as? ContactCardEvent)?.rank()
        }.flowOn(Dispatchers.IO)
}
