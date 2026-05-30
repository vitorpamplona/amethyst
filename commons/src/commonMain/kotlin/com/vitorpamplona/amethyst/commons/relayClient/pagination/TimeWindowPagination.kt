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
package com.vitorpamplona.amethyst.commons.relayClient.pagination

import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Tracks how far back in time a relay subscription should reach.
 *
 * Boot opens a small window (recent-first) so a screen becomes usable before the
 * whole history is fetched and decrypted. Each [loadMore] widens the floor backward
 * so scrolling pulls older history on demand.
 *
 * Only the lower bound ([since]) moves: the subscription stays open so new events
 * keep streaming live regardless of the window. The floor is requested in full on
 * every assembly (the value is small and bounded), which keeps the window robust
 * even if the in-memory note store evicts previously-loaded events under memory
 * pressure.
 */
class TimeWindowPagination(
    private val initialWindow: Long = ONE_WEEK_IN_SECONDS,
    private val step: Long = ONE_WEEK_IN_SECONDS,
) {
    /** Epoch seconds; events older than this are not requested from relays. */
    @Volatile
    var since: Long = TimeUtils.now() - initialWindow
        private set

    /** Widens the window backward by one [step]. */
    fun loadMore() {
        since -= step
    }

    /** Resets the window back to the initial boot size, anchored at the current time. */
    fun reset() {
        since = TimeUtils.now() - initialWindow
    }

    companion object {
        const val ONE_WEEK_IN_SECONDS = TimeUtils.ONE_WEEK.toLong()
    }
}
