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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.frontpage.CronWords
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageClock
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageGate
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageModels
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageRun
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSetup
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageViewModel
import com.vitorpamplona.amethyst.commons.frontpage.MASTHEAD
import com.vitorpamplona.amethyst.commons.frontpage.ModelChoices
import com.vitorpamplona.amethyst.commons.frontpage.MorningScheduleView
import com.vitorpamplona.amethyst.commons.frontpage.ObserverFailure
import com.vitorpamplona.amethyst.commons.frontpage.ObserverPrefs
import com.vitorpamplona.amethyst.commons.frontpage.ObserverStatus
import com.vitorpamplona.amethyst.commons.frontpage.PrintResult
import com.vitorpamplona.amethyst.commons.frontpage.PrintStep
import com.vitorpamplona.amethyst.commons.frontpage.SavedEdition
import com.vitorpamplona.amethyst.commons.frontpage.ScheduleState
import com.vitorpamplona.amethyst.commons.frontpage.ShownEdition
import com.vitorpamplona.amethyst.commons.frontpage.Violation
import com.vitorpamplona.amethyst.commons.frontpage.WriterFailure
import com.vitorpamplona.amethyst.commons.frontpage.dayOfWeek
import com.vitorpamplona.amethyst.commons.frontpage.formatUsd
import com.vitorpamplona.amethyst.commons.frontpage.withPaperCsp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_a_blossom_server
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.dlist_day_friday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_monday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_saturday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_sunday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_thursday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_tuesday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_wednesday
import com.vitorpamplona.amethyst.commons.resources.front_page_add_to_bar
import com.vitorpamplona.amethyst.commons.resources.front_page_added_to_bar
import com.vitorpamplona.amethyst.commons.resources.front_page_can_leave
import com.vitorpamplona.amethyst.commons.resources.front_page_cannot_show
import com.vitorpamplona.amethyst.commons.resources.front_page_changes_title
import com.vitorpamplona.amethyst.commons.resources.front_page_connect
import com.vitorpamplona.amethyst.commons.resources.front_page_cost_label
import com.vitorpamplona.amethyst.commons.resources.front_page_details
import com.vitorpamplona.amethyst.commons.resources.front_page_download
import com.vitorpamplona.amethyst.commons.resources.front_page_edition_label
import com.vitorpamplona.amethyst.commons.resources.front_page_edition_unreadable
import com.vitorpamplona.amethyst.commons.resources.front_page_fixed_once
import com.vitorpamplona.amethyst.commons.resources.front_page_last_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_last_printed_ago
import com.vitorpamplona.amethyst.commons.resources.front_page_model
import com.vitorpamplona.amethyst.commons.resources.front_page_model_hint
import com.vitorpamplona.amethyst.commons.resources.front_page_model_id
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_device_key
import com.vitorpamplona.amethyst.commons.resources.front_page_never_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_new_edition
import com.vitorpamplona.amethyst.commons.resources.front_page_no_tor
import com.vitorpamplona.amethyst.commons.resources.front_page_not_now
import com.vitorpamplona.amethyst.commons.resources.front_page_not_printed_since
import com.vitorpamplona.amethyst.commons.resources.front_page_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_app_label
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_notice_body
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_notice_title
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_pin
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_pinned
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_pinned_label
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_unpinned
import com.vitorpamplona.amethyst.commons.resources.front_page_open_observer
import com.vitorpamplona.amethyst.commons.resources.front_page_pick_time
import com.vitorpamplona.amethyst.commons.resources.front_page_posts_label
import com.vitorpamplona.amethyst.commons.resources.front_page_print
import com.vitorpamplona.amethyst.commons.resources.front_page_print_again
import com.vitorpamplona.amethyst.commons.resources.front_page_print_estimate
import com.vitorpamplona.amethyst.commons.resources.front_page_print_from_device
import com.vitorpamplona.amethyst.commons.resources.front_page_publish
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_confirm
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_no_servers
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_read_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_publishing
import com.vitorpamplona.amethyst.commons.resources.front_page_quotes_label
import com.vitorpamplona.amethyst.commons.resources.front_page_read_full
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_body
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_title
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_cannot_sign
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_change_time
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_daily
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_days
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_every_hours
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_in_zone
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_locked
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_monthly
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_no_write_relays
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_no_zone
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_not_accepted
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_not_expressible
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_not_set_up
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_read_only
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_times_a_day
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_unreadable
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_weekdays
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_weekends
import com.vitorpamplona.amethyst.commons.resources.front_page_scheduled
import com.vitorpamplona.amethyst.commons.resources.front_page_set_up
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_change
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_done
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_key
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_lens
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_on
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_ranked_by
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_title
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_title_again
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_to_do
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_using
import com.vitorpamplona.amethyst.commons.resources.front_page_show_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_step_check
import com.vitorpamplona.amethyst.commons.resources.front_page_step_fix
import com.vitorpamplona.amethyst.commons.resources.front_page_step_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_step_pull
import com.vitorpamplona.amethyst.commons.resources.front_page_step_write
import com.vitorpamplona.amethyst.commons.resources.front_page_tagline
import com.vitorpamplona.amethyst.commons.resources.front_page_title
import com.vitorpamplona.amethyst.commons.resources.front_page_tokens
import com.vitorpamplona.amethyst.commons.resources.front_page_tokens_label
import com.vitorpamplona.amethyst.commons.resources.front_page_too_large
import com.vitorpamplona.amethyst.commons.resources.front_page_tor_pictures
import com.vitorpamplona.amethyst.commons.resources.front_page_unsupported
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_image
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_link
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_markup
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_quote
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.relay_settings
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.resources.yesterday
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.PillColors
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileBytesAccess
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.calendarYearAndDay
import com.vitorpamplona.amethyst.commons.ui.note.durationPhrase
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarEditing
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The house stylesheet's `--paper`: the newsprint the paper sits on, whatever the app's theme. */
private val Newsprint = Color(0xFFFAF8F3)

