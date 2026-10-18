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
package com.vitorpamplona.amethyst.commons.model.nip52Calendar

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderLogStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/** An appointment the user is attending that starts within the reminder lead time. */
class DueCalendarReminder(
    val eventId: HexKey,
    /** The appointment's own title; the platform words a default when it has none. */
    val title: String?,
    val minutesAway: Int,
    /** `nostr:naddr…` of the appointment, for a tap to open it. */
    val deepLink: String,
)

/**
 * Finds the "starting soon" reminders for appointments the user RSVP'd ACCEPTED to. Each platform
 * runs [scan] on its own clock (Android's periodic worker, the desktop's timer) and posts what it
 * returns through its own notifier.
 */
object CalendarReminderScanner {
    // Don't bother remembering "I notified for this" entries for events whose start was
    // more than a day ago; they can't fire again so the entry is pure overhead.
    private const val PRUNE_AGE_SECONDS = 24L * 60L * 60L

    /**
     * Every ACCEPTED kind-31925 RSVP currently present in LocalCache. Any RSVP in the cache counts,
     * not only the foreground account's: looking at one account alone would silently drop the
     * reminders of the others when the user switches accounts during the lead window.
     */
    fun acceptedRsvpsInCache(): List<CalendarRSVPEvent> =
        LocalCache.addressables
            .filterIntoSet { _, note ->
                val e = note.event
                e is CalendarRSVPEvent && e.status() == RSVPStatusTag.STATUS.ACCEPTED
            }.mapNotNull { it.event as? CalendarRSVPEvent }

    /**
     * True while at least one accepted RSVP could still produce a reminder: its target event
     * either starts in the future, or hasn't been fetched yet (start unknown, so the scan must
     * keep running until the target resolves). False means nothing could ever fire.
     */
    fun couldStillFire(
        rsvps: Collection<CalendarRSVPEvent>,
        now: Long,
    ): Boolean =
        rsvps.any { rsvp ->
            val targetAddress = rsvp.calendarEventAddress() ?: return@any false
            val start =
                LocalCache.addressables
                    .get(targetAddress)
                    ?.appointmentView()
                    ?.startSeconds
            start == null || start > now
        }

    /**
     * Calls [notify] once for every accepted appointment that starts between [now] and
     * [now] + [leadMinutes] and has not been notified for at that start time, records it in [log],
     * and prunes the log of events that ended long ago. Keyed on (event id, start), so a moved
     * appointment fires again when its new start enters the window.
     */
    suspend fun scan(
        leadMinutes: Int,
        log: CalendarReminderLogStore,
        now: Long,
        notify: suspend (DueCalendarReminder) -> Unit,
    ) {
        val windowEnd = now + leadMinutes * 60L

        acceptedRsvpsInCache().forEach { rsvp ->
            val targetAddress = rsvp.calendarEventAddress() ?: return@forEach
            val targetNote = LocalCache.addressables.get(targetAddress) ?: return@forEach
            val view = targetNote.appointmentView() ?: return@forEach
            val start = view.startSeconds ?: return@forEach
            val eventId =
                (targetNote.event as? CalendarTimeSlotEvent)?.id
                    ?: (targetNote.event as? CalendarDateSlotEvent)?.id
                    ?: return@forEach

            if (start !in now..windowEnd) return@forEach
            if (log.wasNotified(eventId, start)) return@forEach

            notify(
                DueCalendarReminder(
                    eventId = eventId,
                    title = view.title,
                    minutesAway = ((start - now).coerceAtLeast(0L) / 60L).toInt(),
                    deepLink = "nostr:" + NAddress.create(targetAddress.kind, targetAddress.pubKeyHex, targetAddress.dTag, null),
                ),
            )
            log.markNotified(eventId, start)
        }

        log.forgetBefore(now - PRUNE_AGE_SECONDS)
    }
}
