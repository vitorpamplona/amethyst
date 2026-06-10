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
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.clink.pointers.OfferPriceType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.launch

/**
 * Inline card for a CLINK Offers pointer (`noffer1…`) found in a note. Tapping "Pay"
 * runs the offer round-trip ([ClinkOfferPayer]) to fetch a fresh BOLT-11 over Nostr,
 * then pays it through the user's default payment source (confirmed for in-app wallets,
 * see [InvoicePaymentDispatcher]).
 *
 * When [authorPubKey] is known (the offer appears in someone's note) and the user's
 * default zap type isn't NONZAP, the request carries a NIP-57 zap request so the offer
 * service issues a zappable invoice and publishes a zap receipt — making the payment a
 * real zap on the author rather than a silent invoice. With no author it falls back to
 * a plain invoice.
 */
@Composable
fun ClinkOfferPreview(
    offer: NOffer,
    accountViewModel: AccountViewModel,
    authorPubKey: String? = null,
    zapEvent: Event? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var requesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var payingInvoice by remember { mutableStateOf<String?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var needsAmount by remember { mutableStateOf((offer.priceType ?: OfferPriceType.SPONTANEOUS) == OfferPriceType.SPONTANEOUS) }
    var amountRange by remember { mutableStateOf<SatRange?>(null) }
    // The pointer actually paid: starts as the rendered offer, swapped if the service
    // replies "Expired or Moved" (code 3) with a replacement noffer.
    var activeOffer by remember(offer) { mutableStateOf(offer) }

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

            suspend fun runOfferRequest(
                useOffer: NOffer,
                followMoved: Boolean,
            ) {
                val amount = if (amountRequired) amountInput.toLongOrNull() else useOffer.price?.toLong()

                // Attach a NIP-57 zap request so paying the offer becomes a real zap. With the
                // note's event we zap the post (e-tag); otherwise we fall back to an author
                // (profile) zap. Skipped when there's no target or the user opted out (NONZAP).
                val zapType = accountViewModel.defaultZapType()
                val zapRequest =
                    when {
                        zapType == LnZapEvent.ZapType.NONZAP -> null
                        zapEvent != null ->
                            accountViewModel.account
                                .createZapRequestFor(
                                    event = zapEvent,
                                    pollOption = null,
                                    zapType = zapType,
                                    toUser = null,
                                    amountMillisats = amount?.times(1000),
                                ).toJson()
                        authorPubKey != null ->
                            accountViewModel.account
                                .createZapRequestFor(
                                    user = accountViewModel.account.cache.getOrCreateUser(authorPubKey),
                                    zapType = zapType,
                                    amountMillisats = amount?.times(1000),
                                ).toJson()
                        else -> null
                    }

                val response = ClinkOfferPayer.requestInvoice(accountViewModel.account, useOffer, amountSats = amount, zap = zapRequest)

                val bolt11 = response?.bolt11
                val movedTo =
                    if (response?.code == OfferErrorCode.EXPIRED_OR_MOVED && followMoved) {
                        response.latest?.let { ClinkPointerParser.parse(it) as? NOffer }
                    } else {
                        null
                    }

                when {
                    bolt11 != null -> {
                        requesting = false
                        payingInvoice = bolt11
                    }
                    // Follow a relocated offer once, paying the replacement pointer.
                    movedTo != null -> {
                        activeOffer = movedTo
                        runOfferRequest(movedTo, followMoved = false)
                    }
                    response?.code == OfferErrorCode.INVALID_AMOUNT -> {
                        // Reveal the amount field (or refine it) with the service's range.
                        requesting = false
                        needsAmount = true
                        amountRange = response.range
                        errorMessage =
                            response.error?.takeIf { it.isNotBlank() }
                                ?: stringRes(context, R.string.clink_offer_invalid_amount)
                    }
                    else -> {
                        requesting = false
                        errorMessage =
                            response?.error?.takeIf { it.isNotBlank() }
                                ?: stringRes(context, R.string.error_dialog_pay_invoice_error)
                    }
                }
            }

            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                enabled = !requesting && (!amountRequired || (amountInput.toLongOrNull() ?: 0L) > 0L),
                onClick = {
                    requesting = true
                    scope.launch { runOfferRequest(activeOffer, followMoved = true) }
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
