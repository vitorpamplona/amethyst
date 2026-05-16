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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.ExperimentalMaterial3Api as ExpM3
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

private enum class FeeTier(
    val label: String,
) {
    SLOW("Slow"),
    NORMAL("Normal"),
    FAST("Fast"),
}

private fun FeeEstimates.rateFor(tier: FeeTier): Double =
    when (tier) {
        FeeTier.SLOW -> slowSatPerVbyte
        FeeTier.NORMAL -> normalSatPerVbyte
        FeeTier.FAST -> fastSatPerVbyte
    }

/**
 * Dialog that drives a NIP-BC onchain zap: collect recipient / amount / fee
 * tier / comment, then run [com.vitorpamplona.amethyst.model.Account.sendOnchainZap]
 * and show progress + the result.
 *
 * When [recipientPubKey] is null the user searches for a recipient by display
 * name, NIP-05, or pastes an npub directly; the zap targets that profile. When
 * provided (e.g. from a note's zap menu) the recipient is fixed and
 * [zappedEvent] attributes the zap to that event.
 */
@OptIn(ExpM3::class)
@Composable
fun OnchainZapSendDialog(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    recipientPubKey: HexKey? = null,
    zappedEvent: EventHintBundle<out Event>? = null,
) {
    val scope = rememberCoroutineScope()

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }
    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    var searchInput by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var feeTier by remember { mutableStateOf(FeeTier.NORMAL) }
    var fees by remember { mutableStateOf<FeeEstimates?>(null) }

    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OnchainZapSendResult?>(null) }

    LaunchedEffect(Unit) {
        val backend = LocalCache.onchainBackend ?: return@LaunchedEffect
        fees =
            runCatching { withContext(Dispatchers.IO) { backend.feeEstimates() } }.getOrNull()
    }

    // Recipient resolution priority:
    //   1. preset recipientPubKey (note zap menu)
    //   2. user picked from the suggestion dropdown
    //   3. raw npub / hex pasted into the search field that parses cleanly
    val resolvedRecipient: HexKey? =
        recipientPubKey
            ?: selectedUser?.pubkeyHex
            ?: searchInput.trim().takeIf { it.isNotEmpty() }?.let { decodePublicKeyAsHexOrNull(it) }

    val amountSats = amountInput.trim().toLongOrNull()
    val canSend =
        !sending &&
            result == null &&
            resolvedRecipient != null &&
            amountSats != null &&
            amountSats > 0 &&
            fees != null

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Onchain zap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val r = result) {
                    is OnchainZapSendResult.Success -> SuccessBody(r)
                    is OnchainZapSendResult.Failure -> FailureBody(r)
                    null -> {
                        if (sending) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Text("  Sending onchain zap…")
                            }
                        } else {
                            SendForm(
                                accountViewModel = accountViewModel,
                                recipientPubKey = recipientPubKey,
                                userSuggestions = userSuggestions,
                                selectedUser = selectedUser,
                                onSelectUser = {
                                    selectedUser = it
                                    searchInput = ""
                                    userSuggestions.reset()
                                },
                                onClearUser = {
                                    selectedUser = null
                                    searchInput = ""
                                    userSuggestions.reset()
                                },
                                searchInput = searchInput,
                                onSearchChange = { newValue ->
                                    searchInput = newValue
                                    if (newValue.length > 2) {
                                        userSuggestions.processCurrentWord(newValue)
                                    } else {
                                        userSuggestions.reset()
                                    }
                                },
                                amountInput = amountInput,
                                onAmountChange = { amountInput = it },
                                comment = comment,
                                onCommentChange = { comment = it },
                                feeTier = feeTier,
                                onFeeTierChange = { feeTier = it },
                                fees = fees,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result != null) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                TextButton(
                    enabled = canSend,
                    onClick = {
                        val recipient = resolvedRecipient ?: return@TextButton
                        val amount = amountSats ?: return@TextButton
                        val feeRate = fees?.rateFor(feeTier) ?: return@TextButton
                        sending = true
                        scope.launch {
                            val r =
                                accountViewModel.account.sendOnchainZap(
                                    recipientPubKey = recipient,
                                    amountSats = amount,
                                    feeRateSatPerVByte = feeRate,
                                    comment = comment.trim(),
                                    zappedEvent = zappedEvent,
                                )
                            sending = false
                            result = r
                        }
                    },
                ) {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            if (result == null) {
                TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExpM3::class)
@Composable
private fun SendForm(
    accountViewModel: AccountViewModel,
    recipientPubKey: HexKey?,
    userSuggestions: UserSuggestionState,
    selectedUser: User?,
    onSelectUser: (User) -> Unit,
    onClearUser: () -> Unit,
    searchInput: String,
    onSearchChange: (String) -> Unit,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    feeTier: FeeTier,
    onFeeTierChange: (FeeTier) -> Unit,
    fees: FeeEstimates?,
) {
    RecipientPicker(
        accountViewModel = accountViewModel,
        recipientPubKey = recipientPubKey,
        userSuggestions = userSuggestions,
        selectedUser = selectedUser,
        onSelectUser = onSelectUser,
        onClearUser = onClearUser,
        searchInput = searchInput,
        onSearchChange = onSearchChange,
    )

    OutlinedTextField(
        value = amountInput,
        onValueChange = { onAmountChange(it.filter(Char::isDigit)) },
        label = { Text("Amount (sats)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = comment,
        onValueChange = onCommentChange,
        label = { Text("Comment (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = "Fee priority",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FeeTier.entries.forEach { tier ->
            val rate = fees?.rateFor(tier)
            FilterChip(
                selected = feeTier == tier,
                onClick = { onFeeTierChange(tier) },
                label = {
                    Column {
                        Text(tier.label)
                        if (rate != null) {
                            Text(
                                text = "${formatRate(rate)} sat/vB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
    if (fees == null) {
        Text(
            text = "Loading fee estimates…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecipientPicker(
    accountViewModel: AccountViewModel,
    recipientPubKey: HexKey?,
    userSuggestions: UserSuggestionState,
    selectedUser: User?,
    onSelectUser: (User) -> Unit,
    onClearUser: () -> Unit,
    searchInput: String,
    onSearchChange: (String) -> Unit,
) {
    if (recipientPubKey != null) {
        Text(
            text = "Zapping the post author",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (selectedUser != null) {
        SelectedRecipientChip(selectedUser, accountViewModel, onClearUser)
        return
    }

    OutlinedTextField(
        value = searchInput,
        onValueChange = onSearchChange,
        label = { Text("Recipient") },
        placeholder = { Text("name, NIP-05 or npub") },
        singleLine = true,
        isError =
            searchInput.isNotBlank() &&
                searchInput.length > 50 &&
                decodePublicKeyAsHexOrNull(searchInput.trim()) == null,
        modifier = Modifier.fillMaxWidth(),
    )

    if (searchInput.length > 2) {
        ShowUserSuggestionList(
            userSuggestions = userSuggestions,
            onSelect = onSelectUser,
            accountViewModel = accountViewModel,
            modifier = Modifier.heightIn(0.dp, 200.dp),
        )
    }
}

@Composable
private fun SelectedRecipientChip(
    user: User,
    accountViewModel: AccountViewModel,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserPicture(
                userHex = user.pubkeyHex,
                size = 32.dp,
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = user.toBestDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.pubkeyDisplayHex(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClear) {
                SymbolIcon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = "Change recipient",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuccessBody(result: OnchainZapSendResult.Success) {
    Text("Onchain zap sent.", style = MaterialTheme.typography.bodyLarge)
    Text(
        text = "Transaction: ${result.txid}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text =
            "Fee: ${result.feeSats} sats" +
                if (result.changeSats > 0) " · change: ${result.changeSats} sats" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FailureBody(result: OnchainZapSendResult.Failure) {
    Text(
        text = result.message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
    )
    result.broadcastTxid?.let {
        Text(
            text = "The payment was broadcast (tx $it) but the receipt was not published.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "Failed at: ${result.stage.name.lowercase().replace('_', ' ')}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatRate(rate: Double): String = if (rate == rate.toLong().toDouble()) rate.toLong().toString() else ((rate * 10).toLong() / 10.0).toString()
