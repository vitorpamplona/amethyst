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
package com.vitorpamplona.amethyst.commons.observer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.observer.ObserverRoundup
import com.vitorpamplona.amethyst.commons.observer.ObserverRoundupKind
import com.vitorpamplona.amethyst.commons.observer.ObserverRoundupLine
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.observer_roundup_conversation
import com.vitorpamplona.amethyst.commons.resources.observer_roundup_live
import com.vitorpamplona.amethyst.commons.resources.observer_roundup_people
import com.vitorpamplona.amethyst.commons.resources.observer_roundup_releases
import com.vitorpamplona.amethyst.commons.resources.observer_roundup_topic
import com.vitorpamplona.amethyst.commons.resources.observer_roundup_upcoming
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event

private val Serif = FontFamily.Serif

/**
 * A summary post: several items in one card — a conversation and its trusted
 * replies, a topic and the best line from each person on it, what is on the
 * air, what is coming up, what was released. Built on the device, never
 * published. Each line is its own tap target and opens that event.
 */
@Composable
fun ObserverRoundupCard(
    roundup: ObserverRoundup,
    accountViewModel: AccountViewModel,
    nav: INav,
    onOpen: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon(roundup.kind), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    title(roundup).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringRes(Res.plurals.observer_roundup_people, roundup.people, roundup.people),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            roundup.anchor?.let { anchor ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(anchor.event) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(anchor.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(anchor.byline, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(horizontal = 14.dp))
            }

            Spacer(Modifier.height(4.dp))
            roundup.lines.forEach { line -> Line(line, accountViewModel, nav, onOpen) }
        }
    }
}

@Composable
private fun Line(
    line: ObserverRoundupLine,
    accountViewModel: AccountViewModel,
    nav: INav,
    onOpen: (Event) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(line.event) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserPicture(userHex = line.author, size = 22.dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            Text(line.byline, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line.text, fontFamily = Serif, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            line.detail?.let {
                Text(
                    observerDetailText(it, line.event.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun title(roundup: ObserverRoundup): String =
    when (roundup.kind) {
        ObserverRoundupKind.CONVERSATION -> stringRes(Res.string.observer_roundup_conversation)
        ObserverRoundupKind.TOPIC -> stringRes(Res.string.observer_roundup_topic, roundup.topic ?: "")
        ObserverRoundupKind.LIVE -> stringRes(Res.string.observer_roundup_live)
        ObserverRoundupKind.UPCOMING -> stringRes(Res.string.observer_roundup_upcoming)
        ObserverRoundupKind.RELEASES -> stringRes(Res.string.observer_roundup_releases)
    }

private fun icon(kind: ObserverRoundupKind): MaterialSymbol =
    when (kind) {
        ObserverRoundupKind.CONVERSATION -> MaterialSymbols.Forum
        ObserverRoundupKind.TOPIC -> MaterialSymbols.Tag
        ObserverRoundupKind.LIVE -> MaterialSymbols.Sensors
        ObserverRoundupKind.UPCOMING -> MaterialSymbols.CalendarMonth
        ObserverRoundupKind.RELEASES -> MaterialSymbols.Apps
    }
