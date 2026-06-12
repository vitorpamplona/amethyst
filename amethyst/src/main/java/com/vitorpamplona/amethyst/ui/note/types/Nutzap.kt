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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
import java.math.BigDecimal

/**
 * Renders a NIP-61 nutzap (kind 9321) as a transfer card, like lightning zaps.
 * Unlike kind 9735 receipts, the nutzap is signed by the sender, so [Note.author]
 * is already the right person to attribute.
 */
@Composable
fun RenderNutzap(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val nutzapEvent = note.event as? NutzapEvent ?: return

    val recipientKey = nutzapEvent.linkedPubKeys().firstOrNull() ?: return

    RenderZappedPost(note, quotesLeft, backgroundColor, accountViewModel, nav)

    val card =
        remember(note) {
            ZapAmountCommentNotification(
                user = note.author,
                comment = nutzapEvent.content.ifBlank { null },
                amount = showAmount(BigDecimal(nutzapEvent.claimedSatsTotal())),
                zapNote = note,
            )
        }

    TransferCard(
        card,
        recipientKey,
        backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
