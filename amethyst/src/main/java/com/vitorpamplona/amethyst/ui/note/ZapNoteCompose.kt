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
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserAboutMe
import com.vitorpamplona.amethyst.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav.nav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ZapReqResponse
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
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

    var baseAuthor by remember { mutableStateOf<User?>(baseReqResponse.zapRequest.author) }

    LaunchedEffect(baseNoteRequest) {
        accountViewModel.decryptAmountMessage(baseNoteRequest.note, baseReqResponse.zapEvent) {
            baseAuthor = it?.user
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
            baseAuthor?.let { RenderZapNoteSlim(it, baseReqResponse.zapEvent, accountViewModel, nav) }
        }
    }
}

@Preview
@Composable
fun RenderZapNotePreview() {
    val accountViewModel = mockAccountViewModel()

    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val note1: Note = LocalCache.getOrCreateNote("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")

    ThemeComparisonColumn {
        RenderZapNote(
            user1,
            note1,
            accountViewModel,
            EmptyNav,
        )
    }
}

@Preview
@Composable
fun RenderZapNoteSlimPreview() {
    val accountViewModel = mockAccountViewModel()

    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val note1: Note = LocalCache.getOrCreateNote("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")

    ThemeComparisonColumn {
        RenderZapNoteSlim(
            user1,
            note1,
            accountViewModel,
            EmptyNav,
        )
    }
}

@Composable
private fun RenderZapNote(
    baseAuthor: User,
    zapNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListItem(
        leadingContent = {
            UserPicture(baseAuthor, Size55dp, accountViewModel = accountViewModel, nav = nav)
        },
        headlineContent = {
            UsernameDisplay(baseAuthor, accountViewModel = accountViewModel)
        },
        supportingContent = {
            ZapAmount(zapNote, accountViewModel)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserActionOptions(baseAuthor, accountViewModel, nav)
            }
        },
    )
}

@Composable
private fun RenderZapNoteSlim(
    baseAuthor: User,
    zapNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SlimListItem(
        leadingContent = {
            UserPicture(baseAuthor, Size55dp, accountViewModel = accountViewModel, nav = nav)
        },
        headlineContent = {
            UsernameDisplay(baseAuthor, accountViewModel = accountViewModel)
        },
        supportingContent = {
            ZapAmount(zapNote, accountViewModel)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserActionOptions(baseAuthor, accountViewModel, nav)
            }
        },
    )
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
fun AboutDisplayNoFormat(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val aboutMe by observeUserAboutMe(baseAuthor, accountViewModel)

    Text(
        aboutMe,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
