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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.podcasts.PodcastValue

/**
 * Renders a Podcasting-2.0 value-for-value split as a tinted card: a "Value-for-Value" header, a
 * "Send value" button (amount picker that fires the weighted Lightning split via
 * [AccountViewModel.payV4V]), and one row per recipient (name/address + its share of the split).
 */
@Composable
fun PodcastValueSplits(
    value: PodcastValue,
    note: Note,
    episodeName: String?,
    podcastName: String?,
    accountViewModel: AccountViewModel,
) {
    val recipients = value.recipients.filter { it.split > 0 || it.address != null }
    if (recipients.isEmpty()) return

    val total = value.totalSplit().takeIf { it > 0 } ?: recipients.size

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(R.string.podcast_value_for_value),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SendValueButton(value, note, episodeName, podcastName, accountViewModel)
        }

        recipients.forEach { recipient ->
            val label = recipient.name?.takeIf { it.isNotEmpty() } ?: recipient.address.orEmpty()
            val percent = recipient.split * 100 / total
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recipient.address
                        ?.takeIf { it.isNotEmpty() && it != label }
                        ?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.grayText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
                Text(
                    text = stringRes(R.string.podcast_value_split_percent, percent),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * "Send value" button: opens a dropdown of the account's configured zap amounts. Picking one fires
 * the V4V split for that many sats through [AccountViewModel.payV4V] (which fans the weighted shares
 * out to each recipient). The recipient list is fixed by the show/episode, so the only choice the
 * user makes is the total amount.
 */
@Composable
private fun SendValueButton(
    value: PodcastValue,
    note: Note,
    episodeName: String?,
    podcastName: String?,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val choices = remember { accountViewModel.zapAmountChoices() }

    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = choices.isNotEmpty(),
        ) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringRes(R.string.podcast_value_send),
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { sats ->
                DropdownMenuItem(
                    text = { Text("$sats ${stringRes(R.string.sats)}") },
                    onClick = {
                        expanded = false
                        accountViewModel.toastManager.toast(
                            R.string.podcast_value_for_value,
                            R.string.podcast_value_sending,
                        )
                        accountViewModel.payV4V(
                            value = value,
                            totalSats = sats,
                            podcastName = podcastName,
                            episodeName = episodeName,
                            zappedNote = note,
                            context = context,
                        )
                    },
                )
            }
        }
    }
}
