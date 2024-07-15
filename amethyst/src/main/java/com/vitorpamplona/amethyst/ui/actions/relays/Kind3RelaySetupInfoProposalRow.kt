/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.ui.note.AddRelayButton
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.largeRelayIconModifier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Kind3RelaySetupInfoProposalRow(
    item: Kind3RelayProposalSetupInfo,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 5.dp),
        ) {
            Column(Modifier.clickable(onClick = onClick)) {
                val iconUrlFromRelayInfoDoc =
                    remember(item) {
                        Nip11CachedRetriever.getFromCache(item.url)?.icon
                    }

                RenderRelayIcon(
                    item.briefInfo.displayUrl,
                    iconUrlFromRelayInfoDoc ?: item.briefInfo.favIcon,
                    loadProfilePicture,
                    loadRobohash,
                    MaterialTheme.colorScheme.largeRelayIconModifier,
                )
            }

            Spacer(modifier = HalfHorzPadding)

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = ReactionRowHeightChat.fillMaxWidth()) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.briefInfo.displayUrl,
                            modifier = Modifier.clickable(onClick = onClick),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (item.paidRelay) {
                            Icon(
                                imageVector = Icons.Default.Paid,
                                null,
                                modifier =
                                    Modifier
                                        .padding(start = 5.dp, top = 1.dp)
                                        .size(14.dp),
                                tint = MaterialTheme.colorScheme.allGoodColor,
                            )
                        }
                    }
                }

                FlowRow(verticalArrangement = Arrangement.Center) {
                    item.users.forEach {
                        UserPicture(
                            userHex = it,
                            size = Size25dp,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }

            Column(
                Modifier
                    .padding(start = 10.dp),
            ) {
                AddRelayButton(onAdd)
            }
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}
