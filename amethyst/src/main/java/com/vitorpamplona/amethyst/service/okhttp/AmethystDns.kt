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
import java.util.concurrent.atomic.AtomicBoolean

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
 *     same second.
 *  6. **Persistable across restarts.** [snapshot] and [restore] expose the positive cache as a
 *     plain data list so a companion store can survive process death. Wall-clock millis are
 *     used for expiries (not [System.nanoTime], which resets per process) so a restored entry
 *     keeps its remaining lifetime.
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
    private val positiveTtlMillis = positiveTtlMs
    private val positiveTtlJitterMillis = positiveTtlJitterMs
    private val negativeTtlMillis = negativeTtlMs

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()
    private val dirty = AtomicBoolean(false)

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { entry ->
            val now = System.currentTimeMillis()
            if (entry.expiresAtMillis > now) {
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
                if (cached != null && cached.expiresAtMillis > System.currentTimeMillis()) {
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
            if (positiveTtlJitterMillis > 0L) {
                ThreadLocalRandom.current().nextLong(positiveTtlJitterMillis)
            } else {
                0L
            }
        cache[hostname] = Entry(addresses, System.currentTimeMillis() + positiveTtlMillis + jitter)
        dirty.set(true)
        if (cache.size > maxEntries) evictExpired()
    }

    private fun putNegative(hostname: String) {
        cache[hostname] = Entry(emptyList(), System.currentTimeMillis() + negativeTtlMillis)
        // Negative entries are never persisted, so they don't dirty the cache.
        if (cache.size > maxEntries) evictExpired()
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.expiresAtMillis <= now) it.remove()
        }
    }

    /** Drop all cached entries. Call when the network changes (e.g. WiFi <-> mobile). */
    fun invalidate() {
        cache.clear()
        dirty.set(true)
    }

    /** Drop a single host's cached entry. Call when a connection to it fails. */
    fun invalidate(hostname: String) {
        if (cache.remove(hostname) != null) dirty.set(true)
    }

    /**
     * Snapshot of the positive cache for persistence. Negative entries and expired entries are
     * omitted. The expiry timestamps are wall-clock millis (epoch), so they remain meaningful
     * across process restarts.
     */
    fun snapshot(): List<DnsCacheRecord> {
        val now = System.currentTimeMillis()
        val out = ArrayList<DnsCacheRecord>(cache.size)
        for ((host, entry) in cache) {
            if (entry.addresses.isEmpty()) continue
            if (entry.expiresAtMillis <= now) continue
            val ips = entry.addresses.mapNotNull { it.hostAddress }
            if (ips.isNotEmpty()) {
                out += DnsCacheRecord(host, ips, entry.expiresAtMillis)
            }
        }
        return out
    }

    /**
     * Restore from a previously taken snapshot. Entries already expired on disk are dropped.
     * Existing in-memory entries are NOT overwritten (so a fresh lookup that completes before
     * restore lands keeps its newer answer).
     */
    fun restore(records: List<DnsCacheRecord>) {
        val now = System.currentTimeMillis()
        for (record in records) {
            if (record.expiresAtMillis <= now) continue
            val addresses =
                record.addresses.mapNotNull { literal ->
                    // getByName on a numeric literal parses without doing DNS.
                    runCatching { InetAddress.getByName(literal) }.getOrNull()
                }
            if (addresses.isNotEmpty()) {
                cache.putIfAbsent(record.hostname, Entry(addresses, record.expiresAtMillis))
            }
        }
    }

    /** True if the cache has changed since the last [clearDirty]. */
    fun isDirty(): Boolean = dirty.get()

    /** Marks the cache clean. Call after a successful persist. */
    fun clearDirty() {
        dirty.set(false)
    }

    private class Entry(
        val addresses: List<InetAddress>,
        val expiresAtMillis: Long,
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

/** Persistable record. Public so [AmethystDnsStore] can serialize it via Jackson. */
data class DnsCacheRecord(
    val hostname: String,
    val addresses: List<String>,
    val expiresAtMillis: Long,
) {
    // No-arg constructor for Jackson.
    constructor() : this("", emptyList(), 0L)
}