/** Ink on newsprint: the paper's frame is always light, whatever the app's theme. */
private val Ink = Color(0xFF1A1A1A)

/** "Tue, Nov 10": the shown edition's day, as the top bar's title. */
private val editionDateFormatter = DateSkeletonFormatter("MMMEd")

/**
 * The edition's own day (`yyyy-MM-dd`), as the title: noon UTC of that date, which falls on the
 * same calendar day in every zone from UTC-11 to UTC+11. Its print time can be later than the
 * day it belongs to, or absent.
 */
private fun editionTitle(day: String): String {
    val parts = day.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return day
    val days = SearchDate(parts[0], parts[1], parts[2]).daysFromEpoch()
    val wanted = parts[0] * 1000 + (days - SearchDate(parts[0], 1, 1).daysFromEpoch() + 1).toInt()
    val noonUtc = (days * 86_400L + 43_200L) * 1000
    // Noon UTC is that date only from UTC-11 to UTC+11: past those (UTC+12 to +14), step half a
    // day until the reader's own zone lands on it.
    val at = listOf(noonUtc, noonUtc - 43_200_000L, noonUtc + 43_200_000L).firstOrNull { calendarYearAndDay(it) == wanted } ?: noonUtc
    return editionDateFormatter.format(at)
}

/**
 * Your front page: the Nostr Observer's daily paper, written by the reader's own AI key from the
 * last 24 hours of their web of trust, checked, kept on the device for a week, and shown on the
 * same sandboxed surface as an nSite. The print belongs to the account, not to this screen.
 *
 * Reader first: once an edition exists the paper leads, with its date as the title, and printing
 * moves behind a top-bar action into a sheet. The gradient hero is the first-run and setup view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontPageScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    day: String? = null,
) {
    val services = LocalAppServices.current
    val printers = services.frontPagePrinters
    if (printers == null) {
        Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.front_page_title), nav) }) { padding ->
            Box(Modifier.padding(padding).padding(16.dp)) { Notice(stringRes(Res.string.front_page_unsupported)) }
        }
        return
    }
    val account = accountViewModel.account
    val printer = printers.of(account)
    val viewModel =
        rememberViewModel(key = "front-page:${account.signer.pubKey}") {
            FrontPageViewModel(account, accountViewModel.httpClientBuilder, printer, printers.morning, day)
        }
    // While this screen shows the paper, a finished print needs no notification. Only while it is
    // started: a backgrounded app (or a screen behind another) isn't anyone watching.
    LifecycleStartEffect(printer) {
        val release = printer.watch()
        onStopOrDispose { release() }
    }
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val editions by viewModel.editions.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val estimate by viewModel.estimate.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val scheduleProblem by viewModel.scheduleProblem.collectAsStateWithLifecycle()
    val schedulePending by viewModel.schedulePending.collectAsStateWithLifecycle()
    val scheduleActions =
        remember(viewModel, nav) {
            ScheduleActions(viewModel::setScheduleEnabled, viewModel::setScheduleTime, viewModel::setPrintsHere, viewModel::retryScheduleDecrypt) { nav.nav(Route.EditRelays) }
        }
    val observerPrefs by viewModel.observerPrefs.collectAsStateWithLifecycle()
    val observer by viewModel.observer.collectAsStateWithLifecycle()
    val site by viewModel.site.collectAsStateWithLifecycle()

    var printSheet by remember { mutableStateOf(false) }
    // The public-site confirm comes after a clean print, before anything is uploaded (ObserverLine).
    val requestPrint: () -> Unit = viewModel::print
    val publishPrefs = if (viewModel.canPublish) observerPrefs else null
    var detailsSheet by remember { mutableStateOf(false) }
    val platform = LocalAppPlatform.current
    val download = rememberEditionDownload()

    // The bottom bar and the favorites, for the one-tap shortcuts: the front page as a tab, the
    // reader's Observer site as a pinned app.
    val bottomBar by accountViewModel.bottomBarItemsFlow().collectAsStateWithLifecycle()
    val favoriteApps = services.favoriteApps
    val favorites by favoriteApps.favorites.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedToBar = stringRes(Res.string.front_page_added_to_bar)
    val addToBar: (() -> Unit)? =
        if (FrontPageShortcuts.offersBottomBar(bottomBar)) {
            {
                accountViewModel.changeBottomBarItems(FrontPageShortcuts.withFrontPage(bottomBar))
                scope.launch { snackbar.showSnackbar(addedToBar) }
            }
        } else {
            null
        }
    val pinnedText = stringRes(Res.string.front_page_observer_pinned)
    val unpinnedText = stringRes(Res.string.front_page_observer_unpinned)
    val observerLabel = stringRes(Res.string.front_page_observer_app_label)
    val siteFavoriteId = site?.let { "nostr:${favoriteCoordinateOf(it)}" }
    val sitePinned = siteFavoriteId != null && favorites.any { it.id == siteFavoriteId }
    val togglePin: (NamedSiteEvent) -> Unit = { event ->
        val favorite = FrontPageShortcuts.observerFavorite(event, event.title()?.ifBlank { null } ?: observerLabel, TimeUtils.nowMillis())
        val tab = FrontPageShortcuts.favoriteTab(favorite.id)
        if (favoriteApps.isFavorite(favorite.id)) {
            favoriteApps.remove(favorite.id)
            accountViewModel.changeBottomBarItems(BottomBarEditing.unpin(bottomBar, tab))
            scope.launch { snackbar.showSnackbar(unpinnedText) }
        } else {
            // Pinned as an nSite card pins it, and put in the bar, where pinned apps sit.
            favoriteApps.add(favorite)
            accountViewModel.changeBottomBarItems(BottomBarEditing.pin(bottomBar, tab))
            scope.launch { snackbar.showSnackbar(pinnedText) }
        }
    }

    val current = shown
    // On a phone the bar holds the frequent actions; Download and full screen go in the menu.
    val compactBar = !LocalScreenLayout.current.isLargeScreen
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (current == null) {
                if (addToBar == null) {
                    TopBarWithBackButton(stringRes(Res.string.front_page_title), nav)
                } else {
                    TopBarWithBackButton(stringRes(Res.string.front_page_title), nav) {
                        OverflowMenu { close -> AddToBarItem(close, addToBar) }
                    }
                }
            } else {
                val canDownload = current.html != null
                val canReadFull = platform.showsPapersInline && current.html != null
                val readFull = { nav.nav(Route.FrontPageReader(current.edition.day)) }
                TopBarWithBackButton(editionTitle(current.edition.day), nav) {
                    if (gate == FrontPageGate.READY) {
                        IconButton(onClick = { printSheet = true }) {
                            Icon(MaterialSymbols.AutoAwesome, contentDescription = stringRes(Res.string.front_page_print_again))
                        }
                    }
                    IconButton(onClick = { detailsSheet = true }) {
                        Icon(MaterialSymbols.Info, contentDescription = stringRes(Res.string.front_page_details))
                    }
                    if (!compactBar && canDownload) {
                        IconButton(onClick = { download(current) }) {
                            Icon(MaterialSymbols.Download, contentDescription = stringRes(Res.string.front_page_download))
                        }
                    }
                    if (!compactBar && canReadFull) {
                        IconButton(onClick = readFull) {
                            Icon(MaterialSymbols.Fullscreen, contentDescription = stringRes(Res.string.front_page_read_full))
                        }
                    }
                    val inMenu = (compactBar && (canDownload || canReadFull)) || addToBar != null
                    if (inMenu) {
                        OverflowMenu { close ->
                            if (compactBar && canReadFull) {
                                MenuItem(MaterialSymbols.Fullscreen, stringRes(Res.string.front_page_read_full)) {
                                    close()
                                    readFull()
                                }
                            }
                            if (compactBar && canDownload) {
                                MenuItem(MaterialSymbols.Download, stringRes(Res.string.front_page_download)) {
                                    close()
                                    download(current)
                                }
                            }
                            addToBar?.let { AddToBarItem(close, it) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val firstRun = current == null
            if (firstRun || gate != FrontPageGate.READY) {
                Hero {
                    // First run: the print controls stay inline, in the card.
                    if (gate == FrontPageGate.NO_KEY || gate == FrontPageGate.NO_LENS) {
                        SetupChecklist(
                            setup,
                            hasEditions = editions.isNotEmpty(),
                            onKey = { nav.nav(Route.AgentKeysSettings) },
                            onLens = { nav.nav(Route.WebOfTrust) },
                        )
                    } else if (gate == FrontPageGate.READY) {
                        PrintControls(
                            models,
                            run,
                            estimate,
                            hasEditions = false,
                            schedule,
                            scheduleProblem,
                            schedulePending != null,
                            scheduleActions,
                            onModel = viewModel::selectModel,
                            onPrint = requestPrint,
                            onCancel = viewModel::cancel,
                            publish = publishPrefs,
                            onPublish = viewModel::setPublish,
                        )
                    }
                }
            }
            when (gate) {
                FrontPageGate.NO_KEY, FrontPageGate.NO_LENS -> {}
                FrontPageGate.UNSUPPORTED -> Notice(stringRes(Res.string.front_page_unsupported))
                FrontPageGate.READY -> RunStatus(run, onCancel = viewModel::cancel)
            }
            if (current != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (editions.size > 1) History(editions, current.edition.day, viewModel::selectDay)
                    if (schedule.enabled) ScheduleLine(schedule) { printSheet = true }
                    ScheduleWarnings(schedule)
                    ObserverLine(
                        observer,
                        onPublish = { viewModel.answerObserverNotice(publish = true) },
                        onNotNow = { viewModel.answerObserverNotice(publish = false) },
                        onAddServer = { nav.nav(Route.EditMediaServers) },
                        onRetry = viewModel::retryPublish,
                    )
                }
                if (current.html != null) {
                    Paper(current, "front-page", fill = false, Modifier.fillMaxWidth())
                } else {
                    // The history stays above, so the reader can open another day.
                    Notice(stringRes(Res.string.front_page_edition_unreadable), error = true)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (printSheet) {
        ModalBottomSheet(onDismissRequest = { printSheet = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringRes(Res.string.front_page_new_edition), style = MaterialTheme.typography.titleLarge)
                PrintControls(
                    models,
                    run,
                    estimate,
                    hasEditions = editions.isNotEmpty(),
                    schedule,
                    scheduleProblem,
                    schedulePending != null,
                    scheduleActions,
                    onModel = viewModel::selectModel,
                    onPrint = {
                        printSheet = false
                        requestPrint()
                    },
                    onCancel = viewModel::cancel,
                    publish = publishPrefs,
                    onPublish = viewModel::setPublish,
                )
            }
        }
    }
    if (detailsSheet && current != null) {
        val sharer = rememberTextSharer()
        val launcher = rememberAppLauncher()
        val shareTitle = stringRes(Res.string.front_page_open_observer)
        ModalBottomSheet(onDismissRequest = { detailsSheet = false }) {
            Details(
                current.edition,
                site,
                sitePinned = sitePinned,
                onPinSite = togglePin,
                onOpenSite =
                    if (launcher.opensNapplets) {
                        { event ->
                            detailsSheet = false
                            launcher.openSite(event, nav)
                        }
                    } else {
                        null
                    },
                onShareSite = { event ->
                    NAddress.createOrNull(event.kind, event.pubKey, event.dTag(), null)?.let { sharer.share("nostr:$it", event.title(), shareTitle) }
                },
                Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            )
        }
    }
}

/**
 * The paper alone, edge to edge: no controls, a back arrow, the date and Download. The same
 * surface and status overlay as the inline paper, under its own session id.
 */
