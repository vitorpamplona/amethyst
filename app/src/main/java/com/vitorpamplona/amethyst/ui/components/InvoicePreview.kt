/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.service.lnurl.CachedLnInvoiceParser
import com.vitorpamplona.amethyst.service.lnurl.InvoiceAmount
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LoadValueFromInvoice(
    lnbcWord: String,
    inner: @Composable (invoiceAmount: InvoiceAmount?) -> Unit,
) {
    val lnInvoice by
        produceState(initialValue = CachedLnInvoiceParser.cached(lnbcWord), key1 = lnbcWord) {
            withContext(Dispatchers.IO) {
                val newLnInvoice = CachedLnInvoiceParser.parse(lnbcWord)
                if (value != newLnInvoice) {
                    value = newLnInvoice
                }
            }
        }

    inner(lnInvoice)
}

@Composable
fun MayBeInvoicePreview(lnbcWord: String) {
    LoadValueFromInvoice(lnbcWord = lnbcWord) { invoiceAmount ->
        Crossfade(targetState = invoiceAmount, label = "MayBeInvoicePreview") {
            if (it != null) {
                InvoicePreview(it.invoice, it.amount)
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
) {
    val context = LocalContext.current

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = context.getString(R.string.error_dialog_pay_invoice_error),
            textContent = showErrorMessageDialog ?: "",
            onDismiss = { showErrorMessageDialog = null },
        )
    }

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
                    null,
                    modifier = Size20Modifier,
                    tint = Color.Unspecified,
                )

                Text(
                    text = stringResource(R.string.lightning_invoice),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            amount?.let {
                Text(
                    text = "$it ${stringResource(id = R.string.sats)}",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.W500,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                )
            }

            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                onClick = { payViaIntent(lnInvoice, context) { showErrorMessageDialog = it } },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(text = stringResource(R.string.pay), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}
