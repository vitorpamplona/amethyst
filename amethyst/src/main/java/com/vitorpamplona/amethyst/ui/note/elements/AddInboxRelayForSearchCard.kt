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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.ui.actions.relays.AddSearchRelayListDialog
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BigPadding
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.SearchRelayListEvent
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Preview
@Composable
fun AddInboxRelayForSearchCardPreview() {
    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()
    val myCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    sharedPreferencesViewModel.init()
    sharedPreferencesViewModel.updateTheme(ThemeType.DARK)

    val pubkey = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"

    val myAccount =
        Account(
            keyPair =
                KeyPair(
                    privKey = Hex.decode("0f761f8a5a481e26f06605a1d9b3e9eba7a107d351f43c43a57469b788274499"),
                    pubKey = Hex.decode(pubkey),
                    forcePubKeyCheck = false,
                ),
            scope = myCoroutineScope,
        )

    val accountViewModel =
        AccountViewModel(
            myAccount,
            sharedPreferencesViewModel.sharedPrefs,
        )

    ThemeComparisonColumn {
        AddInboxRelayForSearchCard(
            accountViewModel = accountViewModel,
            nav = {},
        )
    }
}

@Composable
fun ObserveRelayListForSearchAndDisplayIfNotFound(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    ObserveRelayListForSearch(
        accountViewModel = accountViewModel,
    ) { relayListEvent ->
        if (relayListEvent == null || relayListEvent.relays().isEmpty()) {
            AddInboxRelayForSearchCard(
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun ObserveRelayListForSearch(
    accountViewModel: AccountViewModel,
    inner: @Composable (relayListEvent: SearchRelayListEvent?) -> Unit,
) {
    ObserveRelayListForSearch(
        pubkey = accountViewModel.account.userProfile().pubkeyHex,
        accountViewModel = accountViewModel,
    ) { relayListEvent ->
        inner(relayListEvent)
    }
}

@Composable
fun ObserveRelayListForSearch(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
    inner: @Composable (relayListEvent: SearchRelayListEvent?) -> Unit,
) {
    LoadAddressableNote(
        SearchRelayListEvent.createAddressTag(pubkey),
        accountViewModel,
    ) { relayList ->
        if (relayList != null) {
            val relayListNoteState by relayList.live().metadata.observeAsState()
            val relayListEvent = relayListNoteState?.note?.event as? SearchRelayListEvent

            inner(relayListEvent)
        }
    }
}

@Composable
fun AddInboxRelayForSearchCard(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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
                    text = stringRes(id = R.string.search_relays_not_found),
                    style =
                        TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )

                Spacer(modifier = StdVertSpacer)

                Text(
                    text = stringRes(id = R.string.search_relays_not_found_description),
                )

                Spacer(modifier = StdVertSpacer)

                var wantsToEditRelays by remember { mutableStateOf(false) }
                if (wantsToEditRelays) {
                    AddSearchRelayListDialog({ wantsToEditRelays = false }, accountViewModel, nav = nav)
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
