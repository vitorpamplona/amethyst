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

    // Insertion-ordered map so we can evict the oldest entry once we hit the cap.
    // Synchronized externally — every mutating call holds the monitor.
    private val cache: LinkedHashMap<String, LnurlEndpointInfo> = LinkedHashMap()

    @Synchronized
    fun get(url: String): LnurlEndpointInfo? = cache[LnurlForm.normalizeUrl(url)]

    @Synchronized
    fun put(
        url: String,
        info: LnurlEndpointInfo,
    ) {
        val key = LnurlForm.normalizeUrl(url)
        // Re-insert so the entry becomes "youngest" in iteration order.
        cache.remove(key)
        cache[key] = info
        if (cache.size > MAX_ENTRIES) {
            val oldest =
                cache.entries
                    .iterator()
                    .next()
                    .key
            cache.remove(oldest)
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    internal fun size(): Int = cache.size
}
