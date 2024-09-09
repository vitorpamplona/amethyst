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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.AddRelayButton
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChatMaxWidth
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.largeRelayIconModifier
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelayStat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Kind3RelaySetupInfoProposalRow(
    item: Kind3RelayProposalSetupInfo,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 5.dp),
        ) {
            val iconUrlFromRelayInfoDoc =
                remember(item) {
                    Nip11CachedRetriever.getFromCache(item.url)?.icon
                }

            RenderRelayIcon(
                item.briefInfo.displayUrl,
                iconUrlFromRelayInfoDoc ?: item.briefInfo.favIcon,
                loadProfilePicture,
                loadRobohash,
                item.relayStat.pingInMs,
                MaterialTheme.colorScheme.largeRelayIconModifier,
            )

            Spacer(modifier = HalfHorzPadding)

            Column(Modifier.weight(1f)) {
                Row(ReactionRowHeightChatMaxWidth, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.briefInfo.displayUrl,
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

            UsedBy(item, accountViewModel, nav)

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

@Preview
@Composable
fun UsedByPreview() {
    ThemeComparisonColumn {
        UsedBy(
            item =
                Kind3RelayProposalSetupInfo(
                    "wss://nos.lol",
                    true,
                    true,
                    COMMON_FEED_TYPES,
                    relayStat = RelayStat(),
                    paidRelay = false,
                    users = listOf("User1", "User2", "User3", "User4"),
                ),
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsedBy(
    item: Kind3RelayProposalSetupInfo,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    FlowRow(verticalArrangement = Arrangement.Center) {
        item.users.getOrNull(0)?.let {
            UserPicture(
                userHex = it,
                size = Size25dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        item.users.getOrNull(1)?.let {
            UserPicture(
                userHex = it,
                size = Size25dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        item.users.getOrNull(2)?.let {
            UserPicture(
                userHex = it,
                size = Size25dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        if (item.users.size > 3) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(Size25dp)) {
                Text(
                    text = stringRes(R.string.and_more, item.users.size - 3),
                    maxLines = 1,
                )
            }
        }
    }
}
