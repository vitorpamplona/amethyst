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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the calendar screen is *looking at*: which lens is open, which calendar the feed is
 * scoped to, and where each lens is parked — the month on the grid, the week on the strip, the day
 * on the agenda, the selected day, and the scroll offset of each list.
 *
 * Scoped to the screen's `NavBackStackEntry`, so it is built the first time the user opens
 * Calendars and cleared when that entry leaves the back stack — not before. That is the whole
 * point of it being here: this state has to outlive the composition (opening an appointment tears
 * it down and rebuilds it on the way back, and switching lenses disposes the branch the previous
 * lens lived in) without outliving the screen. `rememberSaveable` covers the first case at best
 * and never the second; a process-scoped store covers both but then hangs onto a month the user
 * looked at an hour ago, in another account.
 *
 * Two lifetimes ride along for free, and both are what you'd want: the bottom bar's
 * `popUpTo(Home) { saveState = true }` saves this entry rather than clearing it, so leaving the
 * tab and coming back keeps the lens; and the ViewModel store hangs off the account-scoped owner
 * ([com.vitorpamplona.amethyst.ui.screen.SetAccountCentricViewModelStore]), so switching accounts
 * drops it with everything else that belongs to the old account.
 *
 * Not restored after process death — the defaults below are computed from *today*, which is where
 * a cold start should open anyway.
 */
@Stable
class CalendarsViewModel : ViewModel() {
    /** Read once, at the moment the screen is first opened, so every lens agrees on "today". */
    private val today: LocalDate = LocalDate.now()

    var viewMode by mutableStateOf(CalendarsViewMode.FEED)

    /**
     * d-tag of the kind-31924 calendar the feed is scoped to, or null for "All". Deliberately not
     * persisted anywhere: a filter that survived a relaunch would surprise a user who set it once
     * and forgot.
     */
    var filterDTag by mutableStateOf<String?>(null)

    // Month lens. YearMonth is rebuilt on read from two ints so the pieces stay primitive.
    var visibleYear by mutableIntStateOf(today.year)
    var visibleMonthValue by mutableIntStateOf(today.monthValue)
    var selectedDayKey by mutableStateOf<Long?>(null)

    // Week lens. Epoch-day, so the arithmetic stays in LocalDate and stays DST-safe.
    var weekStartEpochDay by mutableLongStateOf(startOfWeek(today).toEpochDay())
    var selectedDayIndex by mutableIntStateOf(0)

    // Day lens.
    var visibleEpochDay by mutableLongStateOf(today.toEpochDay())

    /**
     * One scroll position per lens, held here for the same reason as everything above. Every other
     * feed in the app parks its offset in the process-scoped store behind `rememberForeverLazyListState`;
     * these live with the rest of this screen's state instead, so they are cleared on the same
     * event and there is one answer to "where did the calendar screen leave off".
     */
    val feedListState = LazyListState()
    val monthListState = LazyListState()
    val weekListState = LazyListState()
    val dayListState = LazyListState()

    var visibleMonth: YearMonth
        get() = YearMonth.of(visibleYear, visibleMonthValue)
        set(value) {
            visibleYear = value.year
            visibleMonthValue = value.monthValue
        }

    val weekStart: LocalDate get() = LocalDate.ofEpochDay(weekStartEpochDay)

    val visibleDate: LocalDate get() = LocalDate.ofEpochDay(visibleEpochDay)

    /** The day the week strip has selected — [weekStart] plus the strip's index. */
    val selectedWeekDate: LocalDate get() = weekStart.plusDays(selectedDayIndex.toLong())

    fun showMonth(month: YearMonth) {
        visibleMonth = month
        selectedDayKey = null
    }

    fun showWeekOf(date: LocalDate) {
        weekStartEpochDay = startOfWeek(date).toEpochDay()
        selectedDayIndex = 0
    }

    fun shiftWeeks(delta: Long) {
        weekStartEpochDay = weekStart.plusWeeks(delta).toEpochDay()
        selectedDayIndex = 0
    }

    fun shiftDays(delta: Long) {
        visibleEpochDay = visibleDate.plusDays(delta).toEpochDay()
    }
}

/**
 * Returns the Sunday on or before [date]. DST-safe because [LocalDate] arithmetic ignores zones.
 * `DayOfWeek.SUNDAY.value` is 7 in java.time, so `% 7` collapses Sunday → 0 with the rest of the
 * week following in order.
 */
fun startOfWeek(date: LocalDate): LocalDate {
    val daysFromSunday = date.dayOfWeek.value % 7
    return date.minusDays(daysFromSunday.toLong())
}
