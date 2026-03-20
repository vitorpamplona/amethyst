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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.net.HostAndPort
import com.google.common.net.HostSpecifier
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Subaddress
import com.vitorpamplona.amethyst.model.TransactionDirection
import com.vitorpamplona.amethyst.model.TransactionInfo
import com.vitorpamplona.amethyst.model.Wallet
import com.vitorpamplona.amethyst.service.MoneroDataSource
import com.vitorpamplona.amethyst.service.WalletService
import com.vitorpamplona.amethyst.service.getEvent
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.TipButton
import com.vitorpamplona.amethyst.ui.note.authenticate
import com.vitorpamplona.amethyst.ui.note.decToPiconero
import com.vitorpamplona.amethyst.ui.note.showMoneroAmount
import com.vitorpamplona.amethyst.ui.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.quartz.encoders.HexValidator
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toNEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MoneroViewModel(val account: Account) : ViewModel() {
    var daemonAddress: String by mutableStateOf(account.moneroDaemonAddress.host)
    var daemonPort by mutableStateOf(account.moneroDaemonAddress.port.toString())

    var daemonUsername by mutableStateOf(account.moneroDaemonUsername)
    var daemonPassword by mutableStateOf(account.moneroDaemonPassword)

    var daemonAddressInvalid by mutableStateOf(false)
    var daemonPortInvalid by mutableStateOf(false)

    val walletStatus = MoneroDataSource.status().asLiveData(Dispatchers.IO)
    val connectionStatus = MoneroDataSource.connectionStatus().asLiveData(Dispatchers.IO)
    val balance =
        MoneroDataSource.balance()
            .map {
                showMoneroAmount(it.toULong())
            }
            .asLiveData(Dispatchers.IO)
    val lockedBalance =
        MoneroDataSource.lockedBalance()
            .map {
                showMoneroAmount(it.toULong())
            }
            .asLiveData(Dispatchers.IO)
    val tips =
        MoneroDataSource.transactions()
            .map {
                it
                    .filter {
                        (it.subaddressLabel.length == 64 && HexValidator.isHex(it.subaddressLabel)) ||
                            (it.description.length == 64 && HexValidator.isHex(it.description))
                    }
                    .sortedByDescending { it.timestamp }
            }
            .asLiveData(Dispatchers.IO)

    fun resetInput() {
        daemonAddress = account.moneroDaemonAddress.host
        daemonPort = account.moneroDaemonAddress.port.toString()

        daemonAddressInvalid = false
        daemonPortInvalid = false
        daemonUsername = ""
        daemonPassword = ""
    }

    fun validate(): Boolean {
        if (!HostSpecifier.isValid(daemonAddress)) {
            daemonPortInvalid = false
            daemonAddressInvalid = true
            return false
        }

        val port = daemonPort.trim().toUShortOrNull()
        if (port == null) {
            daemonAddressInvalid = false
            daemonPortInvalid = true
            return false
        }

        return true
    }

    fun save() {
        if (!validate()) {
            return
        }

        account.changeMoneroDaemonAddress(HostAndPort.fromParts(daemonAddress, daemonPort.toInt()))
        account.changeMoneroDaemonUsername(daemonUsername)
        account.changeMoneroDaemonPassword(daemonPassword)

        account.startMonero()

        resetInput()
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <MoneroViewModel : ViewModel> create(modelClass: Class<MoneroViewModel>): MoneroViewModel {
            return MoneroViewModel(account) as MoneroViewModel
        }
    }
}

