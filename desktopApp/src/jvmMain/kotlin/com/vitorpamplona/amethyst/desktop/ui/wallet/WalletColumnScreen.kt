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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.nwc.NwcPaymentHandler
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.auth.QrCodeCanvas
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

    Box(modifier = Modifier.fillMaxSize()) {
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
            onSend = { invoice ->
                scope.launch {
                    val result = paymentHandler.payInvoice(bolt11 = invoice, nwcConnection = nwcConnection)
                    when (result) {
                        is NwcPaymentHandler.PaymentResult.Success -> {
                            showSendDialog = false
                            snackbarHostState.showSnackbar("Payment successful!")
                        }

                        is NwcPaymentHandler.PaymentResult.Error -> {
                            snackbarHostState.showSnackbar("Error: ${result.message}")
                        }

                        is NwcPaymentHandler.PaymentResult.Timeout -> {
                            snackbarHostState.showSnackbar("Payment timed out")
                        }
                    }
                }
            },
        )
    }

    if (showReceiveDialog && nwcConnection != null) {
        ReceiveDialog(
            onDismiss = { showReceiveDialog = false },
            onGenerate = { amountSats, description ->
                scope.launch {
                    val result =
                        paymentHandler.makeInvoice(
                            nwcConnection = nwcConnection,
                            amountMsats = amountSats * 1000,
                            description = description.ifBlank { null },
                        )
                    when (result) {
                        is NwcPaymentHandler.InvoiceResult.Success -> {
                            result.invoice
                        }

                        is NwcPaymentHandler.InvoiceResult.Error -> {
                            snackbarHostState.showSnackbar("Error: ${result.message}")
                            null
                        }

                        is NwcPaymentHandler.InvoiceResult.Timeout -> {
                            snackbarHostState.showSnackbar("Invoice request timed out")
                            null
                        }
                    }
                }
            },
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

@Composable
private fun SendDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var invoice by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Send Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = invoice,
                    onValueChange = { invoice = it },
                    label = { Text("BOLT11 Invoice") },
                    placeholder = { Text("lnbc...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 6,
                )
                OutlinedButton(onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val text =
                        try {
                            clipboard.getData(DataFlavor.stringFlavor) as? String
                        } catch (_: Exception) {
                            null
                        }
                    if (text != null) invoice = text
                }) {
                    Text("Paste from Clipboard")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSending = true
                    onSend(invoice)
                },
                enabled = invoice.isNotBlank() && !isSending,
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sending...")
                } else {
                    Text("Pay Invoice")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        },
    )
}

@Composable
private fun ReceiveDialog(
    onDismiss: () -> Unit,
    onGenerate: (Long, String) -> Unit,
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
            shape = RoundedCornerShape(16.dp),
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
                        modifier = Modifier.align(Alignment.CenterHorizontally),
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

                    // QR code
                    QrCodeCanvas(
                        data = generatedInvoice!!,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
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
