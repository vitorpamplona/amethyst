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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.note.ActivityAmountRow
import com.vitorpamplona.amethyst.commons.ui.note.ActivityBadge
import com.vitorpamplona.amethyst.commons.ui.note.ActivityCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.ActivityHeaderRow
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.quartz.nipXXBolt12Zaps.zap.Bolt12ZapEvent
import java.text.NumberFormat

/**
 * Standalone card for a NIP-XX BOLT12 zap (kind 9736), styled like the NIP-57
 * lightning-zap card but labeled BOLT12. The sender is the `P` payer tag (or the
 * event pubkey when anonymous); the amount comes straight off the validated
 * `amount` tag — no LNURL provider or private-zap decryption is involved.
 */
@Composable
fun RenderBolt12Zap(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? Bolt12ZapEvent ?: return

    val senderKey = event.payer()
    val recipientKey = event.recipient()
    val amountSats = event.amount()?.div(1000)
    val comment = event.content.takeIf { it.isNotBlank() }

    val orange = MaterialTheme.colorScheme.bitcoinColor

    ActivityCardFrame(orange) { cardBackground ->
        ActivityHeaderRow(
            tint = orange,
            pillLabel = "BOLT12",
            badge = {
                ActivityBadge(orange) {
                    ZapIcon(Modifier.size(18.dp), Color.White)
                }
            },
            senderAvatar = {
                if (senderKey != null) {
                    UserPicture(senderKey, Size25dp, Modifier, accountViewModel, nav)
                } else {
                    // Anonymous zap — no attributable payer.
                    DisplayBlankAuthor(Size25dp, accountViewModel = accountViewModel)
                }
            },
            recipientAvatar =
                recipientKey?.let {
                    { UserPicture(it, Size25dp, Modifier, accountViewModel, nav) }
                },
        )

        RenderZappedPost(note, quotesLeft, cardBackground, accountViewModel, nav)

        amountSats?.let { ActivityAmountRow(NumberFormat.getNumberInstance().format(it), orange) }

        comment?.let {
            CrossfadeToDisplayComment(it, cardBackground, nav, accountViewModel)
        }
    }
}
