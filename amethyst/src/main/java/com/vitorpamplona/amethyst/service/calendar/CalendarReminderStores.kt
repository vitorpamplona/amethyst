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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderLogStore
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderSettings
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.CopyOnceMigration

/**
 * Android wiring for the two calendar-reminder stores.
 *
 * The store classes live in commons; only the file location and the one-off
 * lift out of the legacy SharedPreferences are Android's business.
 */
private const val LEGACY_SETTINGS_FILE = "amethyst_calendar_reminder_prefs"
private const val LEGACY_LOG_FILE = "amethyst_calendar_reminders"

private val Context.calendarReminderSettingsData by preferencesDataStore(
    name = "calendar_reminder_settings",
    produceMigrations = { context ->
        listOf(
            CopyOnceMigration("migrated.calendarReminderSettings") { out ->
                val legacy = context.getSharedPreferences(LEGACY_SETTINGS_FILE, Context.MODE_PRIVATE)
                if (legacy.contains("enabled")) {
                    out[booleanPreferencesKey("enabled")] = legacy.getBoolean("enabled", CalendarReminderSettings.DEFAULT_ENABLED)
                }
                if (legacy.contains("lead_minutes")) {
                    out[intPreferencesKey("lead_minutes")] = legacy.getInt("lead_minutes", CalendarReminderSettings.DEFAULT_LEAD_MINUTES)
                }
            },
        )
    },
)

private val Context.calendarReminderLogData by preferencesDataStore(
    name = "calendar_reminder_log",
    produceMigrations = { context ->
        listOf(
            CopyOnceMigration("migrated.calendarReminderLog") { out ->
                val legacy = context.getSharedPreferences(LEGACY_LOG_FILE, Context.MODE_PRIVATE)
                // Values are the event-start times the reminders fired for; anything
                // else in the file is not ours and is left behind.
                legacy.all.forEach { (key, value) ->
                    if (value is Long) out[CalendarReminderLogStore.keyFor(key.removePrefix("notified:"))] = value
                }
            },
        )
    },
)

fun Context.calendarReminderSettings() = CalendarReminderSettingsStore(calendarReminderSettingsData)

fun Context.calendarReminderLog() = CalendarReminderLogStore(calendarReminderLogData)
