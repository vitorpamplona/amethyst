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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Collapses a burst of edits into a single [publish], run once the edits stop.
 *
 * Superseding a still-pending publish cancels it. That is safe for a whole-state publish — one that
 * reads the current state when it runs rather than carrying a payload — because the replacement
 * carries the same state or newer; it is wrong for anything that publishes a delta.
 *
 * [launch] is injected rather than a [kotlinx.coroutines.CoroutineScope] so the caller keeps its own
 * error handling (for [AccountViewModel], the signer-exception toasts of `launchSigner`), and so the
 * semantics here can be unit-tested on a test scope.
 */
class DebouncedPublisher(
    private val debounceMs: Long,
    private val launch: (suspend () -> Unit) -> Job,
    private val publish: suspend () -> Unit,
) {
    private var pending: Job? = null

    /** True while an edit is waiting out the debounce, or its publish is still in flight. */
    fun isPending(): Boolean = pending?.isActive == true

    /** Records an edit: restarts the wait, so a run of edits publishes once, after the last one. */
    fun schedule() = start(debounceMs)

    /**
     * Publishes a pending edit now instead of waiting out the debounce. A no-op when nothing is
     * pending, so a caller can flush on every exit path without publishing the same state twice.
     */
    fun flush() {
        if (!isPending()) return
        start(0)
    }

    /** Abandons a pending edit without publishing. For a caller that will publish it another way. */
    fun cancel() {
        pending?.cancel()
        pending = null
    }

    private fun start(delayMs: Long) {
        pending?.cancel()
        pending =
            launch {
                delay(delayMs)
                publish()
            }
    }
}
