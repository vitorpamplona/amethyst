package com.vitorpamplona.amethyst.ui.actions

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelayBriefInfo
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

data class RelayList(
    val relay: Relay,
    val relayInfo: RelayBriefInfo,
    val isSelected: Boolean
)

data class RelayInfoDialog(
    val relayBriefInfo: RelayBriefInfo,
    val relayInfo: RelayInformation
)

@Composable
fun RelaySelectionDialog(
    preSelectedList: List<Relay>,
    onClose: () -> Unit,
    onPost: (list: List<Relay>) -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var relays by remember {
        mutableStateOf(
            accountViewModel.account.activeWriteRelays().map {
                RelayList(
                    relay = it,
                    relayInfo = RelayBriefInfo(it.url),
                    isSelected = preSelectedList.any { relay -> it.url == relay.url }
                )
            }
        )
    }
    var relayInfo: RelayInfoDialog? by remember { mutableStateOf(null) }

    relayInfo?.let {
        RelayInformationDialog(
            onClose = {
                relayInfo = null
            },
            relayInfo = it.relayInfo,
            relayBriefInfo = it.relayBriefInfo,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }

    var selected by remember {
        mutableStateOf(true)
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp)

            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(
                        onPress = {
                            onClose()
                        }
                    )

                    SaveButton(
                        onPost = {
                            val selectedRelays = relays.filter { it.isSelected }
                            if (selectedRelays.isEmpty()) {
                                scope.launch {
                                    Toast.makeText(context, context.getString(R.string.select_a_relay_to_continue), Toast.LENGTH_SHORT).show()
                                }
                                return@SaveButton
                            }
                            onPost(selectedRelays.map { it.relay })
                            onClose()
                        },
                        isActive = true
                    )
                }

                RelaySwitch(
                    text = stringResource(R.string.select_deselect_all),
                    checked = selected,
                    onClick = {
                        selected = !selected
                        relays = relays.mapIndexed { _, item ->
                            item.copy(isSelected = selected)
                        }
                    }
                )

                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        bottom = 10.dp
                    )
                ) {
                    itemsIndexed(
                        relays,
                        key = { _, item -> item.relay.url }
                    ) { index, item ->
                        RelaySwitch(
                            text = item.relayInfo.displayUrl,
                            checked = item.isSelected,
                            onClick = {
                                relays = relays.mapIndexed { j, item ->
                                    if (index == j) {
                                        item.copy(isSelected = !item.isSelected)
                                    } else {
                                        item
                                    }
                                }
                            },
                            onLongPress = {
                                accountViewModel.retrieveRelayDocument(
                                    item.relay.url,
                                    onInfo = {
                                        relayInfo = RelayInfoDialog(RelayBriefInfo(item.relay.url), it)
                                    },
                                    onError = { url, errorCode, exceptionMessage ->
                                        val msg = when (errorCode) {
                                            Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL -> context.getString(R.string.relay_information_document_error_assemble_url, url, exceptionMessage)
                                            Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER -> context.getString(R.string.relay_information_document_error_assemble_url, url, exceptionMessage)
                                            Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT -> context.getString(R.string.relay_information_document_error_assemble_url, url, exceptionMessage)
                                            Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS -> context.getString(R.string.relay_information_document_error_assemble_url, url, exceptionMessage)
                                        }

                                        scope.launch {
                                            Toast
                                                .makeText(
                                                    context,
                                                    msg,
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelaySwitch(text: String, checked: Boolean, onClick: () -> Unit, onLongPress: () -> Unit = { }) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                onClick()
            }
        )
    }
}
