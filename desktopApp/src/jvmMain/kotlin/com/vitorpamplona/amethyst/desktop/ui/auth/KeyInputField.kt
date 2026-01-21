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
package com.vitorpamplona.amethyst.desktop.ui.auth

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.SharedRes
import dev.icerock.moko.resources.compose.stringResource

/**
 * Text field for entering Nostr keys (nsec or npub) with visibility toggle.
 */
@Composable
fun KeyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(SharedRes.strings.login_key_label),
    placeholder: String = stringResource(SharedRes.strings.login_key_placeholder),
    errorMessage: String? = null,
) {
    var showKey by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation =
            if (showKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) stringResource(SharedRes.strings.login_hide_key) else stringResource(SharedRes.strings.login_show_key),
                )
            }
        },
        isError = errorMessage != null,
        supportingText =
            errorMessage?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
    )
}

/**
 * Card displaying a Nostr key that users can select/copy.
 */
@Composable
fun SelectableKeyText(
    key: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Text(
            text = key,
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview
@Composable
fun KeyInputFieldPreview() {
    KeyInputField(
        value = "nsec1example1234567890",
        onValueChange = {},
    )
}

@Preview
@Composable
fun SelectableKeyTextPreview() {
    SelectableKeyText(
        key = "npub1example1234567890abcdefghijklmnopqrstuvwxyz1234567890",
    )
}
