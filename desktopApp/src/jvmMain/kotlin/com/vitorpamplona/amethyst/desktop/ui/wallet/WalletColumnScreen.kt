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
package com.vitorpamplona.amethyst.desktop.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.nwc.NwcPaymentHandler
import com.vitorpamplona.amethyst.desktop.security.privacyLockBlurWhenUnfocused
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.auth.QrCodeCanvas
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.lightning.Lud06
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.text.NumberFormat
import java.util.Locale

@Composable
fun WalletColumnScreen(
    account: AccountState.LoggedIn,
    accountManager: AccountManager,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    nwcConnection: Nip47URINorm?,
    appScope: CoroutineScope,
    onZapFeedback: (ZapFeedback) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog visibility
    var showConnectDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var showReceiveDialog by remember { mutableStateOf(false) }

    // Balance state
    var balanceSats by remember { mutableStateOf<Long?>(null) }
    var isLoadingBalance by remember { mutableStateOf(false) }

    val paymentHandler =
        remember(relayManager, localCache) {
            NwcPaymentHandler(relayManager, localCache)
        }

    // Auto-fetch balance when wallet connects
    LaunchedEffect(nwcConnection) {
        if (nwcConnection != null && balanceSats == null) {
            isLoadingBalance = true
            when (val result = paymentHandler.getBalance(nwcConnection)) {
                is NwcPaymentHandler.BalanceResult.Success -> {
                    balanceSats = result.balanceMsats / 1000
                }

                is NwcPaymentHandler.BalanceResult.Error -> {
                    println("NWC balance error: ${result.message}")
                    snackbarHostState.showSnackbar("Balance error: ${result.message}")
                }

                is NwcPaymentHandler.BalanceResult.Timeout -> {
                    println("NWC balance timeout")
                    snackbarHostState.showSnackbar("Balance request timed out")
                }
            }
            isLoadingBalance = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        com.vitorpamplona.amethyst.desktop.security.WalletFirstRunBanner(
            onSaved = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (nwcConnection == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NoWalletContent(onConnect = { showConnectDialog = true })
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        WalletBalanceCard(
                            balanceSats = balanceSats,
                            isLoading = isLoadingBalance,
                            onRefresh = {
                                isLoadingBalance = true
                                scope.launch {
                                    when (val result = paymentHandler.getBalance(nwcConnection)) {
                                        is NwcPaymentHandler.BalanceResult.Success -> {
                                            balanceSats = result.balanceMsats / 1000
                                        }

                                        is NwcPaymentHandler.BalanceResult.Error -> {
                                            snackbarHostState.showSnackbar("Balance error: ${result.message}")
                                        }

                                        is NwcPaymentHandler.BalanceResult.Timeout -> {
                                            snackbarHostState.showSnackbar("Balance request timed out")
                                        }
                                    }
                                    isLoadingBalance = false
                                }
                            },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showSendDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(symbol = MaterialSymbols.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send")
                            }
                            OutlinedButton(
                                onClick = { showReceiveDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(symbol = MaterialSymbols.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Receive")
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Connected Wallet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Relay: ${nwcConnection.relayUri}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Wallet: ${nwcConnection.pubKeyHex.take(8)}...${nwcConnection.pubKeyHex.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        TextButton(onClick = {
                            appScope.launch {
                                accountManager.clearNwcConnection(account.npub)
                                balanceSats = null
                            }
                        }) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // -- Dialogs --

    if (showConnectDialog) {
        ConnectWalletDialog(
            onDismiss = { showConnectDialog = false },
            onConnect = { uri ->
                scope.launch {
                    val result = accountManager.setNwcConnection(account.npub, uri)
                    if (result.isSuccess) {
                        showConnectDialog = false
                        snackbarHostState.showSnackbar("Wallet connected!")
                    }
                }
            },
        )
    }

    if (showSendDialog && nwcConnection != null) {
        SendDialog(
            onDismiss = { showSendDialog = false },
            onSuccess = {
                showSendDialog = false
                scope.launch { snackbarHostState.showSnackbar("Payment successful!") }
            },
            paymentHandler = paymentHandler,
            nwcConnection = nwcConnection,
        )
    }

    if (showReceiveDialog && nwcConnection != null) {
        ReceiveDialog(
            onDismiss = { showReceiveDialog = false },
            paymentHandler = paymentHandler,
            nwcConnection = nwcConnection,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun NoWalletContent(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No Wallet Connected",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Connect a Lightning wallet via\nNostr Wallet Connect (NWC)\nto send and receive sats.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onConnect) {
            Text("Connect Wallet")
        }
    }
}

@Composable
private fun WalletBalanceCard(
    balanceSats: Long?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (balanceSats != null) {
                Text(
                    text = "${formatSats(balanceSats)} sats",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.privacyLockBlurWhenUnfocused(),
                )
            } else {
                Text(
                    text = "-- sats",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// -- Dialogs --

@Composable
private fun ConnectWalletDialog(
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    var nwcUri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Wallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste your Nostr Wallet Connect URI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = nwcUri,
                    onValueChange = {
                        nwcUri = it
                        error = null
                    },
                    label = { Text("NWC URI") },
                    placeholder = { Text("nostr+walletconnect://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                OutlinedButton(onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val text =
                        try {
                            clipboard.getData(DataFlavor.stringFlavor) as? String
                        } catch (_: Exception) {
                            null
                        }
                    if (text != null) nwcUri = text
                }) {
                    Text("Paste from Clipboard")
                }
                Text(
                    "Supported: Alby Hub, Phoenix, Coinos, LNbits, Zeus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nwcUri.trim()
                    if (trimmed.startsWith("nostr+walletconnect://")) {
                        onConnect(trimmed)
                    } else {
                        error = "Invalid NWC URI. Expected: nostr+walletconnect://..."
                    }
                },
                enabled = nwcUri.isNotBlank(),
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Sealed state machine for send dialog — supports BOLT11, LNURL, and lightning addresses.
 */
private sealed class SendState {
    data object Idle : SendState()

    data class Resolving(
        val url: String,
    ) : SendState()

    data class NeedsAmount(
        val originalInput: String,
        val callbackUrl: String,
        val minSats: Long,
        val maxSats: Long,
        val commentAllowed: Int,
    ) : SendState()

    data class FetchingInvoice(
        val callbackUrl: String,
        val amountMilliSats: Long,
        val comment: String,
    ) : SendState()

    data class ReadyToPay(
        val bolt11: String,
    ) : SendState()

    data object Paying : SendState()

    data class Error(
        val message: String,
        val retryState: SendState,
    ) : SendState()
}

/**
 * Classifies payment input as BOLT11, LNURL, lightning address, or unknown.
 */
private fun classifyAndProcess(input: String): SendState {
    val trimmed =
        input
            .trim()
            .removePrefix("lightning:")
            .removePrefix("LIGHTNING:")
            .trim()
    if (trimmed.isBlank()) return SendState.Idle

    // 1. BOLT11 invoice
    LnInvoiceUtil.findInvoice(trimmed)?.let { return SendState.ReadyToPay(it) }

    // 2. LNURL bech32
    if (trimmed.lowercase().startsWith("lnurl")) {
        Lud06().toLnUrlp(trimmed)?.let { return SendState.Resolving(it) }
    }

    // 3. Lightning address (user@domain)
    if (trimmed.contains("@") && trimmed.contains(".")) {
        val parts = trimmed.split("@")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].contains(".")) {
            return SendState.Resolving("https://${parts[1]}/.well-known/lnurlp/${parts[0]}")
        }
    }

    return SendState.Idle
}

@Composable
private fun SendDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    paymentHandler: NwcPaymentHandler,
    nwcConnection: Nip47URINorm,
) {
    var input by remember { mutableStateOf("") }
    var sendState by remember { mutableStateOf<SendState>(SendState.Idle) }
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val mapper = remember { jacksonObjectMapper() }

    val isLoading =
        sendState is SendState.Resolving ||
            sendState is SendState.FetchingInvoice ||
            sendState is SendState.Paying

    // Auto-resolve LNURL endpoint
    LaunchedEffect(sendState) {
        val state = sendState
        if (state is SendState.Resolving) {
            try {
                val httpClient = DesktopHttpClient.currentClient()
                val request =
                    okhttp3.Request
                        .Builder()
                        .url(state.url)
                        .build()
                val response =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        httpClient.newCall(request).execute()
                    }
                val body = response.body.string()
                val json = mapper.readTree(body)
                val callback = json.get("callback")?.asText()?.ifBlank { null }
                if (callback == null) {
                    val errorMsg = json.get("reason")?.asText() ?: json.get("message")?.asText() ?: "Invalid LNURL endpoint"
                    sendState = SendState.Error(errorMsg, SendState.Idle)
                    return@LaunchedEffect
                }
                val minMsats = json.get("minSendable")?.asLong() ?: 1000L
                val maxMsats = json.get("maxSendable")?.asLong() ?: 100_000_000L
                val commentLen = json.get("commentAllowed")?.asInt() ?: 0
                val minSats = minMsats / 1000
                val maxSats = maxMsats / 1000
                // Fixed amount: prepopulate
                if (minSats == maxSats) {
                    amount = minSats.toString()
                }
                sendState = SendState.NeedsAmount(input, callback, minSats, maxSats, commentLen)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                sendState = SendState.Error("Failed to resolve: ${e.message}", SendState.Idle)
            }
        }
    }

    // Auto-fetch invoice from callback
    LaunchedEffect(sendState) {
        val state = sendState
        if (state is SendState.FetchingInvoice) {
            try {
                val httpClient = DesktopHttpClient.currentClient()
                val urlBinder = if (state.callbackUrl.contains("?")) "&" else "?"
                val encodedComment = java.net.URLEncoder.encode(state.comment, "utf-8")
                val url = "${state.callbackUrl}${urlBinder}amount=${state.amountMilliSats}&comment=$encodedComment"
                val request =
                    okhttp3.Request
                        .Builder()
                        .url(url)
                        .build()
                val response =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        httpClient.newCall(request).execute()
                    }
                val body = response.body.string()
                val json = mapper.readTree(body)
                val pr = json.get("pr")?.asText()?.ifBlank { null }
                if (pr != null) {
                    sendState = SendState.ReadyToPay(pr)
                } else {
                    val reason = json.get("reason")?.asText() ?: json.get("message")?.asText() ?: "No invoice returned"
                    sendState = SendState.Error(reason, SendState.Idle)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                sendState = SendState.Error("Invoice fetch failed: ${e.message}", SendState.Idle)
            }
        }
    }

    // Auto-pay when ReadyToPay (only for LNURL flow — BOLT11 uses button click)
    // For direct BOLT11 paste, user clicks Pay explicitly

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            modifier = Modifier.width(480.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Send Payment",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (!isLoading) onDismiss() }) {
                        Icon(
                            MaterialSymbols.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Input field
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        sendState = SendState.Idle
                    },
                    label = { Text("Invoice, LNURL, or lightning address") },
                    placeholder = { Text("lnbc..., lnurl1..., or user@domain") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4,
                    enabled = sendState is SendState.Idle || sendState is SendState.Error,
                )

                Spacer(Modifier.height(8.dp))

                if (sendState is SendState.Idle || sendState is SendState.Error) {
                    OutlinedButton(onClick = {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        val text =
                            try {
                                clipboard.getData(DataFlavor.stringFlavor) as? String
                            } catch (_: Exception) {
                                null
                            }
                        if (text != null) {
                            input = text
                            sendState = classifyAndProcess(text)
                        }
                    }) {
                        Text("Paste from Clipboard")
                    }
                }

                // Resolving spinner
                if (sendState is SendState.Resolving) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Resolving payment request...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Amount + comment form (LNURL flow)
                val needsAmount = sendState as? SendState.NeedsAmount
                if (needsAmount != null) {
                    Spacer(Modifier.height(12.dp))
                    val isFixed = needsAmount.minSats == needsAmount.maxSats
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { new -> if (new.all { it.isDigit() }) amount = new },
                        label = {
                            Text(
                                if (isFixed) {
                                    "Amount (${formatSats(needsAmount.minSats)} sats)"
                                } else {
                                    "Amount (${formatSats(needsAmount.minSats)} - ${formatSats(needsAmount.maxSats)} sats)"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = isFixed,
                    )
                    if (needsAmount.commentAllowed > 0) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { if (it.length <= needsAmount.commentAllowed) comment = it },
                            label = { Text("Comment (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                // Fetching invoice spinner
                if (sendState is SendState.FetchingInvoice) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching invoice...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Error display
                val errorState = sendState as? SendState.Error
                if (errorState != null) {
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action button
                val canPay =
                    when (sendState) {
                        is SendState.Idle -> input.isNotBlank()
                        is SendState.NeedsAmount -> amount.isNotBlank()
                        is SendState.ReadyToPay -> true
                        is SendState.Error -> true
                        else -> false
                    }

                Button(
                    onClick = {
                        when (val state = sendState) {
                            is SendState.Idle -> {
                                sendState = classifyAndProcess(input)
                            }
                            is SendState.NeedsAmount -> {
                                val amountSats = amount.toLongOrNull() ?: 0L
                                if (amountSats < state.minSats || amountSats > state.maxSats) {
                                    sendState =
                                        SendState.Error(
                                            "Amount must be between ${formatSats(state.minSats)} and ${formatSats(state.maxSats)} sats",
                                            state,
                                        )
                                } else {
                                    sendState = SendState.FetchingInvoice(state.callbackUrl, amountSats * 1000, comment)
                                }
                            }
                            is SendState.ReadyToPay -> {
                                sendState = SendState.Paying
                                scope.launch {
                                    val result = paymentHandler.payInvoice(bolt11 = state.bolt11, nwcConnection = nwcConnection)
                                    when (result) {
                                        is NwcPaymentHandler.PaymentResult.Success -> onSuccess()
                                        is NwcPaymentHandler.PaymentResult.Error -> {
                                            sendState = SendState.Error(result.message, SendState.Idle)
                                        }
                                        is NwcPaymentHandler.PaymentResult.Timeout -> {
                                            sendState = SendState.Error("Payment timed out", SendState.Idle)
                                        }
                                    }
                                }
                            }
                            is SendState.Error -> {
                                sendState = state.retryState
                            }
                            else -> {}
                        }
                    },
                    enabled = canPay && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (sendState is SendState.Paying) "Paying..." else "Processing...")
                    } else {
                        Text(
                            when (sendState) {
                                is SendState.Error -> "Retry"
                                is SendState.NeedsAmount -> "Pay"
                                is SendState.ReadyToPay -> "Pay Invoice"
                                else -> "Continue"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiveDialog(
    onDismiss: () -> Unit,
    paymentHandler: NwcPaymentHandler,
    nwcConnection: Nip47URINorm,
    snackbarHostState: SnackbarHostState,
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var generatedInvoice by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }) {
        Card(
            modifier = Modifier.width(400.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header: title + close X
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (generatedInvoice != null) "Invoice Created" else "Receive Payment",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (!isGenerating) onDismiss() }) {
                        Icon(
                            MaterialSymbols.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (generatedInvoice != null) {
                    // Amount + description
                    Text(
                        "${formatSats(amount.toLongOrNull() ?: 0)} sats",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .privacyLockBlurWhenUnfocused(),
                    )
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // QR code — sensitive, blur when window unfocused
                    QrCodeCanvas(
                        data = generatedInvoice!!,
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .privacyLockBlurWhenUnfocused(),
                        size = 240.dp,
                    )

                    Spacer(Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(generatedInvoice), null)
                            scope.launch { snackbarHostState.showSnackbar("Invoice copied!") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copy Invoice")
                    }
                } else {
                    // Input form
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { new -> if (new.all { it.isDigit() }) amount = new },
                        label = { Text("Amount (sats)") },
                        placeholder = { Text("1000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        placeholder = { Text("What's this for?") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val amountSats = amount.toLongOrNull() ?: 0L
                            if (amountSats > 0) {
                                isGenerating = true
                                scope.launch {
                                    val result =
                                        paymentHandler.makeInvoice(
                                            nwcConnection = nwcConnection,
                                            amountMsats = amountSats * 1000,
                                            description = description.ifBlank { null },
                                        )
                                    when (result) {
                                        is NwcPaymentHandler.InvoiceResult.Success -> {
                                            generatedInvoice = result.invoice
                                        }

                                        is NwcPaymentHandler.InvoiceResult.Error -> {
                                            snackbarHostState.showSnackbar("Error: ${result.message}")
                                        }

                                        is NwcPaymentHandler.InvoiceResult.Timeout -> {
                                            snackbarHostState.showSnackbar("Invoice request timed out")
                                        }
                                    }
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = amount.isNotBlank() && !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Create Invoice")
                    }
                }
            }
        }
    }
}

private fun formatSats(sats: Long): String = NumberFormat.getNumberInstance(Locale.getDefault()).format(sats)
