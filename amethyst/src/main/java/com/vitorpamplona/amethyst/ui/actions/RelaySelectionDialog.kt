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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.ui.note.buttons.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.RelayInformationDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.collections.map

data class RelayList(
    val relay: NormalizedRelayUrl,
    val isSelected: Boolean,
)

data class RelayInfoDialog(
    val relay: NormalizedRelayUrl,
    val relayInfo: Nip11RelayInformation,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySelectionDialog(
    preSelectedList: ImmutableList<NormalizedRelayUrl>,
    onClose: () -> Unit,
    onPost: (list: ImmutableList<NormalizedRelayUrl>) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current

    var relays by remember {
        mutableStateOf(
            accountViewModel.account.client.connectedRelayList().map {
                RelayList(
                    relay = it,
                    isSelected = preSelectedList.any { relayUrl -> it == relayUrl },
                )
            },
        )
    }

    val hasSelectedRelay by remember { derivedStateOf { relays.any { it.isSelected } } }

    var relayInfo: RelayInfoDialog? by remember { mutableStateOf(null) }

    relayInfo?.let {
        RelayInformationDialog(
            onClose = { relayInfo = null },
            relayInfo = it.relayInfo,
            relay = it.relay,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    var selected by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        SetDialogToEdgeToEdge()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    actions = {
                        SaveButton(
                            onPost = {
                                val selectedRelays = relays.filter { it.isSelected }.map { it.relay }.toImmutableList()
                                onPost(selectedRelays)
                                onClose()
                            },
                            isActive = hasSelectedRelay,
                        )
                        Spacer(modifier = StdHorzSpacer)
                    },
                    title = {},
                    navigationIcon = {
                        Row {
                            Spacer(modifier = StdHorzSpacer)
                            CloseButton(
                                onPress = { onClose() },
                            )
                        }
                    },
                )
            },
        ) { pad ->
            Column(
                modifier =
                    Modifier.padding(pad).padding(start = 10.dp, end = 10.dp, top = 10.dp),
            ) {
                RelaySwitch(
                    text = stringRes(context, R.string.select_deselect_all),
                    checked = selected,
                    onClick = {
                        selected = !selected
                        relays = relays.mapIndexed { _, item -> item.copy(isSelected = selected) }
                    },
                )

                LazyColumn(
                    contentPadding = FeedPadding,
                ) {
                    itemsIndexed(
                        relays,
                        key = { _, item -> item.relay },
                    ) { index, item ->
                        RelaySwitch(
                            text = item.relay.displayUrl(),
                            checked = item.isSelected,
                            onClick = {
                                relays =
                                    relays.mapIndexed { j, item ->
                                        if (index == j) {
                                            item.copy(isSelected = !item.isSelected)
                                        } else {
                                            item
                                        }
                                    }
                            },
                            onLongPress = {
                                accountViewModel.retrieveRelayDocument(
                                    relay = item.relay,
                                    onInfo = {
                                        relayInfo =
                                            RelayInfoDialog(
                                                item.relay,
                                                it,
                                            )
                                    },
                                    onError = { relay, errorCode, exceptionMessage ->
                                        val msg =
                                            when (errorCode) {
                                                Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL ->
                                                    stringRes(
                                                        context,
                                                        R.string.relay_information_document_error_assemble_url,
                                                        relay.url,
                                                        exceptionMessage,
                                                    )
                                                Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER ->
                                                    stringRes(
                                                        context,
                                                        R.string.relay_information_document_error_assemble_url,
                                                        relay.url,
                                                        exceptionMessage,
                                                    )
                                                Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT ->
                                                    stringRes(
                                                        context,
                                                        R.string.relay_information_document_error_assemble_url,
                                                        relay.url,
                                                        exceptionMessage,
                                                    )
                                                Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS ->
                                                    stringRes(
                                                        context,
                                                        R.string.relay_information_document_error_assemble_url,
                                                        relay.url,
                                                        exceptionMessage,
                                                    )
                                            }

                                        accountViewModel.toastManager.toast(
                                            stringRes(context, R.string.unable_to_download_relay_document),
                                            msg,
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelaySwitch(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
        )
        Switch(
            checked = checked,
            onCheckedChange = { onClick() },
        )
    }
}
