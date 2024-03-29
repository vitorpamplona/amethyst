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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun ZapRaiserRequest(
    titleText: String? = null,
    newPostViewModel: NewPostViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                text = titleText ?: stringResource(R.string.zapraiser),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringResource(R.string.zapraiser_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        OutlinedTextField(
            label = { Text(text = stringResource(R.string.zapraiser_target_amount_in_sats)) },
            modifier = Modifier.fillMaxWidth(),
            value =
                if (newPostViewModel.zapRaiserAmount != null) {
                    newPostViewModel.zapRaiserAmount.toString()
                } else {
                    ""
                },
            onValueChange = {
                runCatching {
                    if (it.isEmpty()) {
                        newPostViewModel.updateZapRaiserAmount(null)
                    } else {
                        newPostViewModel.updateZapRaiserAmount(it.toLongOrNull())
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
    }
}
