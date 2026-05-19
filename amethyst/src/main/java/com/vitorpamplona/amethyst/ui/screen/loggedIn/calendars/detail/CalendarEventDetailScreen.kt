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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.types.CalendarRsvpRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.appointmentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.datasource.CalendarsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.formatCalendarRange
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.relativeTimeLabel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * Dedicated detail screen for a NIP-52 calendar appointment (kind 31922 or 31923). Renders the
 * full event metadata along with three related sections that the inline note render can't
 * easily fit:
 *   - participants (from the event's `p` tags)
 *   - RSVPs (kind 31925 events that a-tag this event)
 *   - calendars this event belongs to (kind 31924 events whose member list includes this event)
 *
 * The "related" sections are snapshotted at composition for simplicity; opening the screen
 * triggers the appointments subscription so freshly-arrived related events are visible on the
 * next entry. A future revision could subscribe to LocalCache.live for true reactivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventDetailScreen(
    kind: Int,
    pubKeyHex: String,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CalendarsFilterAssemblerSubscription(accountViewModel)

    val targetAddress = remember(kind, pubKeyHex, dTag) { Address(kind, pubKeyHex, dTag) }
    val targetNote = remember(targetAddress) { LocalCache.getOrCreateAddressableNote(targetAddress) }
    val noteState by targetNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event

    val isOwnEvent = event?.pubKey == accountViewModel.userProfile().pubkeyHex

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringRes(R.string.route_calendar_event_detail),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    // Export to .ics — available regardless of authorship; anyone viewing the
                    // event may want to drop it into their personal calendar.
                    if (event != null) {
                        IconButton(onClick = {
                            val ics =
                                com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.IcsExport
                                    .appointmentToIcs(
                                        event,
                                        targetAddress,
                                        com.vitorpamplona.quartz.utils.TimeUtils
                                            .now(),
                                    )
                            val filename =
                                com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.IcsExport
                                    .appointmentFilename(event, targetAddress)
                            com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars
                                .shareIcs(context, filename, ics)
                        }) {
                            Icon(
                                symbol = MaterialSymbols.Share,
                                contentDescription = stringRes(R.string.calendar_export_event),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // The Edit affordance is only meaningful when the current account is the
                    // author — relays will reject a signed-by-stranger replacement.
                    if (isOwnEvent && event != null) {
                        IconButton(onClick = {
                            nav.nav(
                                Route.EditCalendarEvent(
                                    kind = event.kind,
                                    pubKeyHex = event.pubKey,
                                    dTag = targetAddress.dTag,
                                ),
                            )
                        }) {
                            Icon(
                                symbol = MaterialSymbols.Edit,
                                contentDescription = stringRes(R.string.edit_calendar_event),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePadding()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (event !is CalendarTimeSlotEvent && event !is CalendarDateSlotEvent) {
                LoadingPlaceholder()
                return@Column
            }
            EventBody(
                note = targetNote,
                accountViewModel = accountViewModel,
                nav = nav,
                targetAddress = targetAddress,
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(R.string.calendar_event_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventBody(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    targetAddress: Address,
) {
    val view = note.appointmentView() ?: return
    val event = note.event ?: return

    val participants =
        remember(note.idHex) {
            when (val e = event) {
                is CalendarTimeSlotEvent -> e.participants()
                is CalendarDateSlotEvent -> e.participants()
                else -> emptyList()
            }
        }

    HeroImage(view.image, accountViewModel)

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        view.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        formatCalendarRange(note)?.let { range ->
            Text(
                text = range,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val relative =
            remember(note.idHex, view.startSeconds) {
                relativeTimeLabel(
                    context,
                    view,
                    com.vitorpamplona.quartz.utils.TimeUtils
                        .now(),
                )
            }
        relative?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        view.location?.let { LocationRow(it) }
        view.summary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalDivider()

    CalendarRsvpRow(
        eventKind = event.kind,
        eventPubKey = event.pubKey,
        eventDTag = targetAddress.dTag,
        eventId = event.id,
        accountViewModel = accountViewModel,
    )

    if (participants.isNotEmpty()) {
        HorizontalDivider()
        ParticipantsSection(participants, accountViewModel, nav)
    }

    HorizontalDivider()
    RsvpsSection(targetAddress, accountViewModel, nav)

    HorizontalDivider()
    InCalendarsSection(targetAddress, accountViewModel, nav)

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun HeroImage(
    image: String?,
    accountViewModel: AccountViewModel,
) {
    if (image.isNullOrBlank()) return
    MyAsyncImage(
        imageUrl = image,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        mainImageModifier = Modifier.fillMaxWidth().height(200.dp),
        loadedImageModifier = Modifier,
        accountViewModel = accountViewModel,
        onLoadingBackground = { Box(modifier = Modifier.fillMaxWidth().height(200.dp)) },
        onError = { Box(modifier = Modifier.fillMaxWidth().height(200.dp)) },
    )
}

@Composable
private fun LocationRow(location: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        // `geo:0,0?q=<location>` is the Android geo intent; falls back to a web
                        // search if no maps app handles geo:.
                        context.startActivity(
                            android.content
                                .Intent(android.content.Intent.ACTION_VIEW, "geo:0,0?q=${android.net.Uri.encode(location)}".let(android.net.Uri::parse))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = location,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = {}) {
            // The button-as-affordance is rendered via the Row's clickable above; the inner
            // TextButton acts as a visual chip with the "open in maps" label.
            Text(text = stringRes(R.string.calendar_open_in_maps))
        }
    }
}

@Composable
private fun ParticipantsSection(
    participants: List<PTag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringRes(R.string.calendar_participants_section, participants.size))
        participants.forEach { p ->
            UserRow(p.pubKey, accountViewModel, nav, trailing = null)
        }
    }
}

@Composable
private fun RsvpsSection(
    targetAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Reactively re-scan when LocalCache emits new bundles. Without this, RSVPs that arrive
    // from relays while the screen is open don't show up until the user leaves and returns.
    val rsvps by rememberRsvpsFor(targetAddress)

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringRes(R.string.calendar_rsvp_section, rsvps.size))
        if (rsvps.isEmpty()) {
            Text(
                text = stringRes(R.string.calendar_rsvp_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        rsvps.forEach { rsvp ->
            UserRow(
                pubKey = rsvp.pubKey,
                accountViewModel = accountViewModel,
                nav = nav,
                trailing = { RsvpStatusBadge(rsvp.status()) },
            )
        }
    }
}

@Composable
private fun InCalendarsSection(
    targetAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val calendars by rememberCalendarsContaining(targetAddress)

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringRes(R.string.calendar_event_in_calendars, calendars.size))
        if (calendars.isEmpty()) {
            Text(
                text = stringRes(R.string.calendar_event_in_no_calendars),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        calendars.forEach { calendar ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            nav.nav(
                                Route.CalendarEventDetail(
                                    kind = CalendarEvent.KIND,
                                    pubKeyHex = calendar.pubKey,
                                    dTag = calendar.dTag(),
                                ),
                            )
                        }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.vitorpamplona.amethyst.ui.note.ClickableUserPicture(
                    baseUserHex = calendar.pubKey,
                    size = com.vitorpamplona.amethyst.ui.theme.Size30dp,
                    accountViewModel = accountViewModel,
                )
                Text(
                    text = calendar.title() ?: stringRes(R.string.calendar_untitled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Shared social-row layout: avatar (clickable → profile), display name, optional trailing slot
 * for things like RSVP badges. Matches the visual language of every other user-list across the
 * app.
 */
@Composable
private fun UserRow(
    pubKey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    trailing: (@Composable () -> Unit)?,
) {
    com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms
        .LoadUser(baseUserHex = pubKey, accountViewModel = accountViewModel) { user ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.vitorpamplona.amethyst.ui.note.ClickableUserPicture(
                    baseUserHex = pubKey,
                    size = com.vitorpamplona.amethyst.ui.theme.Size35dp,
                    accountViewModel = accountViewModel,
                    onClick = {
                        nav.nav(
                            com.vitorpamplona.amethyst.ui.navigation.routes.Route
                                .Profile(pubKey),
                        )
                    },
                )
                if (user != null) {
                    com.vitorpamplona.amethyst.ui.note.UsernameDisplay(
                        baseUser = user,
                        weight = Modifier.weight(1f),
                        accountViewModel = accountViewModel,
                    )
                } else {
                    // LoadUser is still resolving — show the npub-style fallback so the row
                    // doesn't visibly collapse.
                    Text(
                        text = formatPubKeyShort(pubKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailing?.invoke()
            }
        }
}

@Composable
private fun RsvpStatusBadge(status: RSVPStatusTag.STATUS?) {
    val (label, color) =
        when (status) {
            RSVPStatusTag.STATUS.ACCEPTED ->
                stringRes(R.string.calendar_rsvp_going_prefixed) to MaterialTheme.colorScheme.primary
            RSVPStatusTag.STATUS.TENTATIVE ->
                stringRes(R.string.calendar_rsvp_maybe_prefixed) to MaterialTheme.colorScheme.tertiary
            RSVPStatusTag.STATUS.DECLINED ->
                stringRes(R.string.calendar_rsvp_not_going_prefixed) to MaterialTheme.colorScheme.error
            null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

private fun formatPubKeyShort(pubKey: String): String = if (pubKey.length <= 16) pubKey else pubKey.take(8) + "…" + pubKey.takeLast(8)

/**
 * Reactive scan of [LocalCache] for kind-31925 RSVPs that a-tag [targetAddress]. Re-runs on
 * every new-event bundle so RSVPs that arrive while the screen is open appear without a manual
 * refresh. The scan is O(addressables) which is bounded by the relay subscription.
 */
@Composable
private fun rememberRsvpsFor(targetAddress: Address): androidx.compose.runtime.State<List<CalendarRSVPEvent>> =
    androidx.compose.runtime.produceState(initialValue = findRsvpsFor(targetAddress), targetAddress) {
        LocalCache.live.newEventBundles.collect {
            value = findRsvpsFor(targetAddress)
        }
    }

@Composable
private fun rememberCalendarsContaining(targetAddress: Address): androidx.compose.runtime.State<List<CalendarEvent>> =
    androidx.compose.runtime.produceState(initialValue = findCalendarsContaining(targetAddress), targetAddress) {
        LocalCache.live.newEventBundles.collect {
            value = findCalendarsContaining(targetAddress)
        }
    }

private fun findRsvpsFor(targetAddress: Address): List<CalendarRSVPEvent> =
    LocalCache.addressables
        .filterIntoSet { _, note ->
            val e = note.event
            e is CalendarRSVPEvent && e.calendarEventAddress() == targetAddress
        }.mapNotNull { it.event as? CalendarRSVPEvent }
        .sortedByDescending { it.createdAt }

private fun findCalendarsContaining(targetAddress: Address): List<CalendarEvent> =
    LocalCache.addressables
        .filterIntoSet { _, note ->
            val e = note.event
            e is CalendarEvent && e.calendarEventAddresses().contains(targetAddress)
        }.mapNotNull { it.event as? CalendarEvent }
        .sortedByDescending { it.createdAt }
