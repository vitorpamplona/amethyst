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
package com.vitorpamplona.amethyst.service.namecoin

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Synchronous bridge that adapts the suspend [BitRelayResolver] API to the
 * blocking `(NormalizedRelayUrl) -> String` rewriter expected by
 * `OkHttpWebSocket.Builder`.
 *
 * Call site is `OkHttpWebSocket.connect()`, which runs on the relay-pool IO
 * dispatcher. Using [runBlocking] there is acceptable because:
 *   - The thread is already an IO worker.
 *   - The underlying
 *     [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.lookupNameDetailed]
 *     call is timeout-bounded.
 *   - Resolutions are cached, so steady-state cost is zero.
 *
 * Tor-aware routing
 * -----------------
 * If the Namecoin record advertises an `.onion` alias (via `tor` or
 * `_tor.txt` field), the rewriter will swap the resolved clearnet URL
 * for the onion endpoint when [preferOnion] returns `true`. The check is
 * re-evaluated per call so that toggling Tor settings while the app is
 * running takes effect on the next reconnect.
 *
 * On any error or for non-`.bit` URLs, the original URL is returned
 * unchanged so connection behaviour is identical to the pre-feature path.
 */
class BitRelayUrlRewriter(
    private val resolver: BitRelayResolver,
    /**
     * Returns `true` when this connection should prefer an `.onion`
     * endpoint advertised in the Namecoin record over the clearnet
     * `relay` endpoint. Typically wired to the user's live Tor settings
     * (`torType != OFF && onionRelaysViaTor`).
     *
     * Defaults to `false` so callers that don't care about onion routing
     * keep the previous clearnet-only behaviour.
     */
    private val preferOnion: (NormalizedRelayUrl) -> Boolean = { false },
) : (NormalizedRelayUrl) -> String {
    override fun invoke(url: NormalizedRelayUrl): String {
        if (!BitRelayResolver.isBitRelay(url)) return url.url
        return try {
            runBlocking(Dispatchers.IO) {
                when (val outcome = resolver.resolve(url)) {
                    is BitRelayResolver.Resolution.Resolved -> {
                        val target = pickEndpoint(url, outcome)
                        Log.d("BitRelayUrlRewriter") {
                            val routing = if (target != outcome.resolvedUrl) " (Tor onion)" else ""
                            ".bit relay ${url.url} -> $target$routing" +
                                " (${outcome.candidates.size} clearnet candidate(s)," +
                                " ${outcome.onionEndpoints.size} onion endpoint(s))"
                        }
                        target
                    }

                    is BitRelayResolver.Resolution.NotFound -> {
                        Log.w("BitRelayUrlRewriter") {
                            ".bit relay ${url.url}: not found (${outcome.message}); failing connect"
                        }
                        url.url
                    }

                    is BitRelayResolver.Resolution.Error -> {
                        Log.w("BitRelayUrlRewriter") {
                            ".bit relay ${url.url}: ${outcome.message}; falling back to original URL"
                        }
                        url.url
                    }

                    BitRelayResolver.Resolution.NotABitHost -> {
                        url.url
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w("BitRelayUrlRewriter") {
                ".bit resolution threw for ${url.url}: ${t.message}"
            }
            url.url
        }
    }

    /**
     * Choose the wire URL we'll actually hand to OkHttp.
     *
     *   - If Tor is preferred AND the record has an onion endpoint, use it.
     *     The user's existing Tor settings (per-relay `useTor` evaluation)
     *     will then route the resulting `ws://...onion/` connection
     *     through the SOCKS proxy on the Tor service.
     *   - Otherwise return [BitRelayResolver.Resolution.Resolved.resolvedUrl]
     *     unchanged. If the record only has onion endpoints (no clearnet
     *     relay) and Tor is not preferred, the resolver has already set
     *     `resolvedUrl` to the first onion endpoint as a last resort, and
     *     we return that \u2014 connecting will fail without Tor, but the user
     *     gets a clear error rather than a silent no-op.
     */
    private fun pickEndpoint(
        url: NormalizedRelayUrl,
        outcome: BitRelayResolver.Resolution.Resolved,
    ): String {
        if (outcome.onionEndpoints.isNotEmpty() && preferOnion(url)) {
            return outcome.onionEndpoints.first()
        }
        return outcome.resolvedUrl
    }
}
