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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.login_with_qr_code
import com.vitorpamplona.amethyst.commons.resources.nsec_npub_hex_private_key
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import org.jetbrains.compose.resources.stringResource

@Composable
fun KeyTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue, throughQR: Boolean) -> Unit,
    onLogin: () -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    var showCharsKey by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier =
            Modifier
                .semantics { contentType = ContentType.Password },
        value = value,
        onValueChange = { onValueChange(it, false) },
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        placeholder = {
            Text(
                text = stringResource(Res.string.nsec_npub_hex_private_key),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        trailingIcon = {
            Row {
                IconButton(onClick = { showCharsKey = !showCharsKey }) {
                    Icon(
                        imageVector =
                            if (showCharsKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription =
                            if (showCharsKey) {
                                stringResource(Res.string.show_password)
                            } else {
                                stringResource(Res.string.hide_password)
                            },
                    )
                }
            }
        },
        leadingIcon = {
            if (dialogOpen) {
                SimpleQrCodeScanner {
                    dialogOpen = false
                    if (!it.isNullOrEmpty()) {
                        onValueChange(TextFieldValue(it), true)
                    }
                }
            }
            IconButton(onClick = { dialogOpen = true }) {
                Icon(
                    painter = painterRes(R.drawable.ic_qrcode, 5),
                    contentDescription = stringResource(Res.string.login_with_qr_code),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        visualTransformation =
            if (showCharsKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardActions =
            KeyboardActions(
                onGo = {
                    onLogin()
                },
            ),
    )
}
