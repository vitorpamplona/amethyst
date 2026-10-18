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
package com.vitorpamplona.amethyst.desktop.service.calendar

import com.vitorpamplona.amethyst.commons.model.nip52Calendar.CalendarReminderScanner
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.CALENDAR_REMINDER_LOG_STORE
import com.vitorpamplona.amethyst.commons.model.preferences.CALENDAR_REMINDER_SETTINGS_STORE
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderLogStore
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderSettingsStore
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotifKind
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSpec
import com.vitorpamplona.amethyst.commons.moderation.notifications.SendResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_body
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_default_title
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.desktop.app.DesktopNotifications
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Starting soon" notifications for appointments the user RSVP'd ACCEPTED to: the shared
 * [CalendarReminderScanner], run once a minute while the app is open, and posted as OS
 * notifications. Android runs the same scan from a periodic worker.
 *
 * Fires only while both switches are on: the reminder switch in the calendar settings, and the
 * desktop's OS-notifications switch, which owns the system permission. Do-not-disturb skips the
 * scan rather than the notification, so a reminder still due when it ends goes out then.
 */
class DesktopCalendarReminders(
    private val appStores: AppPreferenceStores,
    private val notifications: DesktopNotifications,
    // The accounts loaded on this computer: their RSVPs, not their follows'.
    private val accounts: () -> Set<HexKey>,
    private val scope: CoroutineScope,
) {
    private val settings by lazy { CalendarReminderSettingsStore(appStores.getDataStore(CALENDAR_REMINDER_SETTINGS_STORE)) }
    private val log by lazy { CalendarReminderLogStore(appStores.getDataStore(CALENDAR_REMINDER_LOG_STORE)) }

    // The timer and a scan asked for by the settings switch must not post the same reminder twice.
    private val scanning = Mutex()

    fun start() {
        scope.launch {
            while (isActive) {
                scanNow()
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    /**
     * One pass now, as when the user turns reminders on. The settings screen saves the switch in a
     * coroutine of its own, so wait (briefly) for the saved value before scanning.
     */
    fun requestScan() {
        scope.launch {
            withTimeoutOrNull(SETTINGS_WAIT_MS) { settings.flow.first { it.enabled } }
            scanNow()
        }
    }

    private suspend fun scanNow() {
        try {
            scanning.withLock {
                val notificationSettings = notifications.settings
                if (!notificationSettings.enabled.value) return
                val now = TimeUtils.now()
                if (notificationSettings.dnd.value.isActive(now)) return
                val reminderSettings = settings.load()
                if (!reminderSettings.enabled) return

                CalendarReminderScanner.scan(accounts(), reminderSettings.leadMinutes, log, now) { due ->
                    val title = due.title ?: loadStringRes(Res.string.calendar_reminder_default_title)
                    val body = loadPluralStringRes(Res.plurals.calendar_reminder_body, due.minutesAway, due.minutesAway)
                    val result =
                        notifications.dispatcher.send(
                            NotificationSpec(
                                title = title,
                                body = body,
                                kind = NotifKind.CALENDAR,
                                threadId = THREAD_ID,
                                deepLinkNoteId = due.eventId,
                            ),
                        )
                    // Not posted: leave it unmarked, so the next scan tries again while it is due.
                    if (result is SendResult.Failed) Log.w(TAG, "Could not post the reminder for ${due.eventId}", result.error)
                    result !is SendResult.Failed
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Calendar reminder scan failed", e)
        }
    }

    companion object {
        private const val TAG = "DesktopCalendarReminders"
        private const val THREAD_ID = "calendar_reminders"

        // The reminder screen offers lead times in minutes, so a minute is the resolution worth having.
        private const val SCAN_INTERVAL_MS = 60_000L
        private const val SETTINGS_WAIT_MS = 2_000L
    }
}
