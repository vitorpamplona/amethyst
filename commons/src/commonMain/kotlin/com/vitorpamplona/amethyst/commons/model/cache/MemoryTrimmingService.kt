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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.sync.Mutex

/**
 * Prunes [cache] when the app needs memory back. Android runs it on the OS trim callbacks (and
 * its own heap watchdog); the desktop, which gets no such callback, from a heap watchdog alone.
 */
class MemoryTrimmingService(
    val cache: LocalCache,
) {
    private val trimming = Mutex()

    /**
     * Two tiers.
     *
     * Tier 1 — always (on Android, every app switch): sweep collected WeakRefs and unused note
     * flows, drop expired events. Cheap, and nothing a screen shows changes.
     *
     * Tier 2 — [underPressure], real reclaim pressure: tier 1, plus old versions of replaceables
     * and the stores that hold notes strongly and would otherwise grow for as long as the app runs:
     * channel messages (the newest few hundred kept, muted authors' dropped) and DMs (the recent
     * ones kept). Those prunes rewind the history cursors of what they drop, so scrolling back
     * pages it in again.
     *
     * Feeds, threads and their replies, reactions and zaps are not pruned here. `LocalCache` holds
     * notes weakly: a note lives while a feed, a channel or a screen holds it, and its children live
     * with it, so they go when the holder does (the account feeds trim to their newest notes on the
     * same pressure signal). Removing them earlier did not free memory the GC would not, and broke
     * what still held them: measured on desktop (2026-11-11), a pass removed ~3,000 replies and
     * reactions, most of them items the Replies and Conversations feeds showed, while freeing ~0.5%
     * of the heap; a profile's notes lost every reply and reaction and, their cursors still saying
     * covered, never got them back.
     */
    private fun doTrim(
        account: Collection<Account>,
        underPressure: Boolean,
    ) {
        cache.pruner.cleanMemory()
        cache.pruner.cleanObservers()
        cache.pruner.pruneExpiredEvents()

        if (underPressure) {
            cache.pruner.prunePastVersionsOfReplaceables()
            account.forEach { cache.pruner.pruneHiddenMessages(it) }
            cache.pruner.pruneOldMessages()
        }
    }

    /** Prunes, unless a trim is already running. */
    suspend fun run(
        account: Collection<Account>,
        underPressure: Boolean = true,
    ) {
        if (trimming.tryLock()) {
            Log.d("MemoryTrimmingService") { "Trimming memory (underPressure=$underPressure)" }
            try {
                doTrim(account, underPressure)
            } finally {
                trimming.unlock()
            }
        }
    }
}
