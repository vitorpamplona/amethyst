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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.parseIsoDateToUnixSeconds
import com.vitorpamplona.quartz.nip01Core.core.Address
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

    /**
     * When non-null, [publish] preserves this d-tag and kind so the broadcast replaces an
     * existing addressable appointment instead of minting a new one. The UI also locks the
     * all-day toggle in edit mode — switching kinds mid-edit would leave a stale event under
     * the original kind/d-tag combination.
     */
    private var editAddress: Address? = null

    val isEditing: Boolean
        get() = editAddress != null

    fun init(accountViewModel: AccountViewModel) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
    }

    /**
     * Pre-populate from an existing appointment for edit mode. Idempotent: a recomposition that
     * calls this again is a no-op. Only the author of the appointment should reach this path —
     * the screen guards via UI affordance, but [publish] will also produce an unsigned event if
     * the current account doesn't own the address.
     */
    fun loadForEdit(
        accountViewModel: AccountViewModel,
        kind: Int,
        pubKeyHex: String,
        dTag: String,
    ) {
        init(accountViewModel)
        if (editAddress != null) return // already loaded

        val address = Address(kind, pubKeyHex, dTag)
        val existing = LocalCache.addressables.get(address)?.event ?: return
        editAddress = address

        when (existing) {
            is CalendarTimeSlotEvent -> {
                isAllDay.value = false
                title.value = existing.title().orEmpty()
                summary.value = existing.summary().orEmpty().ifBlank { existing.content }
                location.value = existing.location().orEmpty()
                imageUrl.value = existing.image().orEmpty()
                hashtags.value = existing.hashtags().joinToString(", ")
                startSeconds.value = existing.start() ?: 0L
                endSeconds.value = existing.end() ?: 0L
            }
            is CalendarDateSlotEvent -> {
                isAllDay.value = true
                title.value = existing.title().orEmpty()
                summary.value = existing.summary().orEmpty().ifBlank { existing.content }
                location.value = existing.location().orEmpty()
                imageUrl.value = existing.image().orEmpty()
                hashtags.value = existing.hashtags().joinToString(", ")
                startSeconds.value = parseIsoDateToUnixSeconds(existing.start()) ?: 0L
                endSeconds.value = parseIsoDateToUnixSeconds(existing.end()) ?: 0L
            }
        }
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
            val targetDTag = editAddress?.dTag

            if (isAllDay.value) {
                account.signAndComputeBroadcast(
                    if (targetDTag != null) {
                        CalendarDateSlotEvent.build(
                            title = title.value.trim(),
                            start = toIsoDate(startSeconds.value),
                            end = endSeconds.value.takeIf { it > 0L }?.let { toIsoDate(it) },
                            content = parsedSummary.orEmpty(),
                            dTag = targetDTag,
                        ) {
                            parsedSummary?.let { daySummary(it) }
                            parsedImage?.let { dayImage(it) }
                            parsedLocation?.let { dayLocations(listOf(it)) }
                            if (parsedHashtags.isNotEmpty()) hashtags(parsedHashtags)
                        }
                    } else {
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
                        }
                    },
                )
            } else {
                account.signAndComputeBroadcast(
                    if (targetDTag != null) {
                        CalendarTimeSlotEvent.build(
                            title = title.value.trim(),
                            start = startSeconds.value,
                            end = endSeconds.value.takeIf { it > 0L },
                            startTzId = tzId,
                            endTzId = tzId,
                            content = parsedSummary.orEmpty(),
                            dTag = targetDTag,
                        ) {
                            parsedSummary?.let { timeSummary(it) }
                            parsedImage?.let { timeImage(it) }
                            parsedLocation?.let { timeLocations(listOf(it)) }
                            if (parsedHashtags.isNotEmpty()) hashtags(parsedHashtags)
                        }
                    } else {
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
                        }
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
