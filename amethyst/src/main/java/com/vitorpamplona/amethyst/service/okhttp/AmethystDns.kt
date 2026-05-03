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
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Concurrent, caching DNS resolver for OkHttp.
 *
 * The system resolver call ([InetAddress.getAllByName] used by [Dns.SYSTEM]) is a blocking JNI
 * hop into `getaddrinfo`. On a busy feed we may issue dozens of HTTP calls to a handful of hosts
 * in the same second; the default behaviour pays the resolver tax once per call and serializes
 * the OkHttp dispatcher worker that asked for it.
 *
 * This resolver adds three things on top of the system resolver:
 *
 *  1. An LRU + TTL cache, so repeated lookups of the same host short-circuit before touching the
 *     network. Negative results get a short TTL so a typo doesn't keep hammering DNS.
 *  2. Single-flight coalescing: when N OkHttp threads ask for the same host concurrently, only
 *     one of them performs the upstream lookup. The others block on the same future and pick up
 *     the result. Without this, ten parallel image requests to the same CDN make ten DNS calls.
 *  3. No global lock on the slow path: lookups for *different* hosts proceed in parallel because
 *     the upstream resolver is invoked outside any monitor.
 */
class AmethystDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val maxEntries: Int = 2000,
    positiveTtlMs: Long = TimeUnit.MINUTES.toMillis(5),
    negativeTtlMs: Long = TimeUnit.SECONDS.toMillis(10),
) : Dns {
    private val positiveTtlNanos = TimeUnit.MILLISECONDS.toNanos(positiveTtlMs)
    private val negativeTtlNanos = TimeUnit.MILLISECONDS.toNanos(negativeTtlMs)

    private val cache: MutableMap<String, Entry> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Entry>): Boolean = size > maxEntries
            },
        )
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
            val addresses = delegate.lookup(hostname)
            put(hostname, addresses, positiveTtlNanos)
            future.complete(addresses)
            return addresses
        } catch (e: UnknownHostException) {
            put(hostname, emptyList(), negativeTtlNanos)
            future.completeExceptionally(e)
            throw e
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
    }

    /** Drop all cached entries. Call when the network changes (e.g. WiFi <-> mobile). */
    fun invalidate() {
        cache.clear()
    }

    /** Drop a single host's cached entry. */
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