@Composable
fun FrontPageReaderScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    day: String,
) {
    val printers = LocalAppServices.current.frontPagePrinters
    val printer = printers?.of(accountViewModel.account)
    val editions by (printer?.editions ?: remember { MutableStateFlow(emptyList()) }).collectAsStateWithLifecycle()
    val edition = editions.firstOrNull { it.day == day }
    // Reset first: the last day's page must never show (or download) under this day's title.
    val html by produceState<String?>(null, printer, day, edition?.sha256) {
        value = null
        value = printer?.html(day)
    }
    val shown = if (edition != null && html != null) ShownEdition(edition, html) else null
    val download = rememberEditionDownload()

    Scaffold(
        topBar = {
            TopBarWithBackButton(edition?.let { editionTitle(it.day) } ?: stringRes(Res.string.front_page_title), nav) {
                if (shown != null) {
                    IconButton(onClick = { download(shown) }) {
                        Icon(MaterialSymbols.Download, contentDescription = stringRes(Res.string.front_page_download))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Newsprint)) {
            if (shown != null) Paper(shown, "front-page-reader", fill = true, Modifier.fillMaxSize())
        }
    }
}

/** Saves an edition's page where the reader picks. */
@Composable
private fun rememberEditionDownload(): (ShownEdition) -> Unit {
    val files = rememberFileBytesAccess()
    val scope = rememberCoroutineScope()
    return remember(files, scope) {
        { shown ->
            val html = shown.html
            // The copy leaves the app's sandbox: it carries the paper's CSP (older editions were kept without it).
            if (html != null) scope.launch { files.save("front-page-${shown.edition.day}-${shown.edition.code}.html", "text/html") { withPaperCsp(html).encodeToByteArray() } }
        }
    }
}

/**
 * First run and setup: what the page is, with whatever comes next inside it. A quiet surface in
 * the app's own colours: the paper is what the page is for, not this card.
 */
@Composable
private fun Hero(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(MaterialSymbols.AutoMirrored.Article, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                // The top bar names the screen; the hero only says what it is for.
                Text(
                    stringRes(Res.string.front_page_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            content()
        }
    }
}

/** Model, Print (with its estimate) or Cancel, and the morning schedule: in the first-run card or the print sheet. */
@Composable
private fun PrintControls(
    models: FrontPageModels,
    run: FrontPageRun,
    estimate: Double?,
    hasEditions: Boolean,
    schedule: MorningScheduleView,
    scheduleProblem: ScheduleEdit?,
    scheduleBusy: Boolean,
    scheduleActions: ScheduleActions,
    onModel: (String) -> Unit,
    onPrint: () -> Unit,
    onCancel: () -> Unit,
    publish: ObserverPrefs?,
    onPublish: (Boolean) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelPicker(models, onModel, PillColors.onCard(), ink)
        // A Print that can't be pressed says why: the provider lists nothing and no model is named.
        if (!models.loading && models.ids.isEmpty() && models.selected == null) {
            Text(
                stringRes(Res.string.front_page_model_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        if (run is FrontPageRun.Running) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, ink.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
            ) {
                Text(stringRes(Res.string.cancel), style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Button(
                onClick = onPrint,
                enabled = models.selected != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(MaterialSymbols.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
                Spacer(Modifier.width(8.dp))
                val label = stringRes(if (hasEditions) Res.string.front_page_print_again else Res.string.front_page_print)
                // The reader pays for this: say roughly what before they press it (NIP-FA).
                Text(
                    if (estimate != null) stringRes(Res.string.front_page_print_estimate, label, formatUsd(estimate)) else label,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        ScheduleRow(schedule, scheduleProblem, scheduleBusy, scheduleActions, ink)
        publish?.let { PublishRow(it.publish, onPublish, ink) }
    }
}

/** "Publish to my Observer": each clean edition also goes to the reader's public site. */
@Composable
private fun PublishRow(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    ink: Color,
) {
    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(MaterialSymbols.Public, contentDescription = null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringRes(Res.string.front_page_publish), style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/**
 * Under the facts: the confirm before the first publish (nothing is uploaded until the reader
 * says Publish), the publish in progress, or why it failed. Nothing when fine.
 */
@Composable
private fun ObserverLine(
    status: ObserverStatus,
    onPublish: () -> Unit,
    onNotNow: () -> Unit,
    onAddServer: () -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        ObserverStatus.AwaitingConsent ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbols.Public, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(Res.string.front_page_observer_notice_title), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(stringRes(Res.string.front_page_observer_notice_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = onNotNow) { Text(stringRes(Res.string.front_page_not_now)) }
                        Button(onClick = onPublish, shape = RoundedCornerShape(14.dp)) { Text(stringRes(Res.string.front_page_publish_confirm)) }
                    }
                }
            }
        ObserverStatus.Publishing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.front_page_publishing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        is ObserverStatus.Failed ->
            when (status.reason) {
                ObserverFailure.NO_SERVERS ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.front_page_publish_no_servers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAddServer) { Text(stringRes(Res.string.add_a_blossom_server)) }
                    }
                ObserverFailure.READ ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.front_page_publish_read_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text(stringRes(Res.string.retry)) }
                    }
                else ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.front_page_publish_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text(stringRes(Res.string.retry)) }
                    }
            }
        else -> {}
    }
}

@Composable
private fun ModelPicker(
    models: FrontPageModels,
    onModel: (String) -> Unit,
    colors: PillColors,
    ink: Color,
) {
    if (models.loading) {
        Row(Modifier.heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ink)
            Spacer(Modifier.width(12.dp))
            Text(stringRes(Res.string.front_page_model), style = MaterialTheme.typography.bodyMedium, color = ink)
        }
        return
    }
    if (models.ids.isEmpty()) {
        // The provider lists no models: the reader names one, in the same pill as the key field.
        PillTextField(
            value = models.selected.orEmpty(),
            onValueChange = onModel,
            placeholder = stringRes(Res.string.front_page_model_id),
            leading = MaterialSymbols.AutoAwesome,
            colors = colors,
        )
        return
    }
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val selected = models.selected
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(colors.container)
            .clickable { picking = true }
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = colors.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringRes(Res.string.front_page_model), style = MaterialTheme.typography.labelSmall, color = colors.placeholder)
            Text(
                selected?.let { ModelChoices.readableName(it, models.byId[it]?.name) }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = colors.icon)
    }
    if (picking) {
        ModelPickerDialog(models.ids, models.byId, selected, onSelect = onModel, onDismiss = { picking = false })
    }
}

