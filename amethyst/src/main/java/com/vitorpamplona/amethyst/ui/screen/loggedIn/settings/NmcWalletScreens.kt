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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.service.namecoin.NmcWalletService
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NameAvailability
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcBalance
import com.vitorpamplona.quartz.nip05.namecoin.wallet.PendingNameRegistration
import kotlinx.coroutines.launch

private val NmcBlue = Color(0xFF4A90D9)
private val NmcGreen = Color(0xFF2E8B57)
private val NmcOrange = Color(0xFFFF9800)

/**
 * Full NMC wallet screen: balance, send, key management, name operations.
 */
@Composable
fun NmcWalletFullScreen(
    walletService: NmcWalletService,
    accountViewModel: AccountViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val isLoaded by walletService.isLoaded.collectAsState()
    val balance by walletService.balance.collectAsState()
    val address by walletService.address.collectAsState()
    val history by walletService.history.collectAsState()
    val blockHeight by walletService.blockHeight.collectAsState()
    val pending by walletService.pendingRegistrations.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Text("Namecoin Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (!isLoaded) {
            KeyLoadSection(walletService, accountViewModel)
        } else {
            // Balance card
            BalanceCard(balance, address, blockHeight)

            // Action buttons row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { walletService.refreshAll() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }

            // Key export section
            KeyExportSection(walletService)

            HorizontalDivider()

            // Send NMC
            SendSection(walletService)

            HorizontalDivider()

            // Name registration
            NameRegistrationSection(walletService)

            // Pending registrations
            if (pending.isNotEmpty()) {
                PendingRegistrationsSection(pending, blockHeight, walletService)
            }

            HorizontalDivider()

            // Send/transfer name
            SendNameSection(walletService)

            HorizontalDivider()

            // Transaction history
            if (history.isNotEmpty()) {
                Text("Recent transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                history.take(10).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.txHash.take(20) + "…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("block ${entry.height}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()

            // Address type settings
            AddressTypeSection(walletService)

            HorizontalDivider()

            // Multisig section
            MultisigSection()

            // Lock button
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { walletService.lock() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Lock wallet")
            }
        }
    }
}

@Composable
private fun AddressTypeSection(walletService: NmcWalletService) {
    var selectedType by rememberSaveable { mutableStateOf(walletService.wallet.addressType.name) }
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Address type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose the default address format. Native SegWit (P2WPKH) is recommended for lowest fees.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val types =
            listOf(
                Triple("P2WPKH", "Native SegWit (P2WPKH)", "nc1… addresses — lowest fees, recommended default"),
                Triple("P2SH_P2WPKH", "Wrapped SegWit (P2SH-P2WPKH)", "6… addresses — good fees, wide compatibility"),
                Triple("P2PKH", "Legacy (P2PKH)", "N… addresses — required for name ops"),
            )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            types.forEach { (id, label, desc) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (selectedType == id) {
                                    NmcBlue.copy(alpha = 0.1f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                        ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (id == "P2PKH") {
                                Text(
                                    "⚠ Legacy keys are no longer easily imported into Namecoin Core",
                                    fontSize = 10.sp,
                                    color = NmcOrange,
                                )
                            }
                            if (id != "P2PKH") {
                                Text(
                                    "⚠ Name operations require Legacy (P2PKH) for the name output",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                        if (selectedType == id) {
                            Icon(Icons.Default.Check, null, tint = NmcBlue, modifier = Modifier.size(20.dp))
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedType = id
                                    val type =
                                        com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcAddressType
                                            .valueOf(id)
                                    walletService.setAddressType(type)
                                },
                                contentPadding =
                                    androidx.compose.foundation.layout
                                        .PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text("Select", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultisigSection() {
    var showMultisig by rememberSaveable { mutableStateOf(false) }
    var mValue by rememberSaveable { mutableStateOf("2") }
    var pubKeysInput by rememberSaveable { mutableStateOf("") }
    var importPath by rememberSaveable { mutableStateOf("") }
    var multisigStatus by remember { mutableStateOf<String?>(null) }
    var cosignerStatus by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Multisig", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showMultisig = !showMultisig }) {
                Text(if (showMultisig) "Hide" else "Setup")
            }
        }

        AnimatedVisibility(showMultisig) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // M-of-N config
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Create multisig wallet", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Set up an m-of-n multisig address. All cosigners must provide their compressed public keys (hex).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = mValue,
                            onValueChange = { mValue = it.filter { c -> c.isDigit() } },
                            label = { Text("Required signatures (m)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = pubKeysInput,
                            onValueChange = { pubKeysInput = it },
                            label = { Text("Public keys (one hex key per line)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )

                        Button(
                            onClick = {
                                val keys = pubKeysInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                                val m = mValue.toIntOrNull() ?: 0
                                multisigStatus =
                                    if (m < 1 || m > keys.size) {
                                        "Error: m must be between 1 and ${keys.size}"
                                    } else if (keys.any { it.length != 66 }) {
                                        "Error: all keys must be 66-char hex (compressed pubkeys)"
                                    } else {
                                        try {
                                            val config =
                                                com.vitorpamplona.quartz.nip05.namecoin.wallet.MultisigConfig(
                                                    requiredSigs = m,
                                                    pubKeys = keys,
                                                    label = "$m-of-${keys.size}",
                                                )
                                            "✓ $m-of-${keys.size} multisig\nAddress: ${config.address}"
                                        } catch (e: Exception) {
                                            "Error: ${e.message}"
                                        }
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NmcBlue),
                        ) {
                            Text("Create multisig address")
                        }

                        multisigStatus?.let { status ->
                            Text(
                                status,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color =
                                    if (status.startsWith("Error")) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        NmcGreen
                                    },
                            )
                        }
                    }
                }

                // Filesystem cosigner import
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import cosigner from file", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Import public keys from Electrum-NMC wallets, WIF files, or public key files on the filesystem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = importPath,
                            onValueChange = { importPath = it },
                            label = { Text("File path") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )

                        Button(
                            onClick = {
                                cosignerStatus =
                                    try {
                                        val cosigners =
                                            com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcFilesystemWallet
                                                .importCosigners(importPath.trim())
                                        if (cosigners.isEmpty()) {
                                            "No keys found in file"
                                        } else {
                                            cosigners.joinToString("\n") { c ->
                                                "✓ ${c.label}: ${c.pubKeyHex.take(16)}…" +
                                                    if (c.canSign) " (can sign)" else " (watch-only)"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        "Error: ${e.message}"
                                    }
                            },
                            enabled = importPath.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NmcBlue),
                        ) {
                            Text("Import cosigners")
                        }

                        cosignerStatus?.let { status ->
                            Text(
                                status,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color =
                                    if (status.startsWith("Error") || status.startsWith("No ")) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        NmcGreen
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: NmcBalance,
    address: String?,
    blockHeight: Int?,
) {
    val clipboard = LocalClipboardManager.current
    Card(colors = CardDefaults.cardColors(containerColor = NmcBlue), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("NMC Balance", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text("%.8f NMC".format(balance.totalNmc), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (balance.unconfirmed != 0L) {
                Text("%.8f unconfirmed".format(balance.unconfirmedNmc), color = Color.White.copy(0.5f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            address?.let { addr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(addr, color = Color.White.copy(0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(addr)) }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy address", tint = Color.White.copy(0.7f), modifier = Modifier.size(14.dp))
                    }
                }
            }
            blockHeight?.let { Text("Block #$it", color = Color.White.copy(0.5f), fontSize = 10.sp) }
        }
    }
}

@Composable
private fun KeyLoadSection(
    walletService: NmcWalletService,
    accountViewModel: AccountViewModel? = null,
) {
    var method by rememberSaveable { mutableStateOf("nostr") }
    var wifInput by rememberSaveable { mutableStateOf("") }
    var mnemonicInput by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Load wallet", fontWeight = FontWeight.SemiBold)
            Text("Choose how to load your Namecoin wallet key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("nostr" to "Nostr key", "wif" to "WIF", "mnemonic" to "Mnemonic", "new" to "New").forEachIndexed { i, (id, label) ->
                    SegmentedButton(selected = method == id, onClick = {
                        method = id
                        error = null
                    }, shape = SegmentedButtonDefaults.itemShape(i, 4)) { Text(label, fontSize = 11.sp) }
                }
            }

            when (method) {
                "nostr" -> {
                    Text("Derives a Namecoin key from your Nostr private key. Deterministic — same Nostr key always produces the same NMC address.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            val nostrPrivKey =
                                accountViewModel
                                    ?.account
                                    ?.settings
                                    ?.keyPair
                                    ?.privKey
                            if (nostrPrivKey != null) {
                                try {
                                    walletService.loadFromNostrKey(nostrPrivKey)
                                    walletService.refreshAll()
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            } else {
                                error = "No private key available (read-only account or external signer)"
                            }
                        },
                        enabled = accountViewModel != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NmcBlue),
                    ) {
                        Text("Derive from Nostr key")
                    }
                }

                "wif" -> {
                    OutlinedTextField(
                        value = wifInput,
                        onValueChange = {
                            wifInput = it
                            error = null
                        },
                        label = { Text("WIF private key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Button(onClick = {
                        try {
                            walletService.loadFromWif(wifInput.trim())
                            walletService.refreshAll()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NmcBlue)) { Text("Import WIF") }
                }

                "mnemonic" -> {
                    OutlinedTextField(
                        value = mnemonicInput,
                        onValueChange = {
                            mnemonicInput = it
                            error = null
                        },
                        label = { Text("BIP39 mnemonic (12 or 24 words)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Text("Uses BIP44 path m/44'/7'/0'/0/0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        try {
                            walletService.loadFromMnemonic(mnemonicInput.trim())
                            walletService.refreshAll()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NmcBlue)) { Text("Load from mnemonic") }
                }

                "new" -> {
                    var generatedMnemonic by remember { mutableStateOf<String?>(null) }
                    var generatedAddress by remember { mutableStateOf<String?>(null) }
                    val clipboardMgr = LocalClipboardManager.current

                    Text(
                        "Generate a fresh BIP39 mnemonic and derive a new Namecoin wallet. Write down the mnemonic — it's the only way to recover your funds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (generatedMnemonic == null) {
                        Button(
                            onClick = {
                                try {
                                    val entropy =
                                        java.security.SecureRandom().let { rng ->
                                            ByteArray(16).also { rng.nextBytes(it) } // 128 bits = 12 words
                                        }
                                    val mnemonic =
                                        com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
                                            .toMnemonics(entropy)
                                            .joinToString(" ")
                                    generatedMnemonic = mnemonic
                                    walletService.loadFromMnemonic(mnemonic)
                                    generatedAddress = walletService.wallet.address
                                    walletService.refreshAll()
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NmcGreen),
                        ) {
                            Text("Generate new wallet")
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NmcOrange.copy(alpha = 0.1f)),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚠ WRITE THIS DOWN", fontWeight = FontWeight.Bold, color = NmcOrange, fontSize = 14.sp)
                                Text("This mnemonic is the ONLY way to recover your wallet. Store it safely offline.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(
                                        generatedMnemonic!!,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                                .padding(10.dp),
                                    )
                                }
                                generatedAddress?.let {
                                    Text("Address: $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("BIP44 path: m/44'/7'/0'/0/0", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(
                                    onClick = { clipboardMgr.setText(AnnotatedString(generatedMnemonic!!)) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy mnemonic", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun KeyExportSection(walletService: NmcWalletService) {
    var showKey by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Private key", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Hide" else "Show WIF")
                }
            }
            AnimatedVisibility(showKey) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("WIF (import into Electrum-NMC):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(
                            walletService.exportWif(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(walletService.exportWif())) }, Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy WIF", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(walletService.exportPrivKeyHex())) }, Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy hex", fontSize = 12.sp)
                        }
                    }
                    Text("Keep this secret! Anyone with this key can spend your NMC.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // Mnemonic seed display
                    MnemonicSeedDisplay(walletService)

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // Filesystem export
                    FileExportButtons(walletService)
                }
            }
        }
    }
}

@Composable
private fun MnemonicSeedDisplay(walletService: NmcWalletService) {
    var showMnemonic by rememberSaveable { mutableStateOf(false) }
    var mnemonicInput by rememberSaveable { mutableStateOf("") }
    var verifyResult by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mnemonic seed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            TextButton(onClick = { showMnemonic = !showMnemonic }) {
                Text(if (showMnemonic) "Hide" else "Show", fontSize = 12.sp)
            }
        }

        AnimatedVisibility(showMnemonic) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "If you loaded this wallet from a mnemonic or generated a new wallet, " +
                        "enter the mnemonic below to verify it matches. " +
                        "If you derived from a Nostr key or imported WIF, there is no mnemonic — use WIF for backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )

                OutlinedTextField(
                    value = mnemonicInput,
                    onValueChange = {
                        mnemonicInput = it
                        verifyResult = null
                    },
                    label = { Text("Enter mnemonic to verify") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            verifyResult =
                                try {
                                    val words = mnemonicInput.trim()
                                    com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
                                        .validate(words)
                                    val testKey =
                                        com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcKeyManager
                                            .privateKeyFromMnemonic(words)
                                    val testAddr =
                                        com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcKeyManager
                                            .addressFromPrivKey(testKey)
                                    if (testAddr == walletService.wallet.address) {
                                        "✓ Mnemonic matches! Address: $testAddr"
                                    } else {
                                        "✗ Mnemonic valid but derives a different address: $testAddr"
                                    }
                                } catch (e: Exception) {
                                    "✗ Invalid mnemonic: ${e.message}"
                                }
                        },
                        enabled = mnemonicInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NmcBlue),
                    ) {
                        Text("Verify", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val text = clipboard.getText()?.text ?: ""
                            if (text.isNotBlank()) mnemonicInput = text
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Paste", fontSize = 12.sp)
                    }
                }

                verifyResult?.let { result ->
                    Text(
                        result,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (result.startsWith("✓")) NmcGreen else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileExportButtons(walletService: NmcWalletService) {
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // SAF launcher for public key export
    val pubKeyLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .CreateDocument("text/plain"),
        ) { uri ->
            if (uri != null) {
                exportStatus =
                    try {
                        val pubKeyHex = walletService.wallet.pubKeyHex ?: throw IllegalStateException("No key loaded")
                        val address = walletService.wallet.address ?: "unknown"
                        val content = "# Namecoin public key\n# Address: $address\n$pubKeyHex\n"
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        "✓ Public key saved"
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
            }
        }

    // SAF launcher for raw WIF export
    val wifLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .CreateDocument("text/plain"),
        ) { uri ->
            if (uri != null) {
                exportStatus =
                    try {
                        val wif = walletService.exportWif()
                        val address = walletService.wallet.address ?: "unknown"
                        val content = "# Namecoin WIF private key — KEEP SECRET\n# Address: $address\n$wif\n"
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        "✓ Private key saved (WIF)"
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
            }
        }

    // SAF launcher for Electrum-NMC import script
    val electrumLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .CreateDocument("text/plain"),
        ) { uri ->
            if (uri != null) {
                exportStatus =
                    try {
                        val wif = walletService.exportWif()
                        val p2pkhAddr =
                            com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcKeyManager
                                .addressFromPubKey(
                                    com.vitorpamplona.quartz.utils.Hex
                                        .decode(walletService.wallet.pubKeyHex!!),
                                )
                        val segwitAddr = walletService.wallet.address ?: "unknown"
                        val content =
                            buildString {
                                appendLine("# Electrum-NMC Import Instructions")
                                appendLine("# =================================")
                                appendLine("#")
                                appendLine("# Method 1: Electrum-NMC Console (Wallet → Console)")
                                appendLine("#   For p2wpkh (native SegWit, recommended):")
                                appendLine("#     wallet.import_privkey('p2wpkh:$wif')")
                                appendLine("#")
                                appendLine("#   For p2wpkh-p2sh (wrapped SegWit):")
                                appendLine("#     wallet.import_privkey('p2wpkh-p2sh:$wif')")
                                appendLine("#")
                                appendLine("#   For p2pkh (legacy):")
                                appendLine("#     wallet.import_privkey('p2pkh:$wif')")
                                appendLine("#")
                                appendLine("# Method 2: Electrum-NMC CLI")
                                appendLine("#   electrum-nmc importprivkey 'p2wpkh:$wif'")
                                appendLine("#")
                                appendLine("# Addresses derived from this key:")
                                appendLine("#   Native SegWit (P2WPKH): $segwitAddr")
                                appendLine("#   Legacy (P2PKH):         $p2pkhAddr")
                                appendLine("#")
                                appendLine("# WIF key (Namecoin, compressed):")
                                appendLine(wif)
                            }
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        "✓ Electrum-NMC import file saved"
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
            }
        }

    // SAF launcher for Namecoin Core import script
    val coreLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .CreateDocument("text/plain"),
        ) { uri ->
            if (uri != null) {
                exportStatus =
                    try {
                        val wif = walletService.exportWif()
                        val privKeyHex = walletService.exportPrivKeyHex()
                        val p2pkhAddr =
                            com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcKeyManager
                                .addressFromPubKey(
                                    com.vitorpamplona.quartz.utils.Hex
                                        .decode(walletService.wallet.pubKeyHex!!),
                                )
                        val segwitAddr = walletService.wallet.address ?: "unknown"
                        val pubKeyHex = walletService.wallet.pubKeyHex ?: ""
                        val content =
                            buildString {
                                appendLine("# Namecoin Core Import Instructions")
                                appendLine("# ==================================")
                                appendLine("#")
                                appendLine("# IMPORTANT: Namecoin Core uses descriptor wallets by default")
                                appendLine("# since v23.0. Legacy wallet import (importprivkey) is deprecated.")
                                appendLine("#")
                                appendLine("# Option A: Import as descriptor (recommended for Core ≥23.0)")
                                appendLine("#   1. Create a new descriptor wallet:")
                                appendLine("#      namecoin-cli createwallet \"imported\" false true \"\" false true")
                                appendLine("#")
                                appendLine("#   2. Import the key as a descriptor:")
                                appendLine("#      namecoin-cli -rpcwallet=imported importdescriptors '[{")
                                appendLine("#        \"desc\": \"wpkh($privKeyHex)#CHECKSUM\",")
                                appendLine("#        \"timestamp\": \"now\",")
                                appendLine("#        \"label\": \"amethyst-import\"")
                                appendLine("#      }]'")
                                appendLine("#")
                                appendLine("#   Note: Replace #CHECKSUM with the output of:")
                                appendLine("#      namecoin-cli getdescriptorinfo \"wpkh($privKeyHex)\"")
                                appendLine("#")
                                appendLine("# Option B: Legacy wallet import (Core <23.0 or legacy wallet)")
                                appendLine("#   namecoin-cli importprivkey '$wif' 'amethyst-import' true")
                                appendLine("#   (⚠ Only imports as P2PKH. Not recommended for new wallets.)")
                                appendLine("#")
                                appendLine("# Option C: Import as watch-only (public key only)")
                                appendLine("#   namecoin-cli importpubkey '$pubKeyHex' 'amethyst-watch' true")
                                appendLine("#")
                                appendLine("# Addresses:")
                                appendLine("#   Native SegWit (P2WPKH): $segwitAddr")
                                appendLine("#   Legacy (P2PKH):         $p2pkhAddr")
                                appendLine("#")
                                appendLine("# Raw keys:")
                                appendLine("#   WIF (compressed): $wif")
                                appendLine("#   Private key hex:  $privKeyHex")
                                appendLine("#   Public key hex:   $pubKeyHex")
                            }
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        "✓ Namecoin Core import file saved"
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
            }
        }

    Text("Export keys", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)

    // Row 1: Basic exports
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val address = walletService.wallet.address?.take(8) ?: "nmc"
                pubKeyLauncher.launch("nmc_pubkey_$address.txt")
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("Public key", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = {
                val address = walletService.wallet.address?.take(8) ?: "nmc"
                wifLauncher.launch("nmc_privkey_$address.wif")
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Raw WIF", fontSize = 11.sp)
        }
    }

    // Row 2: Wallet-specific exports
    Text("Import into external wallets", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val address = walletService.wallet.address?.take(8) ?: "nmc"
                electrumLauncher.launch("nmc_electrum_import_$address.txt")
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NmcBlue),
        ) {
            Text("Electrum-NMC", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = {
                val address = walletService.wallet.address?.take(8) ?: "nmc"
                coreLauncher.launch("nmc_core_import_$address.txt")
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NmcBlue),
        ) {
            Text("Namecoin Core", fontSize = 11.sp)
        }
    }

    exportStatus?.let { status ->
        Text(
            status,
            fontSize = 11.sp,
            color =
                if (status.startsWith("Error")) {
                    MaterialTheme.colorScheme.error
                } else {
                    NmcGreen
                },
        )
    }
}

@Composable
private fun SendSection(walletService: NmcWalletService) {
    var toAddress by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var sendState by remember { mutableStateOf<SendState>(SendState.Idle) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Send NMC", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = toAddress,
            onValueChange = { toAddress = it },
            label = { Text("Recipient address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (NMC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                scope.launch {
                    sendState = SendState.Sending
                    try {
                        val satoshis = (amount.toDouble() * 100_000_000).toLong()
                        val txid = walletService.send(toAddress.trim(), satoshis)
                        sendState = SendState.Success(txid)
                        walletService.refreshAll()
                    } catch (e: Exception) {
                        sendState = SendState.Error(e.message ?: "Send failed")
                    }
                }
            },
            enabled = toAddress.isNotBlank() && amount.toDoubleOrNull() != null && amount.toDouble() > 0,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NmcBlue),
        ) { Text("Send") }

        when (val s = sendState) {
            is SendState.Sending -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Broadcasting…", fontSize = 13.sp)
                }
            }

            is SendState.Success -> {
                Text("Sent! txid: ${s.txid.take(20)}…", color = NmcGreen, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }

            is SendState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            else -> {}
        }
    }
}

private sealed class SendState {
    data object Idle : SendState()

    data object Sending : SendState()

    data class Success(
        val txid: String,
    ) : SendState()

    data class Error(
        val message: String,
    ) : SendState()
}

@Composable
private fun NameRegistrationSection(walletService: NmcWalletService) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var availState by remember { mutableStateOf<AvailState>(AvailState.Idle) }
    var regState by remember { mutableStateOf<RegState>(RegState.Idle) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Register name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Register a .bit domain or id/ identity and link it to your Nostr pubkey.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = nameInput,
            onValueChange = {
                nameInput = it.lowercase().trim()
                availState = AvailState.Idle
                regState = RegState.Idle
            },
            label = { Text("Name (d/example or id/myname)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            supportingText = { if (nameInput.isNotBlank() && !walletService.isValidName(nameInput)) Text("Invalid name format", color = MaterialTheme.colorScheme.error) },
        )

        Button(
            onClick = {
                scope.launch {
                    availState = AvailState.Checking
                    availState = AvailState.Result(walletService.checkNameAvailability(nameInput))
                }
            },
            enabled = nameInput.isNotBlank() && walletService.isValidName(nameInput),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NmcBlue),
        ) { Text("Check availability") }

        when (val s = availState) {
            is AvailState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = NmcBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                }
            }

            is AvailState.Result -> {
                val (icon, color, text) =
                    when (s.result) {
                        is NameAvailability.Available -> Triple(Icons.Default.Check, NmcGreen, "$nameInput is available!")
                        is NameAvailability.Expired -> Triple(Icons.Default.Info, NmcOrange, "$nameInput expired — can re-register")
                        is NameAvailability.Taken -> Triple(Icons.Default.Close, MaterialTheme.colorScheme.error, "$nameInput is taken (expires in ~${(s.result as NameAvailability.Taken).expiresIn / 144} days)")
                        is NameAvailability.Error -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, (s.result as NameAvailability.Error).message)
                    }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text, fontSize = 13.sp)
                }

                if (s.result is NameAvailability.Available || s.result is NameAvailability.Expired) {
                    Button(onClick = {
                        scope.launch {
                            regState = RegState.Broadcasting
                            try {
                                val pending = walletService.registerNameNew(nameInput)
                                regState = RegState.Success(pending)
                                walletService.refreshAll()
                            } catch (e: Exception) {
                                regState = RegState.Error(e.message ?: "Registration failed")
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Register (step 1: NAME_NEW, costs 0.01 NMC + fee)") }
                }
            }

            else -> {}
        }

        when (val s = regState) {
            is RegState.Broadcasting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Broadcasting NAME_NEW…")
                }
            }

            is RegState.Success -> {
                Card(colors = CardDefaults.cardColors(containerColor = NmcGreen.copy(0.1f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("NAME_NEW broadcast!", fontWeight = FontWeight.SemiBold, color = NmcGreen, fontSize = 14.sp)
                        Text("txid: ${s.pending.nameNewTxid.take(24)}…", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("Wait ≥12 blocks (~2 hours), then complete step 2 below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            is RegState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            else -> {}
        }
    }
}

private sealed class AvailState {
    data object Idle : AvailState()

    data object Checking : AvailState()

    data class Result(
        val result: NameAvailability,
    ) : AvailState()
}

private sealed class RegState {
    data object Idle : RegState()

    data object Broadcasting : RegState()

    data class Success(
        val pending: PendingNameRegistration,
    ) : RegState()

    data class Error(
        val message: String,
    ) : RegState()
}

@Composable
private fun SendNameSection(walletService: NmcWalletService) {
    var showSection by rememberSaveable { mutableStateOf(false) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var recipientAddress by rememberSaveable { mutableStateOf("") }
    var nameTxid by rememberSaveable { mutableStateOf("") }
    var nameVout by rememberSaveable { mutableStateOf("0") }
    var nameValue by rememberSaveable { mutableStateOf("") }
    var transferState by remember { mutableStateOf<TransferState>(TransferState.Idle) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Transfer name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showSection = !showSection }) {
                Text(if (showSection) "Hide" else "Show")
            }
        }

        AnimatedVisibility(showSection) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Transfer ownership of a name to another address via NAME_UPDATE. The name's value is preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.lowercase().trim() },
                    label = { Text("Name (e.g. d/example)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                OutlinedTextField(
                    value = recipientAddress,
                    onValueChange = { recipientAddress = it.trim() },
                    label = { Text("New owner address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    supportingText = { Text("N… or 6… address of the new owner", fontSize = 10.sp) },
                )

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Current name UTXO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Find this in a block explorer: search the name and use the latest NAME_UPDATE/NAME_FIRSTUPDATE transaction.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = nameTxid,
                            onValueChange = { nameTxid = it.trim() },
                            label = { Text("Transaction ID (txid)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = nameVout,
                                onValueChange = { nameVout = it.filter { c -> c.isDigit() } },
                                label = { Text("Vout") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = nameValue,
                                onValueChange = { nameValue = it },
                                label = { Text("Current value (JSON)") },
                                singleLine = true,
                                modifier = Modifier.weight(2f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                supportingText = { Text("Leave blank to preserve existing", fontSize = 10.sp) },
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            transferState = TransferState.Sending
                            try {
                                val value = nameValue.ifBlank { "{}" }
                                val myPubKeyHex =
                                    walletService.wallet.pubKeyHex
                                        ?: throw IllegalStateException("Wallet not loaded")
                                val myPubKey =
                                    com.vitorpamplona.quartz.utils.Hex
                                        .decode(myPubKeyHex)
                                val myHash160 =
                                    com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcKeyManager
                                        .hash160(myPubKey)

                                // Current name output is a NAME_UPDATE script owned by us
                                val currentScript =
                                    com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcNameScripts
                                        .buildNameUpdateScript(nameInput, value, myHash160)

                                val txid =
                                    walletService.transferName(
                                        nameTxid = nameTxid,
                                        nameVout = nameVout.toIntOrNull() ?: 0,
                                        name = nameInput,
                                        currentScript = currentScript,
                                        currentOutputValue = com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcNameScripts.NAME_NEW_COST,
                                        currentNameValue = value,
                                        newOwnerAddress = recipientAddress,
                                    )
                                transferState = TransferState.Success(txid)
                                walletService.refreshAll()
                            } catch (e: Exception) {
                                transferState = TransferState.Error(e.message ?: "Transfer failed")
                            }
                        }
                    },
                    enabled = nameInput.isNotBlank() && recipientAddress.isNotBlank() && nameTxid.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NmcOrange),
                ) {
                    Text("Transfer name ownership")
                }

                when (val s = transferState) {
                    is TransferState.Sending -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Broadcasting transfer…", fontSize = 13.sp)
                        }
                    }

                    is TransferState.Success -> {
                        Text(
                            "Transferred! txid: ${s.txid.take(20)}…",
                            color = NmcGreen,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    is TransferState.Error -> {
                        Text(
                            s.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

private sealed class TransferState {
    data object Idle : TransferState()

    data object Sending : TransferState()

    data class Success(
        val txid: String,
    ) : TransferState()

    data class Error(
        val message: String,
    ) : TransferState()
}

@Composable
private fun PendingRegistrationsSection(
    pending: List<PendingNameRegistration>,
    blockHeight: Int?,
    walletService: NmcWalletService,
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pending registrations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        pending.forEach { reg ->
            val ready = blockHeight != null && reg.isReadyForFirstUpdate(blockHeight)
            val elapsed = blockHeight?.let { reg.blocksElapsed(it) } ?: 0
            var completeState by remember { mutableStateOf<String?>(null) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(reg.name, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        Text(if (ready) "Ready!" else "$elapsed/12 blocks", color = if (ready) NmcGreen else NmcOrange, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                    Text("NAME_NEW txid: ${reg.nameNewTxid.take(16)}…", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (ready) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val txid = walletService.completeRegistration(reg)
                                    completeState = "Registered! txid: ${txid.take(16)}…"
                                    walletService.refreshAll()
                                } catch (e: Exception) {
                                    completeState = "Error: ${e.message}"
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NmcGreen)) {
                            Text("Complete registration (NAME_FIRSTUPDATE)")
                        }
                    }
                    completeState?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else NmcGreen) }
                }
            }
        }
    }
}
