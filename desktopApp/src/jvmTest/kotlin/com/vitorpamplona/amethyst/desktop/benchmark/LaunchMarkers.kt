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
package com.vitorpamplona.amethyst.desktop.benchmark

import com.vitorpamplona.amethyst.desktop.ui.note.NoteCardInstrumentation
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Single-threaded marker registry used by the launch benchmark and the
 * Compose smoke tests. Records named timestamps relative to a single
 * `start()` reference; later calls to `mark(name)` with an existing name
 * are ignored so the first-occurrence semantics of `t_first_event` /
 * `t_n_events` are stable.
 *
 * Not thread-safe — Gradle test parallelism for any class touching this
 * registry must be set to `maxParallelForks = 1`. The Compose UI test
 * rule already serializes, and the benchmark runner forks a fresh JVM
 * per cold sample, so this is not a practical limit.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 3.1.
 */
object LaunchMarkers {
    private val timestamps = mutableMapOf<String, Duration>()
    private var start: TimeMark? = null
    private val noteCardCounter = AtomicInteger(0)

    const val T_FIRST_COMPOSITION_APPLY: String = "t_first_composition_apply"
    const val T_ACCOUNT_LOGGED_IN: String = "t_account_logged_in"
    const val T_FIRST_EVENT: String = "t_first_event"
    const val T_N_EVENTS: String = "t_n_events"

    /** Default count for the headline `t_n_events` metric. */
    const val DEFAULT_N: Int = 10

    /** Begin a measurement window. Clears any previously recorded markers. */
    fun start() {
        timestamps.clear()
        noteCardCounter.set(0)
        start = TimeSource.Monotonic.markNow()
    }

    /**
     * Record [name] if not already recorded. Returns true when this call
     * was the one to record it.
     */
    fun mark(name: String): Boolean {
        val ref = start ?: return false
        if (name in timestamps) return false
        timestamps[name] = ref.elapsedNow()
        return true
    }

    /** Snapshot of all recorded markers in insertion order. */
    fun snapshot(): Map<String, Duration> = timestamps.toMap()

    /**
     * [NoteCardInstrumentation] adapter that records `t_first_event` on
     * the first placement and `t_n_events` on the [n]th distinct one.
     * Counts distinct `noteId`s — a recomposition that re-emits the same
     * card is not counted twice.
     */
    fun noteCardInstrumentation(n: Int = DEFAULT_N): NoteCardInstrumentation {
        val seen =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<String>()
        return NoteCardInstrumentation { noteId ->
            if (seen.add(noteId)) {
                val placed = noteCardCounter.incrementAndGet()
                when {
                    placed == 1 -> mark(T_FIRST_EVENT)
                    placed >= n -> mark(T_N_EVENTS)
                }
            }
        }
    }
}
