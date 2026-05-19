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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.appointmentView
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.TimeUnit

/**
 * Periodic scan that posts "starting soon" notifications for appointments the user has RSVP'd
 * to as ACCEPTED.
 *
 * The work is bounded — scans LocalCache (which is bounded by the relay subscription) and
 * consults [CalendarReminderStore] to skip events that have already been notified for. Run as
 * a 15-minute periodic worker: that's the WorkManager minimum and matches the resolution of
 * the reminder UI ("starts in ~15 min" is the smallest interval users perceive as "soon").
 */
class CalendarReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = CalendarReminderPrefs(applicationContext)
        if (!prefs.isEnabled()) {
            Log.d(TAG) { "Reminders disabled; skipping scan." }
            return Result.success()
        }
        val now = TimeUtils.now()
        val windowEnd = now + prefs.leadMinutes() * 60L
        val store = CalendarReminderStore(applicationContext)

        // Walk every kind-31925 RSVP authored by an account on this device. We don't have a
        // multi-account "all logged-in pubkeys" view here, so we accept any RSVP that's
        // present in cache — the alternative (looking only at the foreground account) would
        // silently break notifications for account switching during the lead window.
        val acceptedRsvps =
            LocalCache.addressables
                .filterIntoSet { _, note ->
                    val e = note.event
                    e is CalendarRSVPEvent && e.status() == RSVPStatusTag.STATUS.ACCEPTED
                }.mapNotNull { it.event as? CalendarRSVPEvent }

        Log.d(TAG) { "Worker scanning ${acceptedRsvps.size} accepted RSVPs (now=$now, lead=${prefs.leadMinutes()}m)" }

        acceptedRsvps.forEach { rsvp ->
            val targetAddress = rsvp.calendarEventAddress() ?: return@forEach
            val targetNote = LocalCache.addressables.get(targetAddress) ?: return@forEach
            val view = targetNote.appointmentView() ?: return@forEach
            val start = view.startSeconds ?: return@forEach
            val eventId =
                (targetNote.event as? CalendarTimeSlotEvent)?.id
                    ?: (targetNote.event as? CalendarDateSlotEvent)?.id
                    ?: return@forEach

            if (start !in now..windowEnd) return@forEach
            // Keyed on (eventId, start) so a moved appointment re-fires when the new start
            // enters the lead window — the old notification stays valid in the system tray.
            if (store.wasNotified(eventId, start)) return@forEach

            val title = view.title ?: stringRes(applicationContext, R.string.calendar_reminder_default_title)
            val minutesAway = ((start - now).coerceAtLeast(0L) / 60L).toInt()
            val body =
                stringRes(
                    applicationContext,
                    R.string.calendar_reminder_body,
                    minutesAway,
                )
            val deepLink =
                "nostr:" +
                    com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
                        .create(targetAddress.kind, targetAddress.pubKeyHex, targetAddress.dTag, null)
            CalendarReminderNotifier.notifyReminder(applicationContext, eventId, title, body, deepLink)
            store.markNotified(eventId, start)
            Log.d(TAG) { "Notified $eventId (starts in ${minutesAway}m)" }
        }

        // Prune entries for events that ended more than a day ago — they can't fire again.
        store.forgetBefore(now - PRUNE_AGE_SECONDS)
        return Result.success()
    }

    companion object {
        private const val TAG = "CalendarReminderWorker"
        private const val WORK_NAME = "calendar_reminder_worker"

        // Don't bother remembering "I notified for this" entries for events whose start was
        // more than a day ago; they can't fire again so the entry is pure overhead.
        private const val PRUNE_AGE_SECONDS = 24L * 60L * 60L

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<CalendarReminderWorker>(15, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG) { "schedule(): enqueueUniquePeriodicWork($WORK_NAME, 15 MIN, KEEP)" }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
