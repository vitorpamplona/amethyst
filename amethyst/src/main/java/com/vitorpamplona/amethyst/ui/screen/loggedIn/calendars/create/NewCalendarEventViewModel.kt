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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.create

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import com.vitorpamplona.quartz.nip52Calendar.appt.day.image as dayImage
import com.vitorpamplona.quartz.nip52Calendar.appt.day.locations as dayLocations
import com.vitorpamplona.quartz.nip52Calendar.appt.day.summary as daySummary
import com.vitorpamplona.quartz.nip52Calendar.appt.time.image as timeImage
import com.vitorpamplona.quartz.nip52Calendar.appt.time.locations as timeLocations
import com.vitorpamplona.quartz.nip52Calendar.appt.time.summary as timeSummary

class NewCalendarEventViewModel : ViewModel() {
    private lateinit var account: Account

    val isAllDay = mutableStateOf(false)
    val title = mutableStateOf("")
    val summary = mutableStateOf("")
    val location = mutableStateOf("")
    val imageUrl = mutableStateOf("")
    val hashtags = mutableStateOf("") // comma-separated

    /** Start instant in epoch seconds. 0 means unset; the create screen guards against publishing without a real value. */
    val startSeconds = mutableStateOf(0L)
    val endSeconds = mutableStateOf(0L)

    val isPublishing = mutableStateOf(false)

    fun init(accountViewModel: AccountViewModel) {
        this.account = accountViewModel.account
    }

    fun isValid(): Boolean = title.value.isNotBlank() && startSeconds.value > 0L

    fun isEndAfterStart(): Boolean = endSeconds.value == 0L || endSeconds.value >= startSeconds.value

    suspend fun publish(): Boolean {
        if (!isValid() || !isEndAfterStart()) return false
        isPublishing.value = true
        try {
            val parsedHashtags =
                hashtags.value
                    .split(',', '\n', ' ')
                    .map { it.trim().trimStart('#') }
                    .filter { it.isNotBlank() }
            val parsedSummary = summary.value.trim().takeIf { it.isNotBlank() }
            val parsedImage = imageUrl.value.trim().takeIf { it.isNotBlank() }
            val parsedLocation = location.value.trim().takeIf { it.isNotBlank() }
            val tzId = TimeZone.getDefault().id

            if (isAllDay.value) {
                account.signAndComputeBroadcast(
                    CalendarDateSlotEvent.build(
                        title = title.value.trim(),
                        start = toIsoDate(startSeconds.value),
                        end = endSeconds.value.takeIf { it > 0L }?.let { toIsoDate(it) },
                        content = parsedSummary.orEmpty(),
                    ) {
                        parsedSummary?.let { daySummary(it) }
                        parsedImage?.let { dayImage(it) }
                        parsedLocation?.let { dayLocations(listOf(it)) }
                        if (parsedHashtags.isNotEmpty()) hashtags(parsedHashtags)
                    },
                )
            } else {
                account.signAndComputeBroadcast(
                    CalendarTimeSlotEvent.build(
                        title = title.value.trim(),
                        start = startSeconds.value,
                        end = endSeconds.value.takeIf { it > 0L },
                        startTzId = tzId,
                        endTzId = tzId,
                        content = parsedSummary.orEmpty(),
                    ) {
                        parsedSummary?.let { timeSummary(it) }
                        parsedImage?.let { timeImage(it) }
                        parsedLocation?.let { timeLocations(listOf(it)) }
                        if (parsedHashtags.isNotEmpty()) hashtags(parsedHashtags)
                    },
                )
            }
            return true
        } finally {
            isPublishing.value = false
        }
    }
}

private val IsoFormat =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        // 31922 uses calendar-date strings; format the user's local date.
        timeZone = TimeZone.getTimeZone(ZoneId.systemDefault())
    }

private fun toIsoDate(epochSeconds: Long): String = IsoFormat.format(java.util.Date(epochSeconds * 1000))
