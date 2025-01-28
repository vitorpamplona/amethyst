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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.relays.AddDMRelayListDialog
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BigPadding
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip17Dm.ChatMessageRelayListEvent

@Preview
@Composable
fun AddInboxRelayForDMCardPreview() {
    ThemeComparisonColumn {
        AddInboxRelayForDMCard(
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav,
        )
    }
}

@Composable
fun ObserveRelayListForDMsAndDisplayIfNotFound(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveRelayListForDMs(
        accountViewModel = accountViewModel,
    ) { relayListEvent ->
        if (relayListEvent == null || relayListEvent.relays().isEmpty()) {
            AddInboxRelayForDMCard(
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun ObserveRelayListForDMs(
    accountViewModel: AccountViewModel,
    inner: @Composable (relayListEvent: ChatMessageRelayListEvent?) -> Unit,
) {
    ObserveRelayListForDMs(
        pubkey = accountViewModel.account.userProfile().pubkeyHex,
        accountViewModel = accountViewModel,
    ) { relayListEvent ->
        inner(relayListEvent)
    }
}

@Composable
fun ObserveRelayListForDMs(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
    inner: @Composable (relayListEvent: ChatMessageRelayListEvent?) -> Unit,
) {
    LoadAddressableNote(
        ChatMessageRelayListEvent.createAddressTag(pubkey),
        accountViewModel,
    ) { relayList ->
        if (relayList != null) {
            val relayListNoteState by relayList.live().metadata.observeAsState()
            val relayListEvent = relayListNoteState?.note?.event as? ChatMessageRelayListEvent

            inner(relayListEvent)
        }
    }
}

@Composable
fun AddInboxRelayForDMCard(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = StdPadding) {
        Card(
            modifier = MaterialTheme.colorScheme.imageModifier,
        ) {
            Column(
                modifier = BigPadding,
            ) {
                // Title
                Text(
                    text = stringRes(id = R.string.dm_relays_not_found),
                    style =
                        TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )

                Spacer(modifier = StdVertSpacer)

                Text(
                    text = stringRes(id = R.string.dm_relays_not_found_description),
                )

                Spacer(modifier = StdVertSpacer)

                var wantsToEditRelays by remember { mutableStateOf(false) }
                if (wantsToEditRelays) {
                    AddDMRelayListDialog({ wantsToEditRelays = false }, accountViewModel, nav = nav)
                }

                Button(
                    onClick = {
                        wantsToEditRelays = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringRes(id = R.string.dm_relays_not_found_create_now))
                }
            }
        }
    }
}
