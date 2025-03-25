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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Preview
@Composable
fun RelayUrlEditFieldPreview() {
    ThemeComparisonColumn {
        RelayUrlEditField {}
    }
}

@Composable
fun RelayUrlEditField(onNewRelay: (String) -> Unit) {
    var url by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Size10dp)) {
        OutlinedTextField(
            label = { Text(text = stringRes(R.string.add_a_relay)) },
            modifier = Modifier.weight(1f),
            value = url,
            onValueChange = { url = it },
            placeholder = {
                Text(
                    text = "server.com",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                ),
            keyboardActions =
                KeyboardActions(
                    onGo = {
                        if (url.isNotBlank() && url != "/") {
                            onNewRelay(url)
                            url = ""
                        }
                    },
                ),
        )

        Button(
            onClick = {
                if (url.isNotBlank() && url != "/") {
                    onNewRelay(url)
                    url = ""
                }
            },
            shape = ButtonBorder,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (url.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.placeholderText
                        },
                ),
        ) {
            Text(text = stringRes(id = R.string.add), color = Color.White)
        }
    }
}
