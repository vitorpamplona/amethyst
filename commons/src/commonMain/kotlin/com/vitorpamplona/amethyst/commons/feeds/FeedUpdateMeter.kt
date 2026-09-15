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
package com.vitorpamplona.amethyst.commons.feeds

/**
 * What one feed's reaction to an incoming event bundle actually accomplished.
 *
 * Every arriving bundle is fanned out to every [FeedContentState] the account
 * owns — on Android that is ~48 of them, whichever screen is showing and
 * whether or not the app is visible at all. This enum is how we find out what
 * that fan-out buys: see `amethyst/plans/2026-09-14-cpu-battery-diagnosis.md`,
 * hypothesis H1.
 */
enum class FeedUpdateOutcome {
    /**
     * The filter matched nothing and nothing was deleted, so the feed provably
     * could not change and no list work was done. The cheap case — and if it
     * dominates, the fan-out itself is the thing to fix.
     */
    SKIPPED,

    /** The visible list changed. The only outcome a user could ever observe. */
    CHANGED,

    /**
     * Real list work ran (merge, de-duplicate, sort, compare) and produced a
     * list identical to the one already on screen. Wasted, but not trivially
     * detectable in advance the way [SKIPPED] is.
     */
    UNCHANGED,

    /**
     * The additive path did not apply, so the feed was rebuilt from scratch —
     * a full scan of the event cache. By far the most expensive outcome; a
     * feed that has never been opened takes this branch on its first bundle.
     */
    REBUILT,
}

/**
 * Ledger hook for [FeedContentState], in the shape `LocalCache.verifyMeter`
 * uses: a nullable global the Android app assigns at startup and every other
 * front end (desktop, CLI) leaves null, where it costs one null check.
 *
 * It lives here rather than as a constructor parameter because feed states are
 * built in dozens of places and this is a diagnostic, not a dependency.
 */
interface FeedUpdateMeter {
    /** One feed's reaction to one bundle. Called ~48 times per bundle on Android. */
    fun onFeedUpdate(outcome: FeedUpdateOutcome)

    /**
     * One whole fan-out: a bundle of [noteCount] notes handed to every feed,
     * taking [elapsedNanos].
     *
     * Timed here, around the whole loop, rather than per feed — see
     * `UsageKeys.feedsFanoutUs` for why (the ledger lives in the Android module).
     */
    fun onBundleFanOut(
        noteCount: Int,
        elapsedNanos: Long,
    )

    companion object {
        @Volatile
        var instance: FeedUpdateMeter? = null
    }
}
