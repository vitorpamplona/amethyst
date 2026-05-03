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
import java.util.Locale
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
    private val positiveTtlMs: Long = TimeUnit.HOURS.toMillis(24),
    private val positiveTtlJitterMs: Long = TimeUnit.HOURS.toMillis(24),
    private val negativeTtlMs: Long = TimeUnit.SECONDS.toMillis(10),
    private val refreshExecutor: Executor = DEFAULT_REFRESH_EXECUTOR,
) : Dns {
    private val cache = ConcurrentHashMap<String, Entry>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()
    private val dirty = AtomicBoolean(false)

    override fun lookup(hostname: String): List<InetAddress> {
        // DNS hostnames are case-insensitive; canonicalize so "Example.com" and "example.com"
        // share a cache entry. OkHttp normally hands us lowercase, but custom callers may not.
        val key = hostname.lowercase(Locale.ROOT)

        cache[key]?.let { entry ->
            if (entry.expiresAtMillis > System.currentTimeMillis()) {
                return entry.unwrap(key)
            }
            // Soft-expired positive entry: serve stale, refresh in background. Negative
            // entries fall through to the sync path so transient failures recover quickly.
            if (entry.addresses.isNotEmpty()) {
                triggerBackgroundRefresh(key)
                return entry.addresses
            }
        }

        val newFuture = CompletableFuture<List<InetAddress>>()
        val existing = inflight.putIfAbsent(key, newFuture)
        return if (existing == null) resolveAsLeader(key, newFuture) else awaitFollower(key, existing)
    }

    private fun triggerBackgroundRefresh(host: String) {
        val refreshFuture = CompletableFuture<List<InetAddress>>()
        // Coalesce: if a sync lookup or a prior refresh is already in flight, skip.
        if (inflight.putIfAbsent(host, refreshFuture) != null) return
        try {
            // The caller already got the stale answer; refresh failures are recorded on the
            // future and (for UnknownHostException) demoted in the cache by lookupAndCache.
            refreshExecutor.execute {
                runCatching { resolveAsLeader(host, refreshFuture) }
            }
        } catch (_: RejectedExecutionException) {
            inflight.remove(host, refreshFuture)
        }
    }

    private fun resolveAsLeader(
        host: String,
        future: CompletableFuture<List<InetAddress>>,
    ): List<InetAddress> {
        try {
            // Re-check after claiming leadership: a peer may have refreshed the cache between
            // our miss and our putIfAbsent. Skips a duplicate getaddrinfo in that race.
            val fresh = cache[host]?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }
            val addresses = fresh?.unwrap(host) ?: lookupAndCache(host)
            future.complete(addresses)
            return addresses
        } catch (e: Throwable) {
            future.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(host, future)
        }
    }

    private fun lookupAndCache(host: String): List<InetAddress> =
        try {
            delegate.lookup(host).also { putPositive(host, it) }
        } catch (e: UnknownHostException) {
            putNegative(host)
            throw e
        }

    private fun awaitFollower(
        host: String,
        future: CompletableFuture<List<InetAddress>>,
    ): List<InetAddress> =
        try {
            future.get().ifEmpty { throw UnknownHostException(host) }
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is UnknownHostException) throw cause
            throw UnknownHostException(host).apply { if (cause != null) initCause(cause) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UnknownHostException(host).apply { initCause(e) }
        }

    private fun putPositive(
        host: String,
        addresses: List<InetAddress>,
    ) {
        cache[host] = Entry(addresses, positiveExpiry())
        dirty.set(true)
        evictIfOverCap()
    }

    private fun putNegative(host: String) {
        // Negative entries are never persisted, so they don't dirty the cache.
        cache[host] = Entry(emptyList(), negativeExpiry())
        evictIfOverCap()
    }

    /**
     * Jittered expiry so a burst of co-written entries (e.g. ~700 relay reconnects at app start)
     * doesn't all expire at the same instant 24h later.
     */
    private fun positiveExpiry(): Long {
        val jitter = if (positiveTtlJitterMs > 0L) ThreadLocalRandom.current().nextLong(positiveTtlJitterMs) else 0L
        return System.currentTimeMillis() + positiveTtlMs + jitter
    }

    private fun negativeExpiry(): Long = System.currentTimeMillis() + negativeTtlMs

    private fun evictIfOverCap() {
        if (cache.size <= maxEntries) return
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
        val key = hostname.lowercase(Locale.ROOT)
        if (cache.remove(key) != null) dirty.set(true)
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
            // hostAddress is platform-typed (String!); mapNotNull narrows to String and stays
            // defensive against the rare case where it might be null.
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
                val key = record.hostname.lowercase(Locale.ROOT)
                cache.putIfAbsent(key, Entry(addresses, record.expiresAtMillis))
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
        fun unwrap(host: String): List<InetAddress> = addresses.ifEmpty { throw UnknownHostException(host) }
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
    }
}

/** Persistable record. Public so [AmethystDnsStore] can serialize it via Jackson. */
data class DnsCacheRecord(
    val hostname: String,
    val addresses: List<String>,
    val expiresAtMillis: Long,
)
