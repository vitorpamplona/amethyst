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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.chatDayKey
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_all_day
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_going
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_maybe
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_none
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_not_going
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_section
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_calendar_cancelled
import com.vitorpamplona.amethyst.commons.resources.cordn_calendar_declined
import com.vitorpamplona.amethyst.commons.resources.cordn_calendar_going
import com.vitorpamplona.amethyst.commons.resources.cordn_calendar_maybe
import com.vitorpamplona.amethyst.commons.resources.cordn_poll_closed
import com.vitorpamplona.amethyst.commons.resources.cordn_poll_closes
import com.vitorpamplona.amethyst.commons.resources.poll_change_vote
import com.vitorpamplona.amethyst.commons.resources.poll_multiple_choice
import com.vitorpamplona.amethyst.commons.resources.poll_results_option_count
import com.vitorpamplona.amethyst.commons.resources.poll_results_your_pick
import com.vitorpamplona.amethyst.commons.resources.poll_single_choice
import com.vitorpamplona.amethyst.commons.resources.poll_submit
import com.vitorpamplona.amethyst.commons.resources.poll_unsupported
import com.vitorpamplona.amethyst.commons.resources.poll_vote
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.cordn.spec02Envelopes.CordnCalendar
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.CordnPolls
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/**
 * A cordn poll (NIP-88 kind 1068, [CordnPolls]) drawn as a card in its bubble: the question, each
 * option with its share of the votes, the reader's own pick, and voting while it is open.
 *
 * The tally comes from the room's one fold of every poll's votes ([CordnAnnotationIndex.tallyOf]),
 * so which votes count (one per account, the latest the coordinator sequenced, none after the
 * deadline) is cordn-kmp's rule and cordn-web's, never this screen's, and a card does not walk the
 * whole room for its own poll. A vote is the voter's whole selection: changing it sends a new one.
 */
