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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Durable record of inbound events this client has TERMINALLY decided about.
 *
 * A relay subscription cannot express "everything after event X" — only
 * `since`, a timestamp — and NIP-59 gift wraps are deliberately backdated by up
 * to two days, so a `since` cursor that has caught up still re-delivers every
 * wrap in that band on every sync. Without a durable marker each of those is
 * unwrapped, decrypted and re-decided from scratch, forever: a client that has
 * been in a few groups spends most of its sync budget re-deciding events it
 * already resolved, and hammers the relay doing it.
 *
 * "Terminally decided" is a narrow claim. It covers outcomes that cannot change
 * with more information — a Welcome we joined from, and one naming a
 * KeyPackage whose private half we never held (bundles are generated locally
 * before the KeyPackage is published, so a bundle we do not have is one we
 * never will). It does NOT cover an event that is merely undecryptable right
 * now: a kind-445 encrypted under an epoch we have not reached yet becomes
 * readable the moment the commit arrives, and marking it would lose the
 * message permanently.
 */
interface MarmotIngestDedupStore {
    suspend fun mark(eventId: HexKey)

    suspend fun loadAll(): Set<HexKey>
}

/** Non-durable default. Re-decides everything after a restart. */
class InMemoryIngestDedupStore : MarmotIngestDedupStore {
    private val ids = LinkedHashSet<HexKey>()

    override suspend fun mark(eventId: HexKey) {
        ids.add(eventId)
    }

    override suspend fun loadAll(): Set<HexKey> = ids.toSet()
}