@Composable
fun MoneroScreen(
    accountViewModel: AccountViewModel,
    moneroViewModel: MoneroViewModel,
    nav: (String) -> Unit,
) {
    val account = moneroViewModel.account
    val moneroSendViewModel: MoneroSendViewModel = viewModel(factory = MoneroSendViewModel.Factory(account), key = "MoneroSendViewModel")
    val scope = rememberCoroutineScope { Dispatchers.IO }

    val status by moneroViewModel.walletStatus.observeAsState(WalletService.WalletStatus(WalletService.WalletStatusType.OPENING, null))
    val connectionStatus by moneroViewModel.connectionStatus.observeAsState(Wallet.ConnectionStatus.DISCONNECTED)
    val balance by moneroViewModel.balance.observeAsState("0")
    val lockedBalance by moneroViewModel.lockedBalance.observeAsState("0")
    val tips: List<TransactionInfo> by moneroViewModel.tips.observeAsState(listOf())

    var wantsToEditDaemon by remember { mutableStateOf(false) }
    var wantsToReceive by remember { mutableStateOf(false) }
    var wantsToSend by remember { mutableStateOf(false) }
    var showEphemeralWalletWarning by remember { mutableStateOf(!moneroViewModel.account.isMoneroSeedBackedUp) }

    Scaffold(
        modifier = Modifier.padding(top = 10.dp),
        bottomBar = {
            ActionsRow(
                sendEnabled = connectionStatus == Wallet.ConnectionStatus.CONNECTED && status.type == WalletService.WalletStatusType.SYNCED,
                onReceive = {
                    wantsToReceive = true
                },
                onSend = {
                    wantsToSend = true
                },
            )
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = it.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                DaemonInfo(
                    account.moneroDaemonAddress.toString(),
                    status,
                    connectionStatus,
                ) {
                    wantsToEditDaemon = true
                }
                Spacer(modifier = Modifier.height(10.dp))

                Balance(balance, lockedBalance)

                Spacer(Modifier.height(15.dp))
            }

            itemsIndexed(tips) { i, it ->
                Column(
                    modifier =
                        Modifier.clickable {
                            val eventId =
                                if (it.direction == TransactionDirection.IN.ordinal) {
                                    it.subaddressLabel
                                } else {
                                    it.description
                                }

                            scope.launch(Dispatchers.IO) {
                                val event = getEvent(eventId, 10.seconds)
                                event?.let {
                                    val route = routeFor(it, account.userProfile())
                                    if (route != null) {
                                        nav(route)
                                    }
                                }
                            }
                        },
                ) {
                    HorizontalDivider()
                    Box(modifier = Modifier.padding(5.dp)) {
                        TipInfo(it)
                    }
                    if (i == tips.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showEphemeralWalletWarning) {
        EphemeralWalletWarning(
            accountViewModel,
            moneroViewModel.account.moneroRestoreHeight,
            onDismiss = { showEphemeralWalletWarning = false },
            onExplicitDismiss = {
                moneroViewModel.account.changeIsMoneroSeedBackedUp(true)
                showEphemeralWalletWarning = false
            },
            onBackedUp = {
                moneroViewModel.account.changeIsMoneroSeedBackedUp(true)
            },
        )
    }

    if (wantsToEditDaemon) {
        EditDaemonDialog(moneroViewModel) {
            wantsToEditDaemon = false
        }
    }

    if (wantsToReceive) {
        ReceiveDialog(
            account = account,
            onDismiss = {
                wantsToReceive = false
            },
        )
    }

    if (wantsToSend) {
        SendDialog(moneroSendViewModel) {
            wantsToSend = false
        }
    }
}

@Composable
fun DaemonInfo(
    address: String,
    status: WalletService.WalletStatus,
    connectionStatus: Wallet.ConnectionStatus,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val fontSize = 14.sp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column {
            Row {
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    "${stringResource(R.string.daemon)}:",
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                )

                Spacer(modifier = Modifier.width(5.dp))

                Row(
                    modifier =
                        Modifier
                            .weight(2f, fill = false)
                            .height(IntrinsicSize.Min)
                            .clickable { onEdit() },
                ) {
                    Text(
                        address,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .clickable { onEdit() },
                        fontSize = fontSize,
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_daemon),
                        modifier =
                            Modifier
                                .size(
                                    with(LocalDensity.current) {
                                        fontSize.toDp()
                                    },
                                )
                                .clickable { onEdit() },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        val statusText =
            if ((status.type == WalletService.WalletStatusType.SYNCED || status.type == WalletService.WalletStatusType.SYNCING) && connectionStatus == Wallet.ConnectionStatus.DISCONNECTED) {
                status.toLocalizedString(context) + " " + stringResource(R.string.daemon_info_disconnected_indicator_text)
            } else {
                status.toLocalizedString(context)
            }

        Text(
            statusText,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Balance(
    balance: String,
    lockedBalance: String,
) {
    var showEntireBalance by rememberSaveable { mutableStateOf(false) }
    var showEntireLockedBalance by rememberSaveable { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(0.25f))

            if (showEntireBalance) {
                val yOffset =
                    with(LocalDensity.current) {
                        MaterialTheme.typography.displayMedium.fontSize.toPx() + 5.dp.toPx()
                    }
                Popup(
                    alignment = Alignment.BottomCenter,
                    offset = IntOffset(0, -yOffset.toInt()),
                    onDismissRequest = { showEntireBalance = false },
                ) {
                    Surface(shadowElevation = 5.dp, tonalElevation = 1.dp) {
                        Text(
                            "$balance XMR",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }

            ClickableText(
                AnnotatedString(
                    balance,
                    listOf(
                        AnnotatedString.Range(
                            item = SpanStyle(fontWeight = FontWeight.Bold, color = LocalContentColor.current),
                            start = 0,
                            end = balance.length,
                        ),
                    ),
                ),
                style = MaterialTheme.typography.displayMedium,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                onClick = {
                    showEntireBalance = !showEntireBalance
                },
                modifier = Modifier.weight(1f, fill = false),
            )

            Text(
                "XMR",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .wrapContentWidth()
                        .padding(start = 10.dp),
            )

            Spacer(modifier = Modifier.weight(0.25f))
        }

        if (lockedBalance != "0") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.weight(0.75f))

                Text(stringResource(R.string.locked) + ":", fontWeight = FontWeight.Bold)

                if (showEntireLockedBalance) {
                    val yOffset =
                        with(LocalDensity.current) {
                            MaterialTheme.typography.bodyMedium.fontSize.toPx() + 5.dp.toPx()
                        }
                    Popup(
                        alignment = Alignment.BottomCenter,
                        offset = IntOffset(0, -yOffset.toInt()),
                        onDismissRequest = { showEntireLockedBalance = false },
                    ) {
                        Surface(shadowElevation = 5.dp, tonalElevation = 1.dp) {
                            Text(
                                "$lockedBalance XMR",
                                modifier = Modifier.padding(2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(3.dp))

                Text(
                    lockedBalance,
                    fontWeight = FontWeight.Bold,
                    color = LocalContentColor.current,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                showEntireLockedBalance = !showEntireLockedBalance
                            },
                )

                Text(
                    "XMR",
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .wrapContentWidth()
                            .padding(start = 3.dp),
                )

                Spacer(modifier = Modifier.weight(0.75f))
            }
        }
    }
}

@Composable
fun TipInfo(tip: TransactionInfo) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val prefix =
                if (tip.direction == TransactionDirection.IN.ordinal) {
                    Icons.AutoMirrored.Default.ArrowForward
                } else {
                    Icons.AutoMirrored.Default.ArrowBack
                }

            Icon(
                prefix,
                modifier =
                    Modifier.size(
                        with(LocalDensity.current) {
                            MaterialTheme.typography.bodySmall.fontSize.toDp()
                        },
                    ),
                contentDescription =
                    if (tip.direction == TransactionDirection.IN.ordinal) {
                        stringResource(R.string.incoming_tip)
                    } else {
                        stringResource(R.string.outgoing_tip)
                    },
            )

            Spacer(Modifier.width(2.dp))

            val eventId =
                if (tip.direction == TransactionDirection.IN.ordinal) {
                    tip.subaddressLabel
                } else {
                    tip.description
                }
            val displayableEventId = eventId.hexToByteArray().toNEvent()

            Text(
                displayableEventId,
                modifier = Modifier.weight(1f),
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.weight(1f))

            val time =
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochSecond(tip.timestamp))
            Text(
                time,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                showMoneroAmount(tip.amount.toULong()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false),
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.width(3.dp))

            Text(
                "XMR",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (tip.direction == TransactionDirection.IN.ordinal && tip.confirmations < 10) {
            Text(
                "${10 - tip.confirmations} blocks to unlock",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun ActionsRow(
    sendEnabled: Boolean = true,
    onReceive: () -> Unit,
    onSend: () -> Unit,
) {
    Row(modifier = Modifier.padding(5.dp)) {
        Button(
            onClick = onReceive,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
            shape = RoundedCornerShape(5.dp),
        ) {
            Text(
                stringResource(R.string.receive),
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Button(
            onClick = onSend,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
            shape = RoundedCornerShape(5.dp),
            enabled = sendEnabled,
        ) {
            Text(
                stringResource(R.string.send),
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EditDaemonDialog(
    moneroViewModel: MoneroViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            moneroViewModel.resetInput()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CloseButton(onPress = {
                        moneroViewModel.resetInput()
                        onDismiss()
                    })

                    Button(
                        onClick = {
                            if (moneroViewModel.validate()) {
                                moneroViewModel.save()
                                onDismiss()
                            }
                        },
                        shape = ButtonBorder,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }

                val options =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        imeAction = ImeAction.Next,
                    )
                OutlinedTextField(
                    value = moneroViewModel.daemonAddress,
                    label = { Text(stringResource(R.string.daemon_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { moneroViewModel.daemonAddress = it },
                    keyboardOptions = options.copy(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    isError = moneroViewModel.daemonAddressInvalid,
                    supportingText =
                        if (moneroViewModel.daemonAddressInvalid) {
                            { Text(stringResource(R.string.daemon_edit_dialog_address_error)) }
                        } else {
                            null
                        },
                )

                OutlinedTextField(
                    value = moneroViewModel.daemonPort,
                    label = { Text(stringResource(R.string.daemon_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { moneroViewModel.daemonPort = it },
                    keyboardOptions = options.copy(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = moneroViewModel.daemonPortInvalid,
                    supportingText =
                        if (moneroViewModel.daemonPortInvalid) {
                            { Text(stringResource(R.string.daemon_edit_dialog_port_error)) }
                        } else {
                            null
                        },
                )

                OutlinedTextField(
                    value = moneroViewModel.daemonUsername,
                    label = {
                        Row {
                            Text(stringResource(R.string.daemon_username))

                            Spacer(Modifier.width(5.dp))

                            Text(stringResource(R.string.textfield_optional))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { moneroViewModel.daemonUsername = it },
                    keyboardOptions = options,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = moneroViewModel.daemonPassword,
                    label = {
                        Row {
                            Text(stringResource(R.string.daemon_password))

                            Spacer(Modifier.width(5.dp))

                            Text(stringResource(R.string.textfield_optional))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { moneroViewModel.daemonPassword = it },
                    keyboardOptions =
                        options.copy(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (moneroViewModel.validate()) {
                                    moneroViewModel.save()
                                    onDismiss()
                                }
                            },
                        ),
                    singleLine = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiveDialog(
    account: Account,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf<List<Subaddress>>(listOf()) }
    var generating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            addresses =
                account.listMoneroAddresses().filter { it.label.length != 64 || !HexValidator.isHex(it.label) }
        }
    }

    var wantsToEditLabel by remember { mutableStateOf(false) }
    var wantsToSelectAddress by remember { mutableStateOf(false) }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var addressOptions =
        addresses.map {
            val title = it.label.ifEmpty { it.address }
            val description = if (it.label.isNotEmpty()) it.address else null
            TitleExplainer(
                title,
                description,
            )
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            tonalElevation = 1.dp,
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.padding(10.dp)) {
                    CloseButton(onPress = onDismiss)
                }

                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 10.dp)
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.monero_fund_wallet),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.monero_fund_wallet_address_description),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(10.dp))

                    if (addresses.isNotEmpty()) {
                        val selectedAddress = addresses[selectedIndex]

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .clickable { wantsToSelectAddress = true }
                                        .border(1.dp, Color.Gray, RoundedCornerShape(5.dp))
                                        .padding(start = 15.dp)
                                        .padding(vertical = 5.dp)
                                        .weight(1f, fill = false)
                                        .fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                                        Text(
                                            selectedAddress.address,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier =
                                                Modifier
                                                    .weight(1f, fill = false),
                                        )

                                        if (selectedAddress.label.isNotBlank()) {
                                            Text(
                                                "(${selectedAddress.label})",
                                                softWrap = false,
                                                overflow = TextOverflow.Clip,
                                            )
                                        }
                                    }

                                    Icon(
                                        Icons.Default.Edit,
                                        stringResource(R.string.edit_address_label),
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    role = Role.Button,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = rememberRipple(bounded = false, radius = Size24dp),
                                                ) { wantsToEditLabel = true }
                                                .padding(horizontal = 5.dp, vertical = 10.dp),
                                    )

                                    Spacer(Modifier.width(3.dp))

                                    Icon(
                                        Icons.Default.ContentCopy,
                                        stringResource(R.string.copy_to_clipboard),
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    role = Role.Button,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = rememberRipple(bounded = false, radius = Size24dp),
                                                ) {
                                                    clipboardManager.setText(AnnotatedString(selectedAddress.address))

                                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.monero_address_copied_to_clipboard),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                                .padding(horizontal = 5.dp, vertical = 10.dp),
                                    )

                                    Spacer(Modifier.width(10.dp))
                                }
                            }

                            if (!generating) {
                                IconButton(
                                    onClick = {
                                        generating = true
                                        scope.launch(Dispatchers.IO) {
                                            val address = account.newSubaddress()
                                            addresses += address

                                            generating = false
                                            selectedIndex = addresses.lastIndex
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        stringResource(R.string.generate_new_subaddress),
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(10.dp))
                                CircularProgressIndicator(modifier = Modifier.size(30.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            stringResource(R.string.monero_fund_wallet_qrcode_description),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(Modifier.height(10.dp))

                        QrCodeDrawer(contents = "monero:${selectedAddress.address}", margin = 50f)
                    } else {
                        Text(stringResource(R.string.loading))
                    }
                }
            }
        }
    }

    if (wantsToSelectAddress) {
        SpinnerSelectionDialog(
            options = addressOptions.toImmutableList(),
            onDismiss = { wantsToSelectAddress = false },
            onSelect = {
                selectedIndex = it
                wantsToSelectAddress = false
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    it.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            it.explainer?.let {
                Spacer(Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        color = Color.Gray,
                        fontSize = Font14SP,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (wantsToEditLabel) {
        if (addresses.isNotEmpty()) {
            val selectedAddress = addresses[selectedIndex]
            EditAddressLabelDialog(
                currentLabel = selectedAddress.label,
                onSave = {
                    wantsToEditLabel = false
                    val scope = Amethyst.instance.applicationIOScope
                    scope.launch {
                        account.setSubaddressLabel(selectedAddress.index, it)
                        val newAddresses = addresses.toMutableList()
                        newAddresses[selectedIndex] = newAddresses[selectedIndex].copy(label = it)
                        addresses = newAddresses
                    }
                },
                onDismiss = { wantsToEditLabel = false },
            )
        }
    }
}

@Composable
fun EditAddressLabelDialog(
    currentLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(currentLabel) }

    Dialog(
        onDismissRequest = {
            onDismiss()
        },
    ) {
        Surface {
            Column(Modifier.padding(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onPress = {
                        onDismiss()
                    })

                    Button(
                        onClick = { onSave(label) },
                        shape = ButtonBorder,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { onSave(label) }),
                )
            }
        }
    }
}

enum class TransactionPriority {
    UNIMPORTANT,
    NORMAL,
    ELEVATED,
    PRIORITY,
    ;

    fun toLocalizedString(context: Context): String {
        return if (this == UNIMPORTANT) {
            context.getString(R.string.transaction_priority_unimportant)
        } else if (this == NORMAL) {
            context.getString(R.string.transaction_priority_normal)
        } else if (this == ELEVATED) {
            context.getString(R.string.transaction_priority_elevated)
        } else if (this == PRIORITY) {
            context.getString(R.string.transaction_priority_priority)
        } else {
            name
        }
    }
}

class MoneroSendViewModel(val account: Account) : ViewModel() {
    var address by mutableStateOf("")
    var addressInvalid by mutableStateOf(false)

    var amount by mutableStateOf("")
    var amountInvalid by mutableStateOf(false)

    var priority by mutableStateOf(account.defaultMoneroTransactionPriority)

    var sending by mutableStateOf(false)
    var sendError by mutableStateOf("")

    fun updateFromUri(uri: String) {
        reset()

        var moneroUri = Uri.parse(uri)
        if (moneroUri.scheme != "monero") {
            throw IllegalArgumentException("Unsupported scheme")
        }

        // make it relative so the library doesn't complain when we try to parse the query parameters
        moneroUri = Uri.parse(uri.removePrefix("monero:"))

        address =
            moneroUri.schemeSpecificPart
                .split("?")
                .first()

        moneroUri.getQueryParameter("tx_amount")?.let {
            amount = it
        }
    }

    fun validate(onValid: () -> Unit) {
        viewModelScope.launch {
            if (validateSync(address, amount)) {
                onValid()
            }
        }
    }

    private fun validateSync(
        address: String,
        amount: String,
    ): Boolean {
        sendError = ""
        addressInvalid = false
        amountInvalid = false

        if (!account.moneroAddressIsValid(address)) {
            addressInvalid = true
            return false
        }

        val amountDouble = amount.toDoubleOrNull()
        if (amountDouble == null || amountDouble == 0.0 || decToPiconero(amount) == null) {
            amountInvalid = true
            return false
        }

        return true
    }

    fun reset() {
        address = ""
        addressInvalid = false

        amount = ""
        amountInvalid = false

        priority = account.defaultMoneroTransactionPriority

        sendError = ""
    }

    fun send(
        onProgress: (Float) -> Unit,
        onSuccess: () -> Unit,
    ) {
        sending = true
        viewModelScope.launch(Dispatchers.IO) {
            val address = address
            val amount = amount

            if (!validateSync(address, amount)) {
                sending = false
                return@launch
            }

            onProgress(0.2f)

            val scope = Amethyst.instance.applicationIOScope
            scope.launch {
                val status = account.sendMonero(address, decToPiconero(amount)!!, priority)
                if (status.isOk()) {
                    sending = false
                    onProgress(1f)
                    onSuccess()
                } else {
                    onProgress(0f)

                    sending = false
                    sendError = status.error
                }
            }
        }
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <MoneroSendViewModel : ViewModel> create(modelClass: Class<MoneroSendViewModel>): MoneroSendViewModel {
            return MoneroSendViewModel(account) as MoneroSendViewModel
        }
    }
}

@Composable
fun SendDialog(
    moneroViewModel: MoneroSendViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var progress by remember { mutableFloatStateOf(0.0f) }
    val progressAnimation by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "TransferProgressIndicator",
    )

    var qrError by remember { mutableStateOf(false) }

    if (moneroViewModel.sendError.isEmpty() && !qrError) {
        Dialog(
            onDismissRequest = {
                if (!moneroViewModel.sending) {
                    moneroViewModel.reset()
                    onDismiss()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(tonalElevation = 1.dp) {
                LinearProgressIndicator(progress = { progressAnimation })

                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!moneroViewModel.sending) {
                                    moneroViewModel.reset()
                                    onDismiss()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = Size5dp),
                            enabled = !moneroViewModel.sending,
                        ) {
                            CloseIcon()
                        }

                        TipButton(text = stringResource(R.string.send) + " ", isActive = !moneroViewModel.sending) {
                            moneroViewModel.send(
                                onProgress = {
                                    progress = it
                                },
                                onSuccess = {
                                    moneroViewModel.reset()
                                    onDismiss()
                                },
                            )
                        }
                    }

                    val options =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                        )

                    var qrScanning by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = moneroViewModel.address,
                        label = { Text(stringResource(R.string.address)) },
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { moneroViewModel.address = it },
                        keyboardOptions = options.copy(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = moneroViewModel.addressInvalid,
                        supportingText =
                            if (moneroViewModel.addressInvalid) {
                                { Text(stringResource(R.string.invalid_monero_address)) }
                            } else {
                                null
                            },
                        trailingIcon = {
                            IconButton(onClick = { qrScanning = true }) {
                                Icon(
                                    Icons.Default.QrCode,
                                    stringResource(R.string.accessibility_scan_qr_code),
                                )
                            }
                        },
                    )

                    if (qrScanning) {
                        SimpleQrCodeScanner {
                            qrScanning = false

                            if (!it.isNullOrEmpty()) {
                                try {
                                    moneroViewModel.updateFromUri(it)
                                } catch (e: Exception) {
                                    qrError = true
                                }
                            }
                        }
                    }

                    Row {
                        OutlinedTextField(
                            value = moneroViewModel.amount,
                            label = { Text(stringResource(R.string.amount)) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(end = 5.dp),
                            onValueChange = { moneroViewModel.amount = it },
                            keyboardOptions =
                                options.copy(
                                    keyboardType = KeyboardType.Number,
                                ),
                            singleLine = true,
                            isError = moneroViewModel.amountInvalid,
                            supportingText =
                                if (moneroViewModel.amountInvalid) {
                                    { Text(stringResource(R.string.invalid_monero_amount)) }
                                } else {
                                    null
                                },
                        )

                        TextSpinner(
                            label = stringResource(R.string.priority),
                            placeholder = moneroViewModel.priority.toLocalizedString(context),
                            options =
                                TransactionPriority.entries.map {
                                    TitleExplainer(it.toLocalizedString(context))
                                }.toPersistentList(),
                            onSelect = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    } else if (moneroViewModel.sendError.isNotEmpty()) {
        val text = "${moneroViewModel.sendError[0].uppercase()}${moneroViewModel.sendError.substring(1)}"
        AlertDialog(
            title = { Text(stringResource(R.string.error_dialog_transfer_error)) },
            text = {
                SelectionContainer {
                    Text(text)
                }
            },
            onDismissRequest = { moneroViewModel.sendError = "" },
            confirmButton = { Button({ moneroViewModel.sendError = "" }) { Text(stringResource(android.R.string.ok)) } },
        )
    } else if (qrError) {
        AlertDialog(
            title = { Text(stringResource(R.string.invalid_qr_code)) },
            text = { Text(stringResource(R.string.invalid_monero_qr_code)) },
            onDismissRequest = { qrError = false },
            confirmButton = { Button({ qrError = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
fun BackupSeedDialog(
    accountViewModel: AccountViewModel,
    restoreHeight: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val clipboardManager = LocalClipboardManager.current

    var offsetPassphrase by remember { mutableStateOf("") }
    var showCharsOffsetPassphrase by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copySeed(context, scope, accountViewModel, clipboardManager, offsetPassphrase, onLoading = { loading = it })
                accountViewModel.account.changeIsMoneroSeedBackedUp(true)
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            tonalElevation = 1.dp,
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(Modifier.padding(10.dp)) {
                    CloseButton(onPress = onDismiss)
                }

                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.backup_seed),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(10.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.backup_seed_dialog_description_1),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(Modifier.height(15.dp))

                        Text(
                            stringResource(R.string.backup_seed_dialog_description_2),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(Modifier.height(15.dp))

                        Button(
                            onClick = {
                                authenticate(
                                    title = context.getString(R.string.copy_my_seed),
                                    context = context,
                                    keyguardLauncher = keyguardLauncher,
                                    onApproved = {
                                        copySeed(context, scope, accountViewModel, clipboardManager, offsetPassphrase, onLoading = { loading = it })
                                        accountViewModel.account.changeIsMoneroSeedBackedUp(true)
                                    },
                                    onError = { title, message -> accountViewModel.toast(title, message) },
                                )
                            },
                            shape = ButtonBorder,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            contentPadding = ButtonPadding,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            if (loading) {
                                LoadingAnimation()
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(stringResource(R.string.copy_my_seed))
                        }

                        Spacer(Modifier.height(15.dp))

                        Text(
                            stringResource(R.string.backup_seed_dialog_description_restore_height),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(Modifier.height(15.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.restore_height_with_height, restoreHeight),
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            Spacer(Modifier.width(2.dp))

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("$restoreHeight"))

                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.restore_height_copied_to_clipboard),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                modifier =
                                    Modifier.size(
                                        with(LocalDensity.current) {
                                            MaterialTheme.typography.bodyLarge.fontSize.toDp() + 3.dp
                                        },
                                    ),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    stringResource(R.string.copy_restore_height),
                                    modifier =
                                        Modifier.size(
                                            with(LocalDensity.current) {
                                                MaterialTheme.typography.bodyLarge.fontSize.toDp()
                                            },
                                        ),
                                )
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        Text(
                            stringResource(R.string.backup_seed_dialog_description_offset_passphrase),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = offsetPassphrase,
                            onValueChange = {
                                offsetPassphrase = it
                            },
                            label = { Text(stringResource(R.string.offset_passphrase)) },
                            keyboardOptions =
                                KeyboardOptions.Default.copy(
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                            singleLine = true,
                            visualTransformation = if (showCharsOffsetPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showCharsOffsetPassphrase = !showCharsOffsetPassphrase }) {
                                        Icon(
                                            imageVector =
                                                if (showCharsOffsetPassphrase) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription =
                                                if (showCharsOffsetPassphrase) {
                                                    stringResource(R.string.show_passphrase)
                                                } else {
                                                    stringResource(
                                                        R.string.hide_passphrase,
                                                    )
                                                },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

fun copySeed(
    context: Context,
    scope: CoroutineScope,
    accountViewModel: AccountViewModel,
    clipboardManager: ClipboardManager,
    offsetPassphrase: String,
    onLoading: (Boolean) -> Unit,
) {
    if (offsetPassphrase.isEmpty()) {
        val seed = accountViewModel.account.moneroSeed!!
        clipboardManager.setText(AnnotatedString(seed))

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(
                context,
                context.getString(R.string.monero_seed_copied_to_clipboard),
                Toast.LENGTH_SHORT,
            ).show()
        }
    } else {
        onLoading(true)

        scope.launch {
            val seed = accountViewModel.account.seedWithPassphrase(offsetPassphrase)
            clipboardManager.setText(AnnotatedString(seed))

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.monero_seed_copied_to_clipboard),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                onLoading(false)
            }
        }
    }
}

@Composable
fun CustomRestoreHeightDialog(
    maxHeight: Long,
    onDismiss: () -> Unit,
    onSetRestoreHeight: (Long) -> Unit,
) {
    val context = LocalContext.current

    var restoreHeightText by rememberSaveable {
        mutableStateOf("")
    }
    var error by rememberSaveable {
        mutableStateOf("")
    }

    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(tonalElevation = 1.dp) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CloseButton(onPress = onDismiss)

                    Button(
                        onClick = {
                            val restoreHeight = restoreHeightText.toLongOrNull()
                            if (restoreHeight != null && restoreHeight <= maxHeight) {
                                onSetRestoreHeight(restoreHeight)
                            } else {
                                error = context.getString(R.string.invalid_height)
                            }
                        },
                        shape = ButtonBorder,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }

                OutlinedTextField(
                    value = restoreHeightText,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(0.dp),
                    label = { Text(stringResource(R.string.restore_height)) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    singleLine = true,
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                val restoreHeight = restoreHeightText.toLongOrNull()
                                if (restoreHeight != null && restoreHeight <= maxHeight) {
                                    onSetRestoreHeight(restoreHeight)
                                } else {
                                    error = context.getString(R.string.invalid_height)
                                }
                            },
                        ),
                    isError = error.isNotEmpty(),
                    supportingText =
                        if (error.isNotEmpty()) {
                            { Text(error) }
                        } else {
                            null
                        },
                    onValueChange = {
                        restoreHeightText = it
                    },
                )
            }
        }
    }
}

@Composable
fun EphemeralWalletWarning(
    accountViewModel: AccountViewModel,
    restoreHeight: Long,
    onDismiss: () -> Unit,
    onExplicitDismiss: () -> Unit,
    onBackedUp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val clipboardManager = LocalClipboardManager.current

    var offsetPassphrase by remember { mutableStateOf("") }
    var showCharsOffsetPassphrase by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copySeed(context, scope, accountViewModel, clipboardManager, offsetPassphrase, onLoading = { loading = it })
                onBackedUp()
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(Modifier.padding(10.dp)) {
                    CloseButton {
                        onExplicitDismiss()
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.warning),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.monero_ephemeral_wallet_seed_backup_warning),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(15.dp))

                    Button(
                        onClick = {
                            authenticate(
                                title = context.getString(R.string.copy_my_seed),
                                context = context,
                                keyguardLauncher = keyguardLauncher,
                                onApproved = {
                                    copySeed(context, scope, accountViewModel, clipboardManager, offsetPassphrase, onLoading = { loading = it })
                                    onBackedUp()
                                },
                                onError = { title, message -> accountViewModel.toast(title, message) },
                            )
                        },
                        shape = ButtonBorder,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        contentPadding = ButtonPadding,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        if (loading) {
                            LoadingAnimation()
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(stringResource(R.string.copy_my_seed))
                    }

                    Spacer(Modifier.height(15.dp))

                    Text(
                        stringResource(R.string.backup_seed_dialog_description_restore_height),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(15.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.restore_height_with_height, restoreHeight),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(Modifier.width(2.dp))

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("$restoreHeight"))

                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.restore_height_copied_to_clipboard),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            modifier =
                                Modifier.size(
                                    with(LocalDensity.current) {
                                        MaterialTheme.typography.bodyLarge.fontSize.toDp() + 3.dp
                                    },
                                ),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                stringResource(R.string.copy_restore_height),
                                modifier =
                                    Modifier.size(
                                        with(LocalDensity.current) {
                                            MaterialTheme.typography.bodyLarge.fontSize.toDp()
                                        },
                                    ),
                            )
                        }
                    }

                    Spacer(Modifier.height(15.dp))

                    Text(
                        stringResource(R.string.backup_seed_dialog_description_offset_passphrase),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = offsetPassphrase,
                        onValueChange = {
                            offsetPassphrase = it
                        },
                        label = { Text(stringResource(R.string.offset_passphrase)) },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                autoCorrect = false,
                                keyboardType = KeyboardType.Password,
                            ),
                        singleLine = true,
                        visualTransformation = if (showCharsOffsetPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { showCharsOffsetPassphrase = !showCharsOffsetPassphrase }) {
                                    Icon(
                                        imageVector =
                                            if (showCharsOffsetPassphrase) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription =
                                            if (showCharsOffsetPassphrase) {
                                                stringResource(R.string.show_passphrase)
                                            } else {
                                                stringResource(
                                                    R.string.hide_passphrase,
                                                )
                                            },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
