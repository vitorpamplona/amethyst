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
package com.vitorpamplona.amethyst.service.okhttp

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Concurrent, caching DNS resolver for OkHttp.
 *
 * Tuned for an Amethyst-shaped workload: ~700 relays plus a small set of media/profile/NIP-05
 * hosts that reappear constantly. Steady state is well under [maxEntries] distinct hosts whose
 * IPs change on the order of days, so we want to resolve each one once per session and never
 * touch DNS again.
 *
 * Properties:
 *
 *  1. **Lock-free reads.** Cache is a [ConcurrentHashMap], so the hot path (cache hit) does no
 *     locking. The previous incarnation used `synchronizedMap(LinkedHashMap(access-order=true))`,
 *     which turned every `get` into a monitor-protected write because access-order LRU rewrites
 *     the linked list on read — at 700 concurrent relay reconnects, the lock dominated.
 *  2. **Single-flight coalescing.** N concurrent OkHttp threads asking for the same host share
 *     one upstream `getaddrinfo`. The leader resolves; followers block on the same future. If a
 *     peer leader refreshed the entry while we were claiming leadership, the leader re-checks
 *     the cache and skips the system call entirely.
 *  3. **Generous positive TTL.** Defaults to 24h. Relay and CDN IPs almost never move, and we
 *     are not a recursive resolver — there is no correctness reason to honor authoritative TTLs.
 *     Pair with [invalidate] on connection failures or network changes to recover from real
 *     IP moves.
 *  4. **Short negative TTL.** Failed lookups are remembered for 10s so a typo or a transiently
 *     down host doesn't keep paying for `getaddrinfo`, but a real outage recovers quickly.
 *
 * Remaining blocking points are unavoidable: the leader's [Dns.lookup] call is a synchronous JNI
 * hop into the system resolver, and followers must wait on the leader's future. Both are
 * bypassed entirely on cache hit, which is the steady state.
 */
class AmethystDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val maxEntries: Int = 2000,
    positiveTtlMs: Long = TimeUnit.HOURS.toMillis(24),
    negativeTtlMs: Long = TimeUnit.SECONDS.toMillis(10),
) : Dns {
    private val positiveTtlNanos = TimeUnit.MILLISECONDS.toNanos(positiveTtlMs)
    private val negativeTtlNanos = TimeUnit.MILLISECONDS.toNanos(negativeTtlMs)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { entry ->
            if (entry.expiresAtNanos > System.nanoTime()) {
                return entry.unwrap(hostname)
            }
        }

        val newFuture = CompletableFuture<List<InetAddress>>()
        val existing = inflight.putIfAbsent(hostname, newFuture)
        return if (existing == null) {
            resolveAsLeader(hostname, newFuture)
        } else {
            awaitFollower(hostname, existing)
        }
    }

    private fun resolveAsLeader(
        hostname: String,
        future: CompletableFuture<List<InetAddress>>,
    ): List<InetAddress> {
        try {
            // Re-check after claiming leadership: a peer may have refreshed the cache between
            // our miss and our putIfAbsent. Skips a duplicate getaddrinfo in that race.
            val cached = cache[hostname]
            val addresses =
                if (cached != null && cached.expiresAtNanos > System.nanoTime()) {
                    cached.unwrap(hostname)
                } else {
                    try {
                        delegate.lookup(hostname).also { put(hostname, it, positiveTtlNanos) }
                    } catch (e: UnknownHostException) {
                        put(hostname, emptyList(), negativeTtlNanos)
                        throw e
                    }
                }
            future.complete(addresses)
            return addresses
        } catch (e: Throwable) {
            future.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(hostname, future)
        }
    }

    private fun awaitFollower(
        hostname: String,
        future: CompletableFuture<List<InetAddress>>,
    ): List<InetAddress> {
        try {
            val addresses = future.get()
            return addresses.ifEmpty { throw UnknownHostException(hostname) }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is UnknownHostException -> throw cause
                null -> throw UnknownHostException(hostname)
                else -> throw UnknownHostException(hostname).apply { initCause(cause) }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UnknownHostException(hostname).apply { initCause(e) }
        }
    }

    private fun put(
        hostname: String,
        addresses: List<InetAddress>,
        ttlNanos: Long,
    ) {
        cache[hostname] = Entry(addresses, System.nanoTime() + ttlNanos)
        if (cache.size > maxEntries) evictExpired()
    }

    private fun evictExpired() {
        val now = System.nanoTime()
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.expiresAtNanos <= now) it.remove()
        }
    }

    /** Drop all cached entries. Call when the network changes (e.g. WiFi <-> mobile). */
    fun invalidate() {
        cache.clear()
    }

    /** Drop a single host's cached entry. Call when a connection to it fails. */
    fun invalidate(hostname: String) {
        cache.remove(hostname)
    }

    private class Entry(
        val addresses: List<InetAddress>,
        val expiresAtNanos: Long,
    ) {
        fun unwrap(hostname: String): List<InetAddress> = addresses.ifEmpty { throw UnknownHostException(hostname) }
    }

    companion object {
        /**
         * Process-wide instance shared by every OkHttp client built in the app, so a host resolved
         * for an image fetch is reused when a relay handshake or NIP-05 lookup hits the same host.
         */
        val shared: AmethystDns by lazy { AmethystDns() }
    }
}
