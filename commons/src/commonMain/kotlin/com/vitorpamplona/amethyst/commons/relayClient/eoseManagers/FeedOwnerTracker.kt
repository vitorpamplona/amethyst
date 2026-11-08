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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 * Remembers, per key, which feed earned that key's EOSE cursors, and reports when a different one
 * shows up.
 *
 * A cursor says "the app already has everything up to T", but the app only has it while something
 * holds the notes: [com.vitorpamplona.amethyst.commons.model.cache.LargeSoftCache] keeps them by
 * [WeakReference], and the strong references live in the feed that rendered them. The assemblers are
 * app-wide and outlive those feeds. When an account switch (or the window closing) clears the
 * account's view models, the GC reclaims its notes while the cursors stay. Coming back builds new,
 * empty feeds, and the stale cursors ask the relays only for what is newer than T, so the feed shows
 * whatever happened to survive (DMs, which the chatroom index holds strongly) plus anything new.
 *
 * A new feed object for the same key is the signal to drop the cursors. The previous owner is held
 * weakly so this never keeps a closed account's feed (and its notes) alive.
 */
class FeedOwnerTracker<K : Any> {
    private val lock = KmpLock()
    private val owners = mutableMapOf<K, WeakReference<Any>>()

    /**
     * Records [owner] as the feed behind [key] and returns true when a different one (or one the GC
     * has since reclaimed) held it before: the caller's cursors for [key] no longer describe what
     * is in memory. False the first time [key] is seen and while the owner stays the same.
     */
    fun ownerChanged(
        key: K,
        owner: Any,
    ): Boolean =
        lock.withLock {
            val previous = owners[key]
            if (previous != null && previous.get() === owner) return@withLock false
            owners[key] = WeakReference(owner)
            previous != null
        }

    /** Forgets everything, for teardown. */
    fun clear() = lock.withLock { owners.clear() }
}
