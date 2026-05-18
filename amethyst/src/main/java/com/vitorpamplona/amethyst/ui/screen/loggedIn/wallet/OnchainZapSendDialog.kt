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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

private enum class FeeTier(
    val label: String,
    val etaLabel: String,
) {
    SLOW("Slow", "~1 hr"),
    NORMAL("Normal", "~30 min"),
    FAST("Fast", "~10 min"),
}

private fun FeeEstimates.rateFor(tier: FeeTier): Double =
    when (tier) {
        FeeTier.SLOW -> slowSatPerVbyte
        FeeTier.NORMAL -> normalSatPerVbyte
        FeeTier.FAST -> fastSatPerVbyte
    }

/**
 * Modal bottom sheet that drives a NIP-BC onchain zap.
 *
 * Layout (top to bottom):
 *   - Title row with close button
 *   - Recipient picker — search field with inline dropdown, or selected-user chip
 *   - Amount section — quick-pick chips (reuses [AccountViewModel.zapAmountChoices])
 *     and a big sats text field
 *   - Optional comment
 *   - Fee priority — three chips with rate + ETA, in a FlowRow that wraps
 *   - Sticky bottom send button
 *
 * When [recipientPubKey] is null the user picks a recipient. When provided
 * (e.g. from a note's zap menu) the recipient is fixed and [zappedEvent]
 * attributes the zap to that event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnchainZapSendDialog(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    recipientPubKey: HexKey? = null,
    zappedEvent: EventHintBundle<out Event>? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    val presetAmounts =
        remember(accountViewModel) {
            accountViewModel.zapAmountChoices()
        }

    // Mirror the dropdown's NIP-05 / Namecoin (.bit) resolution so Send can
    // enable as soon as the typed name resolves, without forcing the user to
    // tap the suggestion. Reuses the exact same path as the dropdown, so
    // bare .bit names (e.g. testls.bit) and m@testls.bit both work.
    val nip05Resolved by userSuggestions.nip05ResolutionFlow.collectAsStateWithLifecycle(initialValue = null)

    val resolvedRecipient: HexKey? =
        recipientPubKey
            ?: selectedUser?.pubkeyHex
            ?: searchInput.trim().takeIf { it.isNotEmpty() }?.let { decodePublicKeyAsHexOrNull(it) }
            ?: nip05Resolved?.pubkeyHex
    val amountSats = amountInput.trim().toLongOrNull()
    val canSend =
        !sending &&
            result == null &&
            resolvedRecipient != null &&
            amountSats != null &&
            amountSats > 0 &&
            fees != null

    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
        ) {
            Header(onClose = { if (!sending) onDismiss() })

            when (val r = result) {
                is OnchainZapSendResult.Success -> {
                    Column(
                        modifier =
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        SuccessBody(r)
                    }
                    DoneButton(label = "Done", onClick = onDismiss)
                }

                is OnchainZapSendResult.Failure -> {
                    Column(
                        modifier =
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        FailureBody(r)
                    }
                    DoneButton(label = "Close", onClick = onDismiss)
                }

                null -> {
                    if (sending) {
                        SendingState()
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp),
                        ) {
                            RecipientSection(
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
                            )

                            SectionSpacer()

                            AmountSection(
                                amountInput = amountInput,
                                onAmountChange = { amountInput = it },
                                presetAmounts = presetAmounts,
                            )

                            SectionSpacer()

                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text("Comment (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            SectionSpacer()

                            FeeSection(
                                feeTier = feeTier,
                                onFeeTierChange = { feeTier = it },
                                fees = fees,
                            )

                            Spacer(Modifier.height(20.dp))
                        }

                        SendButton(
                            enabled = canSend,
                            amountSats = amountSats,
                            onClick = {
                                val recipient = resolvedRecipient ?: return@SendButton
                                val amount = amountSats ?: return@SendButton
                                val feeRate = fees?.rateFor(feeTier) ?: return@SendButton
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.CurrencyBitcoin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.bitcoinColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "Send onchain zap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        )
        IconButton(onClick = onClose) {
            Icon(
                symbol = MaterialSymbols.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipientSection(
    accountViewModel: AccountViewModel,
    recipientPubKey: HexKey?,
    userSuggestions: UserSuggestionState,
    selectedUser: User?,
    onSelectUser: (User) -> Unit,
    onClearUser: () -> Unit,
    searchInput: String,
    onSearchChange: (String) -> Unit,
) {
    SectionLabel("To")

    if (recipientPubKey != null) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserPicture(
                    userHex = recipientPubKey,
                    size = 32.dp,
                    accountViewModel = accountViewModel,
                    nav = EmptyNav(),
                )
                Text(
                    text = "Post author",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        return
    }

    if (selectedUser != null) {
        SelectedRecipientChip(selectedUser, accountViewModel, onClearUser)
        return
    }

    // Tight column: no extra parent spacing between the field and its
    // suggestion dropdown — the dropdown sits flush under the field.
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

    // Inline Namecoin lookup feedback. Local-cache suggestions can race
    // ahead of the on-chain resolution (especially when the user has
    // resolved a sibling `user@<host>.bit` earlier in the session and the
    // current query is the bare host), so we surface the in-flight state +
    // the eventual on-chain match in its own row, distinct from the
    // generic dropdown. Failures are surfaced here too.
    NamecoinResolutionRow(
        searchInput = searchInput,
        accountViewModel = accountViewModel,
        onUserResolved = onSelectUser,
    )

    if (searchInput.length > 2) {
        ShowUserSuggestionList(
            userSuggestions = userSuggestions,
            onSelect = onSelectUser,
            accountViewModel = accountViewModel,
            modifier = Modifier.heightIn(0.dp, 220.dp),
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
                size = 36.dp,
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
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
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = "Change recipient",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSection(
    amountInput: String,
    onAmountChange: (String) -> Unit,
    presetAmounts: List<Long>,
) {
    SectionLabel("Amount")

    OutlinedTextField(
        value = amountInput,
        onValueChange = { onAmountChange(it.filter(Char::isDigit)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text("0") },
        suffix = { Text("sats", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (presetAmounts.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presetAmounts.forEach { amount ->
                SuggestionChip(
                    onClick = { onAmountChange(amount.toString()) },
                    label = { Text("⚡ ${showAmount(BigDecimal(amount))}") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeSection(
    feeTier: FeeTier,
    onFeeTierChange: (FeeTier) -> Unit,
    fees: FeeEstimates?,
) {
    SectionLabel("Priority")

    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeeTier.entries.forEach { tier ->
            val rate = fees?.rateFor(tier)
            FilterChip(
                selected = feeTier == tier,
                onClick = { onFeeTierChange(tier) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.bitcoinColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                label = {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = tier.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (rate != null) "${formatRate(rate)} sat/vB · ${tier.etaLabel}" else tier.etaLabel,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        }
    }

    if (fees == null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Loading fee estimates…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SendButton(
    enabled: Boolean,
    amountSats: Long?,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.CurrencyBitcoin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text =
                if (amountSats != null && amountSats > 0) {
                    "Send ${NumberFormat.getNumberInstance().format(amountSats)} sats"
                } else {
                    "Send"
                },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DoneButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SendingState() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.bitcoinColor,
        )
        Text(
            text = "Building, signing and broadcasting…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessBody(result: OnchainZapSendResult.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.bitcoinColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Onchain zap sent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ResultRow("Transaction", result.txid)
        ResultRow("Fee", "${NumberFormat.getNumberInstance().format(result.feeSats)} sats")
        if (result.changeSats > 0) {
            ResultRow("Change", "${NumberFormat.getNumberInstance().format(result.changeSats)} sats")
        }
    }
}

@Composable
private fun FailureBody(result: OnchainZapSendResult.Failure) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        result.broadcastTxid?.let {
            Text(
                text = "Payment was broadcast (tx $it) but the receipt was not published.",
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
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatRate(rate: Double): String = if (rate == rate.toLong().toDouble()) rate.toLong().toString() else ((rate * 10).toLong() / 10.0).toString()
