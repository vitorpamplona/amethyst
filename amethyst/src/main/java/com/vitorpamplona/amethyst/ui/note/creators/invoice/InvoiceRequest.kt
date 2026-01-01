/**
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder

@Composable
fun InvoiceRequestCard(
    lud16: String,
    user: User,
    accountViewModel: AccountViewModel,
    titleText: String? = null,
    buttonText: String? = null,
    onSuccess: (String) -> Unit,
    onError: (String, String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp)
                .clip(shape = QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(30.dp),
    ) {
        InvoiceRequest(
            lud16,
            user,
            accountViewModel,
            titleText,
            buttonText,
            onSuccess,
            onError,
        )
    }
}

@Composable
fun InvoiceRequest(
    lud16: String,
    user: User,
    accountViewModel: AccountViewModel,
    titleText: String? = null,
    buttonText: String? = null,
    onNewInvoice: (String) -> Unit,
    onError: (String, String) -> Unit,
) {
    val context = LocalContext.current

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
            Icon(
                imageVector = CustomHashTagIcons.Lightning,
                null,
                modifier = Size20Modifier,
                tint = Color.Unspecified,
            )

            Text(
                text = titleText ?: stringRes(R.string.lightning_tips),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        var message by remember { mutableStateOf("") }
        var amount by remember { mutableLongStateOf(1000L) }

        OutlinedTextField(
            label = { Text(text = stringRes(R.string.note_to_receiver)) },
            modifier = Modifier.fillMaxWidth(),
            value = message,
            onValueChange = { message = it },
            placeholder = {
                Text(
                    text = stringRes(R.string.thank_you_so_much),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            singleLine = true,
        )

        OutlinedTextField(
            label = { Text(text = stringRes(R.string.amount_in_sats)) },
            modifier = Modifier.fillMaxWidth(),
            value = amount.toString(),
            onValueChange = {
                runCatching {
                    if (it.isEmpty()) {
                        amount = 0
                    } else {
                        amount = it.toLong()
                    }
                }
            },
            placeholder = {
                Text(
                    text = "1000",
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                ),
            singleLine = true,
        )

        Button(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            onClick = {
                accountViewModel.sendSats(
                    lnAddress = lud16,
                    user = user,
                    milliSats = amount * 1000,
                    message = message,
                    onNewInvoice = onNewInvoice,
                    onError = onError,
                    onProgress = {},
                    context = context,
                )
            },
            shape = QuoteBorder,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Text(
                text = buttonText ?: stringRes(R.string.send_sats),
                color = Color.White,
                fontSize = 20.sp,
            )
        }
    }
}
