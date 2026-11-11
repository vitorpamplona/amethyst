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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageClock
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageGate
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageModels
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageRun
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSchedule
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageViewModel
import com.vitorpamplona.amethyst.commons.frontpage.MASTHEAD
import com.vitorpamplona.amethyst.commons.frontpage.MorningScheduleView
import com.vitorpamplona.amethyst.commons.frontpage.ObserverFailure
import com.vitorpamplona.amethyst.commons.frontpage.ObserverPrefs
import com.vitorpamplona.amethyst.commons.frontpage.ObserverStatus
import com.vitorpamplona.amethyst.commons.frontpage.PrintResult
import com.vitorpamplona.amethyst.commons.frontpage.PrintStep
import com.vitorpamplona.amethyst.commons.frontpage.SavedEdition
import com.vitorpamplona.amethyst.commons.frontpage.ShownEdition
import com.vitorpamplona.amethyst.commons.frontpage.Violation
import com.vitorpamplona.amethyst.commons.frontpage.WriterFailure
import com.vitorpamplona.amethyst.commons.frontpage.dayOfWeek
import com.vitorpamplona.amethyst.commons.frontpage.formatUsd
import com.vitorpamplona.amethyst.commons.frontpage.withPaperCsp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
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
import com.vitorpamplona.amethyst.commons.resources.front_page_cannot_show
import com.vitorpamplona.amethyst.commons.resources.front_page_changes_title
import com.vitorpamplona.amethyst.commons.resources.front_page_connect
import com.vitorpamplona.amethyst.commons.resources.front_page_cost_label
import com.vitorpamplona.amethyst.commons.resources.front_page_details
import com.vitorpamplona.amethyst.commons.resources.front_page_download
import com.vitorpamplona.amethyst.commons.resources.front_page_edition_label
import com.vitorpamplona.amethyst.commons.resources.front_page_edition_unreadable
import com.vitorpamplona.amethyst.commons.resources.front_page_every_morning
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_credit
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_cut_off
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_empty
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_http
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_insecure
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_model
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_rate
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_rejected
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_fixed_once
import com.vitorpamplona.amethyst.commons.resources.front_page_last_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_model
import com.vitorpamplona.amethyst.commons.resources.front_page_model_hint
import com.vitorpamplona.amethyst.commons.resources.front_page_model_id
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_device_key
import com.vitorpamplona.amethyst.commons.resources.front_page_never_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_new_edition
import com.vitorpamplona.amethyst.commons.resources.front_page_no_key
import com.vitorpamplona.amethyst.commons.resources.front_page_no_lens
import com.vitorpamplona.amethyst.commons.resources.front_page_no_tor
import com.vitorpamplona.amethyst.commons.resources.front_page_not_now
import com.vitorpamplona.amethyst.commons.resources.front_page_not_printed_since
import com.vitorpamplona.amethyst.commons.resources.front_page_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_notice_body
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_notice_title
import com.vitorpamplona.amethyst.commons.resources.front_page_open_observer
import com.vitorpamplona.amethyst.commons.resources.front_page_pick_time
import com.vitorpamplona.amethyst.commons.resources.front_page_posts_read
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
import com.vitorpamplona.amethyst.commons.resources.front_page_quotes_verified
import com.vitorpamplona.amethyst.commons.resources.front_page_read_full
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_body
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_title
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_cannot_sign
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_line
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_unreadable
import com.vitorpamplona.amethyst.commons.resources.front_page_scheduled
import com.vitorpamplona.amethyst.commons.resources.front_page_set_up
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
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.resources.yesterday
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.PillColors
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileBytesAccess
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.calendarYearAndDay
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

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
    val models by viewModel.models.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val editions by viewModel.editions.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val estimate by viewModel.estimate.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val scheduleProblem by viewModel.scheduleProblem.collectAsStateWithLifecycle()
    val scheduleActions =
        remember(viewModel) {
            ScheduleActions(viewModel::setScheduleEnabled, viewModel::setScheduleTime, viewModel::setPrintsHere)
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

    val current = shown
    Scaffold(
        topBar = {
            if (current == null) {
                TopBarWithBackButton(stringRes(Res.string.front_page_title), nav)
            } else {
                TopBarWithBackButton(editionTitle(current.edition.day), nav) {
                    if (gate == FrontPageGate.READY) {
                        IconButton(onClick = { printSheet = true }) {
                            Icon(MaterialSymbols.AutoAwesome, contentDescription = stringRes(Res.string.front_page_print_again))
                        }
                    }
                    if (current.html != null) {
                        IconButton(onClick = { download(current) }) {
                            Icon(MaterialSymbols.Download, contentDescription = stringRes(Res.string.front_page_download))
                        }
                    }
                    if (platform.showsPapersInline && current.html != null) {
                        IconButton(onClick = { nav.nav(Route.FrontPageReader(current.edition.day)) }) {
                            Icon(MaterialSymbols.Fullscreen, contentDescription = stringRes(Res.string.front_page_read_full))
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
                    // First run: the print controls stay inline, on the gradient.
                    if (gate == FrontPageGate.READY) {
                        PrintControls(
                            models,
                            run,
                            estimate,
                            hasEditions = false,
                            schedule,
                            scheduleProblem,
                            scheduleActions,
                            onModel = viewModel::selectModel,
                            onPrint = requestPrint,
                            onCancel = viewModel::cancel,
                            publish = publishPrefs,
                            onPublish = viewModel::setPublish,
                            onAccent = true,
                        )
                    }
                }
            }
            when (gate) {
                FrontPageGate.NO_KEY -> GateCard(Res.string.front_page_no_key, Res.string.front_page_connect) { nav.nav(Route.AgentKeysSettings) }
                FrontPageGate.NO_LENS -> GateCard(Res.string.front_page_no_lens, Res.string.front_page_set_up) { nav.nav(Route.WebOfTrust) }
                FrontPageGate.UNSUPPORTED -> Notice(stringRes(Res.string.front_page_unsupported))
                FrontPageGate.READY -> RunStatus(run, onCancel = viewModel::cancel)
            }
            if (current != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (editions.size > 1) History(editions, current.edition.day, viewModel::selectDay)
                    Facts(current.edition, onDetails = { detailsSheet = true })
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
                    scheduleActions,
                    onModel = viewModel::selectModel,
                    onPrint = {
                        printSheet = false
                        requestPrint()
                    },
                    onCancel = viewModel::cancel,
                    publish = publishPrefs,
                    onPublish = viewModel::setPublish,
                    onAccent = false,
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

/** First run and setup: what the page is, on the gradient, with whatever comes next inside it. */
@Composable
private fun Hero(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(onHero.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(MaterialSymbols.AutoMirrored.Article, contentDescription = null, tint = onHero, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                // The top bar names the screen; the hero only says what it is for.
                Text(
                    stringRes(Res.string.front_page_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onHero,
                    modifier = Modifier.weight(1f),
                )
            }
            content()
        }
    }
}

/**
 * Model, Print (with its estimate) or Cancel, and the morning schedule: inline on the first-run
 * hero ([onAccent]), or on the print sheet's surface.
 */
@Composable
private fun PrintControls(
    models: FrontPageModels,
    run: FrontPageRun,
    estimate: Double?,
    hasEditions: Boolean,
    schedule: MorningScheduleView,
    scheduleProblem: ScheduleEdit?,
    scheduleActions: ScheduleActions,
    onModel: (String) -> Unit,
    onPrint: () -> Unit,
    onCancel: () -> Unit,
    publish: ObserverPrefs?,
    onPublish: (Boolean) -> Unit,
    onAccent: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary
    val ink = if (onAccent) onHero else scheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelPicker(models, onModel, if (onAccent) PillColors.onAccent(onHero) else PillColors.onCard(), ink)
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
                colors =
                    if (onAccent) {
                        ButtonDefaults.buttonColors(
                            containerColor = onHero,
                            contentColor = scheme.primary,
                            disabledContainerColor = onHero.copy(alpha = 0.3f),
                            disabledContentColor = scheme.primary.copy(alpha = 0.6f),
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
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
        ScheduleRow(schedule, scheduleProblem, scheduleActions, ink)
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
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(shape)
                .background(colors.container)
                .clickable { expanded = true }
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = colors.icon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringRes(Res.string.front_page_model), style = MaterialTheme.typography.labelSmall, color = colors.placeholder)
                Text(models.selected.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = colors.icon)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.ids.forEach { id ->
                DropdownMenuItem(text = { Text(id) }, onClick = {
                    onModel(id)
                    expanded = false
                })
            }
        }
    }
}

/** What the schedule controls do: the first two edit the synced schedule, the last this device only. */
@Stable
class ScheduleActions(
    val onEnabled: (Boolean) -> Unit,
    val onTime: (hour: Int, minute: Int) -> Unit,
    val onPrintsHere: (Boolean) -> Unit,
)

/**
 * "Every morning" with its time (the reader's clock: 6:00 AM or 06:00) and a switch, for the
 * schedule all of the reader's devices share; then "Print from this device", who printed last,
 * and a warning when a due morning didn't print. A schedule the editor can't show (a cron set
 * elsewhere) is shown as written, read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRow(
    schedule: MorningScheduleView,
    problem: ScheduleEdit?,
    actions: ScheduleActions,
    ink: Color,
) {
    var picking by remember { mutableStateOf(false) }
    val time = rememberScheduleTime(schedule.schedule())
    val simple = schedule.simple
    val daily = simple != null && simple.weekdays == null
    val note = ink.copy(alpha = 0.75f)
    val indent = Modifier.padding(start = 34.dp)
    Column {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Schedule, contentDescription = null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringRes(if (daily) Res.string.front_page_every_morning else Res.string.front_page_scheduled),
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            if (schedule.readable) {
                if (simple != null) {
                    TextButton(onClick = { picking = true }, colors = ButtonDefaults.textButtonColors(contentColor = ink)) {
                        Text(time, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Switch(checked = schedule.enabled, onCheckedChange = actions.onEnabled)
            }
        }
        if (!schedule.readable) {
            ScheduleNote(stringRes(Res.string.front_page_schedule_unreadable), note, indent)
        } else if (!daily) {
            // A weekday or cron schedule says exactly what it is.
            ScheduleNote(schedule.cron, note, indent)
        }
        Row(Modifier.heightIn(min = 40.dp).then(indent), verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(Res.string.front_page_print_from_device), style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.weight(1f))
            Switch(checked = schedule.printsHere && schedule.canPrintHere, onCheckedChange = actions.onPrintsHere, enabled = schedule.canPrintHere)
        }
        // Amber and remote signers need the reader to decrypt the AI key: say so, in few words.
        if (!schedule.canPrintHere) ScheduleNote(stringRes(Res.string.front_page_needs_device_key), note, indent)
        LastPrinted(schedule, note, indent)
        ScheduleWarnings(schedule, indent, ink)
        problem?.let { ScheduleNote(stringRes(scheduleProblemText(it)), MaterialTheme.colorScheme.error, indent) }
    }
    if (picking) {
        val start = remember(schedule) { pickerStart(schedule) }
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

private fun scheduleProblemText(problem: ScheduleEdit) =
    when (problem) {
        ScheduleEdit.Unreadable -> Res.string.front_page_schedule_unreadable
        ScheduleEdit.CannotSign -> Res.string.front_page_schedule_cannot_sign
        else -> Res.string.front_page_schedule_unreachable
    }

/** The picker opens on the schedule's time as this device's clock reads it, from the cron's own fields when it can. */
private fun pickerStart(schedule: MorningScheduleView): Pair<Int, Int> = schedule.schedule().localTime(TimeUtils.now()) ?: schedule.simple?.let { it.hour to it.minute } ?: (7 to 0)

@Composable
private fun ScheduleNote(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** "Last printed by Desktop (Linux), 2 hours ago": the last claim's device and when it finished; "Not printed yet" before. */
@Composable
private fun LastPrinted(
    schedule: MorningScheduleView,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (!schedule.readable) return
    val ran = schedule.lastRan
    if (ran == null) {
        if (schedule.enabled) ScheduleNote(stringRes(Res.string.front_page_never_printed), color, modifier)
        return
    }
    val by = schedule.lastBy ?: return
    val labels = rememberTimeAgoLabels()
    ScheduleNote(stringRes(Res.string.front_page_last_printed, by, timeAgoShort(ran, labels)), color, modifier)
}

/** A due morning that didn't print, shown on every device: dates on this device's calendar. */
@Composable
private fun ScheduleWarnings(
    schedule: MorningScheduleView,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onSurface,
) {
    val warn = if (ink == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.error else ink
    schedule.missedSince?.let { ran ->
        ScheduleNote(stringRes(Res.string.front_page_not_printed_since, editionTitle(FrontPageClock.dayKey(ran))), warn, modifier)
    }
}

/** The schedule's time on the reader's clock, kept current: a screen left open overnight moves on. */
@Composable
private fun rememberScheduleTime(schedule: FrontPageSchedule): String {
    val format = rememberTimeOfDayFormatter()
    val now by produceState(TimeUtils.now()) {
        while (true) {
            delay(60_000)
            value = TimeUtils.now()
        }
    }
    return remember(format, schedule.cron, schedule.zone, now / 3_600) {
        schedule.displayRun(now)?.let { format(it * 1000) } ?: schedule.cron
    }
}

/** When the schedule is on, one line under the facts: when, and the way to change it. */
@Composable
private fun ScheduleLine(
    schedule: MorningScheduleView,
    onOpen: () -> Unit,
) {
    if (!schedule.readable) return
    val time = rememberScheduleTime(schedule.schedule())
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringRes(Res.string.front_page_schedule_line, time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** One quiet line: what the edition read, what it verified, what it cost. The rest is a tap away. */
@Composable
private fun Facts(
    edition: SavedEdition,
    onDetails: () -> Unit,
) {
    val facts =
        listOfNotNull(
            pluralStringRes(Res.plurals.front_page_posts_read, edition.posts, edition.posts),
            pluralStringRes(Res.plurals.front_page_quotes_verified, edition.quotes, edition.quotes),
            edition.costUsd?.let { formatUsd(it) },
        ).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(facts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        IconButton(onClick = onDetails) {
            Icon(MaterialSymbols.Info, contentDescription = stringRes(Res.string.front_page_details), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** The edition's particulars: code, model, tokens, and anything the check took out. */
@Composable
private fun Details(
    edition: SavedEdition,
    site: NamedSiteEvent?,
    onOpenSite: ((NamedSiteEvent) -> Unit)?,
    onShareSite: (NamedSiteEvent) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringRes(Res.string.front_page_details), style = MaterialTheme.typography.titleLarge)
        // The reader's public Observer site, once there is one: open it as any nSite, or share it.
        site?.let { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
        DetailRow(stringRes(Res.string.front_page_edition_label), edition.code)
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

@Composable
private fun GateCard(
    message: StringResource,
    action: StringResource,
    onAction: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(message), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(stringRes(action)) }
        }
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
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
        }
    }
}

@Composable
private fun failureText(run: FrontPageRun.Failed): String =
    when (run.failure) {
        WriterFailure.REJECTED -> stringRes(Res.string.front_page_fail_rejected)
        WriterFailure.NO_CREDIT -> stringRes(Res.string.front_page_fail_credit)
        WriterFailure.RATE_LIMITED -> stringRes(Res.string.front_page_fail_rate)
        WriterFailure.UNKNOWN_MODEL -> stringRes(Res.string.front_page_fail_model)
        WriterFailure.EMPTY -> stringRes(Res.string.front_page_fail_empty)
        WriterFailure.HTTP -> stringRes(Res.string.front_page_fail_http, run.status ?: 0)
        WriterFailure.UNSUPPORTED -> stringRes(Res.string.front_page_unsupported)
        WriterFailure.UNREACHABLE -> stringRes(Res.string.front_page_fail_unreachable)
        WriterFailure.CUT_OFF -> stringRes(Res.string.front_page_fail_cut_off)
        WriterFailure.INSECURE_URL -> stringRes(Res.string.front_page_fail_insecure)
    }

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
