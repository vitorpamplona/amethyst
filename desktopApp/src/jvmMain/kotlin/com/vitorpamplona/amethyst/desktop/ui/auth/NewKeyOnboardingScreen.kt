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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_copy
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copied
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copy_plain_warning
import com.vitorpamplona.amethyst.commons.resources.backup_keys_encrypt_failed
import com.vitorpamplona.amethyst.commons.resources.backup_keys_hide_qr
import com.vitorpamplona.amethyst.commons.resources.backup_keys_show_qr
import com.vitorpamplona.amethyst.commons.resources.new_key_back
import com.vitorpamplona.amethyst.commons.resources.new_key_cancel
import com.vitorpamplona.amethyst.commons.resources.new_key_confirm_recap
import com.vitorpamplona.amethyst.commons.resources.new_key_confirm_title
import com.vitorpamplona.amethyst.commons.resources.new_key_continue_button
import com.vitorpamplona.amethyst.commons.resources.new_key_copy_encrypted_button
import com.vitorpamplona.amethyst.commons.resources.new_key_encrypt_password_label
import com.vitorpamplona.amethyst.commons.resources.new_key_keys_title
import com.vitorpamplona.amethyst.commons.resources.new_key_next
import com.vitorpamplona.amethyst.commons.resources.new_key_public_label
import com.vitorpamplona.amethyst.commons.resources.new_key_readonly_info
import com.vitorpamplona.amethyst.commons.resources.new_key_saved_checkbox
import com.vitorpamplona.amethyst.commons.resources.new_key_secret_label
import com.vitorpamplona.amethyst.commons.resources.new_key_step_indicator
import com.vitorpamplona.amethyst.commons.resources.new_key_step_intro_npub
import com.vitorpamplona.amethyst.commons.resources.new_key_step_intro_nsec
import com.vitorpamplona.amethyst.commons.resources.new_key_step_intro_title
import com.vitorpamplona.amethyst.commons.resources.new_key_warning_message
import com.vitorpamplona.amethyst.desktop.util.copyToClipboard
import com.vitorpamplona.amethyst.desktop.util.copyToClipboardThenClear
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private const val TOTAL_STEPS = 3

/**
 * Full-window, three-step onboarding shown ONCE right after a new Nostr keypair
 * is generated. This is the only moment the plaintext nsec is displayed.
 *
 * - Step 0 explains why the keys matter (npub is shareable, nsec can never be reset).
 * - Step 1 shows both keys: the npub with a plain copy + QR toggle, and the nsec
 *   with a prominent auto-clearing plaintext copy plus a de-emphasised NIP-49
 *   encrypted copy. The nsec is NEVER rendered as a QR code.
 * - Step 2 asks the user to confirm they saved the keys before proceeding.
 *
 * The whole thing is scrollable so nothing clips regardless of window size.
 *
 * @param npub The public key in npub format (shareable)
 * @param nsec The secret key in nsec format, or null for a read-only account
 * @param onFinish Called when the user acknowledged and wants to enter the app
 * @param onCancel Called when the user backs out; the new key should be discarded
 * @param modifier Modifier applied to the root container
 */
@Composable
fun NewKeyOnboardingScreen(
    npub: String,
    nsec: String?,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(0) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepIndicator(step = step)

            Spacer(Modifier.height(24.dp))

            when (step) {
                0 ->
                    IntroStep(
                        onCancel = onCancel,
                        onNext = { step = 1 },
                    )
                1 ->
                    KeysStep(
                        npub = npub,
                        nsec = nsec,
                        onBack = { step = 0 },
                        onNext = { step = 2 },
                    )
                else ->
                    ConfirmStep(
                        onBack = { step = 1 },
                        onFinish = onFinish,
                    )
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(TOTAL_STEPS) { index ->
                val color =
                    if (index <= step) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.new_key_step_indicator, step + 1, TOTAL_STEPS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntroStep(
    onCancel: () -> Unit,
    onNext: () -> Unit,
) {
    StepScaffold {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(Res.string.new_key_step_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(Res.string.new_key_warning_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        FramingRow(
            symbol = MaterialSymbols.Info,
            tint = MaterialTheme.colorScheme.primary,
            text = stringResource(Res.string.new_key_step_intro_npub),
        )

        Spacer(Modifier.height(12.dp))

        FramingRow(
            symbol = MaterialSymbols.Key,
            tint = MaterialTheme.colorScheme.error,
            text = stringResource(Res.string.new_key_step_intro_nsec),
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.new_key_cancel))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.new_key_next))
            }
        }
    }
}

