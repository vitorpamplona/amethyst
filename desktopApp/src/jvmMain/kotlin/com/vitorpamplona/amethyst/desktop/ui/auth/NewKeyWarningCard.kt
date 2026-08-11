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
package com.vitorpamplona.amethyst.desktop.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_copy
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copied
import com.vitorpamplona.amethyst.commons.resources.backup_keys_encrypt_failed
import com.vitorpamplona.amethyst.commons.resources.new_key_continue_button
import com.vitorpamplona.amethyst.commons.resources.new_key_copy_encrypted_button
import com.vitorpamplona.amethyst.commons.resources.new_key_encrypt_password_label
import com.vitorpamplona.amethyst.commons.resources.new_key_public_label
import com.vitorpamplona.amethyst.commons.resources.new_key_saved_checkbox
import com.vitorpamplona.amethyst.commons.resources.new_key_secret_label
import com.vitorpamplona.amethyst.commons.resources.new_key_warning_message
import com.vitorpamplona.amethyst.commons.resources.new_key_warning_title
import com.vitorpamplona.amethyst.desktop.util.copyToClipboard
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Warning card displayed after generating a new Nostr key pair.
 * Reminds users to save their keys and shows both public and secret keys.
 *
 * The npub is shareable (plain + copy). The nsec is an unrecoverable password:
 * it is shown so the user can save it, offers plaintext AND NIP-49 encrypted
 * copy, and is never rendered as a QR code. A soft acknowledgement checkbox
 * nudges the user to confirm they saved their keys before continuing.
 *
 * @param npub The public key in npub format
 * @param nsec The secret key in nsec format (nullable for read-only accounts)
 * @param onContinue Callback when user acknowledges they've saved their keys
 * @param modifier Modifier for the card
 * @param cardWidth Width of the card (default 500.dp)
 */
@Composable
fun NewKeyWarningCard(
    npub: String,
    nsec: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 500.dp,
) {
    var acknowledged by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.width(cardWidth),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                stringResource(Res.string.new_key_warning_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(Res.string.new_key_warning_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(Res.string.new_key_public_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectableKeyText(npub)
            Spacer(Modifier.height(8.dp))
            CopyKeyButton(value = npub)

            Spacer(Modifier.height(12.dp))

            nsec?.let { secretKey ->
                Text(
                    stringResource(Res.string.new_key_secret_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SelectableKeyText(secretKey)
                Spacer(Modifier.height(8.dp))
                CopyKeyButton(value = secretKey)

                Spacer(Modifier.height(16.dp))
                EncryptedCopyRow(nsec = secretKey)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Checkbox(
                    checked = acknowledged,
                    onCheckedChange = { acknowledged = it },
                )
                Text(
                    stringResource(Res.string.new_key_saved_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                enabled = acknowledged,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.new_key_continue_button))
            }
        }
    }
}

@Composable
private fun CopyKeyButton(value: String) {
    var copied by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            copyToClipboard(value)
            copied = true
        },
    ) {
        Icon(
            symbol = MaterialSymbols.ContentCopy,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            if (copied) {
                stringResource(Res.string.backup_keys_copied)
            } else {
                stringResource(Res.string.action_copy)
            },
        )
    }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }
}

@Composable
private fun EncryptedCopyRow(nsec: String) {
    var password by remember { mutableStateOf("") }
    var showChars by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = {
            password = it
            error = false
        },
        label = { Text(stringResource(Res.string.new_key_encrypt_password_label)) },
        singleLine = true,
        isError = error,
        supportingText =
            if (error) {
                { Text(stringResource(Res.string.backup_keys_encrypt_failed)) }
            } else {
                null
            },
        visualTransformation =
            if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Icon(
                symbol = if (showChars) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            val hex = decodePrivateKeyAsHexOrNull(nsec)
            val encrypted =
                hex?.let { runCatching { Nip49().encrypt(it, password) }.getOrNull() }
            if (encrypted != null) {
                copyToClipboard(encrypted)
                copied = true
            } else {
                error = true
            }
        },
        enabled = password.isNotBlank(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Icon(
            symbol = MaterialSymbols.Key,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            if (copied) {
                stringResource(Res.string.backup_keys_copied)
            } else {
                stringResource(Res.string.new_key_copy_encrypted_button)
            },
        )
    }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }
}

@Preview
@Composable
fun NewKeyWarningCardPreview() {
    NewKeyWarningCard(
        npub = "npub1example1234567890abcdefghijklmnopqrstuvwxyz",
        nsec = "nsec1example1234567890abcdefghijklmnopqrstuvwxyz",
        onContinue = {},
    )
}
