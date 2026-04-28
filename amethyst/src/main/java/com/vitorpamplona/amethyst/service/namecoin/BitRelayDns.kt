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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * `okhttp3.Dns` adapter that resolves `.bit` hostnames using the IPv4
 * addresses published in their Namecoin record (read from the same
 * cache [BitRelayResolver] populated when the URL rewriter ran).
 *
 * Non-`.bit` hosts are delegated to [Dns.SYSTEM] unchanged, so the
 * adapter is safe to install globally on any [okhttp3.OkHttpClient]
 * that talks to mixed clearnet relays.
 *
 * Why this is needed: when a Namecoin record's `relay` field is itself
 * a `.bit` URL (the natural shape, e.g. `wss://relay.testls.bit/`),
 * the rewriter resolves it to the same `.bit` URL plus the TLSA pin,
 * and OkHttp then asks the DNS layer for the address. ICANN DNS has
 * no record (the whole point of `.bit`), so without this adapter the
 * connection fails with `UnknownHostException` even though the
 * Namecoin record carries an `ip` field at the same walked node.
 *
 * Cache contract: the adapter is non-blocking on the happy path. It
 * uses `runBlocking(Dispatchers.IO)` to read the resolver's mutex-
 * guarded cache (a memory read), not to issue a fresh lookup, so the
 * latency cost is negligible.
 *
 * Failure modes:
 *   - Host is `.bit`, cache has IPs       → return them as `InetAddress`es.
 *   - Host is `.bit`, cache miss          → fall back to [Dns.SYSTEM]
 *                                           (matches the resolver's
 *                                           "we don't know yet" state;
 *                                           system DNS will then raise
 *                                           `UnknownHostException` and
 *                                           OkHttp will surface that to
 *                                           the caller, same as today).
 *   - Host is `.bit`, cache has empty IPs → fall back to [Dns.SYSTEM]
 *                                           (publisher chose not to
 *                                           ship `ip`; not our place
 *                                           to fail outright).
 *   - Host is not `.bit`                  → fall back to [Dns.SYSTEM].
 */
class BitRelayDns(
    private val resolver: BitRelayResolver,
    private val fallback: Dns = Dns.SYSTEM,
) : Dns {
    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.trim().lowercase()
        if (!host.endsWith(".bit")) return fallback.lookup(hostname)

        val cachedIps =
            try {
                runBlocking(Dispatchers.IO) {
                    // Cache-first: if the WS path already resolved this
                    // host (the common case once strfry is connected),
                    // this is a memory read.
                    var ips = resolver.cachedIpFor(host)
                    if (ips == null) {
                        // Cold cache. The HTTP path (NIP-11, favicons,
                        // etc.) can run before any WebSocket attempt has
                        // populated the cache, so kick off a real resolve
                        // here. resolveRaw is idempotent and cheap on
                        // subsequent hits.
                        resolver.resolveRaw("wss://$host/")
                        ips = resolver.cachedIpFor(host)
                    }
                    ips
                }
            } catch (t: Throwable) {
                Log.w("BitRelayDns") {
                    "cache lookup failed for $host: ${t.message}; falling back to system DNS"
                }
                null
            }

        if (cachedIps.isNullOrEmpty()) {
            // Either the host hasn't been resolved yet (cache miss) or
            // the publisher didn't ship an `ip` field. Either way the
            // platform resolver is the right next step — it'll raise
            // UnknownHostException for us if there's truly nothing.
            return fallback.lookup(hostname)
        }

        val resolved =
            cachedIps.mapNotNull { ip ->
                try {
                    // getAllByName on an IPv4 literal does NOT touch DNS,
                    // it just parses. We use getByName with a literal so
                    // there's no risk of a sneaky lookup.
                    InetAddress.getByName(ip)
                } catch (_: Exception) {
                    null
                }
            }

        if (resolved.isEmpty()) {
            Log.w("BitRelayDns") {
                "all cached IPs for $host failed parsing; falling back to system DNS"
            }
            return fallback.lookup(hostname)
        }

        Log.d("BitRelayDns") {
            "$host -> ${resolved.joinToString(",") { it.hostAddress ?: "?" }} (from Namecoin)"
        }
        return resolved
    }
}
