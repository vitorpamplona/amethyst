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
package com.vitorpamplona.quartz.utils.concurrent

/**
 * A blocking mutual-exclusion lock whose waiters **park** (yield the core to the
 * scheduler) instead of busy-waiting.
 *
 * Why this exists: `commonMain` has no `java.util.concurrent.locks.Lock`, so the
 * relay client previously hand-rolled a spin lock over an `AtomicBoolean`. A
 * busy-wait is only ever correct when the holder cannot be descheduled while
 * holding the lock — and on Android that assumption is false. A production ANR on
 * a Pixel 8 (`anr_2026-08-03-12-55-26-256`, Amethyst 1.13.1) caught the exact
 * failure: the thread holding the lock was parked in `WaitingForGcToComplete`
 * while **51 of 52** runnable relay-dispatch threads sat in the inlined spin loop,
 * burning 596% CPU (6 of the phone's 9 cores) waiting for a holder that could not
 * be scheduled to release it. The UI thread, needing to allocate, then waited on
 * the same GC for over 5s and Android killed the frame with
 * "Input dispatching timed out".
 *
 * Measured on `SpinLockConvoyBenchmark` (12-core dev machine — a phone is worse):
 * the spin lock delivered 138M critical sections/s uncontended but only 1.2M/s
 * with 52 contenders (0.86%, a 116x collapse), and an *unrelated* allocating
 * thread's p90 latency went from 22µs to 10ms. Parking removes the CPU burn: a
 * waiter costs one context switch instead of a whole core.
 *
 * Splits the same way [ConcurrentMap] does:
 *  - JVM / Android → `ReentrantLock` (real parking via `AbstractQueuedSynchronizer`).
 *  - Apple → `NSRecursiveLock`, which also parks. The relay client genuinely runs
 *    on iOS, so this must not spin — same choice commons' `KmpLock` already made.
 *  - Linux → a spin, since Kotlin/Native ships no parking lock and there is no
 *    Foundation; linuxX64 is a build/CI target, not a host for the many-relay
 *    workload. Swap in a pthread mutex if that changes.
 *
 * (`commons` has an equivalent `KmpLock`, but `commons` depends on `quartz` and not
 * the reverse, so quartz cannot use it — keep the two in sync by hand.)
 *
 * Unlike the primitive it replaces, the JVM/Android actual is **reentrant**, so an
 * accidental re-entry degrades into a no-op rather than a self-deadlock. Callers
 * should still keep I/O and listener callbacks outside the critical section — that
 * discipline is about holding the lock briefly, not about avoiding a hang.
 */
expect class PlatformLock() {
    fun lock()

    fun unlock()
}

/** Runs [block] holding [this]. Inline so hot paths allocate no closure. */
inline fun <R> PlatformLock.withLock(block: () -> R): R {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
