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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentSet

/**
 * Which relays are worth dialling, within one fan-out cycle. A discovered
 * relay list is five figures of urls and most of them are corpses; without
 * this, every cycle re-dials all of them and the working relays queue behind
 * hosts that stopped existing years ago.
 *
 * Failures are counted per AUTHORITY (`host[:port]`), not per url: the outbox
 * model mints one url per user for a filtering relay, so a per-url counter
 * never reaches a threshold on any single one. The authority is host-only and
 * does NOT fold a subdomain into its parent — those are different servers.
 *
 * [produced] overrides a strike race: a host that has ever delivered is never
 * treated as dead for the rest of the cycle, whichever order the two events
 * land in. Cycle-local; nothing persists — see [RelayReachabilityStore] for
 * the part that survives a restart.
 */
class HostStrikes(
    private val strikeLimit: Int = DEFAULT_STRIKE_LIMIT,
    // Relays a previous run proved unreachable, and still within their TTL.
    private val knownDead: Set<NormalizedRelayUrl> = emptySet(),
) {
    private val strikes = ConcurrentMap<String, Int>()
    private val deadHosts = ConcurrentSet<String>()
    private val producedHosts = ConcurrentSet<String>()

    private val delivered = ConcurrentSet<NormalizedRelayUrl>()
    private val failed = ConcurrentSet<NormalizedRelayUrl>()

    /** Relays this cycle actually got something from — worth remembering as live. */
    val reachable: Set<NormalizedRelayUrl> get() = delivered.snapshot()

    /** Relays this cycle could not reach at all. A relay that later delivered is not in it. */
    val unreachable: Set<NormalizedRelayUrl> get() = failed.snapshot() - delivered.snapshot()

    /**
     * Skip this relay? True when a previous run proved it dead (and no
     * [produced] since), or when its whole authority has been struck out here.
     */
    fun isDead(url: NormalizedRelayUrl): Boolean {
        val authority = authorityOf(url.url)
        if (authority in producedHosts) return false
        return url in knownDead || authority in deadHosts
    }

    /**
     * This relay connected but delivered nothing before giving up. Count it
     * against its authority and, at [strikeLimit], stop dialling the host.
     * Returns the eviction — for the caller to publish — exactly when this
     * strike is the one that took the host down: that is the only point where
     * the evidence exists, because every sibling url is skipped without being
     * dialled from here on.
     */
    fun strike(url: NormalizedRelayUrl): Evicted? {
        failed.add(url)
        if (strikeLimit <= 0) return null
        val authority = authorityOf(url.url)
        if (authority in producedHosts || authority in deadHosts) return null
        if (strikes.merge(authority, 1) { old, new -> old + new } < strikeLimit) return null
        deadHosts.add(authority)
        return Evicted(authority, strikeLimit)
    }

    /** An authority struck out, and the evidence for it. */
    class Evicted(
        val authority: String,
        val strikes: Int,
    )

    /** This relay delivered. Its authority is alive, whatever else happened. */
    fun produced(url: NormalizedRelayUrl) {
        delivered.add(url)
        producedHosts.add(authorityOf(url.url))
    }

    /** For a cycle's closing line: how many hosts were dropped. */
    fun evictedHosts(): Int = deadHosts.size()

    fun summary(total: Int): String =
        "${reachable.size} live, ${unreachable.size} unreachable, " +
            "${deadHosts.size()} host(s) struck out, ${knownDead.size} skipped as known-dead of $total"

    companion object {
        /**
         * Three, because a single timeout is ordinary — a busy relay that
         * never answered one REQ is not a dead one — while three separate
         * urls on the same host all going silent is a server, not a
         * coincidence.
         */
        const val DEFAULT_STRIKE_LIMIT = 3

        /**
         * `host[:port]` — everything between the scheme and the first path
         * slash. The port is part of it: two ports on one machine are two
         * relays.
         */
        fun authorityOf(url: String): String {
            val afterScheme =
                when {
                    url.startsWith("wss://") -> url.substring(6)
                    url.startsWith("ws://") -> url.substring(5)
                    else -> url
                }
            val slash = afterScheme.indexOf('/')
            return if (slash >= 0) afterScheme.substring(0, slash) else afterScheme
        }
    }
}
