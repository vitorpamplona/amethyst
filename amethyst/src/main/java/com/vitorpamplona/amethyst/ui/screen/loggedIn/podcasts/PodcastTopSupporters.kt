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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivityTopZappersAggregator
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.TopZapperEntry
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ZapContribution
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteZaps
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import java.math.BigDecimal

private val Gold = Color(0xFFFFC300)
private val Silver = Color(0xFFB0B7C0)
private val Bronze = Color(0xFFCD7F32)

/**
 * "Top Supporters" leaderboard for a podcast show: aggregates the zaps on the show note into a
 * sats-ranked list (reusing [LiveActivityTopZappersAggregator], the same engine as the live-stream
 * leaderboard), top 3 flagged with gold/silver/bronze medals. Renders nothing until there's at least
 * one zap. Matches PodStr's ZapLeaderboard, Nostr-native.
 */
@Composable
fun PodcastTopSupporters(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val zapState by observeNoteZaps(note, accountViewModel)

    val entries =
        remember(zapState) {
            val contributions =
                note.zaps.mapNotNull { (_, receiptNote) ->
                    val receipt = receiptNote?.event as? LnZapEvent ?: return@mapNotNull null
                    val request = receipt.zapRequest ?: return@mapNotNull null
                    val sats = receipt.amount()?.toLong() ?: return@mapNotNull null
                    // Anon/private zaps carry an `anon` tag; collapse them into the shared bucket.
                    val isAnon = request.tags.any { it.isNotEmpty() && it[0] == "anon" }
                    ZapContribution(receiptNote.idHex, request.pubKey, isAnon, sats)
                }
            LiveActivityTopZappersAggregator.aggregate(contributions)
        }

    if (entries.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringRes(R.string.podcast_top_supporters),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        entries.forEachIndexed { index, entry ->
            SupporterRow(index, entry, accountViewModel, nav)
        }
    }
}

@Composable
private fun SupporterRow(
    index: Int,
    entry: TopZapperEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RankBadge(index)

        if (entry.isAnonymous) {
            Text(
                text = stringRes(R.string.chat_zap_anonymous),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        } else {
            LoadUser(entry.bucketKey, accountViewModel) { user ->
                if (user != null) {
                    ClickableUserPicture(user, Size35dp, accountViewModel, onClick = { nav.nav(routeFor(it)) })
                    UsernameDisplay(user, Modifier.weight(1f), accountViewModel = accountViewModel)
                } else {
                    Text(
                        text = "",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        ZapIcon(Size16Modifier, BitcoinOrange)
        Text(
            text = showAmountInteger(BigDecimal.valueOf(entry.totalSats)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Gold/silver/bronze medal for the top 3, plain "#N" for the rest. */
@Composable
private fun RankBadge(index: Int) {
    val medal =
        when (index) {
            0 -> Gold
            1 -> Silver
            2 -> Bronze
            else -> null
        }

    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (medal != null) {
            Icon(
                symbol = MaterialSymbols.MilitaryTech,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = medal,
            )
        } else {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}
