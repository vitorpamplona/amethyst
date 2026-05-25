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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.TextButton
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
import com.vitorpamplona.amethyst.commons.onchain.DustRecipientException
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendStage
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapShare
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSplitter
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.namecoin.NamecoinResolutionRow
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
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

// UX floor for onchain zaps. Below this any on-chain transaction is dominated
// by miner fees — the recipient nets close to nothing even at low fee rates,
// so quietly funneling the user to a Lightning zap is the friendlier outcome.
// This is stricter than the protocol-level [OnchainZapBuilder.DUST_THRESHOLD_SATS]
// (330 sats), which only guards against creating outputs the network rejects.
private const val MIN_ONCHAIN_ZAP_SATS = 1_000L

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
    prefillAmountSats: Long? = null,
    prefillComment: String = "",
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
    var amountInput by remember { mutableStateOf(prefillAmountSats?.toString().orEmpty()) }
    var comment by remember { mutableStateOf(prefillComment) }
    var feeTier by remember { mutableStateOf(FeeTier.NORMAL) }
    var fees by remember { mutableStateOf<FeeEstimates?>(null) }

    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OnchainZapSendResult?>(null) }

    // Pull pubkey-based zap splits off the zapped event. Lightning-address-only
    // splits are dropped because we can't derive a Taproot output from an
    // lnAddress — they appear in [skippedLnSplits] so the UI can warn the user
    // that those recipients won't be paid on-chain. The sender's own pubkey is
    // also dropped (zapping your own post is common; the on-chain builder
    // refuses self-pays) and duplicate pubkeys are merged.
    val senderPubKey = accountViewModel.account.signer.pubKey
    val zappedEventId = zappedEvent?.event?.id
    val rawSplits =
        remember(zappedEventId) {
            zappedEvent?.event?.zapSplitSetup().orEmpty()
        }
    val onchainSplits =
        remember(zappedEventId, senderPubKey) {
            val raw = rawSplits.filterIsInstance<ZapSplitSetup>().map { it.pubKeyHex to it.weight }
            OnchainZapSplitter.prepare(raw, senderPubKey)
        }
    val skippedLnSplits =
        remember(zappedEventId) {
            rawSplits.filterIsInstance<ZapSplitSetupLnAddress>()
        }
    var useSplits by remember(zappedEventId) { mutableStateOf(onchainSplits.isNotEmpty()) }
    val splitMode = useSplits && onchainSplits.isNotEmpty()

    // Fetch fee estimates with bounded retry. Covers two boot races:
    //  - LocalCache.onchainBackend is null briefly while AppModules wires it up
    //  - feeEstimates() throws on a flaky network
    // Without retry, the Send button would stay permanently disabled because
    // the build path needs a fee rate.
    LaunchedEffect(Unit) {
        repeat(4) { attempt ->
            if (fees != null) return@LaunchedEffect
            val backend = LocalCache.onchainBackend
            if (backend != null) {
                val newFees = runCatching { withContext(Dispatchers.IO) { backend.feeEstimates() } }.getOrNull()
                if (newFees != null) {
                    fees = newFees
                    return@LaunchedEffect
                }
            }
            if (attempt < 3) delay(1_000L * (attempt + 1))
        }
    }

    val presetAmounts by accountViewModel.account.settings.syncedSettings.zaps.onchainZapAmountChoices
        .collectAsStateWithLifecycle()

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

    // Preview the per-recipient share allocation. Always compute the full
    // list (even when some shares would land below dust) so the UI can show
    // every recipient's amount; below-dust offenders are flagged separately
    // and gate the Send button so the user can't tap into a guaranteed
    // BUILDING-stage failure.
    val previewShares =
        remember(splitMode, onchainSplits, amountSats) {
            if (!splitMode || amountSats == null || amountSats <= 0) {
                null
            } else {
                runCatching {
                    OnchainZapSplitter.distributeUnchecked(amountSats, onchainSplits)
                }.getOrNull()
            }
        }
    val belowDustShares =
        remember(previewShares) {
            previewShares.orEmpty().filter { it.sats < OnchainZapBuilder.DUST_THRESHOLD_SATS }
        }

    val belowMinimum = amountSats != null && amountSats > 0 && amountSats < MIN_ONCHAIN_ZAP_SATS

    val canSend =
        !sending &&
            result == null &&
            (splitMode || (resolvedRecipient != null && resolvedRecipient != senderPubKey)) &&
            amountSats != null &&
            amountSats >= MIN_ONCHAIN_ZAP_SATS &&
            fees != null &&
            (!splitMode || (previewShares != null && belowDustShares.isEmpty()))

    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .imePadding()
                    .navigationBarsPadding(),
        ) {
            Header(onClose = { if (!sending) onDismiss() })

            when (val r = result) {
                is OnchainZapSendResult.Success -> {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
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
                                .weight(1f, fill = false)
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
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp),
                        ) {
                            if (splitMode) {
                                SplitsRecipientSection(
                                    splits = onchainSplits,
                                    previewShares = previewShares,
                                    skippedLnSplits = skippedLnSplits,
                                    onDisable = { useSplits = false },
                                    accountViewModel = accountViewModel,
                                )
                            } else {
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
                                if (onchainSplits.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(
                                        onClick = { useSplits = true },
                                    ) {
                                        Text("Use this note's ${onchainSplits.size}-way zap split")
                                    }
                                }
                            }

                            SectionSpacer()

                            AmountSection(
                                amountInput = amountInput,
                                onAmountChange = { amountInput = it },
                                presetAmounts = presetAmounts,
                                belowMinimum = belowMinimum,
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
                            splitWays = if (splitMode) onchainSplits.size else 0,
                            onClick = {
                                val amount = amountSats ?: return@SendButton
                                val feeRate = fees?.rateFor(feeTier) ?: return@SendButton
                                sending = true
                                scope.launch {
                                    val r =
                                        if (splitMode) {
                                            val shares =
                                                try {
                                                    OnchainZapSplitter.distribute(
                                                        totalSats = amount,
                                                        splits = onchainSplits,
                                                        dustThresholdSats = OnchainZapBuilder.DUST_THRESHOLD_SATS,
                                                    )
                                                } catch (e: DustRecipientException) {
                                                    sending = false
                                                    result =
                                                        OnchainZapSendResult.Failure(
                                                            stage = OnchainZapSendStage.BUILDING,
                                                            message = e.message ?: "A recipient share is below dust",
                                                        )
                                                    return@launch
                                                }
                                            accountViewModel.account.sendOnchainZapWithSplits(
                                                recipients = shares,
                                                feeRateSatPerVByte = feeRate,
                                                comment = comment.trim(),
                                                zappedEvent = zappedEvent,
                                            )
                                        } else {
                                            val recipient = resolvedRecipient ?: return@launch
                                            accountViewModel.account.sendOnchainZap(
                                                recipientPubKey = recipient,
                                                amountSats = amount,
                                                feeRateSatPerVByte = feeRate,
                                                comment = comment.trim(),
                                                zappedEvent = zappedEvent,
                                            )
                                        }
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
    belowMinimum: Boolean,
) {
    SectionLabel("Amount")

    if (presetAmounts.isNotEmpty()) {
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
        Spacer(Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = amountInput,
        onValueChange = { onAmountChange(it.filter(Char::isDigit)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text("0") },
        suffix = { Text("sats", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        isError = belowMinimum,
        modifier = Modifier.fillMaxWidth(),
    )

    if (belowMinimum) {
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                "Minimum on-chain zap is ${NumberFormat.getNumberInstance().format(MIN_ONCHAIN_ZAP_SATS)} sats — " +
                    "smaller amounts are eaten by miner fees. Use a Lightning zap instead.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
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
    splitWays: Int,
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
        val sats = if (amountSats != null && amountSats > 0) NumberFormat.getNumberInstance().format(amountSats) else null
        Text(
            text =
                when {
                    sats != null && splitWays > 1 -> "Send $sats sats, $splitWays ways"
                    sats != null -> "Send $sats sats"
                    else -> "Send"
                },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SplitsRecipientSection(
    splits: List<Pair<HexKey, Double>>,
    previewShares: List<OnchainZapShare>?,
    skippedLnSplits: List<ZapSplitSetupLnAddress>,
    onDisable: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    SectionLabel("Splits among ${splits.size} recipients")

    val totalWeight = splits.sumOf { it.second }
    // Index the preview by pubkey once — the splits list scan would otherwise
    // be O(N²) for the per-row sat amount lookup.
    val previewByPubKey =
        remember(previewShares) {
            previewShares?.associateBy { it.recipientPubKey }
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            splits.forEach { (pubKey, weight) ->
                val share = previewByPubKey?.get(pubKey)
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserPicture(
                        userHex = pubKey,
                        size = 28.dp,
                        accountViewModel = accountViewModel,
                        nav = EmptyNav(),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = formatWeight(weight, totalWeight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (share != null) {
                        val belowDust = share.sats < OnchainZapBuilder.DUST_THRESHOLD_SATS
                        Text(
                            text = "${NumberFormat.getNumberInstance().format(share.sats)} sats",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (belowDust) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (skippedLnSplits.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        val word = if (skippedLnSplits.size == 1) "recipient" else "recipients"
        Text(
            text =
                "Skipping ${skippedLnSplits.size} Lightning-address-only $word — " +
                    "on-chain needs a Nostr pubkey to derive the address.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onDisable) {
        Text("Don't split — pay one recipient instead")
    }
}

private fun formatWeight(
    weight: Double,
    totalWeight: Double,
): String {
    val pct = (weight / totalWeight) * 100.0
    return if (pct >= 99.95) {
        "100%"
    } else {
        // Round to a tenth of a percent. Drop the trailing ".0" so whole
        // percentages render as "50%" instead of "50.0%".
        val tenths = (pct * 10).toLong()
        if (tenths % 10 == 0L) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
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
