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

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarsViewMode
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarsViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.startOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The paging arithmetic the calendar lenses share, now that it lives on the screen's ViewModel
 * instead of being spelled out in each view's click handlers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarsViewModelTest {
    // The model's derived flows are `stateIn(viewModelScope, …)`, and stateIn launches its
    // sharing coroutine as soon as the property initializer runs — so simply CONSTRUCTING the
    // model touches Dispatchers.Main. Without these, that launch fails on a plain JVM test and
    // the failure lands on a background thread, where `runTest` reports it against whichever
    // test happens to start next. Clearing the store afterwards cancels the scope, exactly as
    // the back stack does when the screen goes.
    private var store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()

        // `clear()` CANCELS viewModelScope; it does not WAIT for it, and the two are not the same
        // thing here. Every derived flow is `flowOn(Dispatchers.Default).stateIn(viewModelScope,
        // ...)`: the upstream runs on the Default pool while stateIn's sharing coroutine lives on
        // Main. A cancelled upstream therefore finishes on a Default worker and resumes its parent
        // onto Main — and if `resetMain()` has already run, that resume throws
        // `IllegalStateException: Dispatchers.Main was accessed when the platform dispatcher was
        // absent` on a background thread.
        //
        // Nothing in THIS suite fails when that happens. The next test in the JVM to call
        // `runTest` does, with `UncaughtExceptionsBeforeTest` — for a long time that was
        // `Nip46ConsentInfoBuilderTest`, which is innocent and passes in isolation, which is what
        // made it so slow to find.
        //
        // So: drain the pool while Main is still valid. This is a mitigation, not a proof — there
        // is no public handle on viewModelScope's Job to join, and a thread pool has no
        // "everything queued has run" barrier. It closes the window rather than removing it. The
        // real fix is for the ViewModel to take its dispatcher as a parameter, at which point the
        // test supplies one it can drain deterministically.
        runBlocking { withContext(Dispatchers.Default) { yield() } }

        Dispatchers.resetMain()
    }

    private fun newModel(): CalendarsViewModel = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[CalendarsViewModel::class.java]

    @Test
    fun aFreshScreenOpensOnToday() {
        val today = LocalDate.now()
        val model = newModel()

        assertEquals(CalendarsViewMode.FEED, model.viewMode)
        assertNull(model.filterDTag.value)
        assertEquals(YearMonth.from(today), model.visibleMonth)
        assertEquals(today, model.visibleDate)
        assertEquals(startOfWeek(today), model.weekStart)
        assertEquals(0, model.selectedDayIndex)
        assertNull(model.selectedDayKey)
    }

    @Test
    fun pagingTheMonthDropsTheDaySelectedInTheOldOne() {
        val model = newModel()
        model.selectedDayKey = LocalDate.of(2025, 1, 15).toEpochDay()

        model.showMonth(model.visibleMonth.plusMonths(1))

        assertNull("a day picked in January cannot stay selected in February", model.selectedDayKey)
    }

    @Test
    fun pagingTheWeekMovesSevenDaysAndReturnsTheStripToTheFirstDay() {
        val model = newModel()
        model.showWeekOf(LocalDate.of(2025, 1, 15)) // a Wednesday
        model.selectedDayIndex = 4

        val before = model.weekStart
        model.shiftWeeks(1)

        assertEquals(before.plusDays(7), model.weekStart)
        assertEquals(0, model.selectedDayIndex)
    }

    @Test
    fun showWeekOfSnapsToTheSundayOnOrBeforeTheDate() {
        val model = newModel()

        model.showWeekOf(LocalDate.of(2025, 1, 15)) // Wednesday
        assertEquals(LocalDate.of(2025, 1, 12), model.weekStart)
        assertEquals(DayOfWeek.SUNDAY, model.weekStart.dayOfWeek)

        model.showWeekOf(LocalDate.of(2025, 1, 12)) // already a Sunday: stays put
        assertEquals(LocalDate.of(2025, 1, 12), model.weekStart)
    }

    @Test
    fun theStripsSelectedDateIsTheWeekStartPlusItsIndex() {
        val model = newModel()
        model.showWeekOf(LocalDate.of(2025, 1, 15))
        model.selectedDayIndex = 3

        assertEquals(LocalDate.of(2025, 1, 15), model.selectedWeekDate)
    }

    @Test
    fun pagingTheDayCrossesDaylightSavingWithoutSlipping() {
        // US spring-forward lands on 2025-03-09; stepping by milliseconds used to land an hour
        // short and repeat a day. LocalDate arithmetic ignores zones, so a step is always a day.
        val model = newModel()
        model.visibleEpochDay = LocalDate.of(2025, 3, 8).toEpochDay()

        model.shiftDays(1)
        assertEquals(LocalDate.of(2025, 3, 9), model.visibleDate)

        model.shiftDays(1)
        assertEquals(LocalDate.of(2025, 3, 10), model.visibleDate)

        model.shiftDays(-2)
        assertEquals(LocalDate.of(2025, 3, 8), model.visibleDate)
    }

    @Test
    fun everyLensKeepsItsOwnScrollPosition() {
        val model = newModel()
        val states = listOf(model.feedListState, model.monthListState, model.weekListState, model.dayListState)

        assertEquals("the lenses must not share one scroll offset", 4, states.distinct().size)
    }
}
