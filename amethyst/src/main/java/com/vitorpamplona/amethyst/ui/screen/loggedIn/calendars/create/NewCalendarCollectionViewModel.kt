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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTags
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Lightweight projection of a calendar appointment authored by the current user, used to power
 * the multi-select picker on the collection editor.
 */
@Immutable
data class OwnedAppointmentSummary(
    val address: Address,
    val title: String,
    val startSeconds: Long?,
    val isAllDay: Boolean,
)

class NewCalendarCollectionViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val description = mutableStateOf("")
    val isPublishing = mutableStateOf(false)

    /** Stable d-tag for the addressable: random for create, preserved when editing. */
    private var dTag: String? = null

    /** The full original event in edit mode; needed to publish a NIP-09 deletion. */
    private var loadedEvent: com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent? = null

    val selectedAddresses = mutableStateListOf<Address>()
    val availableAppointments = mutableStateOf<List<OwnedAppointmentSummary>>(emptyList())

    val isEditing: Boolean
        get() = dTag != null

    fun init(
        accountViewModel: AccountViewModel,
        editDTag: String?,
    ) {
        if (::account.isInitialized) return // idempotent across recompositions
        this.account = accountViewModel.account
        dTag = editDTag

        editDTag?.let { existingDTag ->
            val existingAddress = Address(CalendarEvent.KIND, account.userProfile().pubkeyHex, existingDTag)
            val existingNote = LocalCache.addressables.get(existingAddress)
            (existingNote?.event as? CalendarEvent)?.let { existing ->
                loadedEvent = existing
                title.value = existing.title().orEmpty()
                description.value = existing.content
                selectedAddresses.addAll(existing.calendarEventAddresses())
            }
        }

        availableAppointments.value = loadOwnedAppointments()
    }

    fun toggle(address: Address) {
        if (selectedAddresses.remove(address)) return
        selectedAddresses.add(address)
    }

    /**
     * Publishes a NIP-09 deletion event for the loaded calendar. No-op when called outside
     * edit mode (we wouldn't have a target to delete). Returns true when the deletion was
     * dispatched so the caller can pop back.
     */
    suspend fun deleteLoaded(): Boolean {
        val target = loadedEvent ?: return false
        account.delete(target, emptySet())
        return true
    }

    fun isValid(): Boolean = title.value.isNotBlank()

    suspend fun publish(): Boolean {
        if (!isValid()) return false
        isPublishing.value = true
        try {
            val effectiveDTag = dTag
            val selected = selectedAddresses.toList()
            val parsedTitle = title.value.trim()
            val parsedDescription = description.value.trim()

            account.signAndComputeBroadcast(
                if (effectiveDTag != null) {
                    CalendarEvent.build(
                        title = parsedTitle,
                        content = parsedDescription,
                        dTag = effectiveDTag,
                    ) {
                        if (selected.isNotEmpty()) aTags(selected.map { ATag(it) })
                    }
                } else {
                    CalendarEvent.build(
                        title = parsedTitle,
                        content = parsedDescription,
                    ) {
                        if (selected.isNotEmpty()) aTags(selected.map { ATag(it) })
                    }
                },
            )
            return true
        } finally {
            isPublishing.value = false
        }
    }

    private fun loadOwnedAppointments(): List<OwnedAppointmentSummary> {
        val mePubKey = account.userProfile().pubkeyHex
        val results =
            LocalCache.notes
                .filterIntoSet { _, note ->
                    val e = note.event
                    (e is CalendarTimeSlotEvent || e is CalendarDateSlotEvent) && e.pubKey == mePubKey
                }.mapNotNull { note ->
                    when (val e = note.event) {
                        is CalendarTimeSlotEvent ->
                            OwnedAppointmentSummary(
                                address = e.address(),
                                // `title` may be empty; the picker row substitutes a localised
                                // "(untitled)" string at render time — the VM stays string-free.
                                title = e.title().orEmpty(),
                                startSeconds = e.start(),
                                isAllDay = false,
                            )
                        is CalendarDateSlotEvent ->
                            OwnedAppointmentSummary(
                                address = e.address(),
                                // `title` may be empty; the picker row substitutes a localised
                                // "(untitled)" string at render time — the VM stays string-free.
                                title = e.title().orEmpty(),
                                // Date-only events don't have an instant; null sorts last in the
                                // upcoming-first comparator below.
                                startSeconds = null,
                                isAllDay = true,
                            )
                        else -> null
                    }
                }
        // Upcoming events first (closest start), then date-only/past — same intent as the
        // main feed's UpcomingFirst ordering, simplified for the picker context.
        val now = TimeUtils.now()
        return results.sortedWith(
            compareBy(
                { if (it.startSeconds == null || it.startSeconds >= now) 0 else 1 },
                { it.startSeconds ?: Long.MAX_VALUE },
            ),
        )
    }
}
