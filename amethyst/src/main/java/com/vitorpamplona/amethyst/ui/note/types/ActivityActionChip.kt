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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.InLineIconRenderer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LikedIcon
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size19Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
import kotlinx.collections.immutable.persistentListOf
import java.math.BigDecimal
import androidx.compose.material3.Icon as Material3Icon

/**
 * Inline "activity" chip for the author header of reaction and zap cards:
 * the action (emoji, or amount → recipient) renders next to the actor's name,
 * so the card reads as "Alice ❤️" / "Alice ⚡ 500 → Bob" with the target post
 * quoted below. Renders nothing for other kinds.
 */
@Composable
fun ActivityActionChip(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val event = baseNote.event) {
        is ReactionEvent -> {
            Spacer(HalfPadding)
            ReactionActionChip(event.content)
        }

        is LnZapEvent -> {
            Spacer(HalfPadding)
            AmountToRecipientChip(
                amount = showAmountInteger(event.amount),
                recipient = event.zappedAuthor().firstOrNull(),
                accountViewModel = accountViewModel,
                nav = nav,
            ) {
                ZapIcon(Size19Modifier, MaterialTheme.colorScheme.bitcoinColor)
            }
        }

        is NutzapEvent -> {
            Spacer(HalfPadding)
            AmountToRecipientChip(
                amount = showAmount(BigDecimal(event.claimedSatsTotal())),
                recipient = event.linkedPubKeys().firstOrNull(),
                accountViewModel = accountViewModel,
                nav = nav,
            ) {
                Material3Icon(
                    imageVector = CustomHashTagIcons.Cashu,
                    contentDescription = null,
                    modifier = Size16Modifier,
                )
            }
        }
    }
}

@Composable
private fun ReactionActionChip(reactionType: String) {
    if (reactionType.startsWith(":")) {
        val url = reactionType.removePrefix(":").substringAfter(":")
        InLineIconRenderer(
            persistentListOf(CustomEmoji.ImageUrlType(url)),
            style = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
            maxLines = 1,
            fontSize = 16.sp,
        )
    } else {
        when (reactionType) {
            "+" -> LikedIcon(Size19Modifier)
            "-" ->
                Text(
                    text = "👎",
                    maxLines = 1,
                    fontSize = 16.sp,
                )

            else ->
                Text(
                    text = reactionType,
                    maxLines = 1,
                    fontSize = 16.sp,
                )
        }
    }
}

@Composable
private fun AmountToRecipientChip(
    amount: String,
    recipient: HexKey?,
    accountViewModel: AccountViewModel,
    nav: INav,
    icon: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()

        Text(
            text = amount,
            color = MaterialTheme.colorScheme.bitcoinColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
        )

        recipient?.let {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.bitcoinColor,
                modifier = Size16Modifier,
            )
            UserPicture(it, Size20dp, accountViewModel = accountViewModel, nav = nav)
        }
    }
}
