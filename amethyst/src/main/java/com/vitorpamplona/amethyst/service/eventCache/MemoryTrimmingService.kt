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
package com.vitorpamplona.amethyst.service.eventCache

import android.content.ComponentCallbacks2
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.atomic.AtomicBoolean

class MemoryTrimmingService(
    val cache: LocalCache,
) {
    var isTrimmingMemoryMutex = AtomicBoolean(false)

    /**
     * Tiered pruning scaled to the OS memory-pressure level.
     *
     * Tier 1 — mild pressure (UI hidden, running-moderate):
     *   Sweep stale WeakRefs, drop expired and superseded-replaceable events.
     *   Safe to run frequently; no UI-visible side effects.
     *
     * Tier 2 — low memory (running-low, background):
     *   Tier 1 + old chat messages + unobserved thread replies / reactions.
     *   May cause feeds to re-fetch content that was scrolled past.
     *
     * Tier 3 — critical / imminent kill (running-critical, moderate, complete):
     *   Tier 2 + sever all observer links + drop every event from muted/blocked users.
     *   Aggressive; triggers recomposition wherever StateFlows were cleared.
     */
    private fun doTrim(
        account: Collection<Account>,
        otherAccounts: List<AccountInfo>,
        level: Int,
    ) {
        // Tier 1: always run — cheap housekeeping; cleanObservers only removes flows that are
        // not currently held by the UI, so it is safe and inexpensive at any pressure level.
        cache.cleanMemory()
        cache.cleanObservers()
        cache.pruneExpiredEvents()
        cache.prunePastVersionsOfReplaceables()

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Tier 2: medium pressure — drop messages and unobserved reactions
            val accounts = otherAccounts.mapNotNull { decodePublicKeyAsHexOrNull(it.npub) }.toSet()
            cache.pruneOldMessages()
            cache.pruneRepliesAndReactions(accounts)
        }

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // Tier 3: critical pressure — drop muted content
            account.forEach {
                cache.pruneHiddenEvents(it)
                cache.pruneHiddenMessages(it)
            }
        }
    }

    suspend fun run(
        account: Collection<Account>,
        otherAccounts: List<AccountInfo>,
        level: Int = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
    ) {
        if (isTrimmingMemoryMutex.compareAndSet(false, true)) {
            Log.d("ServiceManager", "Trimming Memory (level=$level)")
            try {
                doTrim(account, otherAccounts, level)
            } finally {
                isTrimmingMemoryMutex.getAndSet(false)
            }
        }
    }
}
