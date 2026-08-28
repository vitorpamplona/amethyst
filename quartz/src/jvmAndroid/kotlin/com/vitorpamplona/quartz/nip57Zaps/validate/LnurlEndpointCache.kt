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
package com.vitorpamplona.quartz.nip57Zaps.validate

import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cache of LNURL-pay endpoint metadata, keyed by the canonical
 * `/.well-known/lnurlp/<user>` URL the recipient resolves to.
 *
 * Hot on the zap path: every incoming kind-9735 receipt has to know the
 * recipient's LNURL provider's `nostrPubkey` to validate the signer (NIP-57
 * Appendix F). Without a cache, we'd re-fetch the same lnurlp endpoint for
 * every zap from every popular author. Outbound zaps populate the cache as a
 * side effect when [com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver]
 * fetches the recipient's metadata.
 *
 * Keys are URLs (not lud16 forms) so callers can convert lud16 / bech32 LNURL
 * to a URL once via [normalizeUrl] and look up consistently.
 */
object LnurlEndpointCache {
    private const val MAX_ENTRIES = 1000

    // Bounded cache with a lock-free get — hot on the zap-validation read path.
    // Eviction is least-recently-put (a get does not refresh recency), matching
    // the previous LinkedHashMap-based behaviour where only put reordered.
    private val cache = ConcurrentLruCache<String, LnurlEndpointInfo>(MAX_ENTRIES)

    // Fetches in progress, keyed exactly as [cache] is. The two must agree on
    // what counts as "the same address", which is why they live together: a
    // flight map that keyed URLs differently would silently stop deduplicating
    // the moment this object's canonicalisation changed, and nothing would fail.
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<LnurlEndpointInfo?>>()

    fun get(url: String): LnurlEndpointInfo? = cache.get(LnurlForm.normalizeUrl(url))

    fun put(
        url: String,
        info: LnurlEndpointInfo,
    ) {
        cache.put(LnurlForm.normalizeUrl(url), info)
    }

    /**
     * Returns the cached entry for [url], or runs [fetch] — exactly once, however
     * many callers ask at once — and caches what it returns.
     *
     * A zap-receipt burst asks once per receipt: twenty receipts for one
     * lightning address arrive together, all miss, and without this they would
     * be twenty requests to a stranger's unthrottled `/.well-known/lnurlp/`
     * endpoint for one user action. The first caller fetches; the rest await it.
     *
     * A [fetch] returning null is not cached, so a provider having a bad minute
     * is retried by the next caller rather than remembered as unresolvable.
     *
     * [fetch] is expected to report failure by returning null rather than by
     * throwing. Cancellation is the exception: if the coroutine that owns the
     * fetch is cancelled, the CancellationException propagates to that caller
     * alone, and everyone awaiting it gets null — "unresolved", the same as a
     * failed fetch. That asymmetry is deliberate. Awaiters are separate
     * coroutines that are still alive, and cancelling them because the caller
     * that happened to win the race went away would be wrong. Any other
     * throwable reaches awaiters the same way, as null, while propagating to
     * the owner — so a [fetch] that throws gives the two a different answer for
     * one failure. Return null instead.
     */
    suspend fun getOrFetch(
        url: String,
        fetch: suspend (String) -> LnurlEndpointInfo?,
    ): LnurlEndpointInfo? {
        val key = LnurlForm.normalizeUrl(url)
        cache.get(key)?.let { return it }

        val ours = CompletableDeferred<LnurlEndpointInfo?>()
        // Whoever wins putIfAbsent owns the fetch; it returns the previous entry,
        // so a non-null result means someone else is already in flight.
        inFlight.putIfAbsent(key, ours)?.let { return it.await() }

        var info: LnurlEndpointInfo? = null
        try {
            info = fetch(url)?.also { cache.put(key, it) }
        } finally {
            // The cache is populated before the flight is released, so a caller
            // arriving in between reads the result instead of starting a second
            // fetch. Non-suspending, so awaiters are released even if the fetch is
            // cancelled.
            inFlight.remove(key, ours)
            ours.complete(info)
        }
        return info
    }

    fun clear() {
        cache.clear()
        inFlight.clear()
    }

    internal fun size(): Int = cache.size()
}
