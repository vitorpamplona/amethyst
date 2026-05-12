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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.nwc.NwcPaymentHandler
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.text.NumberFormat
import java.util.Locale

enum class WalletScreen {
    HOME,
    CONNECT,
    SEND,
    RECEIVE,
}

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
    var currentScreen by remember { mutableStateOf(WalletScreen.HOME) }

    // NWC connection state
    var nwcUri by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    // Balance state
    var balanceSats by remember { mutableStateOf<Long?>(null) }
    var isLoadingBalance by remember { mutableStateOf(false) }

    // Send state
    var sendInvoice by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }

    // Receive state
    var receiveAmount by remember { mutableStateOf("") }
    var receiveDescription by remember { mutableStateOf("") }
    var generatedInvoice by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

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

                else -> { /* silently fail on auto-fetch */ }
            }
            isLoadingBalance = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            WalletScreen.HOME -> {
                WalletHomeContent(
                    nwcConnection = nwcConnection,
                    balanceSats = balanceSats,
                    isLoadingBalance = isLoadingBalance,
                    onConnect = { currentScreen = WalletScreen.CONNECT },
                    onSend = { currentScreen = WalletScreen.SEND },
                    onReceive = { currentScreen = WalletScreen.RECEIVE },
                    onRefreshBalance = {
                        if (nwcConnection != null) {
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
                        }
                    },
                    onDisconnect = {
                        accountManager.clearNwcConnection()
                        scope.launch {
                            snackbarHostState.showSnackbar("Wallet disconnected")
                        }
                    },
                )
            }

            WalletScreen.CONNECT -> {
                ConnectWalletContent(
                    nwcUri = nwcUri,
                    isConnecting = isConnecting,
                    error = connectionError,
                    onUriChanged = {
                        nwcUri = it
                        connectionError = null
                    },
                    onPasteFromClipboard = {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        val text =
                            try {
                                clipboard.getData(DataFlavor.stringFlavor) as? String
                            } catch (_: Exception) {
                                null
                            }
                        if (text != null) {
                            nwcUri = text
                        }
                    },
                    onConnect = {
                        val result = accountManager.setNwcConnection(nwcUri)
                        if (result.isSuccess) {
                            nwcUri = ""
                            currentScreen = WalletScreen.HOME
                            scope.launch {
                                snackbarHostState.showSnackbar("Wallet connected!")
                            }
                        } else {
                            connectionError = "Invalid NWC URI. Expected: nostr+walletconnect://..."
                        }
                    },
                    onBack = { currentScreen = WalletScreen.HOME },
                )
            }

            WalletScreen.SEND -> {
                SendContent(
                    invoice = sendInvoice,
                    isSending = isSending,
                    result = sendResult,
                    onInvoiceChanged = {
                        sendInvoice = it
                        sendResult = null
                    },
                    onPaste = {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        val text =
                            try {
                                clipboard.getData(DataFlavor.stringFlavor) as? String
                            } catch (_: Exception) {
                                null
                            }
                        if (text != null) sendInvoice = text
                    },
                    onSend = {
                        if (nwcConnection != null && sendInvoice.isNotBlank()) {
                            isSending = true
                            sendResult = null
                            scope.launch {
                                val result =
                                    paymentHandler.payInvoice(
                                        bolt11 = sendInvoice,
                                        nwcConnection = nwcConnection,
                                    )
                                when (result) {
                                    is NwcPaymentHandler.PaymentResult.Success -> {
                                        sendResult = "Payment successful!"
                                        sendInvoice = ""
                                    }

                                    is NwcPaymentHandler.PaymentResult.Error -> {
                                        sendResult = "Error: ${result.message}"
                                    }

                                    is NwcPaymentHandler.PaymentResult.Timeout -> {
                                        sendResult = "Payment timed out"
                                    }
                                }
                                isSending = false
                            }
                        }
                    },
                    hasWallet = nwcConnection != null,
                    onBack = { currentScreen = WalletScreen.HOME },
                )
            }

            WalletScreen.RECEIVE -> {
                ReceiveContent(
                    amount = receiveAmount,
                    description = receiveDescription,
                    generatedInvoice = generatedInvoice,
                    isGenerating = isGenerating,
                    onAmountChanged = { receiveAmount = it },
                    onDescriptionChanged = { receiveDescription = it },
                    onGenerate = {
                        if (nwcConnection != null) {
                            val amountSats = receiveAmount.toLongOrNull() ?: 0L
                            if (amountSats > 0) {
                                isGenerating = true
                                scope.launch {
                                    val amountMsats = amountSats * 1000
                                    val desc = receiveDescription.ifBlank { null }
                                    when (val result = paymentHandler.makeInvoice(nwcConnection, amountMsats, desc)) {
                                        is NwcPaymentHandler.InvoiceResult.Success -> {
                                            generatedInvoice = result.invoice
                                        }

                                        is NwcPaymentHandler.InvoiceResult.Error -> {
                                            snackbarHostState.showSnackbar("Invoice error: ${result.message}")
                                        }

                                        is NwcPaymentHandler.InvoiceResult.Timeout -> {
                                            snackbarHostState.showSnackbar("Invoice request timed out")
                                        }
                                    }
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    onCopyInvoice = { invoice ->
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(StringSelection(invoice), null)
                        scope.launch {
                            snackbarHostState.showSnackbar("Invoice copied to clipboard")
                        }
                    },
                    hasWallet = nwcConnection != null,
                    onBack = { currentScreen = WalletScreen.HOME },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun WalletHomeContent(
    nwcConnection: Nip47URINorm?,
    balanceSats: Long?,
    isLoadingBalance: Boolean,
    onConnect: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onRefreshBalance: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (nwcConnection == null) {
            // No wallet connected
            NoWalletContent(onConnect = onConnect)
        } else {
            // Connected wallet
            WalletBalanceCard(
                balanceSats = balanceSats,
                isLoading = isLoadingBalance,
                walletRelay = nwcConnection.relayUri.toString(),
                onRefresh = onRefreshBalance,
            )

            // Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSend,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(symbol = MaterialSymbols.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send")
                }
                OutlinedButton(
                    onClick = onReceive,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(symbol = MaterialSymbols.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Receive")
                }
            }

            HorizontalDivider()

            // Connection info
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

            TextButton(onClick = onDisconnect) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NoWalletContent(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
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
    walletRelay: String,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
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

@Composable
private fun ConnectWalletContent(
    nwcUri: String,
    isConnecting: Boolean,
    error: String?,
    onUriChanged: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back")
        }

        Text(
            text = "Connect Wallet",
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = "Paste your Nostr Wallet Connect URI to connect a Lightning wallet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = nwcUri,
            onValueChange = onUriChanged,
            label = { Text("NWC URI") },
            placeholder = { Text("nostr+walletconnect://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPasteFromClipboard) {
                Text("Paste from Clipboard")
            }
        }

        Button(
            onClick = onConnect,
            enabled = nwcUri.isNotBlank() && !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Connect")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Supported wallets:",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Alby Hub, Phoenix, Coinos, LNbits, Zeus, Mutiny, Strike",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Get an NWC connection URI from your wallet's settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SendContent(
    invoice: String,
    isSending: Boolean,
    result: String?,
    onInvoiceChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onSend: () -> Unit,
    hasWallet: Boolean,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back")
        }

        Text(
            text = "Send Payment",
            style = MaterialTheme.typography.titleLarge,
        )

        if (!hasWallet) {
            Text(
                text = "Connect a wallet first to send payments.",
                color = MaterialTheme.colorScheme.error,
            )
            return
        }

        OutlinedTextField(
            value = invoice,
            onValueChange = onInvoiceChanged,
            label = { Text("BOLT11 Invoice") },
            placeholder = { Text("lnbc...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 6,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPaste) {
                Text("Paste")
            }
        }

        Button(
            onClick = onSend,
            enabled = invoice.isNotBlank() && !isSending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sending...")
            } else {
                Text("Pay Invoice")
            }
        }

        if (result != null) {
            val isError = result.startsWith("Error") || result.contains("timed out")
            Text(
                text = result,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ReceiveContent(
    amount: String,
    description: String,
    generatedInvoice: String?,
    isGenerating: Boolean,
    onAmountChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onCopyInvoice: (String) -> Unit,
    hasWallet: Boolean,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back")
        }

        Text(
            text = "Receive Payment",
            style = MaterialTheme.typography.titleLarge,
        )

        if (!hasWallet) {
            Text(
                text = "Connect a wallet first to receive payments.",
                color = MaterialTheme.colorScheme.error,
            )
            return
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { new -> if (new.all { it.isDigit() }) onAmountChanged(new) },
            label = { Text("Amount (sats)") },
            placeholder = { Text("1000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            label = { Text("Description (optional)") },
            placeholder = { Text("What's this for?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = onGenerate,
            enabled = amount.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Create Invoice")
        }

        if (generatedInvoice != null) {
            HorizontalDivider()
            Text(
                text = "Invoice Created",
                style = MaterialTheme.typography.titleSmall,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = generatedInvoice,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                )
            }
            Button(
                onClick = { onCopyInvoice(generatedInvoice) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copy Invoice")
            }
        }
    }
}

private fun formatSats(sats: Long): String = NumberFormat.getNumberInstance(Locale.getDefault()).format(sats)