/** What the schedule controls do: the first two edit the synced schedule, the third this device's part in it. */
@Stable
class ScheduleActions(
    val onEnabled: (Boolean) -> Unit,
    val onTime: (hour: Int, minute: Int) -> Unit,
    val onPrintsHere: (Boolean) -> Unit,
    val onRetryDecrypt: () -> Unit,
    val onRelaySettings: () -> Unit,
)

/**
 * The schedule all of the reader's devices share: its switch and what it says in words ("Every
 * weekday at 6:00 AM", on this device's clock; tap to change the time when this editor can), then
 * "Print from this device", who printed last, and a warning when a due morning didn't print.
 * While an edit is on its way ([busy]) the switches show it and every control waits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRow(
    schedule: MorningScheduleView,
    problem: ScheduleEdit?,
    busy: Boolean,
    actions: ScheduleActions,
    ink: Color,
) {
    if (schedule.state == ScheduleState.LOADING) return
    var picking by remember { mutableStateOf(false) }
    val note = ink.copy(alpha = 0.75f)
    val indent = Modifier.padding(start = 34.dp)
    val canToggle = schedule.state == ScheduleState.READY || schedule.state == ScheduleState.NOT_SET_UP
    Column {
        SwitchRow(
            label = stringRes(Res.string.front_page_scheduled),
            checked = schedule.enabled,
            enabled = canToggle && !busy,
            onChange = actions.onEnabled,
            ink = ink,
            icon = MaterialSymbols.Schedule,
            style = MaterialTheme.typography.bodyLarge,
        )
        when (schedule.state) {
            ScheduleState.NOT_SET_UP -> ScheduleNote(stringRes(Res.string.front_page_schedule_not_set_up), note, indent)
            ScheduleState.CANT_DECRYPT -> {
                ScheduleNote(stringRes(if (schedule.retryable) Res.string.front_page_schedule_locked else Res.string.front_page_schedule_read_only), note, indent)
                if (schedule.retryable) {
                    TextButton(onClick = actions.onRetryDecrypt, modifier = indent, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text(stringRes(Res.string.try_again)) }
                }
            }
            ScheduleState.UNREADABLE -> {
                ScheduleNote(stringRes(Res.string.front_page_schedule_unreadable), note, indent)
                schedule.words?.let { ScheduleNote(scheduleWords(it), note, indent) }
            }
            else ->
                schedule.words?.let { words ->
                    val sentence = scheduleWords(words)
                    val editable = schedule.editableTime != null && !busy
                    Text(
                        sentence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink,
                        modifier =
                            indent
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = editable, onClickLabel = stringRes(Res.string.front_page_schedule_change_time), role = Role.Button) { picking = true }
                                .padding(vertical = 12.dp),
                    )
                }
        }
        // When it last printed belongs to the schedule, not to this device's switch below.
        LastPrinted(schedule, note, indent)
        ScheduleWarnings(schedule, indent)
        if (!schedule.hasWriteRelays) {
            ScheduleNote(stringRes(Res.string.front_page_schedule_no_write_relays), note, indent)
            TextButton(onClick = actions.onRelaySettings, modifier = indent, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text(stringRes(Res.string.relay_settings)) }
        }
        SwitchRow(
            label = stringRes(Res.string.front_page_print_from_device),
            checked = schedule.printsHere && schedule.canPrintHere,
            enabled = schedule.canPrintHere && !busy && schedule.state != ScheduleState.CANT_DECRYPT && schedule.state != ScheduleState.UNREADABLE,
            onChange = actions.onPrintsHere,
            ink = ink,
            modifier = indent,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Amber and remote signers need the reader to decrypt the AI key: say so, in few words.
        if (!schedule.canPrintHere) ScheduleNote(stringRes(Res.string.front_page_needs_device_key), note, indent)
        problem?.let { ScheduleProblem(stringRes(scheduleProblemText(it)), indent) }
    }
    if (picking) {
        val start = remember(schedule.editableTime) { schedule.editableTime ?: (7 to 0) }
        val state = rememberTimePickerState(initialHour = start.first, initialMinute = start.second, is24Hour = rememberIs24HourClock())
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringRes(Res.string.front_page_pick_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    actions.onTime(state.hour, state.minute)
                }) { Text(stringRes(Res.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

/** A labelled switch whose whole row toggles it, and is one switch to a screen reader. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
    icon: MaterialSymbol? = null,
    style: TextStyle,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, style = style, color = ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun scheduleProblemText(problem: ScheduleEdit) =
    when (problem) {
        ScheduleEdit.Unreadable -> Res.string.front_page_schedule_unreadable
        ScheduleEdit.CannotSign -> Res.string.front_page_schedule_cannot_sign
        ScheduleEdit.NotAccepted -> Res.string.front_page_schedule_not_accepted
        ScheduleEdit.NoWriteRelays -> Res.string.front_page_schedule_no_write_relays
        ScheduleEdit.NotExpressible -> Res.string.front_page_schedule_not_expressible
        ScheduleEdit.NoZone -> Res.string.front_page_schedule_no_zone
        else -> Res.string.front_page_schedule_unreachable
    }

/**
 * The schedule in words, on this device's clock: "Every weekday at 6:00 AM", "3 times a day at
 * …", "Every 6 hours at :15", "Monthly on day 1 at …", the zone named when the times are the
 * schedule's own, else the expression as written.
 */
