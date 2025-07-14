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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KeyTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue, throughQR: Boolean) -> Unit,
    onLogin: () -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    var showCharsKey by remember { mutableStateOf(false) }

    val autofillNodeKey =
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { onValueChange(TextFieldValue(it), false) },
        )

    val autofillNodePassword =
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { onValueChange(TextFieldValue(it), false) },
        )

    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNodeKey
    LocalAutofillTree.current += autofillNodePassword

    OutlinedTextField(
        modifier =
            Modifier
                .onGloballyPositioned { coordinates ->
                    autofillNodeKey.boundingBox = coordinates.boundsInWindow()
                }.onFocusChanged { focusState ->
                    autofill?.run {
                        if (focusState.isFocused) {
                            requestAutofillForNode(autofillNodeKey)
                        } else {
                            cancelAutofillForNode(autofillNodeKey)
                        }
                    }
                },
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
                text = stringRes(R.string.nsec_npub_hex_private_key),
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
                                stringRes(R.string.show_password)
                            } else {
                                stringRes(
                                    R.string.hide_password,
                                )
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
                    contentDescription = stringRes(R.string.login_with_qr_code),
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
