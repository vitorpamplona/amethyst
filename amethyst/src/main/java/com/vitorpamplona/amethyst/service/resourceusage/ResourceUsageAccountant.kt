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
package com.vitorpamplona.amethyst.service.resourceusage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder

/**
 * In-memory hot path for usage counters. [add] is called from network threads
 * per relay frame / HTTP response chunk, so it must be allocation-light and
 * lock-free: a ConcurrentHashMap of LongAdders, drained into
 * [ResourceUsageStore] by a debounced flush (at most one write per
 * [flushDebounceMs] while traffic flows; nothing scheduled when idle).
 *
 * Day attribution: deltas are drained into the bucket of the day they are
 * drained on. Counts within one debounce window of midnight may land on the
 * neighboring day — irrelevant at the ledger's day-level granularity.
 */
class ResourceUsageAccountant(
    private val store: ResourceUsageStore,
    private val scope: CoroutineScope,
    private val epochDay: () -> Long = { System.currentTimeMillis() / DAY_MS },
    private val flushDebounceMs: Long = 30_000L,
) {
    private val live = ConcurrentHashMap<String, LongAdder>()
    private val flushScheduled = AtomicBoolean(false)

    /** Hooks run right before a flush drains the counters (e.g. the connection-time integrator closing its open segment). */
    private val preFlushHooks = ConcurrentHashMap.newKeySet<() -> Unit>()

    fun addPreFlushHook(hook: () -> Unit) {
        preFlushHooks.add(hook)
    }

    fun add(
        key: String,
        amount: Long,
    ) {
        if (amount <= 0) return
        live.computeIfAbsent(key) { LongAdder() }.add(amount)
        if (flushScheduled.compareAndSet(false, true)) {
            scope.launch {
                delay(flushDebounceMs)
                flushScheduled.set(false)
                flush()
            }
        }
    }

    /** Drains the in-memory counters into today's persisted bucket. */
    suspend fun flush() {
        preFlushHooks.forEach { runCatching { it() } }
        val deltas = drain()
        if (deltas.isNotEmpty()) {
            store.mergeInto(epochDay(), deltas)
        }
    }

    /** Fire-and-forget flush for non-suspending callers (onTrimMemory). */
    fun flushAsync() {
        scope.launch { flush() }
    }

    fun today(): Long = epochDay()

    /**
     * Persisted buckets merged with the not-yet-flushed live counters
     * (attributed to today). This is what the UI, the report assembler,
     * and the alert evaluator read.
     */
    suspend fun allDaysIncludingLive(): Map<Long, Map<String, Long>> {
        preFlushHooks.forEach { runCatching { it() } }
        val persisted = store.allDays()
        val liveSnapshot = live.mapValues { it.value.sum() }.filterValues { it > 0 }
        if (liveSnapshot.isEmpty()) return persisted
        val today = epochDay()
        val merged = persisted.toMutableMap()
        val todayBucket = merged[today].orEmpty().toMutableMap()
        liveSnapshot.forEach { (key, value) -> todayBucket[key] = (todayBucket[key] ?: 0L) + value }
        merged[today] = todayBucket
        return merged
    }

    private fun drain(): Map<String, Long> {
        if (live.isEmpty()) return emptyMap()
        val deltas = mutableMapOf<String, Long>()
        for (key in live.keys) {
            val adder = live.remove(key) ?: continue
            val value = adder.sum()
            if (value > 0) deltas[key] = value
        }
        return deltas
    }

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
