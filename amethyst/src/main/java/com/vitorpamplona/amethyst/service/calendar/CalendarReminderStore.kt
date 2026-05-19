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
package com.vitorpamplona.amethyst.service.calendar

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent "I've already notified for this event" set. Backed by [SharedPreferences] because
 * the worker that consults it runs in the app process and the dataset is tiny (≤ a few hundred
 * IDs at most). Without persistence, every worker run after a restart would re-notify for the
 * same upcoming event until it started, since LocalCache has no memory of past reminders.
 *
 * Keys are event ids (the 32-byte hex from a 31922/31923 appointment). Values aren't used; only
 * presence in the set matters. Entries are pruned by [forgetBefore] when the worker has just
 * fired so the store doesn't grow unbounded over time.
 */
class CalendarReminderStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun wasNotified(eventId: String): Boolean = prefs.contains(keyFor(eventId))

    fun markNotified(
        eventId: String,
        eventStartSeconds: Long,
    ) {
        prefs.edit().putLong(keyFor(eventId), eventStartSeconds).apply()
    }

    /**
     * Drops any entry whose recorded event-start time is older than [cutoffSeconds]. Called
     * after each worker run so the store stays bounded — events that have long since ended
     * can't fire a second reminder, so their entries are dead weight.
     */
    fun forgetBefore(cutoffSeconds: Long) {
        val editor = prefs.edit()
        var changed = false
        prefs.all.forEach { (key, value) ->
            if (value is Long && value < cutoffSeconds) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    companion object {
        private const val PREF_NAME = "amethyst_calendar_reminders"
        private const val KEY_PREFIX = "notified:"

        private fun keyFor(eventId: String) = KEY_PREFIX + eventId
    }
}
