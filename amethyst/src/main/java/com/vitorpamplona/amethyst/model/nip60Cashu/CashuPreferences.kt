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
package com.vitorpamplona.amethyst.model.nip60Cashu

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.vitorpamplona.amethyst.Amethyst

/**
 * Per-account Cashu state that needs durable, synchronous persistence —
 * separate from [com.vitorpamplona.amethyst.model.AccountSettings] which
 * batches writes through a 1-second debounced StateFlow.
 *
 * # Why a separate store
 *
 * The NUT-13 keyset counter is the critical bit. Every mint / swap /
 * melt reserves counter slots, derives deterministic blinded outputs at
 * those slots, sends them to the mint, and the mint signs them. The
 * mint persists which (keyset, blind_message) pairs it has ever signed;
 * a second request to sign the same blind_message returns HTTP 400
 * "outputs already signed". So once the wallet hands a counter to the
 * mint, the local counter advance MUST survive a crash — otherwise the
 * next reservation pulls the same slot and the mint rejects it.
 *
 * The default settings save path debounces writes by 1000 ms, which is
 * exactly the race window between "we asked the mint to sign" and "the
 * mint replied". A crash inside that window (ART JIT crash on Android
 * 15+, signer dialog dismiss, OOM, etc.) loses the counter advance and
 * makes the wallet unusable. This store writes via `commit = true` so
 * each reservation is durable before the function returns.
 *
 * # Layout
 *
 * One SharedPreferences file per account, named
 * `cashu_prefs_<npub>.xml`. Keys are flat:
 *   - `counter_<keysetId>` → Long, the next free NUT-13 counter
 *
 * Plain (non-encrypted) prefs because keyset counters aren't secret —
 * they're not the seed, they don't carry value, and a leak would only
 * tell an attacker how many proofs the wallet has minted at each
 * keyset (a privacy signal at most).
 *
 * # Migration
 *
 * Older builds stored counters inside `AccountSettings.cashuKeysetCounters`.
 * On first read of a given keyset, callers should pre-seed the store
 * from the legacy map (one-time copy) so an upgrade doesn't reset the
 * counter to zero. See `AccountSettings.migrateCashuCountersTo` for
 * the helper.
 */
class CashuPreferences(
    private val prefs: SharedPreferences,
) {
    /** Inspect the next free counter for [keysetId] without advancing it. */
    @Synchronized
    fun peekCounter(keysetId: String): Long = prefs.getLong(counterKey(keysetId), 0L)

    /**
     * Atomically reserve [count] consecutive NUT-13 counters for
     * [keysetId] and return the first reserved index. The write is
     * forced to disk with `commit = true` BEFORE returning — see the
     * class header for why this isn't optional.
     */
    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun reserveCounters(
        keysetId: String,
        count: Int,
    ): Long {
        require(count > 0) { "Counter reservation must be positive" }
        val current = peekCounter(keysetId)
        val next = current + count.toLong()
        prefs.edit(commit = true) { putLong(counterKey(keysetId), next) }
        return current
    }

    /**
     * Seed [keysetId]'s counter from a legacy value found in
     * [AccountSettings.cashuKeysetCounters]. No-op when the store
     * already has a value at or above [legacyValue] — never moves the
     * counter backwards. Called once at wallet load to carry forward
     * pre-migration state.
     */
    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun seedCounterIfMissing(
        keysetId: String,
        legacyValue: Long,
    ) {
        if (legacyValue <= 0L) return
        val current = peekCounter(keysetId)
        if (current >= legacyValue) return
        prefs.edit(commit = true) { putLong(counterKey(keysetId), legacyValue) }
    }

    companion object {
        private const val FILE_PREFIX = "cashu_prefs_"

        private fun counterKey(keysetId: String) = "counter_$keysetId"

        /** Per-account instance. [npub] keys the on-disk file so each account is isolated. */
        fun forAccount(npub: String): CashuPreferences {
            val context = Amethyst.instance.appContext
            val prefs = context.getSharedPreferences("$FILE_PREFIX$npub", Context.MODE_PRIVATE)
            return CashuPreferences(prefs)
        }
    }
}
