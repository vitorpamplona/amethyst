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
 * Device-wide preferences for the calendar reminder worker.
 *
 * Stored at device scope (rather than per-account) because the worker that consults them runs
 * globally — multiplexing per-account preferences would require account-context plumbing into
 * WorkManager that the rest of the app doesn't have. A user who flips between two accounts on
 * the same device shares the same lead-time and enabled-state. Per-account preferences could be
 * a follow-up if anyone asks.
 */
class CalendarReminderPrefs(
    context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun leadMinutes(): Int = prefs.getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES)

    fun setLeadMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_LEAD_MINUTES, minutes).apply()
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 15
        const val DEFAULT_ENABLED = true

        // Choices presented in the settings UI. Anchored to the worker cadence — lead times
        // smaller than the cadence (15 min) can't be honoured reliably; 60 is the largest the
        // UX shape supports without an extra "hours" picker.
        val LEAD_TIME_CHOICES = listOf(5, 15, 30, 60)

        private const val PREF_NAME = "amethyst_calendar_reminder_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEAD_MINUTES = "lead_minutes"
    }
}
