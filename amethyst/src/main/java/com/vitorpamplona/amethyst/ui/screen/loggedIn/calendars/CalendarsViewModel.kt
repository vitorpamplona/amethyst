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
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.MonthGridBarSegment
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.computeMonthGridBars
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.groupByDayKeyExpanded
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the calendar screen is *looking at*: which lens is open, which calendar the feed is
 * scoped to, where each lens is parked, and the note lists the lenses draw.
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
    /** The day this screen was first opened. Only the defaults below are anchored to it. */
    private val openedOn: LocalDate = LocalDate.now()

    /**
     * Read live, not captured: this entry can outlive midnight, and a captured value left the
     * month grid and the week strip highlighting yesterday while the "Today" buttons (which call
     * [LocalDate.now] themselves) jumped somewhere else.
     */
    val today: LocalDate get() = LocalDate.now()

    var viewMode by mutableStateOf(CalendarsViewMode.FEED)

    // Month lens. YearMonth is rebuilt on read from two ints so the pieces stay primitive.
    var visibleYear by mutableIntStateOf(openedOn.year)
    var visibleMonthValue by mutableIntStateOf(openedOn.monthValue)
    var selectedDayKey by mutableStateOf<Long?>(null)

    // Week lens. Epoch-day, so the arithmetic stays in LocalDate and stays DST-safe.
    var weekStartEpochDay by mutableLongStateOf(startOfWeek(openedOn).toEpochDay())
    var selectedDayIndex by mutableIntStateOf(0)

    // Day lens.
    var visibleEpochDay by mutableLongStateOf(openedOn.toEpochDay())

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

    /**
     * What [init] binds. A flow rather than `lateinit` so the derived flows below can be declared
     * as plain properties and simply wait for it: nothing downstream has to know whether the
     * screen has composed yet.
     */
    private data class Inputs(
        val myPubKey: HexKey,
        val feed: FeedContentState,
    )

    private val inputs = MutableStateFlow<Inputs?>(null)

    /** Idempotent — the screen calls it on every composition. */
    fun init(
        myPubKey: HexKey,
        feedState: FeedContentState,
    ) {
        val bound = Inputs(myPubKey, feedState)
        if (inputs.value != bound) inputs.value = bound
    }

    // ------------------------------------------------------------------------------------------
    // The membership filter
    // ------------------------------------------------------------------------------------------

    private val _filterDTag = MutableStateFlow<String?>(null)

    /**
     * d-tag of the kind-31924 calendar the feed is scoped to, or null for "All". Deliberately not
     * persisted anywhere: a filter that survived a relaunch would surprise a user who set it once
     * and forgot.
     */
    val filterDTag = _filterDTag.asStateFlow()

    fun selectCalendar(dTag: String?) {
        _filterDTag.value = dTag
    }

    /**
     * The calendars this account owns, for the top-bar picker.
     *
     * [LocalCache.observeEvents] takes the Nostr filter to the cache's own index, so this wakes up
     * for kind-31924s by this author and nothing else. The previous version collected
     * `LocalCache.live.newEventBundles` — *every* event the app ingests — and answered each batch
     * by rescanning the whole addressable cache and re-sorting, to render a chip label that
     * changes about once a week.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ownCalendars: StateFlow<List<CalendarEvent>> =
        inputs
            .filterNotNull()
            .flatMapLatest { (myPubKey, _) ->
                LocalCache
                    .observeEvents<CalendarEvent>(
                        Filter(kinds = listOf(CalendarEvent.KIND), authors = listOf(myPubKey)),
                    ).map { calendars -> calendars.sortedBy { it.title()?.lowercase() ?: "" } }
            }
            // The seed scan inside observeEvents walks the whole notes cache (LocalCache.filter
            // does that for every query, whatever the kinds), so it must not run on the UI thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * The selected calendar's member addresses, or null when nothing is narrowing the feed.
     *
     * Resolved against [ownCalendars] — the picker's own list — rather than a second cache
     * observer filtered on `#d`. Two reasons: it is one observer instead of two for the same
     * data, and a `#d` filter cannot match a calendar published WITHOUT a d tag. Such a calendar
     * is addressed as `31924:<pubkey>:` and the picker happily offers it (`dTag()` is ""), so
     * filtering by it silently resolved to nothing and the chip sat there claiming a filter that
     * wasn't applied. A string compare has no such blind spot.
     *
     * Null covers both "All is selected" and "the calendar hasn't loaded yet", and the difference
     * matters: the old code answered the second case with an EMPTY SET, which reads as "match
     * nothing" to [applyCalendarFilter] and blanked every lens until the kind-31924 arrived. An
     * empty set now only ever means a calendar that genuinely lists no appointments.
     */
    val filterAddresses: StateFlow<Set<Address>?> =
        combine(_filterDTag, ownCalendars) { dTag, calendars ->
            if (dTag == null) {
                null
            } else {
                calendars
                    .firstOrNull { it.dTag() == dTag }
                    ?.calendarEventAddresses()
                    ?.toSet()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // ------------------------------------------------------------------------------------------
    // What the lenses draw
    // ------------------------------------------------------------------------------------------

    /**
     * The appointments on screen: the feed, narrowed by the membership filter. Every lens used to
     * assemble this for itself, which is four copies of the same collect-and-filter and four
     * chances for them to disagree.
     *
     * An empty list here means the feed is empty — [FeedState.Empty] is what
     * [FeedContentState.updateFeed] emits when the rebuilt list has nothing in it, so it is the
     * truth and not something to paper over.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<Note>> =
        combine(
            inputs.filterNotNull().flatMapLatest { (_, feed) ->
                feed.feedContent.flatMapLatest { state ->
                    if (state is FeedState.Loaded) state.feed.map { it.list } else flowOf(emptyList())
                }
            },
            filterAddresses,
        ) { feedNotes, addresses ->
            feedNotes.applyCalendarFilter(addresses)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Appointments bucketed by local day, multi-day ones repeated on every day they cover. Shared
     * by the month grid, the week strip and the day agenda — each of them used to derive it from
     * the same list and throw it away on every lens switch.
     */
    val eventsByDay: StateFlow<Map<Long, List<Note>>> =
        notes
            .map { groupByDayKeyExpanded(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** Lane assignments for the month grid's bars. */
    val monthBars: StateFlow<Map<Long, List<MonthGridBarSegment>>> =
        notes
            .map { computeMonthGridBars(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** The feed lens's upcoming/past split. */
    val upcomingPast: StateFlow<UpcomingPastSplit> =
        notes
            .map { partitionUpcomingPast(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                UpcomingPastSplit(emptyList(), emptyList()),
            )

    // ------------------------------------------------------------------------------------------
    // Paging
    // ------------------------------------------------------------------------------------------

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

    companion object {
        /**
         * How long the cache observers and the feed collection stay up after the screen stops
         * being watched. Long enough to ride out a configuration change without re-seeding every
         * list, short enough that a backgrounded screen stops holding observers open.
         */
        private const val STOP_TIMEOUT_MS = 5_000L
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