@Composable
private fun KeysStep(
    npub: String,
    nsec: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    StepScaffold {
        Text(
            stringResource(Res.string.new_key_keys_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(24.dp))

        PublicKeySection(npub = npub)

        Spacer(Modifier.height(24.dp))

        if (nsec != null) {
            SecretKeySection(nsec = nsec)
        } else {
            FramingRow(
                symbol = MaterialSymbols.Info,
                tint = MaterialTheme.colorScheme.primary,
                text = stringResource(Res.string.new_key_readonly_info),
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.new_key_back))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.new_key_next))
            }
        }
    }
}

@Composable
private fun PublicKeySection(npub: String) {
    var showQr by remember { mutableStateOf(false) }

    Text(
        stringResource(Res.string.new_key_public_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    SelectableKeyText(npub)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyButton(
            symbol = MaterialSymbols.ContentCopy,
            idleLabel = stringResource(Res.string.action_copy),
            onCopy = { copyToClipboard(npub) },
        )
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
    val scope = rememberCoroutineScope()

    Text(
        stringResource(Res.string.new_key_secret_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(4.dp))
    SelectableKeyText(nsec)
    Spacer(Modifier.height(8.dp))

    CopyButton(
        symbol = MaterialSymbols.Key,
        idleLabel = stringResource(Res.string.action_copy),
        primary = true,
        onCopy = { copyToClipboardThenClear(nsec, scope, delayMs = 60_000L) },
    )

    Spacer(Modifier.height(8.dp))

    FramingRow(
        symbol = MaterialSymbols.Warning,
        tint = MaterialTheme.colorScheme.error,
        text = stringResource(Res.string.backup_keys_copy_plain_warning),
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    EncryptedCopySection(nsec = nsec)
}

@Composable
private fun EncryptedCopySection(nsec: String) {
    var password by remember { mutableStateOf("") }
    var showChars by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            IconButton(onClick = { showChars = !showChars }) {
                Icon(
                    symbol = if (showChars) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                    contentDescription = null,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            error = false
            working = true
            scope.launch {
                val encrypted =
                    withContext(Dispatchers.Default) {
                        decodePrivateKeyAsHexOrNull(nsec)?.let {
                            runCatching { Nip49().encrypt(it, password) }.getOrNull()
                        }
                    }
                working = false
                if (encrypted != null) {
                    copyToClipboard(encrypted)
                    copied = true
                } else {
                    error = true
                }
            }
        },
        enabled = password.isNotBlank() && !working,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
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
                stringResource(Res.string.new_key_copy_encrypted_button)
            },
        )
    }

    ResetCopiedAfterDelay(copied) { copied = false }
}

@Composable
private fun ConfirmStep(
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }

    StepScaffold {
        Icon(
            symbol = MaterialSymbols.Check,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(Res.string.new_key_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(Res.string.new_key_confirm_recap),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.new_key_back))
            }
            Button(
                onClick = onFinish,
                enabled = acknowledged,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.new_key_continue_button))
            }
        }
    }
}

/** Shared per-step body: a card whose content scrolls so it never clips. */
@Composable
private fun StepScaffold(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            content = content,
        )
    }
}

/** A small icon + explainer text row used to frame npub/nsec and warnings. */
@Composable
private fun FramingRow(
    symbol: MaterialSymbol,
    tint: Color,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Copy button that flashes a transient "Copied!" for ~2s after activation. */
@Composable
private fun CopyButton(
    symbol: MaterialSymbol,
    idleLabel: String,
    primary: Boolean = false,
    onCopy: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    val label =
        if (copied) stringResource(Res.string.backup_keys_copied) else idleLabel
    val onClick: () -> Unit = {
        onCopy()
        copied = true
    }

    if (primary) {
        Button(onClick = onClick) {
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(label)
        }
    }

    ResetCopiedAfterDelay(copied) { copied = false }
}

@Composable
private fun ResetCopiedAfterDelay(
    copied: Boolean,
    onReset: () -> Unit,
) {
    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            onReset()
        }
    }
}

@Preview
@Composable
fun NewKeyOnboardingScreenPreview() {
    NewKeyOnboardingScreen(
        npub = "npub1example1234567890abcdefghijklmnopqrstuvwxyz1234567890",
        nsec = "nsec1example1234567890abcdefghijklmnopqrstuvwxyz1234567890",
        onFinish = {},
        onCancel = {},
    )
}
