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
package com.vitorpamplona.amethyst.ios.ui.calendar

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent

/**
 * Display data for a calendar event card (NIP-52, kind 31922/31923).
 */
data class CalendarEventDisplayData(
    val id: String,
    val addressId: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val title: String,
    val summary: String? = null,
    val description: String = "",
    val location: String? = null,
    val image: String? = null,
    /** Epoch seconds for time-slot events; null for date-slot. */
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    /** ISO date string for date-slot events (e.g. "2025-04-15"). */
    val startDate: String? = null,
    val endDate: String? = null,
    val startTzId: String? = null,
    val endTzId: String? = null,
    val isDateBased: Boolean = false,
    val participantCount: Int = 0,
    val rsvpCount: Int = 0,
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a CalendarTimeSlotEvent to display data.
 */
fun Note.toCalendarEventDisplayData(cache: IosLocalCache? = null): CalendarEventDisplayData? {
    val event = this.event ?: return null

    return when (event) {
        is CalendarTimeSlotEvent -> {
            val user = cache?.getUserIfExists(event.pubKey)
            val displayName = resolveDisplayName(event.pubKey, user)
            val pictureUrl = user?.profilePicture()

            CalendarEventDisplayData(
                id = event.id,
                addressId = event.addressTag(),
                pubKeyHex = event.pubKey,
                pubKeyDisplay = displayName,
                profilePictureUrl = pictureUrl,
                title = event.title() ?: "Untitled Event",
                summary = event.summary(),
                description = event.content,
                location = event.location(),
                image = event.image(),
                startTimestamp = event.start(),
                endTimestamp = event.end(),
                startTzId = event.startTzId(),
                endTzId = event.endTzId(),
                isDateBased = false,
                participantCount = event.participants().size,
                rsvpCount = replies.size,
                createdAt = event.createdAt,
            )
        }

        is CalendarDateSlotEvent -> {
            val user = cache?.getUserIfExists(event.pubKey)
            val displayName = resolveDisplayName(event.pubKey, user)
            val pictureUrl = user?.profilePicture()

            CalendarEventDisplayData(
                id = event.id,
                addressId = event.addressTag(),
                pubKeyHex = event.pubKey,
                pubKeyDisplay = displayName,
                profilePictureUrl = pictureUrl,
                title = event.title() ?: "Untitled Event",
                summary = event.summary(),
                description = event.content,
                location = event.location(),
                image = event.image(),
                startDate = event.start(),
                endDate = event.end(),
                isDateBased = true,
                participantCount = event.participants().size,
                rsvpCount = replies.size,
                createdAt = event.createdAt,
            )
        }

        else -> {
            null
        }
    }
}

private fun resolveDisplayName(
    pubKey: String,
    user: com.vitorpamplona.amethyst.commons.model.User?,
): String =
    user?.toBestDisplayName()
        ?: try {
            pubKey.hexToByteArrayOrNull()?.toNpub() ?: pubKey.take(16) + "..."
        } catch (e: Exception) {
            pubKey.take(16) + "..."
        }
