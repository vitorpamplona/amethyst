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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Each KeyPackage this session published, by its slot (`d` tag), and the relays that took it.
 * When the account's relay lists change (first of all, when they load after a first login put
 * the KeyPackage on the app's defaults), the relay watcher republishes these to the relays they
 * have not reached yet ([MarmotKeyPackageRelays.missing]), so only relays that accepted count.
 */
class MarmotKeyPackageReach {
    private val published = HashMap<String, Pair<KeyPackageEvent, Set<NormalizedRelayUrl>>>()
    private val lock = KmpLock()

    fun isEmpty(): Boolean = lock.withLock { published.isEmpty() }

    /** Every KeyPackage held, with the relays it reached. */
    fun held(): List<Pair<KeyPackageEvent, Set<NormalizedRelayUrl>>> = lock.withLock { published.values.toList() }

    /**
     * Publishes [event] to [relays] with [publish], which answers each relay's accept or refusal,
     * and records the KeyPackage as reaching only the relays that accepted it: `publishAndConfirm`
     * is true when any one relay did, and recording every target then kept the watcher from ever
     * retrying the rest. Returns the accepting relays.
     */
    suspend fun publish(
        event: KeyPackageEvent,
        relays: Set<NormalizedRelayUrl>,
        publish: suspend (KeyPackageEvent, Set<NormalizedRelayUrl>) -> Map<NormalizedRelayUrl, Boolean>,
    ): Set<NormalizedRelayUrl> {
        val reached = publish(event, relays).filterValues { it }.keys
        if (reached.isNotEmpty()) record(event, reached)
        return reached
    }

    /** [event] is on [relays] (also: found on relays an earlier session published it to). */
    fun record(
        event: KeyPackageEvent,
        relays: Set<NormalizedRelayUrl>,
    ) {
        val slot = event.dTag()
        lock.withLock {
            val current = published[slot]
            // A newer KeyPackage in the slot replaces the old one; an older one never comes back.
            if (current == null || current.first.id != event.id) {
                if (current == null || event.createdAt >= current.first.createdAt) published[slot] = event to relays
            } else {
                published[slot] = event to (current.second + relays)
            }
        }
    }

    fun forget(event: KeyPackageEvent) {
        val slot = event.dTag()
        lock.withLock {
            if (published[slot]?.first?.id == event.id) published.remove(slot)
        }
    }
}