@Composable
private fun scheduleWords(words: CronWords): String {
    val format = rememberTimeOfDayFormatter()
    val dayNames = remember { DateSkeletonFormatter("EEE") }
    val now = TimeUtils.now()

    fun time(
        hour: Int,
        minute: Int,
    ) = format(FrontPageClock.todayAt(now, hour, minute) * 1000)
    val (text, zone) =
        when (words) {
            is CronWords.Days -> {
                val at = time(words.time.hour, words.time.minute)
                val days = words.time.weekdays?.takeIf { it.size in 1..6 }
                val said =
                    when (days) {
                        null -> stringRes(Res.string.front_page_schedule_daily, at)
                        WEEKDAYS -> stringRes(Res.string.front_page_schedule_weekdays, at)
                        WEEKEND -> stringRes(Res.string.front_page_schedule_weekends, at)
                        else -> {
                            // A noon of each weekday this week, on this device's clock, named the platform's way.
                            val noon = FrontPageClock.todayAt(now, 12, 0)
                            val today = dayOfWeek(FrontPageClock.dayKey(noon)) ?: 1
                            val names = days.sorted().joinToString(", ") { dayNames.format((noon + (it - today) * 86_400L) * 1000) }
                            stringRes(Res.string.front_page_schedule_days, names, at)
                        }
                    }
                said to words.zone
            }
            is CronWords.TimesADay -> pluralStringRes(Res.plurals.front_page_schedule_times_a_day, words.times.size, words.times.size, words.times.joinToString(", ") { time(it.first, it.second) }) to words.zone
            is CronWords.EveryHours -> pluralStringRes(Res.plurals.front_page_schedule_every_hours, words.hours, words.hours, words.minute.toString().padStart(2, '0')) to null
            is CronWords.Monthly -> stringRes(Res.string.front_page_schedule_monthly, words.day, time(words.hour, words.minute)) to words.zone
            is CronWords.Raw -> words.cron to null
        }
    return if (zone != null) stringRes(Res.string.front_page_schedule_in_zone, text, zone) else text
}

