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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-process DNS cache for OkHttp that makes mass parallel connections viable:
 *
 *  - **Positive cache** ([positiveTtlMs]): the JVM's own `InetAddress` cache holds
 *    entries for ~30s — useless across a 30-minute crawl that re-dials the same
 *    hosts every round. Resolved addresses are kept for the TTL so reconnects and
 *    the outbox model's many per-user path URLs on one host
 *    (`wss://filter.example/npubA`, `/npubB`, …) resolve instantly.
 *  - **Negative cache** ([negativeTtlMs]): a dead domain is the single most
 *    expensive lookup — glibc `getaddrinfo` retries nameservers for 10–30s while
 *    holding an OkHttp dispatcher thread — and a crawl re-dials dead relays from
 *    every straggler's outbox. The first [UnknownHostException] is remembered and
 *    re-thrown immediately for the TTL, so later dials fail in microseconds.
 *  - **In-flight dedup**: concurrent lookups of the same host (a connect storm
 *    dialing hundreds of URLs on one authority at once) collapse onto a single
 *    delegate call; the rest wait on its [CompletableFuture] instead of stacking
 *    N identical blocking `getaddrinfo` calls.
 *
 * Only [UnknownHostException] is negative-cached — a resolver that *errors*
 * (interrupted, SecurityException, …) is not proof the name is bad, so those
 * propagate uncached. Entries are evicted lazily on the next lookup after
 * expiry; the map is bounded by the distinct-host universe (a few thousand for
 * a full crawl), so no active eviction is needed.
 */
class CachingDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val positiveTtlMs: Long = 10 * 60_000L,
    private val negativeTtlMs: Long = 10 * 60_000L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : Dns {
    private class Entry(
        /** Resolved addresses, or null for a cached resolution failure. */
        val addresses: List<InetAddress>?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        val hit = cache[hostname]
        if (hit != null) {
            if (nowMs() < hit.expiresAtMs) {
                return hit.addresses
                    ?: throw UnknownHostException("$hostname (cached DNS failure)")
            }
            cache.remove(hostname, hit)
        }

        // One resolver call per host at a time: the creator runs the delegate,
        // everyone else who raced in blocks on the same future.
        val future = CompletableFuture<List<InetAddress>>()
        val existing = inFlight.putIfAbsent(hostname, future)
        if (existing != null) {
            return try {
                existing.join()
            } catch (e: Exception) {
                throw (e.cause as? UnknownHostException) ?: UnknownHostException("$hostname (concurrent lookup failed)")
            }
        }

        try {
            val addresses = delegate.lookup(hostname)
            cache[hostname] = Entry(addresses, nowMs() + positiveTtlMs)
            future.complete(addresses)
            return addresses
        } catch (e: UnknownHostException) {
            cache[hostname] = Entry(null, nowMs() + negativeTtlMs)
            future.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            // Not proof the name is bad (interrupt, security, …) — don't cache.
            future.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(hostname, future)
        }
    }
}
