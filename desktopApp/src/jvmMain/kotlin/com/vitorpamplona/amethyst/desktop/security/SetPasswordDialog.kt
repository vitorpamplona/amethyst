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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Set or change the privacy-lock password.
 *
 * Pass [existingHash] = null when the user hasn't set a password yet
 * (first-run banner path, or a fresh Settings toggle). In that case the
 * "current password" field is hidden. Pass a real hash to force
 * verification of the current password before letting the user rotate.
 *
 * On successful validation, [onConfirm] is invoked with a fresh
 * `salt$hash` string ready for [com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.setPasswordHashed].
 */
@Composable
fun SetPasswordDialog(
    existingHash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val submit: () -> Unit = {
        val currentOk =
            existingHash == null ||
                PasswordHasher.verify(current.toCharArray(), existingHash)
        when {
            !currentOk -> error = "Current password is wrong"
            new1.length < 4 -> error = "New password must be at least 4 characters"
            new1 != new2 -> error = "Passwords don't match"
            else -> onConfirm(PasswordHasher.hash(new1.toCharArray()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingHash == null) "Set a password" else "Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existingHash != null) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = {
                            current = it
                            error = null
                        },
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                OutlinedTextField(
                    value = new1,
                    onValueChange = {
                        new1 = it
                        error = null
                    },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = new2,
                    onValueChange = {
                        new2 = it
                        error = null
                    },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
