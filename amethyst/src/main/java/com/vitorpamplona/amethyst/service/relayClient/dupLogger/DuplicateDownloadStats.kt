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
package com.vitorpamplona.amethyst.service.relayClient.dupLogger

import com.vitorpamplona.amethyst.service.relayClient.dupLogger.RelayDuplicateDownloadLogger.Companion.TAG
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.timer

/**
 * Aggregates wasted (duplicated) downloads over a rolling window and periodically
 * logs a digest that points at the filter/assembler interactions that are pulling
 * the same event from the same relay more than once.
 *
 * The digest is grouped by the most actionable dimensions:
 *  - by interaction (sorted subscription-id set, or a single re-requesting sub) so
 *    the developer can map it back to a filter via the REQ that [RelayLogger] prints;
 *  - by relay, to spot relays that we double-pull from;
 *  - by kind, to spot event kinds whose filters overlap across assemblers.
 */
class DuplicateDownloadStats {
    companion object {
        const val KB: Int = 1024

        // how often the digest is printed, in milliseconds
        const val REPORT_PERIOD_MS: Long = 10_000
    }

    private val duplicateDownloads = AtomicInteger(0)
    private val wastedBytes = AtomicInteger(0)

    // interactionKey -> wasted downloads + a sample so we know what it is
    private val byInteraction = LargeCache<String, InteractionTally>()
    private val byRelay = LargeCache<NormalizedRelayUrl, AtomicInteger>()
    private val byKind = LargeCache<Int, AtomicInteger>()

    fun add(dup: DownloadRecord.Duplicate) {
        duplicateDownloads.incrementAndGet()
        wastedBytes.addAndGet(dup.lastBytes)

        increment(byKind, dup.kind)
        increment(byRelay, dup.relayUrl)

        val key = dup.interactionKey()
        val tally = byInteraction.get(key)
        if (tally != null) {
            tally.add(dup)
        } else {
            byInteraction.put(key, InteractionTally(dup.category).also { it.add(dup) })
        }
    }

    private fun <K> increment(
        cache: LargeCache<K, AtomicInteger>,
        key: K,
    ) {
        val stat = cache.get(key)
        if (stat != null) {
            stat.incrementAndGet()
        } else {
            cache.put(key, AtomicInteger(1))
        }
    }

    fun hasAnything() = duplicateDownloads.get() > 0

    fun reset() {
        duplicateDownloads.set(0)
        wastedBytes.set(0)
        byInteraction.clear()
        byRelay.forEach { _, value -> value.set(0) }
        byKind.forEach { _, value -> value.set(0) }
    }

    fun log() {
        Log.w(TAG) {
            "Duplicated downloads in the last ${REPORT_PERIOD_MS / 1000}s: " +
                "${duplicateDownloads.get()} events, ${wastedBytes.get() / KB}kb wasted"
        }

        byInteraction
            .map { key, value -> key to value }
            .sortedByDescending { it.second.count.get() }
            .forEach { (key, tally) ->
                Log.w(TAG) { "-- ${tally.category}: $key -> $tally" }
            }

        Log.w(TAG) {
            "-- By relay: " +
                byRelay.joinToString(", ") { key, value ->
                    if (value.get() > 0) "${key.displayUrl()} (${value.get()})" else ""
                }
        }

        Log.w(TAG) {
            "-- By kind: " +
                byKind.joinToString(", ") { key, value ->
                    if (value.get() > 0) "kind $key (${value.get()})" else ""
                }
        }
    }

    init {
        timer(name = "DuplicateDownloadReporter", period = REPORT_PERIOD_MS, daemon = true) {
            if (hasAnything()) {
                log()
                reset()
            }
        }
    }

    /** Running waste for a single offending filter interaction within the window. */
    class InteractionTally(
        val category: DownloadRecord.Category,
    ) {
        val count = AtomicInteger(0)
        val wastedBytes = AtomicInteger(0)
        private val kinds = LargeCache<Int, AtomicInteger>()
        private val relays = LargeCache<NormalizedRelayUrl, AtomicInteger>()

        fun add(dup: DownloadRecord.Duplicate) {
            count.incrementAndGet()
            wastedBytes.addAndGet(dup.lastBytes)

            val kindStat = kinds.get(dup.kind)
            if (kindStat != null) kindStat.incrementAndGet() else kinds.put(dup.kind, AtomicInteger(1))

            val relayStat = relays.get(dup.relayUrl)
            if (relayStat != null) relayStat.incrementAndGet() else relays.put(dup.relayUrl, AtomicInteger(1))
        }

        private fun printKinds() = kinds.joinToString(", ") { key, value -> "k$key x${value.get()}" }

        private fun printRelays() = relays.joinToString(", ") { key, value -> "${key.displayUrl()} x${value.get()}" }

        override fun toString() = "${count.get()} dups, ${wastedBytes.get() / KB}kb; kinds [${printKinds()}]; relays [${printRelays()}]"
    }
}
