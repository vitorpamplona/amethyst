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
package com.vitorpamplona.amethyst.commons.ui.keystorage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.keystorage.KeyPrompt
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.keystorage.NewPasswordAnswer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_confirm_password
import com.vitorpamplona.amethyst.commons.resources.key_storage_create_password_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_create_password_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_dont_remember
import com.vitorpamplona.amethyst.commons.resources.key_storage_enter_key_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_enter_key_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_keep_key
import com.vitorpamplona.amethyst.commons.resources.key_storage_not_now
import com.vitorpamplona.amethyst.commons.resources.key_storage_password
import com.vitorpamplona.amethyst.commons.resources.key_storage_skip
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_detail
import com.vitorpamplona.amethyst.commons.resources.key_storage_try_again
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_unreachable_description
import com.vitorpamplona.amethyst.commons.resources.key_storage_unreachable_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_use_key
import com.vitorpamplona.amethyst.commons.resources.key_storage_wrong_key
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_mismatch
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_too_short
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_wrong
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import org.jetbrains.compose.resources.StringResource

// The prompts stay open until answered: a dismissal by clicking outside would leave the storage
// waiting on an answer, so each dialog answers through its own buttons only.
private val ModalOnly = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

/** Asks for a new password for the key file, or for not keeping keys on this device. */
@Composable
internal fun CreateKeyPasswordDialog(prompt: KeyPrompt.CreatePassword) {
    var password by remember(prompt) { mutableStateOf("") }
    var confirm by remember(prompt) { mutableStateOf("") }
    var error by remember(prompt) { mutableStateOf<String?>(null) }

    val tooShort = pluralStringRes(Res.plurals.privacy_lock_password_too_short, Nip49.MIN_PASSWORD_LENGTH, Nip49.MIN_PASSWORD_LENGTH)
    val mismatch = stringRes(Res.string.privacy_lock_password_mismatch)

    AlertDialog(
        onDismissRequest = {},
        properties = ModalOnly,
        title = { Text(stringRes(Res.string.key_storage_create_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.key_storage_create_password_description), style = MaterialTheme.typography.bodySmall)
                SecretField(password, Res.string.key_storage_password) { password = it }
                SecretField(confirm, Res.string.key_storage_confirm_password) { confirm = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error =
                        when {
                            !Nip49.isLongEnough(password) -> tooShort
                            password != confirm -> mismatch
                            else -> null
                        }
                    if (error == null) prompt.answer(NewPasswordAnswer.Password(password))
                },
            ) { Text(stringRes(Res.string.key_storage_keep_key)) }
        },
        dismissButton = {
            TextButton(onClick = { prompt.answer(NewPasswordAnswer.DontRemember) }) {
                Text(stringRes(Res.string.key_storage_dont_remember))
            }
        },
    )
}

/** Asks for the key file's password; Skip leaves it locked for this run. */
@Composable
internal fun UnlockKeysDialog(prompt: KeyPrompt.Unlock) {
    var password by remember(prompt) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        properties = ModalOnly,
        title = { Text(stringRes(Res.string.key_storage_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.key_storage_unlock_description), style = MaterialTheme.typography.bodySmall)
                SecretField(password, Res.string.key_storage_password, onDone = { prompt.answer(password) }) { password = it }
                if (prompt.wrongPassword) {
                    Text(stringRes(Res.string.privacy_lock_password_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { prompt.answer(password) }, enabled = password.isNotEmpty()) {
                Text(stringRes(Res.string.key_storage_unlock))
            }
        },
        dismissButton = { TextButton(onClick = { prompt.answer(null) }) { Text(stringRes(Res.string.key_storage_skip)) } },
    )
}

/** Asks for the key of an account this device does not keep; Skip loads it without signing. */
@Composable
internal fun EnterKeyDialog(prompt: KeyPrompt.EnterKey) {
    var key by remember(prompt) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        properties = ModalOnly,
        title = { Text(stringRes(Res.string.key_storage_enter_key_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.key_storage_enter_key_description, prompt.npub.take(16) + "…"), style = MaterialTheme.typography.bodySmall)
                SecretField(key, Res.string.key_storage_enter_key_title, onDone = { prompt.answer(key) }) { key = it }
                if (prompt.wrongKey) {
                    Text(stringRes(Res.string.key_storage_wrong_key), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { prompt.answer(key) }, enabled = key.isNotBlank()) {
                Text(stringRes(Res.string.key_storage_use_key))
            }
        },
        dismissButton = { TextButton(onClick = { prompt.answer(null) }) { Text(stringRes(Res.string.key_storage_skip)) } },
    )
}

/** The OS keyring is there but did not answer: say so, and offer to ask it again. */
@Composable
internal fun KeyringUnreachableDialog(
    status: KeyStorageStatus.KeyringUnreachable,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.key_storage_unreachable_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.key_storage_unreachable_description, keyringName(status.keyring)))
                Text(stringRes(Res.string.key_storage_status_detail, status.detail), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text(stringRes(Res.string.key_storage_try_again)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.key_storage_not_now)) } },
    )
}

@Composable
private fun SecretField(
    value: String,
    label: StringResource,
    onDone: (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringRes(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
