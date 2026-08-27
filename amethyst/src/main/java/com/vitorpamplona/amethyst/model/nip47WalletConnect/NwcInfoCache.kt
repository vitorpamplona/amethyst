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
package com.vitorpamplona.amethyst.model.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-account cache of NWC wallets' kind 13194 info events, keyed by wallet
 * service pubkey. One fetch backs every capability question we ask about a
 * wallet — the advertised encryption schemes (NIP-44 vs NIP-04 negotiation), the
 * supported RPC methods, and whether it emits notifications.
 *
 * Entries expire after [ttlSeconds] (default 2 days) so a wallet that later
 * changes its advertised capabilities is eventually re-checked. Four entry
 * points, in increasing order of how much they will wait:
 *
 *  - [current] returns whatever is cached (possibly stale, possibly null) with no
 *    side effect and never blocks — for callers that can act on "don't know".
 *  - [refreshIfStale] triggers a background fetch when the entry is missing or
 *    expired, and returns immediately — call it right before using a wallet so a
 *    stale entry self-heals without holding up the transaction.
 *  - [currentOrFetch] waits only when nothing at all is cached, and returns a
 *    stale entry as-is — for callers where "don't know" and "no" are different
 *    answers, such as NIP-44 negotiation.
 *  - [getFresh] waits whenever the entry is missing *or* expired, for a caller
 *    that must not act on a stale answer. No production caller needs that today.
 *
 * Every fetching path funnels through one request per wallet, so the startup
 * warm-up, a payment waiting on a cold cache and the notification watcher join
 * the same call rather than racing each other.
 *
 * A completed fetch — including a definitive "wallet published no info event"
 * (null) — is cached with a timestamp. A *failed* fetch (network error/timeout)
 * is never cached, so a transient error retries on the next use instead of
 * pinning the wallet to the fallback for the whole TTL window.
 */
class NwcInfoCache(
    private val fetch: suspend (Nip47WalletConnect.Nip47URINorm) -> NwcInfoEvent?,
    private val scope: CoroutineScope,
    private val ttlSeconds: Long = DEFAULT_TTL_SECONDS,
    private val now: () -> Long = { TimeUtils.now() },
) {
    private class Entry(
        val info: NwcInfoEvent?,
        val fetchedAt: Long,
    )

    private val cache = ConcurrentHashMap<HexKey, Entry>()

    // Fetches in progress, keyed like [cache]. Every path that fetches goes through
    // [fetchOnce], so the startup warm-up, a payment waiting on a cold cache and the
    // notification watcher all join one request per wallet instead of racing.
    private val inFlight = ConcurrentHashMap<HexKey, CompletableDeferred<NwcInfoEvent?>>()

    private fun isFresh(entry: Entry): Boolean = now() - entry.fetchedAt < ttlSeconds

    /** Non-blocking read of the currently cached info event (may be stale or null). */
    fun current(uri: Nip47WalletConnect.Nip47URINorm): NwcInfoEvent? = cache[uri.pubKeyHex]?.info

    /**
     * Non-blocking. Kicks off a background fetch when the wallet's entry is missing
     * or expired; a fetch already running for that wallet is not duplicated. Safe
     * to call on the hot path — it never suspends.
     */
    fun refreshIfStale(uri: Nip47WalletConnect.Nip47URINorm) {
        val entry = cache[uri.pubKeyHex]
        if (entry != null && isFresh(entry)) return
        if (inFlight.containsKey(uri.pubKeyHex)) return

        scope.launch(Dispatchers.IO) { fetchOnce(uri) }
    }

    /**
     * Suspends until a fresh-enough info event is available, fetching when the
     * entry is missing or expired. Returns the last cached (possibly stale) value
     * if the fetch fails.
     */
    suspend fun getFresh(uri: Nip47WalletConnect.Nip47URINorm): NwcInfoEvent? {
        val entry = cache[uri.pubKeyHex]
        if (entry != null && isFresh(entry)) return entry.info
        return fetchOnce(uri)
    }

    /**
     * Returns whatever is cached, waiting for a fetch only when there is nothing
     * cached at all.
     *
     * This is the encryption-negotiation entry point. [current] answers "what does
     * this wallet advertise" from memory, but on a cold cache it answers null, and
     * a null there is indistinguishable from "no NIP-44" — so the caller silently
     * downgrades to NIP-04 on the first transaction after every app start. Waiting
     * once, only when nothing is known, removes that.
     *
     * A stale entry is returned as-is without waiting: it still says which
     * encryption the wallet advertises, and [refreshIfStale] self-heals it in the
     * background for next time.
     */
    suspend fun currentOrFetch(uri: Nip47WalletConnect.Nip47URINorm): NwcInfoEvent? {
        val entry = cache[uri.pubKeyHex]
        if (entry != null) {
            refreshIfStale(uri)
            return entry.info
        }
        return fetchOnce(uri)
    }

    /**
     * Runs [fetchAndStore] for [uri] exactly once, however many callers ask at
     * once; the rest await that one result.
     *
     * Cancellation reaches only the caller that owns the fetch — awaiters are
     * separate coroutines that are still alive, and get the last cached value (or
     * null) rather than being cancelled along with it.
     */
    private suspend fun fetchOnce(uri: Nip47WalletConnect.Nip47URINorm): NwcInfoEvent? {
        val key = uri.pubKeyHex
        val ours = CompletableDeferred<NwcInfoEvent?>()
        // putIfAbsent returns the previous entry, so a non-null result means
        // someone else already owns this wallet's fetch.
        inFlight.putIfAbsent(key, ours)?.let { return it.await() }

        var info: NwcInfoEvent? = null
        try {
            info = fetchAndStore(uri)
        } finally {
            // Non-suspending, so awaiters are released even if we are cancelled.
            inFlight.remove(key, ours)
            ours.complete(info)
        }
        return info
    }

    private suspend fun fetchAndStore(uri: Nip47WalletConnect.Nip47URINorm): NwcInfoEvent? {
        val info =
            try {
                fetch(uri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return cache[uri.pubKeyHex]?.info // keep the old value; retry on next use
            }

        cache[uri.pubKeyHex] = Entry(info, now())
        return info
    }

    companion object {
        const val DEFAULT_TTL_SECONDS = 2L * 24 * 60 * 60 // 2 days
    }
}
