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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.IEoseManager
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState

/**
 * Bridges missing-addressable-note authors into [UserFinderFilterAssembler].
 *
 * When an [AddressableNote] stub (event == null) references an author whose NIP-65 relay list
 * has not been loaded yet, [potentialRelaysToFindAddress] returns an empty set and the event
 * is never fetched. This manager detects those cases and injects a [UserFinderQueryState] for
 * the author into the existing [UserFinderFilterAssembler], which already knows how to fetch
 * kind-0 / kind-10002 and resolve outbox relays via [UserOutboxFinderSubAssembler]. Once the
 * relay list arrives, [EventFinderFilterAssembler] is invalidated and can query the correct relay.
 */
class AddressableAuthorRelayLoaderSubAssembler(
    val cache: LocalCache,
    val allKeys: () -> Set<EventFinderQueryState>,
    val userFinder: UserFinderFilterAssembler,
) : IEoseManager {
    private val activeSubscriptions = mutableSetOf<UserFinderQueryState>()

    override fun invalidateFilters(ignoreIfDoing: Boolean) {
        val needed = mutableSetOf<UserFinderQueryState>()

        allKeys().forEach { key ->
            val note = key.note
            if (note is AddressableNote && note.event == null) {
                val author = cache.getOrCreateUser(note.address.pubKeyHex)
                if (author.authorRelayList() == null) {
                    needed.add(UserFinderQueryState(author, key.account))
                }
            }
        }

        val toAdd = needed - activeSubscriptions
        val toRemove = activeSubscriptions - needed

        userFinder.subscribe(toAdd.toList())
        userFinder.unsubscribe(toRemove.toList())

        activeSubscriptions.clear()
        activeSubscriptions.addAll(needed)
    }

    override fun destroy() {
        userFinder.unsubscribe(activeSubscriptions.toList())
        activeSubscriptions.clear()
    }
}
