/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import android.R.attr.onClick
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.loadRelayInfo
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.LargeRelayIconModifier
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChatMaxWidth
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BasicRelaySetupInfoClickableRow(
    item: BasicRelaySetupInfo,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    onDelete: ((BasicRelaySetupInfo) -> Unit)?,
    onClick: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(item.relay.url))
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = HalfVertPadding,
        ) {
            val iconUrlFromRelayInfoDoc by loadRelayInfo(item.relay, accountViewModel)

            RenderRelayIcon(
                iconUrlFromRelayInfoDoc.id ?: item.relay.displayUrl(),
                iconUrlFromRelayInfoDoc.icon,
                loadProfilePicture,
                loadRobohash,
                item.relayStat.pingInMs,
                LargeRelayIconModifier,
            )

            Spacer(modifier = HalfHorzPadding)

            Column(Modifier.weight(1f)) {
                RelayNameAndRemoveButton(
                    item,
                    onClick,
                    onDelete,
                    ReactionRowHeightChatMaxWidth,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = ReactionRowHeightChatMaxWidth,
                ) {
                    RelayStatusRow(
                        item = item,
                        modifier = HalfStartPadding.weight(1f),
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}