private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
private val WEEKEND = setOf(6, 7)

@Composable
private fun ScheduleNote(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** Why the last change wasn't saved, or a morning that didn't print, announced politely. */
@Composable
private fun ScheduleProblem(
    text: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.error
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Icon(MaterialSymbols.Warning, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * "Last printed by Desktop (Linux), 2 hours ago": the finished claim's device and when; "Last
 * printed 2 days ago" when the last run isn't the latest claim's; "Not printed yet" before the first.
 */
@Composable
private fun LastPrinted(
    schedule: MorningScheduleView,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (schedule.state != ScheduleState.READY) return
    val ago = schedule.lastRanAgo
    if (schedule.lastRan == null || ago == null) {
        if (schedule.enabled) ScheduleNote(stringRes(Res.string.front_page_never_printed), color, modifier)
        return
    }
    val span = durationPhrase(ago)
    val by = schedule.lastBy
    ScheduleNote(if (by != null) stringRes(Res.string.front_page_last_printed, by, span) else stringRes(Res.string.front_page_last_printed_ago, span), color, modifier)
}

/** A due morning that didn't print, shown on every device: dates on this device's calendar. */
@Composable
private fun ScheduleWarnings(
    schedule: MorningScheduleView,
    modifier: Modifier = Modifier,
) {
    schedule.missedSince?.let { ran ->
        ScheduleProblem(stringRes(Res.string.front_page_not_printed_since, editionTitle(FrontPageClock.dayKey(ran))), modifier)
    }
}

/** When the schedule is on, one line under the facts: when, and the way to change it. */
@Composable
private fun ScheduleLine(
    schedule: MorningScheduleView,
    onOpen: () -> Unit,
) {
    if (schedule.state != ScheduleState.READY) return
    val words = schedule.words ?: return
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(scheduleWords(words), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** The top bar's ⋮: the actions that don't earn an icon of their own. */
@Composable
private fun OverflowMenu(content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.more_options))
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection { content { open = false } }
        }
    }
}

@Composable
private fun MenuItem(
    icon: MaterialSymbol,
    text: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Size20Modifier) },
        modifier = RoundedMenuItemModifier,
        onClick = onClick,
    )
}

/** "Add to bottom bar": offered only while the front page isn't there ([FrontPageShortcuts.offersBottomBar]). */
@Composable
private fun AddToBarItem(
    close: () -> Unit,
    onAdd: () -> Unit,
) = MenuItem(MaterialSymbols.AddToHomeScreen, stringRes(Res.string.front_page_add_to_bar)) {
    close()
    onAdd()
}

