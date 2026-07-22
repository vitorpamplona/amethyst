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
import com.vitorpamplona.amethyst.commons.service.BundledUpdate
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
@OptIn(ExperimentalAtomicApi::class)
class AddressableAuthorRelayLoaderSubAssembler(
    val cache: LocalCache,
    val allKeys: () -> Set<EventFinderQueryState>,
    val userFinder: UserFinderFilterAssembler,
) : IEoseManager {
    // Immutable snapshots swapped atomically, so a diff can never observe a half-written set.
    // Mutual exclusion between runs comes from the bundler (one body at a time), not from these
    // atomics — they exist to hand state over to destroy(), the one caller the bundler cannot
    // serialize.
    private val activeSubscriptions = AtomicReference<Set<UserFinderQueryState>>(emptySet())
    private val destroyed = AtomicBoolean(false)

    // Keeps the scan off the caller's thread. invalidateFilters() is reached synchronously from
    // ComposeSubscriptionManager.subscribe/unsubscribe on every note composable mount/unmount,
    // and those are documented "called by main. Keep it really fast."
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    override fun invalidateFilters(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing, ::forceInvalidate)
    }

    private fun forceInvalidate() {
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

        if (destroyed.load()) return

        val previous = activeSubscriptions.exchange(needed)

        userFinder.subscribe((needed - previous).toList())
        userFinder.unsubscribe((previous - needed).toList())

        // destroy() landed while we were subscribing. bundler.cancel() cannot stop a body that is
        // already running — it has no suspension points — so the body releases what it just
        // acquired. destroy() may unsubscribe the same states concurrently; that is a no-op.
        if (destroyed.load()) {
            activeSubscriptions.store(emptySet())
            userFinder.unsubscribe(needed.toList())
        }
    }

    override fun destroy() {
        // Flag before cancelling so an in-flight body is guaranteed to see the teardown.
        destroyed.store(true)
        bundler.cancel()
        userFinder.unsubscribe(activeSubscriptions.exchange(emptySet()).toList())
    }
}
