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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The payment rails the profile Send Payment screen can drive. [routeKey] is
 * the stable string used in [com.vitorpamplona.amethyst.ui.navigation.routes.Route.SendPayment]
 * to preselect a rail when navigating from a specific profile chip.
 */
enum class ProfilePaymentMethod(
    val routeKey: String,
) {
    LIGHTNING("lightning"),
    CLINK("clink"),
    ONCHAIN("onchain"),
    CASHU("cashu"),
    ;

    companion object {
        fun fromRouteKey(key: String?): ProfilePaymentMethod? = entries.firstOrNull { it.routeKey == key }
    }
}

/** A selectable rail plus the short detail line shown under the selector. */
@Immutable
data class PaymentMethodUi(
    val method: ProfilePaymentMethod,
    val detail: String? = null,
)

/** One entry of the zap-type selector (Public / Private / Anonymous / Non-Zap). */
@Immutable
data class ZapTypeOption(
    val type: LnZapEvent.ZapType,
    val label: String,
    val explainer: String,
)

/**
 * In-screen payment lifecycle. The screen never closes itself on send: it walks
 * Editing → InProgress (invoice request, then payment) → Success/Failure, and
 * only the user's Done tap leaves the screen.
 */
@Immutable
sealed interface PaymentFlowStage {
    object Editing : PaymentFlowStage

    data class InProgress(
        val label: String,
        val progress: Float? = null,
    ) : PaymentFlowStage

    data class Success(
        val title: String,
        val detail: String? = null,
    ) : PaymentFlowStage

    data class Failure(
        val message: String,
    ) : PaymentFlowStage
}

private fun ProfilePaymentMethod.symbol(): MaterialSymbol =
    when (this) {
        ProfilePaymentMethod.LIGHTNING -> MaterialSymbols.Bolt
        ProfilePaymentMethod.CLINK -> MaterialSymbols.Paid
        ProfilePaymentMethod.ONCHAIN -> MaterialSymbols.CurrencyBitcoin
        ProfilePaymentMethod.CASHU -> MaterialSymbols.AccountBalanceWallet
    }

@Composable
private fun ProfilePaymentMethod.label(): String =
    when (this) {
        ProfilePaymentMethod.LIGHTNING -> stringRes(R.string.send_payment_method_lightning)
        ProfilePaymentMethod.CLINK -> stringRes(R.string.send_payment_method_clink)
        ProfilePaymentMethod.ONCHAIN -> stringRes(R.string.send_payment_method_onchain)
        ProfilePaymentMethod.CASHU -> stringRes(R.string.send_payment_method_cashu)
    }

