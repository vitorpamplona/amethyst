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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarsViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.CalendarAppointmentsFeedFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The model's cache-backed flows: the calendar picker's list, and the membership filter whose
 * "not loaded yet" answer used to be an empty set — which reads as "match nothing" and blanked
 * every lens until the kind-31924 arrived.
 *
 * `LocalCache` is a process-wide object and JUnit's method order is hash-based, so every method
 * here uses its own author key and asserts only over that key's events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarsViewModelFlowTest {
    // viewModelScope is Dispatchers.Main, and every flow below is a WhileSubscribed stateIn —
    // nothing runs until something subscribes. The SAME dispatcher drives Main and the test body,
    // so the model's coroutines and the test share one scheduler; two schedulers would leave the
    // collectors parked and every assertion reading the initial value.
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun calendar(
        id: String,
        pubKey: String,
        dTag: String,
        title: String,
        members: List<Address> = emptyList(),
    ) = CalendarEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags =
            (
                listOf(arrayOf("d", dTag), arrayOf("title", title)) +
                    members.map { arrayOf("a", it.toValue()) }
            ).toTypedArray(),
        content = "",
        sig = "sig",
    )

    private fun appointment(
        id: String,
        pubKey: String,
        dTag: String,
    ) = CalendarTimeSlotEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags =
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("title", "Standup"),
                arrayOf("start", "1800000000"),
            ),
        content = "",
        sig = "sig",
    )

    private fun boundModel(
        scope: CoroutineScope,
        pubKey: String,
    ): CalendarsViewModel {
        val feed = FeedContentState(CalendarAppointmentsFeedFilter(seeEverythingAccount()), scope, LocalCache)
        return CalendarsViewModel().also { it.init(pubKey, feed) }
    }

    @Test
    fun theCalendarPickerListsThisAccountsCalendars() =
        runTest(mainDispatcher) {
            val mine = "f1".repeat(32)
            val someoneElse = "f2".repeat(32)
            LocalCache.justConsumeMyOwnEvent(calendar("f3".repeat(32), mine, "work", "Work"))
            LocalCache.justConsumeMyOwnEvent(calendar("f4".repeat(32), mine, "gigs", "Also mine"))
            LocalCache.justConsumeMyOwnEvent(calendar("f5".repeat(32), someoneElse, "theirs", "Not mine"))

            val model = boundModel(backgroundScope, mine)
            backgroundScope.launch { model.ownCalendars.collect {} }

            val titles = model.ownCalendars.value.map { it.title() }
            assertEquals(listOf("Also mine", "Work"), titles) // sorted by title, case-insensitive
        }

    @Test
    fun nothingIsFilteredUntilTheUserPicksACalendar() =
        runTest(mainDispatcher) {
            val mine = "e1".repeat(32)
            val model = boundModel(backgroundScope, mine)
            backgroundScope.launch { model.filterAddresses.collect {} }

            assertNull("\"All\" must not narrow anything", model.filterAddresses.value)
        }

    @Test
    fun aCalendarThatHasNotLoadedYetDoesNotFilterEverythingOut() =
        runTest(mainDispatcher) {
            val mine = "e2".repeat(32)
            val model = boundModel(backgroundScope, mine)
            backgroundScope.launch { model.filterAddresses.collect {} }

            model.selectCalendar("a-calendar-that-is-not-in-the-cache")

            // The regression: this used to answer with an empty set, which `applyCalendarFilter`
            // reads as "match nothing" — every lens went blank.
            assertNull("an unresolved calendar must not blank the screen", model.filterAddresses.value)
        }

    @Test
    fun pickingACalendarResolvesItsMembers() =
        runTest(mainDispatcher) {
            val mine = "e3".repeat(32)
            val member = Address(CalendarTimeSlotEvent.KIND, mine, "standup")
            LocalCache.justConsumeMyOwnEvent(appointment("e4".repeat(32), mine, "standup"))
            LocalCache.justConsumeMyOwnEvent(calendar("e5".repeat(32), mine, "work", "Work", listOf(member)))

            val model = boundModel(backgroundScope, mine)
            backgroundScope.launch { model.filterAddresses.collect {} }

            model.selectCalendar("work")

            assertEquals(setOf(member), model.filterAddresses.value)
        }

    @Test
    fun pickingAnEmptyCalendarDoesFilterEverythingOut() =
        runTest(mainDispatcher) {
            val mine = "e6".repeat(32)
            LocalCache.justConsumeMyOwnEvent(calendar("e7".repeat(32), mine, "empty", "Empty"))

            val model = boundModel(backgroundScope, mine)
            backgroundScope.launch { model.filterAddresses.collect {} }

            model.selectCalendar("empty")

            // A calendar that really lists nothing is the one case where an empty set is right.
            assertEquals(emptySet<Address>(), model.filterAddresses.value)
            assertTrue(model.filterAddresses.value?.isEmpty() == true)
        }
}
