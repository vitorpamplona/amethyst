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
package com.vitorpamplona.amethyst.desktop.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols

/** Enforced minimum length for a new/rotated password. */
const val PRIVACY_LOCK_MIN_PASSWORD_LENGTH = 6

/**
 * Set or change the privacy-lock password.
 *
 * Pass [existingHash] = null when the user hasn't set a password yet
 * (first-run banner path, or a fresh Settings toggle). In that case the
 * dialog shows a single New password field with a reveal toggle. Pass a
 * real hash to force verification of the current password before letting
 * the user rotate — the dialog then shows a Current password field
 * followed by the New password field, each with its own reveal toggle.
 *
 * On successful validation, [onConfirm] is invoked with a fresh
 * `salt$hash` string ready for [com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.setPasswordHashed].
 *
 * UX: auto-focus the first field on open, Enter submits, Escape cancels.
 * Real-time checklist under the New password field shows a green ✓ once
 * the length threshold is reached. Reveal toggles are per-field
 * independent so revealing Current does not reveal New.
 */
@Composable
fun SetPasswordDialog(
    existingHash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val isChange = existingHash != null
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var currentError by remember { mutableStateOf<String?>(null) }
    var newError by remember { mutableStateOf<String?>(null) }

    val firstFieldFocus = remember { FocusRequester() }

    val submit: () -> Unit = {
        val currentOk =
            !isChange ||
                PasswordHasher.verify(current.toCharArray(), existingHash!!)
        when {
            !currentOk -> {
                currentError = "Wrong password"
                newError = null
            }
            new.length < PRIVACY_LOCK_MIN_PASSWORD_LENGTH -> {
                currentError = null
                newError = "Must be at least $PRIVACY_LOCK_MIN_PASSWORD_LENGTH characters"
            }
            else -> {
                onConfirm(PasswordHasher.hash(new.toCharArray()))
            }
        }
    }

    LaunchedEffect(Unit) {
        firstFieldFocus.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(440.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DialogHeader(title = if (isChange) "Change password" else "Set a password")

                if (!isChange) {
                    Text(
                        text = "Choose a password to lock the Messages tab. You'll enter it to unlock later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isChange) {
                    PasswordField(
                        value = current,
                        onValueChange = {
                            current = it
                            currentError = null
                        },
                        label = "Current password",
                        errorMessage = currentError,
                        modifier = Modifier.focusRequester(firstFieldFocus),
                        imeAction = ImeAction.Next,
                        onImeAction = { /* Tab handled by focus system */ },
                    )
                }

                PasswordField(
                    value = new,
                    onValueChange = {
                        new = it
                        newError = null
                    },
                    label = "New password",
                    errorMessage = newError,
                    modifier =
                        if (isChange) Modifier else Modifier.focusRequester(firstFieldFocus),
                    imeAction = ImeAction.Done,
                    onImeAction = { submit() },
                )

                RequirementChecklistRow(satisfied = new.length >= PRIVACY_LOCK_MIN_PASSWORD_LENGTH)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = submit,
                        enabled = new.length >= PRIVACY_LOCK_MIN_PASSWORD_LENGTH,
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Lock,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * Verify the user's current password before removing the privacy lock.
 *
 * On successful verification, [onConfirm] is invoked with no arguments.
 * The caller is responsible for clearing `passwordHashed` and disabling
 * `lockEnabled` — this dialog only proves possession of the current
 * password.
 *
 * Deliberate design choice: users cannot disable the lock without
 * demonstrating they know the password, matching the security posture
 * of Signal PIN, WhatsApp Chat Lock, and macOS FileVault disable.
 */
@Composable
fun RemovePasswordDialog(
    existingHash: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val firstFieldFocus = remember { FocusRequester() }

    val submit: () -> Unit = {
        if (PasswordHasher.verify(current.toCharArray(), existingHash)) {
            onConfirm()
        } else {
            error = "Wrong password"
        }
    }

    LaunchedEffect(Unit) {
        firstFieldFocus.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(440.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DialogHeader(title = "Remove password")

                Text(
                    text =
                        "Enter your current password to remove the lock. " +
                            "You'll need to set a new password if you turn the lock back on later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PasswordField(
                    value = current,
                    onValueChange = {
                        current = it
                        error = null
                    },
                    label = "Current password",
                    errorMessage = error,
                    modifier = Modifier.focusRequester(firstFieldFocus),
                    imeAction = ImeAction.Done,
                    onImeAction = { submit() },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = submit,
                        enabled = current.isNotEmpty(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier =
            modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && imeAction == ImeAction.Done) {
                        onImeAction()
                        true
                    } else {
                        false
                    }
                },
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
            ),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    symbol = if (revealed) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                    contentDescription = if (revealed) "Hide password" else "Show password",
                )
            }
        },
        isError = errorMessage != null,
        supportingText =
            errorMessage?.let {
                {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
    )
}

@Composable
private fun RequirementChecklistRow(satisfied: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = if (satisfied) MaterialSymbols.CheckCircle else MaterialSymbols.Circle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint =
                if (satisfied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Text(
            text = "Min $PRIVACY_LOCK_MIN_PASSWORD_LENGTH characters",
            style = MaterialTheme.typography.bodySmall,
            color =
                if (satisfied) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
