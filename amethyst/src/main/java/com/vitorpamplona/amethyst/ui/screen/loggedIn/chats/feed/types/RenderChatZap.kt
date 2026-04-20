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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

private const val BIG_ZAP_THRESHOLD_SATS = 50_000L

@Composable
fun RenderChatZap(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val zapEvent = baseNote.event as? LnZapEvent ?: return

    val card by produceState(
        ZapAmountCommentNotification(user = null, comment = null, amount = null),
        baseNote,
    ) {
        value = accountViewModel.innerDecryptAmountMessage(baseNote) ?: value
    }

    val isBigZap =
        remember(zapEvent) {
            (zapEvent.amount?.toLong() ?: 0L) >= BIG_ZAP_THRESHOLD_SATS
        }

    val accentAlpha = if (isBigZap) 0.18f else 0.08f
    val amountText = card.amount ?: showAmountInteger(zapEvent.amount)

    StreamSystemCard(accent = BitcoinOrange, accentAlpha = accentAlpha) {
        val backgroundColor = remember(accentAlpha) { mutableStateOf(BitcoinOrange.copy(alpha = accentAlpha)) }

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZapIcon(
                if (isBigZap) Size24Modifier else Size20Modifier,
                BitcoinOrange,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sender = card.user
                    if (sender != null) {
                        UserPicture(sender, Size20dp, Modifier, accountViewModel, nav)
                        Spacer(StdHorzSpacer)
                        UsernameDisplay(
                            baseUser = sender,
                            fontWeight = FontWeight.Bold,
                            accountViewModel = accountViewModel,
                        )
                    } else {
                        Text(
                            text = stringRes(R.string.chat_zap_anonymous),
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(StdHorzSpacer)
                    Text(
                        text = stringRes(R.string.chat_zap_amount_suffix, amountText),
                        color = BitcoinOrange,
                        fontWeight = if (isBigZap) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                card.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                    Spacer(Modifier.padding(top = 2.dp))
                    CrossfadeToDisplayComment(
                        comment = comment,
                        backgroundColor = backgroundColor,
                        nav = nav,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }
}
