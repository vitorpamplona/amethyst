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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.service.ClinkOfferPayer
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.experimental.clink.offers.OfferErrorCode
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.clink.pointers.OfferPriceType
import kotlinx.coroutines.launch

/**
 * Inline card for a CLINK Offers pointer (`noffer1…`) found in a note. Tapping "Pay"
 * runs the offer round-trip ([ClinkOfferPayer]) to fetch a fresh BOLT-11 over Nostr,
 * then pays it through the user's default payment source (confirmed for in-app wallets,
 * see [InvoicePaymentDispatcher]).
 */
@Composable
fun ClinkOfferPreview(
    offer: NOffer,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var requesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var payingInvoice by remember { mutableStateOf<String?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var needsAmount by remember { mutableStateOf((offer.priceType ?: OfferPriceType.SPONTANEOUS) == OfferPriceType.SPONTANEOUS) }
    var amountRange by remember { mutableStateOf<SatRange?>(null) }

    errorMessage?.let {
        ErrorMessageDialog(
            title = stringRes(context, R.string.error_dialog_pay_invoice_error),
            textContent = it,
            onDismiss = { errorMessage = null },
        )
    }

    InvoicePaymentDispatcher(
        bolt11 = payingInvoice,
        accountViewModel = accountViewModel,
        onClear = { payingInvoice = null },
        onError = { errorMessage = it },
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
                .clip(shape = QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
            ) {
                Icon(
                    imageVector = CustomHashTagIcons.Lightning,
                    contentDescription = null,
                    modifier = Size20Modifier,
                    tint = Color.Unspecified,
                )

                Text(
                    text = stringRes(R.string.clink_lightning_offer),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            // FIXED offers display their preset price; SPONTANEOUS offers (and the default
            // when the pointer omits a price type) require the payer to enter an amount.
            val effectiveType = offer.priceType ?: OfferPriceType.SPONTANEOUS

            if (effectiveType == OfferPriceType.FIXED) {
                offer.price?.let {
                    Text(
                        text = "$it ${stringRes(id = R.string.sats)}",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.W500,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                    )
                }
            }

            if (needsAmount) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { new -> amountInput = new.filter(Char::isDigit) },
                    label = { Text(stringRes(R.string.clink_offer_amount_sats)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText =
                        amountRange?.let { range ->
                            val min = range.min
                            val max = range.max
                            if (min != null && max != null) {
                                { Text(stringRes(R.string.clink_offer_amount_range, min.toString(), max.toString())) }
                            } else {
                                null
                            }
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                )
            }

            val amountRequired = needsAmount
            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                enabled = !requesting && (!amountRequired || (amountInput.toLongOrNull() ?: 0L) > 0L),
                onClick = {
                    requesting = true
                    scope.launch {
                        val amount = if (amountRequired) amountInput.toLongOrNull() else null
                        val response = ClinkOfferPayer.requestInvoice(accountViewModel.account, offer, amountSats = amount)
                        requesting = false

                        val bolt11 = response?.bolt11
                        when {
                            bolt11 != null -> payingInvoice = bolt11
                            response?.code == OfferErrorCode.INVALID_AMOUNT -> {
                                // Reveal the amount field (or refine it) with the service's range.
                                needsAmount = true
                                amountRange = response.range
                                errorMessage =
                                    response.error?.takeIf { it.isNotBlank() }
                                        ?: stringRes(context, R.string.clink_offer_invalid_amount)
                            }
                            response?.error != null -> errorMessage = response.error
                            else -> errorMessage = stringRes(context, R.string.error_dialog_pay_invoice_error)
                        }
                    }
                },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(
                    text = stringRes(if (requesting) R.string.clink_requesting_invoice else R.string.pay),
                    color = Color.White,
                    fontSize = 20.sp,
                )
            }
        }
    }
}
