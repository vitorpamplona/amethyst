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
package com.vitorpamplona.amethyst.service.workouts.health

import android.content.Context

/**
 * Remembers which Health Connect workouts the user has already handled (accepted
 * the suggestion or dismissed it), per account, so a session is offered once and
 * the suggestion survives process death. Stored in a small private
 * SharedPreferences file keyed by npub.
 */
class HealthConnectStore(
    private val context: Context,
) {
    // Lazy so constructing the store touches no disk; the first getSharedPreferences (which
    // hits the filesystem) happens on the IO dispatcher from the callers below, not in
    // composition on the main thread.
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun handledIds(npub: String): Set<String> = prefs.getStringSet(key(npub), emptySet()) ?: emptySet()

    fun markHandled(
        npub: String,
        workoutId: String,
    ) {
        val current = handledIds(npub).toMutableSet()
        current.add(workoutId)
        // Cap the set so it cannot grow without bound; HC ids are opaque so we
        // simply keep the most recent MAX_IDS by dropping arbitrary extras.
        val capped = if (current.size > MAX_IDS) current.toList().takeLast(MAX_IDS).toSet() else current
        prefs.edit().putStringSet(key(npub), capped).apply()
    }

    private fun key(npub: String) = "handled_$npub"

    companion object {
        private const val PREFS_NAME = "health_connect_workouts"
        private const val MAX_IDS = 500

        /** How far back the foreground scan looks for workouts to suggest. */
        const val LOOKBACK_DAYS = 7L

        /** Cap on suggestions surfaced at once, newest first. */
        const val MAX_SUGGESTIONS = 10
    }
}