/** The edition's particulars: code, counts, model, tokens, cost, and anything the check took out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    edition: SavedEdition,
    site: NamedSiteEvent?,
    sitePinned: Boolean,
    onPinSite: (NamedSiteEvent) -> Unit,
    onOpenSite: ((NamedSiteEvent) -> Unit)?,
    onShareSite: (NamedSiteEvent) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringRes(Res.string.front_page_details), style = MaterialTheme.typography.titleLarge)
        // The reader's public Observer site, once there is one: open it as any nSite, or share it.
        site?.let { event ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                onOpenSite?.let { open ->
                    OutlinedButton(onClick = { open(event) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(MaterialSymbols.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.front_page_open_observer))
                    }
                }
                OutlinedButton(onClick = { onShareSite(event) }, shape = RoundedCornerShape(14.dp)) {
                    Icon(MaterialSymbols.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringRes(Res.string.quick_action_share))
                }
                // A favorite app, like a pinned nSite, so the site sits in the bottom bar; tap again to unpin.
                if (sitePinned) {
                    FilledTonalButton(onClick = { onPinSite(event) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(MaterialSymbols.PushPin, contentDescription = null, modifier = Modifier.size(18.dp), filled = true)
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.front_page_observer_pinned_label))
                    }
                } else {
                    OutlinedButton(onClick = { onPinSite(event) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(MaterialSymbols.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.front_page_observer_pin))
                    }
                }
            }
        }
        DetailRow(stringRes(Res.string.front_page_edition_label), edition.code)
        DetailRow(stringRes(Res.string.front_page_posts_label), grouped(edition.posts.toLong()))
        DetailRow(stringRes(Res.string.front_page_quotes_label), grouped(edition.quotes.toLong()))
        DetailRow(stringRes(Res.string.front_page_model), edition.model)
        edition.tokens?.let { tokens ->
            DetailRow(
                stringRes(Res.string.front_page_tokens_label),
                pluralStringRes(Res.plurals.front_page_tokens, tokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), grouped(tokens)),
            )
        }
        edition.costUsd?.let { DetailRow(stringRes(Res.string.front_page_cost_label), formatUsd(it)) }
        if (edition.rounds == 2) Text(stringRes(Res.string.front_page_fixed_once), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (edition.removed.isNotEmpty()) {
            HorizontalDivider()
            Text(stringRes(Res.string.front_page_changes_title), style = MaterialTheme.typography.titleSmall)
            edition.removed.take(10).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The kept week as tabs right above the paper: the open day underlined in the accent colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun History(
    editions: List<SavedEdition>,
    open: String,
    onDay: (String) -> Unit,
) {
    val selected = editions.indexOfFirst { it.day == open }.coerceAtLeast(0)
    SecondaryScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
    ) {
        editions.forEachIndexed { index, edition ->
            Tab(
                selected = index == selected,
                onClick = { onDay(edition.day) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(dayLabel(edition.day), style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}

/**
 * The checked page on the nSite surface. While it isn't showing, the frame shrinks to its
 * message (a blank 900dp box explains nothing); once it paints it takes its full height, or
 * the whole screen in the reader ([fill]).
 */
@Composable
private fun Paper(
    shown: ShownEdition,
    instancePrefix: String,
    fill: Boolean,
    modifier: Modifier,
) {
    val html = shown.html ?: return
    val platform = LocalAppPlatform.current
    if (!platform.showsPapersInline) {
        Notice(stringRes(Res.string.front_page_cannot_show))
        return
    }
    val edition = shown.edition
    // What the surface says about itself: a blank frame never goes unexplained.
    var status by remember(edition.code) { mutableStateOf(InlineNappletStatus.LOADING) }
    var attempt by remember(edition.code) { mutableIntStateOf(0) }
    val showing = status == InlineNappletStatus.SHOWING
    // The model writes the layout, some pages run edge to edge: a margin of newsprint keeps the
    // rounded corners off the folio. The paper itself is always light.
    Box(
        modifier
            .then(if (fill) Modifier else Modifier.clip(RoundedCornerShape(20.dp)))
            .background(Newsprint)
            .padding(if (fill) 0.dp else 8.dp),
    ) {
        platform.InlinePaperSurface(
            html = html,
            title = "$MASTHEAD · ${edition.code}",
            // A new id on Retry opens a fresh session rather than waking the failed one.
            instanceId = "$instancePrefix-${edition.day}-${edition.code}-$attempt",
            onStatus = { status = it },
            modifier =
                when {
                    fill -> Modifier.fillMaxSize()
                    showing -> Modifier.fillMaxWidth().height(900.dp)
                    else -> Modifier.fillMaxWidth().height(220.dp)
                },
            // Full screen there is nothing else to scroll: no click first.
            takesWheel = fill,
        )
        PaperStatus(status, onRetry = {
            attempt++
            status = InlineNappletStatus.LOADING
        })
    }
}

/**
 * Over the paper while it isn't showing. Tor gets its own words: with Tor on, the surface
 * fetches nothing (the art) until Tor runs, and gives up rather than go around it.
 */
