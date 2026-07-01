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
package com.vitorpamplona.amethyst.ui.note.creators.invoice

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.service.lnurl.CachedLnInvoiceParser
import com.vitorpamplona.amethyst.service.lnurl.InvoiceAmount
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.PaymentCard
import com.vitorpamplona.amethyst.ui.components.PaymentCardAmount
import com.vitorpamplona.amethyst.ui.components.PaymentCardDescription
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LoadValueFromInvoice(
    lnbcWord: String,
    inner: @Composable (invoiceAmount: InvoiceAmount?) -> Unit,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val lnInvoice by
        produceState(initialValue = CachedLnInvoiceParser.cached(lnbcWord), key1 = lnbcWord) {
            val newLnInvoice = withContext(Dispatchers.IO) { CachedLnInvoiceParser.parse(lnbcWord) }
            if (value != newLnInvoice) {
                value = newLnInvoice
            }
        }

    inner(lnInvoice)
}

@Composable
fun MayBeInvoicePreview(
    lnbcWord: String,
    accountViewModel: AccountViewModel,
) {
    LoadValueFromInvoice(lnbcWord = lnbcWord) { invoiceAmount ->
        CrossfadeIfEnabled(targetState = invoiceAmount, label = "MayBeInvoicePreview", accountViewModel = accountViewModel) {
            if (it != null) {
                InvoicePreview(it.invoice, it.amount, it.description, it.expiresAt, accountViewModel)
            } else {
                Text(
                    text = lnbcWord,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                )
            }
        }
    }
}

@Composable
fun InvoicePreview(
    lnInvoice: String,
    amount: String?,
    description: String?,
    expiresAt: Long?,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }
    var payingInvoice by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = stringRes(context, R.string.error_dialog_pay_invoice_error),
            textContent = showErrorMessageDialog ?: "",
            onDismiss = { showErrorMessageDialog = null },
        )
    }

    InvoicePaymentDispatcher(
        bolt11 = payingInvoice,
        accountViewModel = accountViewModel,
        onClear = { payingInvoice = null },
        onError = { showErrorMessageDialog = it },
    )

    // Snapshot at composition: enough to flag clearly stale invoices without
    // ticking a clock while the card is on screen.
    val isExpired = remember(expiresAt) { expiresAt != null && expiresAt < TimeUtils.now() }

    PaymentCard(
        title = stringRes(R.string.lightning_invoice),
        icon = {
            Icon(
                imageVector = CustomHashTagIcons.Lightning,
                contentDescription = null,
                modifier = Size18Modifier,
                tint = Color.Unspecified,
            )
        },
        copyValue = lnInvoice,
    ) {
        amount?.let {
            PaymentCardAmount(amount = it, unit = stringRes(R.string.sats))
        }

        description?.let {
            PaymentCardDescription(it)
        }

        if (isExpired) {
            Text(
                text = stringRes(R.string.invoice_expired),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
            )
        }

        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            enabled = !isExpired,
            onClick = { payingInvoice = lnInvoice },
            shape = ButtonBorder,
        ) {
            Text(text = stringRes(R.string.pay))
        }
    }
}
