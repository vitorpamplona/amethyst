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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserAboutMe
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserIsFollowing
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.UnfollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ShowUserButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ZapReqResponse
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ZapNoteCompose(
    baseReqResponse: ZapReqResponse,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseNoteRequest by observeNote(baseReqResponse.zapRequest, accountViewModel)

    var baseAuthor by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(baseNoteRequest) {
        baseNoteRequest?.note?.let {
            accountViewModel.decryptAmountMessage(it, baseReqResponse.zapEvent) { baseAuthor = it?.user }
        }
    }

    if (baseAuthor == null) {
        BlankNote()
    } else {
        Column(
            modifier =
                Modifier.clickable(
                    onClick = { baseAuthor?.let { nav.nav(routeFor(it)) } },
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            baseAuthor?.let { RenderZapNote(it, baseReqResponse.zapEvent, nav, accountViewModel) }
        }
    }
}

@Composable
private fun RenderZapNote(
    baseAuthor: User,
    zapNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier =
            remember {
                Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(baseAuthor, Size55dp, accountViewModel = accountViewModel, nav = nav)

        Column(
            modifier = remember { Modifier.padding(start = 10.dp).weight(1f) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { UsernameDisplay(baseAuthor, accountViewModel = accountViewModel) }
            Row(verticalAlignment = Alignment.CenterVertically) { AboutDisplay(baseAuthor, accountViewModel) }
        }

        Column(
            modifier = remember { Modifier.padding(start = 10.dp) },
            verticalArrangement = Arrangement.Center,
        ) {
            ZapAmount(zapNote, accountViewModel)
        }

        Column(modifier = Modifier.padding(start = 10.dp)) {
            UserActionOptions(baseAuthor, accountViewModel)
        }
    }
}

@Composable
private fun ZapAmount(
    zapEventNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteState by observeNote(zapEventNote, accountViewModel)

    var zapAmount by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = noteState) {
        launch(Dispatchers.IO) {
            val newZapAmount = showAmountInteger((noteState?.note?.event as? LnZapEvent)?.amount)
            if (zapAmount != newZapAmount) {
                zapAmount = newZapAmount
            }
        }
    }

    zapAmount?.let {
        Text(
            text = it,
            color = BitcoinOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.W500,
        )
    }
}

@Composable
fun UserActionOptions(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseAuthor)
    if (isHidden) {
        ShowUserButton { accountViewModel.show(baseAuthor) }
    } else {
        ShowFollowingOrUnfollowingButton(baseAuthor, accountViewModel)
    }
}

@Composable
fun ShowFollowingOrUnfollowingButton(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val isFollowing = observeUserIsFollowing(accountViewModel.account.userProfile(), baseAuthor, accountViewModel)

    if (isFollowing.value) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollow(baseAuthor)
            }
        }
    } else {
        FollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.follow(baseAuthor)
            }
        }
    }
}

@Composable
fun AboutDisplay(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val aboutMe by observeUserAboutMe(baseAuthor, accountViewModel)

    Text(
        aboutMe,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
