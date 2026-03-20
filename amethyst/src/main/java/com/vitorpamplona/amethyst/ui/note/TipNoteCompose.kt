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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.MoneroOrange
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TipNoteCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val liveNote by baseNote.live().metadata.observeAsState()
    val baseAuthor = liveNote?.note?.author

    val route = remember(baseAuthor) { "User/${baseAuthor?.pubkeyHex}" }

    if (baseAuthor == null) {
        BlankNote()
    } else {
        Column(
            modifier =
                Modifier.clickable(
                    onClick = { nav(route) },
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            RenderTipNote(baseAuthor, baseNote, nav, accountViewModel)
        }
    }
}

@Composable
private fun RenderTipNote(
    baseAuthor: User,
    tipNote: Note,
    nav: (String) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) { UsernameDisplay(baseAuthor) }
            Row(verticalAlignment = Alignment.CenterVertically) { AboutDisplay(baseAuthor) }
        }

        Column(
            modifier = remember { Modifier.padding(start = 10.dp) },
            verticalArrangement = Arrangement.Center,
        ) {
            TipAmount(accountViewModel.userProfile(), tipNote)
        }

        Column(modifier = Modifier.padding(start = 10.dp)) {
            UserActionOptions(baseAuthor, accountViewModel)
        }
    }
}

@Composable
private fun TipAmount(
    user: User,
    tipEventNote: Note,
) {
    val noteState by tipEventNote.live().metadata.observeAsState()

    var tipAmount by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = noteState) {
        launch(Dispatchers.IO) {
            (noteState?.note?.event as? TipEvent)
                ?.totalValue
                ?.let {
                    val newTipAmount = showMoneroAmount(it)
                    if (tipAmount != newTipAmount) {
                        tipAmount = newTipAmount
                    }
                }
        }
    }

    tipAmount?.let {
        Text(
            text = it,
            color = MoneroOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.W500,
        )
    }
}
