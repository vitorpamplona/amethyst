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
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * Concurrent, caching, stale-while-revalidate DNS resolver for OkHttp.
 *
 * Tuned for an Amethyst-shaped workload: ~700 relays plus a small set of media/profile/NIP-05
 * hosts that reappear constantly, whose IPs change on the order of days.
 *
 * Properties:
 *
 *  1. **Lock-free reads.** Cache is a [ConcurrentHashMap]. The hot path (cache hit) takes no
 *     locks, so 700 concurrent relay reconnects fan out across OkHttp dispatcher threads
 *     instead of serializing through one monitor.
 *  2. **Single-flight coalescing.** N concurrent threads asking for the same host share one
 *     upstream `getaddrinfo`. The leader resolves; followers block on the same future.
 *  3. **Stale-while-revalidate.** Once a host has been resolved, recurring lookups *never*
 *     block on `getaddrinfo` again. After the soft TTL expires, we return the previous answer
 *     immediately and kick a background refresh. The refresh is coalesced through the same
 *     `inflight` map, so a burst of stale reads triggers one refresh per host. If the refresh
 *     fails (`UnknownHostException`), the cache entry is demoted to negative, so the next
 *     caller gets a fresh failure rather than forever-wrong stale IPs.
 *  4. **Negative entries do *not* serve stale.** When an `UnknownHostException` cache entry
 *     expires, the next call goes through the synchronous path. We want transient failures to
 *     recover quickly, not keep returning stale failures.
 *  5. **Generous positive TTL with jitter.** Defaults to 24h plus up to 24h of random jitter,
 *     so each entry expires somewhere in [base, base + jitter]. This breaks the synchronized
 *     herd that would otherwise form when ~700 relay reconnects all populate the cache in the
 *     same second: their expiries spread across a 24h window instead of landing at the same
 *     instant 24h later. A user who opens the app once a day catches only a small fraction of
 *     entries stale per session, and refreshes drip through the executor pool naturally.
 *
 * Remaining blocking points: the very first lookup of a host blocks on `getaddrinfo`
 * (unavoidable — there's nothing to serve stale yet), and followers waiting on that first
 * lookup block on `future.get()`. Background refreshes never block any caller.
 */
class AmethystDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val maxEntries: Int = 2000,
    positiveTtlMs: Long = TimeUnit.HOURS.toMillis(24),
    positiveTtlJitterMs: Long = TimeUnit.HOURS.toMillis(24),
    negativeTtlMs: Long = TimeUnit.SECONDS.toMillis(10),
    private val refreshExecutor: Executor = DEFAULT_REFRESH_EXECUTOR,
) : Dns {
    private val positiveTtlNanos = TimeUnit.MILLISECONDS.toNanos(positiveTtlMs)
    private val positiveTtlJitterNanos = TimeUnit.MILLISECONDS.toNanos(positiveTtlJitterMs)
    private val negativeTtlNanos = TimeUnit.MILLISECONDS.toNanos(negativeTtlMs)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { entry ->
            val now = System.nanoTime()
            if (entry.expiresAtNanos > now) {
                return entry.unwrap(hostname)
            }
            // Soft-expired positive entry: serve stale, refresh in background. Negative
            // entries fall through to the sync path so transient failures recover quickly.
            if (entry.addresses.isNotEmpty()) {
                triggerBackgroundRefresh(hostname)
                return entry.addresses
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

    private fun triggerBackgroundRefresh(hostname: String) {
        val refreshFuture = CompletableFuture<List<InetAddress>>()
        // Coalesce: if a sync lookup or a prior refresh is already in flight, skip.
        if (inflight.putIfAbsent(hostname, refreshFuture) != null) return
        try {
            refreshExecutor.execute {
                try {
                    resolveAsLeader(hostname, refreshFuture)
                } catch (_: Throwable) {
                    // Caller already got the stale answer; the failure is recorded on the
                    // future and (for UnknownHostException) demoted in the cache.
                }
            }
        } catch (_: RejectedExecutionException) {
            inflight.remove(hostname, refreshFuture)
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
                        delegate.lookup(hostname).also { putPositive(hostname, it) }
                    } catch (e: UnknownHostException) {
                        putNegative(hostname)
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

    private fun putPositive(
        hostname: String,
        addresses: List<InetAddress>,
    ) {
        // Jitter the expiry so a burst of co-written entries (e.g. ~700 relay reconnects at app
        // start) doesn't all expire at the same instant 24h later.
        val jitter =
            if (positiveTtlJitterNanos > 0L) {
                ThreadLocalRandom.current().nextLong(positiveTtlJitterNanos)
            } else {
                0L
            }
        cache[hostname] = Entry(addresses, System.nanoTime() + positiveTtlNanos + jitter)
        if (cache.size > maxEntries) evictExpired()
    }

    private fun putNegative(hostname: String) {
        cache[hostname] = Entry(emptyList(), System.nanoTime() + negativeTtlNanos)
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
        // Small fixed pool of daemon threads. Refreshes block on getaddrinfo (~tens of ms),
        // so a handful of threads is plenty even when many hosts go stale at once — extra
        // refreshes queue up without blocking any caller, since callers always get the
        // stale answer instantly.
        private val DEFAULT_REFRESH_EXECUTOR: Executor =
            Executors.newFixedThreadPool(8) { r ->
                Thread(r, "amethyst-dns-refresh").apply { isDaemon = true }
            }

        /**
         * Process-wide instance shared by every OkHttp client built in the app, so a host resolved
         * for an image fetch is reused when a relay handshake or NIP-05 lookup hits the same host.
         */
        val shared: AmethystDns by lazy { AmethystDns() }
    }
}
