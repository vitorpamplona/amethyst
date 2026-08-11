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
package com.vitorpamplona.amethyst.desktop.ui.keyBackup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import com.vitorpamplona.amethyst.commons.privacylock.lockStateFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copied
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copy
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copy_encrypted
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copy_plain
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copy_plain_warning
import com.vitorpamplona.amethyst.commons.resources.backup_keys_encrypt_failed
import com.vitorpamplona.amethyst.commons.resources.backup_keys_encrypt_password_label
import com.vitorpamplona.amethyst.commons.resources.backup_keys_external_signer
import com.vitorpamplona.amethyst.commons.resources.backup_keys_hide
import com.vitorpamplona.amethyst.commons.resources.backup_keys_hide_qr
import com.vitorpamplona.amethyst.commons.resources.backup_keys_public_help
import com.vitorpamplona.amethyst.commons.resources.backup_keys_public_label
import com.vitorpamplona.amethyst.commons.resources.backup_keys_reveal
import com.vitorpamplona.amethyst.commons.resources.backup_keys_secret_hidden
import com.vitorpamplona.amethyst.commons.resources.backup_keys_secret_label
import com.vitorpamplona.amethyst.commons.resources.backup_keys_secret_warning
import com.vitorpamplona.amethyst.commons.resources.backup_keys_show_qr
import com.vitorpamplona.amethyst.commons.resources.backup_keys_title
import com.vitorpamplona.amethyst.commons.resources.backup_keys_unlock_subtitle
import com.vitorpamplona.amethyst.commons.resources.backup_keys_unlock_title
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.security.DesktopLockScreen
import com.vitorpamplona.amethyst.desktop.ui.auth.QrCodeCanvas
import com.vitorpamplona.amethyst.desktop.util.copyToClipboard
import com.vitorpamplona.amethyst.desktop.util.copyToClipboardThenClear
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Account key backup card shown in the Desktop settings/profile screen.
 *
 * The public key (npub) is treated as shareable: plain, copyable, QR is fine.
 * The secret key (nsec) is treated as an unrecoverable password: masked by
 * default, its reveal AND copy are gated behind the existing PrivacyLock
 * ([LockScope.KeyBackup]), and it is NEVER rendered as a QR code. Encrypted
 * (NIP-49 `ncryptsec1…`) copy is offered as the recommended path.
 */
@Composable
fun BackupKeysCard(
    account: AccountState.LoggedIn,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(Res.string.backup_keys_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            PublicKeySection(npub = account.npub)

            val nsec = account.nsec
            if (nsec != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                SecretKeySection(nsec = nsec)
            } else {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.backup_keys_external_signer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicKeySection(npub: String) {
    var showQr by remember { mutableStateOf(false) }

    Text(
        stringResource(Res.string.backup_keys_public_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(Res.string.backup_keys_public_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    MonospaceKeyValue(value = npub)

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyButton(value = npub)
        OutlinedButton(onClick = { showQr = !showQr }) {
            Icon(
                symbol = MaterialSymbols.QrCode2,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                if (showQr) {
                    stringResource(Res.string.backup_keys_hide_qr)
                } else {
                    stringResource(Res.string.backup_keys_show_qr)
                },
            )
        }
    }

    if (showQr) {
        Spacer(Modifier.height(12.dp))
        QrCodeCanvas(data = npub)
    }
}

@Composable
private fun SecretKeySection(nsec: String) {
    val lockState = lockStateFor(LockScope.KeyBackup)
    val current by lockState.state.collectAsState()

    var revealed by remember { mutableStateOf(false) }
    var awaitingUnlock by remember { mutableStateOf(false) }

    // Re-hide whenever we leave this route/composable.
    DisposableEffect(lockState) {
        onDispose {
            lockState.onLeaveRoute()
            revealed = false
            awaitingUnlock = false
        }
    }

    // When the user asked to reveal and the gate becomes usable, show the key.
    LaunchedEffect(current, awaitingUnlock) {
        if (awaitingUnlock && current !is LockState.Locked) {
            revealed = true
            awaitingUnlock = false
        }
    }

    Text(
        stringResource(Res.string.backup_keys_secret_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))

    // Warning banner
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            stringResource(Res.string.backup_keys_secret_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Spacer(Modifier.height(12.dp))

    if (awaitingUnlock && current is LockState.Locked) {
        // Force an unlock before revealing. DesktopLockScreen is a fillMaxSize
        // Surface, so present it inside a modal Dialog with a bounded box rather
        // than letting it take over the whole settings pane.
        Dialog(onDismissRequest = { awaitingUnlock = false }) {
            Surface(
                modifier = Modifier.size(width = 420.dp, height = 380.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                DesktopLockScreen(
                    scope = LockScope.KeyBackup,
                    title = stringResource(Res.string.backup_keys_unlock_title),
                    subtitle = stringResource(Res.string.backup_keys_unlock_subtitle),
                )
            }
        }
    } else if (!revealed) {
        Text(
            stringResource(Res.string.backup_keys_secret_hidden),
            style =
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (current is LockState.Locked) {
                    awaitingUnlock = true
                } else {
                    revealed = true
                }
            },
        ) {
            Icon(
                symbol = MaterialSymbols.Visibility,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(Res.string.backup_keys_reveal))
        }
    } else {
        RevealedSecret(nsec = nsec, onHide = { revealed = false })
    }
}

@Composable
private fun RevealedSecret(
    nsec: String,
    onHide: () -> Unit,
) {
    MonospaceKeyValue(value = nsec, isSensitive = true)

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyButton(
            value = nsec,
            label = stringResource(Res.string.backup_keys_copy_plain),
            autoClearSensitive = true,
        )
        OutlinedButton(onClick = onHide) {
            Icon(
                symbol = MaterialSymbols.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(Res.string.backup_keys_hide))
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(Res.string.backup_keys_copy_plain_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )

    Spacer(Modifier.height(16.dp))

    EncryptedCopy(nsec = nsec)
}

@Composable
private fun EncryptedCopy(nsec: String) {
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
        label = { Text(stringResource(Res.string.backup_keys_encrypt_password_label)) },
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
                stringResource(Res.string.backup_keys_copy_encrypted)
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
private fun MonospaceKeyValue(
    value: String,
    isSensitive: Boolean = false,
) {
    Text(
        value,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color =
            if (isSensitive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp),
                ).padding(8.dp),
    )
}

@Composable
private fun CopyButton(
    value: String,
    label: String = stringResource(Res.string.backup_keys_copy),
    autoClearSensitive: Boolean = false,
) {
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            if (autoClearSensitive) {
                copyToClipboardThenClear(value, scope)
            } else {
                copyToClipboard(value)
            }
            copied = true
        },
    ) {
        Icon(
            symbol = MaterialSymbols.ContentCopy,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(if (copied) stringResource(Res.string.backup_keys_copied) else label)
    }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }
}