@Composable
fun CordnPollCard(
    message: CordnDeliveredMessage,
    annotations: CordnAnnotationIndex,
    me: HexKey,
    onVote: (List<String>) -> Unit,
) {
    val endsAt = remember(message.envelope) { CordnPolls.definition(message.envelope.kind, message.envelope.content, message.envelope.tags)?.endsAt }
    val now = rememberNowUntil(endsAt)
    val tally = remember(annotations, message.envelope.id, me, now) { annotations.tallyOf(message.envelope.id, me, now) }

    if (tally == null) {
        Text(
            text = stringRes(Res.string.poll_unsupported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val definition = tally.definition
    var choosing by remember(message.envelope.id) { mutableStateOf(false) }
    val voting = tally.isOpen && (choosing || tally.selection.isEmpty())

    Column(Modifier.widthIn(min = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = definition.question,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringRes(if (definition.type == CordnPolls.Type.MULTIPLE) Res.string.poll_multiple_choice else Res.string.poll_single_choice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )

        if (voting) {
            CordnPollChoices(tally) { ids ->
                choosing = false
                onVote(ids)
            }
        } else {
            tally.options.forEach { option -> CordnPollResult(option, tally.participants, option.id in tally.selection) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text =
                    pluralStringRes(Res.plurals.poll_results_option_count, tally.participants, tally.participants) +
                        (definition.endsAt?.let { " · " + if (tally.isOpen) stringRes(Res.string.cordn_poll_closes, formatDateTimeSeconds(it)) else stringRes(Res.string.cordn_poll_closed) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (tally.isOpen && tally.selection.isNotEmpty()) {
                Text(
                    text = stringRes(if (choosing) Res.string.cancel else Res.string.poll_change_vote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { choosing = !choosing }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** The options, to pick from: one tap votes in a single-choice poll; a multiple-choice one ticks, then submits. */
@Composable
private fun CordnPollChoices(
    tally: CordnPolls.Tally,
    onVote: (List<String>) -> Unit,
) {
    val multiple = tally.definition.type == CordnPolls.Type.MULTIPLE
    var picked by remember(tally.definition) { mutableStateOf(tally.selection.toSet()) }

    tally.options.forEach { option ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SmallishBorder)
                    .border(1.dp, MaterialTheme.colorScheme.grayText, SmallishBorder)
                    .clickable {
                        if (multiple) {
                            picked = if (option.id in picked) picked - option.id else picked + option.id
                        } else {
                            onVote(listOf(option.id))
                        }
                    }.padding(horizontal = if (multiple) 0.dp else 12.dp, vertical = if (multiple) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiple) {
                Checkbox(
                    checked = option.id in picked,
                    onCheckedChange = { checked -> picked = if (checked) picked + option.id else picked - option.id },
                )
            }
            Text(option.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }

    if (multiple) {
        Button(
            onClick = { onVote(tally.options.map { it.id }.filter { it in picked }) },
            enabled = picked.isNotEmpty(),
        ) {
            Text(stringRes(if (tally.selection.isEmpty()) Res.string.poll_submit else Res.string.poll_vote))
        }
    }
}

/** One option's result: a bar filled to its share of the voters, its count, and a tick on your pick. */
@Composable
private fun CordnPollResult(
    option: CordnPolls.OptionResult,
    participants: Int,
    isMine: Boolean,
) {
    val share = if (participants == 0) 0f else option.votes.toFloat() / participants
    val barColor = if (isMine) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.primary

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SmallishBorder)
                .border(1.dp, if (isMine) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.grayText, SmallishBorder),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    clipRect(right = size.width * share) { drawRect(barColor.copy(alpha = 0.25f)) }
                },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isMine) {
                Icon(
                    symbol = MaterialSymbols.CheckCircle,
                    contentDescription = stringRes(Res.string.poll_results_your_pick),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.allGoodColor,
                )
            }
            Text(option.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(option.votes.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
            Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A cordn calendar event (NIP-52 kind 31922/31923, `calendar-events.md`) as a card: its title, when
 * and where, whether its author cancelled it, and who answered its RSVP ([CordnCalendar.fold]).
 *
 * [attendees] is the fold's answer for this revision, one RSVP per account, the newest counting.
 * [onRsvp] answers it, or is null where this card offers no answer (a cancelled event).
 */
@Composable
fun CordnCalendarCard(
    message: CordnDeliveredMessage,
    annotations: CordnAnnotationIndex,
    attendees: CordnCalendar.Attendees?,
    me: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRsvp: ((CordnCalendar.RsvpStatus) -> Unit)? = null,
) {
    val envelope = message.envelope
    val cancelled = annotations.isCancelled(envelope.id)
    val whenText = remember(envelope) { calendarWhen(envelope) }
    val location =
        remember(envelope) {
            envelope.tags
                .firstOrNull { it.size > 1 && it[0] == "location" }
                ?.get(1)
                ?.takeIf { it.isNotBlank() }
        }
    val description = remember(envelope) { (envelope.tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1) ?: envelope.content).trim().takeIf { it.isNotEmpty() } }

    var showWho by remember(envelope.id) { mutableStateOf(false) }

    Column(Modifier.widthIn(min = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cancelled) {
            Text(
                text = stringRes(Res.string.cordn_calendar_cancelled),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = CordnCalendar.titleOf(envelope),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )

        whenText?.let { (text, allDay) ->
            CalendarLine(MaterialSymbols.CalendarMonth, if (allDay) "$text · ${stringRes(Res.string.calendar_all_day)}" else text)
        }
        location?.let { CalendarLine(MaterialSymbols.LocationOn, it) }
        description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }

        val going = attendees?.going.orEmpty()
        val maybe = attendees?.maybe.orEmpty()
        val declined = attendees?.declined.orEmpty()
        val answered = going.size + maybe.size + declined.size

        Text(
            text =
                if (answered == 0) {
                    stringRes(Res.string.calendar_rsvp_none)
                } else {
                    listOf(
                        pluralStringRes(Res.plurals.cordn_calendar_going, going.size, going.size),
                        pluralStringRes(Res.plurals.cordn_calendar_maybe, maybe.size, maybe.size),
                        pluralStringRes(Res.plurals.cordn_calendar_declined, declined.size, declined.size),
                    ).joinToString(" · ")
                },
            style = MaterialTheme.typography.bodySmall,
            color = if (answered == 0) MaterialTheme.colorScheme.placeholderText else MaterialTheme.colorScheme.primary,
            modifier = if (answered == 0) Modifier else Modifier.clickable { showWho = true }.padding(vertical = 2.dp),
        )

        if (onRsvp != null && !cancelled) {
            val mine =
                when (me) {
                    in going -> CordnCalendar.RsvpStatus.ACCEPTED
                    in maybe -> CordnCalendar.RsvpStatus.TENTATIVE
                    in declined -> CordnCalendar.RsvpStatus.DECLINED
                    else -> null
                }
            CordnRsvpButtons(mine, onRsvp)
        }
    }

    if (showWho && attendees != null) {
        CordnAttendeesSheet(attendees, accountViewModel, nav) { showWho = false }
    }
}

/** The three answers, the reader's own highlighted. Answering again replaces it: the newest RSVP counts. */
@Composable
private fun CordnRsvpButtons(
    mine: CordnCalendar.RsvpStatus?,
    onRsvp: (CordnCalendar.RsvpStatus) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            CordnCalendar.RsvpStatus.ACCEPTED to Res.string.calendar_rsvp_going,
            CordnCalendar.RsvpStatus.TENTATIVE to Res.string.calendar_rsvp_maybe,
            CordnCalendar.RsvpStatus.DECLINED to Res.string.calendar_rsvp_not_going,
        ).forEach { (status, label) ->
            FilterChip(
                selected = mine == status,
                // Sending the answer you already gave would only add a duplicate.
                onClick = { if (mine != status) onRsvp(status) },
                label = { Text(stringRes(label), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun CalendarLine(
    symbol: MaterialSymbol,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.placeholderText)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Who answered, grouped by answer, as the reaction sheet lists who reacted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnAttendeesSheet(
    attendees: CordnCalendar.Attendees,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val total = attendees.going.size + attendees.maybe.size + attendees.declined.size
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                text = stringRes(Res.string.calendar_rsvp_section, total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            listOf(
                pluralStringRes(Res.plurals.cordn_calendar_going, attendees.going.size, attendees.going.size) to attendees.going,
                pluralStringRes(Res.plurals.cordn_calendar_maybe, attendees.maybe.size, attendees.maybe.size) to attendees.maybe,
                pluralStringRes(Res.plurals.cordn_calendar_declined, attendees.declined.size, attendees.declined.size) to attendees.declined,
            ).forEach { (label, who) ->
                if (who.isEmpty()) return@forEach
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                who.forEach { pubKey ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserPicture(userHex = pubKey, size = Size20dp, accountViewModel = accountViewModel, nav = nav)
                        Text(
                            text = observeUserNameByHex(pubKey, accountViewModel),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * When a calendar event happens, in the reader's zone, and whether it is all-day. A date-based
 * event (31922) has ISO dates and an exclusive `end`; a time-based one (31923) Unix seconds.
 * Null when the event carries no start this can read ([CordnCalendar.isValidEvent] checked it).
 */
private fun calendarWhen(envelope: CordnEnvelope): Pair<String, Boolean>? {
    fun tag(name: String) =
        envelope.tags
            .firstOrNull { it.size > 1 && it[0] == name }
            ?.get(1)
            ?.trim()
    return runCatching {
        if (envelope.kind == CordnMessageKinds.CALENDAR_DATE) {
            // ISO days, read strictly (an impossible day is refused, not rolled over), and shown
            // from the reader's own midnight so the day printed is the day written.
            fun dateLabel(day: SearchDate) = formatDateTime(LocalClock.startOfDay(day) * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)
            val start = SearchDate.parse(tag("start")) ?: return null
            val last = tag("end")?.takeIf { it.isNotEmpty() }?.let { SearchDate.parse(it)?.plusDays(-1) ?: return null }
            val text = if (last == null || last <= start) dateLabel(start) else "${dateLabel(start)} – ${dateLabel(last)}"
            text to true
        } else {
            val start = tag("start")!!.toDouble().toLong()
            val end = tag("end")?.takeIf { it.isNotEmpty() }?.toDouble()?.toLong()
            val text =
                when {
                    end == null -> formatDateTimeSeconds(start)
                    chatDayKey(end) == chatDayKey(start) -> "${formatDateTimeSeconds(start)} – ${formatDateTime(end * 1000, DateTimeStyle.NONE, DateTimeStyle.SHORT)}"
                    else -> "${formatDateTimeSeconds(start)} – ${formatDateTimeSeconds(end)}"
                }
            text to false
        }
    }.getOrNull()
}

private fun formatDateTimeSeconds(epochSeconds: Long): String = formatDateTime(epochSeconds * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)

/**
 * Now, in Unix seconds, refreshed until [until] has passed, so a poll closes on screen at its
 * deadline rather than at the next unrelated recomposition. Without a deadline it never ticks.
 */
@Composable
private fun rememberNowUntil(until: Long?): Long =
    produceState(TimeUtils.now(), until) {
        if (until == null) return@produceState
        while (value <= until) {
            delay(((until - value + 1) * 1000).coerceIn(1_000L, 60_000L))
            value = TimeUtils.now()
        }
    }.value