@Composable
private fun BoxScope.PaperStatus(
    status: InlineNappletStatus,
    onRetry: () -> Unit,
) {
    if (status == InlineNappletStatus.SHOWING) return
    Column(
        Modifier.matchParentSize().background(Newsprint).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        when (status) {
            InlineNappletStatus.LOADING -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Ink.copy(alpha = 0.6f))
            InlineNappletStatus.WAITING_FOR_TOR -> {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Ink.copy(alpha = 0.6f))
                Text(stringRes(Res.string.tor_waiting), style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(stringRes(Res.string.front_page_tor_pictures), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
            InlineNappletStatus.NO_TOR, InlineNappletStatus.FAILED -> {
                Text(
                    stringRes(if (status == InlineNappletStatus.NO_TOR) Res.string.front_page_no_tor else Res.string.front_page_show_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                if (status == InlineNappletStatus.NO_TOR) {
                    Text(stringRes(Res.string.front_page_tor_pictures), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Ink.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) { Text(stringRes(Res.string.retry)) }
            }
            InlineNappletStatus.TOO_LARGE -> {
                // Retrying can't shrink it; the Download can still open it in a browser.
                Text(stringRes(Res.string.front_page_too_large), style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
            }
            InlineNappletStatus.SHOWING -> {}
        }
    }
}

/**
 * Both steps before the first paper, in the setup card at once: the reader sees the second
 * before finishing the first. A done row stays tappable, to change it. A reader with papers
 * already, who removed a step, isn't told it is their first.
 */
@Composable
private fun SetupChecklist(
    setup: FrontPageSetup,
    hasEditions: Boolean,
    onKey: () -> Unit,
    onLens: () -> Unit,
) {
    Column {
        Text(
            stringRes(if (hasEditions) Res.string.front_page_setup_title_again else Res.string.front_page_setup_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        SetupStep(
            label = stringRes(Res.string.front_page_setup_key),
            status = setup.provider?.let { stringRes(Res.string.front_page_setup_using, it) },
            action = stringRes(Res.string.front_page_connect),
            onClick = onKey,
        )
        SetupStep(
            label = stringRes(Res.string.front_page_setup_lens),
            status =
                if (setup.hasLens) {
                    setup.lensName?.let { stringRes(Res.string.front_page_setup_ranked_by, it) } ?: stringRes(Res.string.front_page_setup_on)
                } else {
                    null
                },
            action = stringRes(Res.string.front_page_set_up),
            onClick = onLens,
        )
    }
}

/**
 * One checklist row: a check when [status] says it is done, an empty circle and [action] when
 * not. One element for a screen reader, its state announced, and a button that names what it does.
 */
@Composable
private fun SetupStep(
    label: String,
    status: String?,
    action: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val done = status != null
    val state = stringRes(if (done) Res.string.front_page_setup_done else Res.string.front_page_setup_to_do)
    val clickLabel = if (done) stringRes(Res.string.front_page_setup_change) else action
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) MaterialSymbols.CheckCircle else MaterialSymbols.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) scheme.primary else scheme.outline,
            filled = done,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        // The status or the action, at the end of the row; a long provider name is cut, not the label.
        Text(
            status ?: action,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
            color = if (done) scheme.onSurfaceVariant else scheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            MaterialSymbols.AutoMirrored.KeyboardArrowRight,
            contentDescription = null,
            tint = if (done) scheme.outline else scheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Notice(
    text: String,
    error: Boolean = false,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** `12,480` in the reader's locale (`12.480`, `12 480`, …). */
private fun grouped(n: Long): String = formatGrouped(n)

/** The print in progress, or why the last one gave no page. A clean page shows as a kept edition. */
@Composable
private fun RunStatus(
    run: FrontPageRun,
    onCancel: () -> Unit,
) {
    when (run) {
        FrontPageRun.Idle -> {}
        is FrontPageRun.Running -> Progress(run.step, onCancel)
        is FrontPageRun.Failed -> Notice(failureText(run), error = true)
        is FrontPageRun.Done ->
            when (val result = run.result) {
                is PrintResult.NotReady -> Notice(stringRes(Res.string.front_page_not_ready))
                is PrintResult.Refused -> Refused(result.violations)
                is PrintResult.Printed -> {}
            }
    }
}

@Composable
private fun Progress(
    step: PrintStep,
    onCancel: () -> Unit,
) {
    val text =
        when (step) {
            PrintStep.Probe -> stringRes(Res.string.front_page_step_probe)
            is PrintStep.Pull -> stringRes(Res.string.front_page_step_pull, step.desksDone, step.desksTotal)
            is PrintStep.Write ->
                pluralStringRes(if (step.round == 1) Res.plurals.front_page_step_write else Res.plurals.front_page_step_fix, step.chars, grouped(step.chars.toLong()))
            is PrintStep.Check -> stringRes(Res.string.front_page_step_check)
        }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                // The print belongs to the account: the reader can go, and is told when it's ready.
                Text(stringRes(Res.string.front_page_can_leave), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
        }
    }
}

@Composable
private fun failureText(run: FrontPageRun.Failed): String = if (run.failure == WriterFailure.HTTP) stringRes(run.failure.sentenceRes(), run.status ?: 0) else stringRes(run.failure.sentenceRes())

/** The boundary's verdict kinds in the reader's words, never the enum's name. */
@Composable
private fun violationLabel(kind: Violation.Kind): String =
    stringRes(
        when (kind) {
            Violation.Kind.QUOTE -> Res.string.front_page_violation_quote
            Violation.Kind.IMAGE -> Res.string.front_page_violation_image
            Violation.Kind.LINK -> Res.string.front_page_violation_link
            Violation.Kind.MARKUP -> Res.string.front_page_violation_markup
        },
    )

@Composable
private fun Refused(violations: List<Violation>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.front_page_refused_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringRes(Res.string.front_page_refused_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            violations.take(12).forEach {
                Text("${violationLabel(it.kind)} · ${it.excerpt.take(90)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/** "Today", "Yesterday", then weekday names: the week of kept editions. */
@Composable
private fun dayLabel(day: String): String {
    val today = FrontPageClock.dayKey(TimeUtils.now())
    val yesterday = FrontPageClock.dayKey(TimeUtils.now() - 86_400)
    return when (day) {
        today -> stringRes(Res.string.today)
        yesterday -> stringRes(Res.string.yesterday)
        else ->
            when (dayOfWeek(day)) {
                1 -> stringRes(Res.string.dlist_day_monday)
                2 -> stringRes(Res.string.dlist_day_tuesday)
                3 -> stringRes(Res.string.dlist_day_wednesday)
                4 -> stringRes(Res.string.dlist_day_thursday)
                5 -> stringRes(Res.string.dlist_day_friday)
                6 -> stringRes(Res.string.dlist_day_saturday)
                7 -> stringRes(Res.string.dlist_day_sunday)
                else -> day
            }
    }
}
