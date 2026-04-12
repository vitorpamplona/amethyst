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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun RenderLnZap(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val zapEvent = note.event as? LnZapEvent ?: return

    val card by parseAuthorCommentAndAmount(note, accountViewModel)

    val destinationKey = zapEvent.zappedAuthor().firstOrNull() ?: return

    TransferCard(
        card,
        destinationKey,
        backgroundColor,
        Modifier,
        accountViewModel,
        nav,
    )
}

@Composable
private fun parseAuthorCommentAndAmount(
    zapEventNote: Note,
    accountViewModel: AccountViewModel,
) = produceState(
    ZapAmountCommentNotification(
        user = null,
        comment = null,
        amount = null,
    ),
    zapEventNote,
) {
    val newState = accountViewModel.innerDecryptAmountMessage(zapEventNote)
    value = newState ?: ZapAmountCommentNotification(
        user = null,
        comment = null,
        amount = null,
    )
}

@Preview
@Composable
fun TransferCardPreview() {
    var user1: User? = null
    var user2: User? = null

    runBlocking {
        withContext(Dispatchers.IO) {
            user1 = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
            user2 = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")
        }
    }

    ThemeComparisonColumn {
        TransferCard(
            transfer =
                ZapAmountCommentNotification(
                    user = user1,
                    comment = "This is a comment",
                    amount = "100",
                ),
            toUser = user2!!.pubkeyHex,
            backgroundColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}

@Composable
fun TransferCard(
    transfer: ZapAmountCommentNotification,
    toUser: String,
    backgroundColor: MutableState<Color>,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = SpacedBy5dp,
    ) {
        transfer.user?.let {
            UserPicture(it, Size25dp, Modifier, accountViewModel, nav)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZapIcon(
                Size24Modifier,
                MaterialTheme.colorScheme.bitcoinColor,
            )

            // Amount Display
            Text(
                text = transfer.amount ?: "",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.bitcoinColor,
            )

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.bitcoinColor,
                modifier = Size20Modifier,
            )
        }

        UserPicture(toUser, Size25dp, Modifier, accountViewModel, nav)

        // Optional Message (conditionally displayed)
        transfer.comment?.let { message ->
            Spacer(StdHorzSpacer)
            CrossfadeToDisplayComment(message, backgroundColor, accountViewModel = accountViewModel, nav = nav)
        }
    }
}