/**
 * Stateless body of the profile Send Payment screen. All data comes in as plain
 * values so the layout can be previewed without an account; the live screen
 * ([SendPaymentScreen]) owns the state and the actual payment calls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendPaymentContent(
    methods: ImmutableList<PaymentMethodUi>,
    selectedMethod: ProfilePaymentMethod?,
    onSelectMethod: (ProfilePaymentMethod) -> Unit,
    presetAmounts: ImmutableList<Long>,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    amountLocked: Boolean,
    amountSupportText: String?,
    amountIsError: Boolean,
    message: String,
    onMessageChange: (String) -> Unit,
    showMessageField: Boolean,
    messageLabel: String,
    zapTypes: ImmutableList<ZapTypeOption>?,
    selectedZapType: LnZapEvent.ZapType,
    onZapTypeChange: (LnZapEvent.ZapType) -> Unit,
    receiptNote: String?,
    stage: PaymentFlowStage,
    canSend: Boolean,
    sendLabel: String,
    onSend: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageRecipient: (() -> Unit)? = null,
    extraSection: (@Composable ColumnScope.() -> Unit)? = null,
    recipientHeader: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        recipientHeader()

        if (methods.isEmpty()) {
            Text(
                text = stringRes(R.string.send_payment_no_methods),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val editing = stage is PaymentFlowStage.Editing

        MethodSelector(
            methods = methods,
            selected = selectedMethod,
            enabled = editing,
            onSelect = onSelectMethod,
        )

        AmountSection(
            presetAmounts = presetAmounts,
            amountInput = amountInput,
            onAmountChange = onAmountChange,
            locked = amountLocked,
            enabled = editing,
            supportText = amountSupportText,
            isError = amountIsError,
        )

        if (showMessageField) {
            OutlinedTextField(
                label = { Text(messageLabel) },
                value = message,
                onValueChange = onMessageChange,
                enabled = editing,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                placeholder = {
                    Text(
                        text = stringRes(R.string.custom_zaps_add_a_message_example),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ReceiptSection(
            zapTypes = zapTypes,
            selectedZapType = selectedZapType,
            onZapTypeChange = onZapTypeChange,
            receiptNote = receiptNote,
            enabled = editing,
        )

        extraSection?.invoke(this)

        when (stage) {
            is PaymentFlowStage.Editing -> {
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    shape = ButtonBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = sendLabel, fontWeight = FontWeight.Bold)
                }
            }

            is PaymentFlowStage.InProgress -> ProgressCard(stage)

            is PaymentFlowStage.Success -> {
                ResultCard(
                    symbol = MaterialSymbols.CheckCircle,
                    tint = BitcoinOrange,
                    title = stage.title,
                    detail = stage.detail,
                )
                Button(
                    onClick = onDone,
                    shape = ButtonBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(R.string.send_payment_done))
                }
            }

            is PaymentFlowStage.Failure -> {
                ResultCard(
                    symbol = MaterialSymbols.Error,
                    tint = MaterialTheme.colorScheme.error,
                    title = stringRes(R.string.send_payment_failed),
                    detail = stage.message,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDone,
                        shape = ButtonBorder,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringRes(R.string.send_payment_done))
                    }
                    Button(
                        onClick = onRetry,
                        shape = ButtonBorder,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringRes(R.string.send_payment_try_again))
                    }
                }
                if (onMessageRecipient != null) {
                    TextButton(
                        onClick = onMessageRecipient,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.send_payment_message_recipient))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodSelector(
    methods: ImmutableList<PaymentMethodUi>,
    selected: ProfilePaymentMethod?,
    enabled: Boolean,
    onSelect: (ProfilePaymentMethod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(R.string.send_payment_method))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            methods.forEach { entry ->
                FilterChip(
                    selected = selected == entry.method,
                    enabled = enabled,
                    onClick = { onSelect(entry.method) },
                    leadingIcon = {
                        Icon(
                            symbol = entry.method.symbol(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(entry.method.label()) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            }
        }

        val detail = methods.firstOrNull { it.method == selected }?.detail
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountSection(
    presetAmounts: ImmutableList<Long>,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    locked: Boolean,
    enabled: Boolean,
    supportText: String?,
    isError: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(R.string.send_payment_amount))

        if (!locked && presetAmounts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetAmounts.forEach { amount ->
                    SuggestionChip(
                        enabled = enabled,
                        onClick = { onAmountChange(amount.toString()) },
                        label = { Text("⚡ ${showAmount(amount.toBigDecimal())}") },
                    )
                }
            }
        }

        OutlinedTextField(
            label = { Text(stringRes(R.string.amount_in_sats)) },
            value = amountInput,
            onValueChange = { new -> onAmountChange(new.filter(Char::isDigit)) },
            enabled = enabled && !locked,
            isError = isError,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            suffix = { Text(stringRes(R.string.sats)) },
            supportingText =
                when {
                    supportText != null -> {
                        { Text(supportText) }
                    }
                    locked -> {
                        { Text(stringRes(R.string.send_payment_fixed_price)) }
                    }
                    else -> null
                },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptSection(
    zapTypes: ImmutableList<ZapTypeOption>?,
    selectedZapType: LnZapEvent.ZapType,
    onZapTypeChange: (LnZapEvent.ZapType) -> Unit,
    receiptNote: String?,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(R.string.send_payment_receipt_section))

        if (zapTypes != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                zapTypes.forEach { option ->
                    FilterChip(
                        selected = selectedZapType == option.type,
                        enabled = enabled,
                        onClick = { onZapTypeChange(option.type) },
                        label = { Text(option.label) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
            zapTypes.firstOrNull { it.type == selectedZapType }?.let { option ->
                Text(
                    text = option.explainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (receiptNote != null) {
            Text(
                text = receiptNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressCard(stage: PaymentFlowStage.InProgress) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val progress = stage.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            Text(
                text = stage.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ResultCard(
    symbol: MaterialSymbol,
    tint: Color,
    title: String,
    detail: String?,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// Previews — fake data only; no account or network required.
// ---------------------------------------------------------------------------

private val previewMethods =
    persistentListOf(
        PaymentMethodUi(ProfilePaymentMethod.LIGHTNING, "alice@walletofsatoshi.com"),
        PaymentMethodUi(ProfilePaymentMethod.CLINK),
        PaymentMethodUi(ProfilePaymentMethod.ONCHAIN),
        PaymentMethodUi(ProfilePaymentMethod.CASHU, "Spendable for this recipient: 4,200 sats"),
    )

private val previewZapTypes =
    listOf(
        ZapTypeOption(LnZapEvent.ZapType.PUBLIC, "Public", "Everybody can see the transaction and message"),
        ZapTypeOption(LnZapEvent.ZapType.PRIVATE, "Private", "Sender and receiver can see each other and read the message"),
        ZapTypeOption(LnZapEvent.ZapType.ANONYMOUS, "Anonymous", "Receiver does not know who sent the payment"),
        ZapTypeOption(LnZapEvent.ZapType.NONZAP, "Non-Zap", "No trace on Nostr, only in Lightning"),
    ).toImmutableList()

@Composable
private fun PreviewRecipientHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.AccountBalanceWallet,
            contentDescription = null,
            tint = BitcoinOrange,
            modifier = Modifier.size(40.dp),
        )
        Column {
            Text("Alice", fontWeight = FontWeight.Bold)
            Text(
                "alice@walletofsatoshi.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentLightningPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.LIGHTNING,
            onSelectMethod = {},
            presetAmounts = persistentListOf(100L, 500L, 1_000L, 5_000L),
            amountInput = "1000",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "Thank you for all your work!",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = "Add a public message",
            zapTypes = previewZapTypes,
            selectedZapType = LnZapEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = null,
            stage = PaymentFlowStage.Editing,
            canSend = true,
            sendLabel = "Pay 1,000 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentClinkFixedPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.CLINK,
            onSelectMethod = {},
            presetAmounts = persistentListOf(100L, 500L, 1_000L),
            amountInput = "2100",
            onAmountChange = {},
            amountLocked = true,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = false,
            messageLabel = "",
            zapTypes = null,
            selectedZapType = LnZapEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = "Direct Lightning payment — no zap receipt is published on Nostr.",
            stage = PaymentFlowStage.Editing,
            canSend = true,
            sendLabel = "Pay 2,100 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentInProgressPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.CASHU,
            onSelectMethod = {},
            presetAmounts = persistentListOf(100L, 500L),
            amountInput = "500",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = "Add a public message",
            zapTypes = null,
            selectedZapType = LnZapEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = "The nutzap event delivers the ecash itself, so it is always published.",
            stage = PaymentFlowStage.InProgress("Locking and sending ecash…", progress = 0.55f),
            canSend = false,
            sendLabel = "Pay 500 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentSuccessPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.LIGHTNING,
            onSelectMethod = {},
            presetAmounts = persistentListOf(100L, 500L),
            amountInput = "1000",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = "Add a public message",
            zapTypes = previewZapTypes,
            selectedZapType = LnZapEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = null,
            stage = PaymentFlowStage.Success("Payment sent!", "1,000 sats to Alice"),
            canSend = false,
            sendLabel = "Pay 1,000 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentFailurePreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.ONCHAIN,
            onSelectMethod = {},
            presetAmounts = persistentListOf(10_000L, 50_000L),
            amountInput = "10000",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = "Comment",
            zapTypes = null,
            selectedZapType = LnZapEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = "An on-chain zap receipt is published on Nostr so the recipient can find the payment.",
            stage = PaymentFlowStage.Failure("Insufficient funds to cover the amount plus the network fee."),
            canSend = false,
            sendLabel = "Pay 10,000 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}
