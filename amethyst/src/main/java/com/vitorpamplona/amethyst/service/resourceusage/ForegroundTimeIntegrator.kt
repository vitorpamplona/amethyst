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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Integrates time-with-UI-visible into the ledger ([UsageKeys.APP_FG_MS]).
 * Screen-on time is what display power is proportional to, and it's the
 * denominator that makes every other counter interpretable (MB per hour in
 * app vs MB while backgrounded). Same timer-free segment pattern as
 * [RelayConnectionTimeIntegrator]: segments close on transitions and on the
 * accountant's pre-flush hook.
 */
class ForegroundTimeIntegrator(
    private val isForeground: Flow<Boolean>,
    private val accountant: ResourceUsageAccountant,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private var foreground = false
    private var segmentStartMs = 0L

    fun start(scope: CoroutineScope): Job {
        accountant.addPreFlushHook(::closeOpenSegment)
        return scope.launch {
            isForeground.collect { fg -> transitionTo(fg) }
        }
    }

    fun closeOpenSegment() {
        synchronized(lock) {
            if (!foreground) return
            val now = nowMs()
            account(now - segmentStartMs)
            segmentStartMs = now
        }
    }

    private fun transitionTo(fg: Boolean) {
        synchronized(lock) {
            val now = nowMs()
            if (foreground) account(now - segmentStartMs)
            foreground = fg
            segmentStartMs = now
        }
    }

    private fun account(elapsedMs: Long) {
        if (elapsedMs > 0) accountant.add(UsageKeys.APP_FG_MS, elapsedMs)
    }
}
