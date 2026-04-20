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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteZaps
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import java.math.BigDecimal

private const val TOP_ZAPPERS_LIMIT = 10

/** Sentinel key used to bucket every anonymous/private zap into a single leaderboard entry. */
private const val ANON_KEY = "anon"

private data class TopZapperEntry(
    val zapperPubKey: HexKey,
    val totalSats: BigDecimal,
    val isAnonymous: Boolean,
)

/**
 * Horizontally-scrollable leaderboard of the top zappers on a live stream.
 * Aggregates zaps from both the stream's #a subscription and the attached
 * NIP-75 zap goal (if any), de-duplicating by zap-receipt id. Matches
 * zap.stream's TopZappers UX: pill chips with avatar + lightning + sats.
 */
@Composable
fun LiveStreamTopZappers(
    channel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val goalId = channel.info?.goalEventId()

    if (goalId != null) {
        var goalNote by remember(goalId) { mutableStateOf(accountViewModel.getNoteIfExists(goalId)) }
        if (goalNote == null) {
            LaunchedEffect(goalId) {
                goalNote = accountViewModel.checkGetOrCreateNote(goalId)
            }
        }
        TopZappersStrip(channel, goalNote, accountViewModel, nav)
    } else {
        TopZappersStrip(channel, null, accountViewModel, nav)
    }
}

@Composable
private fun TopZappersStrip(
    channel: LiveActivitiesChannel,
    goalNote: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Trigger recomposition whenever a new zap lands in the channel cache.
    val channelTick by channel.changesFlow().collectAsStateWithLifecycle(initialValue = null)

    // Trigger recomposition when zaps arrive for the goal note.
    val goalZapsState =
        if (goalNote != null) {
            observeNoteZaps(goalNote, accountViewModel).value
        } else {
            null
        }

    val entries =
        remember(channel, channelTick, goalNote, goalZapsState) {
            aggregateTopZappers(channel, goalNote)
        }

    if (entries.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(entries, key = { it.zapperPubKey }) { entry ->
            ZapperPill(entry, accountViewModel, nav)
        }
    }
}

@Composable
private fun ZapperPill(
    entry: TopZapperEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clickModifier =
        if (entry.isAnonymous) {
            Modifier
        } else {
            Modifier.clickable { nav.nav(Route.Profile(entry.zapperPubKey)) }
        }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = clickModifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (entry.isAnonymous) {
                Text(
                    text = stringRes(R.string.chat_zap_anonymous),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                UserPicture(
                    userHex = entry.zapperPubKey,
                    size = Size24dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
            ZapIcon(Size16Modifier, BitcoinOrange)
            Text(
                text = showAmountInteger(entry.totalSats),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.padding(start = 2.dp))
        }
    }
}

private fun aggregateTopZappers(
    channel: LiveActivitiesChannel,
    goalNote: Note?,
): List<TopZapperEntry> {
    // receiptId -> (zapperBucketKey, sats) — dedupes a zap that appears via both #a and #e.
    val byReceipt = HashMap<HexKey, Pair<HexKey, BigDecimal>>()

    // Stream-level zaps routed to the channel cache.
    channel.notes.forEach { _, note ->
        val ev = note.event
        if (ev is LnZapEvent) {
            val request = ev.zapRequest ?: return@forEach
            val sats = ev.amount() ?: return@forEach
            byReceipt[note.idHex] = bucketKeyFor(request) to sats
        }
    }

    // Goal-scoped zaps attached to the goal note via #e.
    goalNote?.zaps?.forEach { (zapRequestNote, receiptNote) ->
        val receiptEv = receiptNote?.event as? LnZapEvent ?: return@forEach
        val request = zapRequestNote.event as? LnZapRequestEvent ?: return@forEach
        val sats = receiptEv.amount() ?: return@forEach
        byReceipt[receiptNote.idHex] = bucketKeyFor(request) to sats
    }

    if (byReceipt.isEmpty()) return emptyList()

    val totals = HashMap<HexKey, BigDecimal>(byReceipt.size)
    byReceipt.values.forEach { (pk, sats) ->
        totals[pk] = (totals[pk] ?: BigDecimal.ZERO) + sats
    }

    return totals.entries
        .asSequence()
        .sortedByDescending { it.value }
        .take(TOP_ZAPPERS_LIMIT)
        .map { TopZapperEntry(it.key, it.value, isAnonymous = it.key == ANON_KEY) }
        .toList()
}

/** Returns the aggregation key for a zap request: real pubkey, or the anon sentinel for any `anon`-tagged zap. */
private fun bucketKeyFor(request: LnZapRequestEvent): HexKey = if (request.tags.any { it.isNotEmpty() && it[0] == "anon" }) ANON_KEY else request.pubKey
