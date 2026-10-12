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
package com.vitorpamplona.amethyst.commons.account.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.login_with_remote_signer
import com.vitorpamplona.amethyst.commons.resources.relay_url_not_valid
import com.vitorpamplona.amethyst.commons.resources.remote_signer_default_relays
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relay_apply
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relay_hint
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relay_label
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relays
import com.vitorpamplona.amethyst.commons.resources.remote_signer_scan_description
import com.vitorpamplona.amethyst.commons.resources.remote_signer_scan_title
import com.vitorpamplona.amethyst.commons.resources.remote_signer_waiting
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/**
 * Logs in through a NIP-46 remote signer: shows a `nostrconnect://` code for the signer to scan.
 * (A `bunker://` address the signer hands out goes in the key field instead.)
 */
@Composable
fun RemoteSignerLoginButton(loginViewModel: LoginViewModel) {
    TextButton(
        onClick = loginViewModel::loginWithNostrConnect,
        enabled = !loginViewModel.processingLogin,
    ) {
        Text(stringRes(Res.string.login_with_remote_signer))
    }

    if (loginViewModel.nostrConnectPending) {
        NostrConnectDialog(loginViewModel)
    }
}

@Composable
private fun NostrConnectDialog(loginViewModel: LoginViewModel) {
    val uri = loginViewModel.nostrConnectUri
    val onCancel = loginViewModel::cancelNostrConnect
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringRes(Res.string.remote_signer_scan_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringRes(Res.string.remote_signer_scan_description), textAlign = TextAlign.Center)
                // A quiet zone of white around the code, so scanners find it on a dark theme too.
                Box(Modifier.background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
                    if (uri != null) {
                        QrCodeDrawer(uri, Modifier.size(220.dp))
                    } else {
                        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (uri != null) {
                    SelectionContainer {
                        Text(uri, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
                NostrConnectRelayPicker(loginViewModel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringRes(Res.string.remote_signer_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { uri?.let { scope.launch { clipboard.setText(it) } } }, enabled = uri != null) {
                Text(stringRes(Res.string.copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/**
 * The relays the code names, and a field to name one of the account's inbox relays instead: a
 * signer that can't reach the defaults never sees the request.
 */
@Composable
private fun NostrConnectRelayPicker(loginViewModel: LoginViewModel) {
    var relay by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    val apply = {
        if (loginViewModel.useNostrConnectRelay(relay)) {
            relay = ""
            invalid = false
        } else {
            invalid = true
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringRes(
                Res.string.remote_signer_relays,
                loginViewModel.nostrConnectRelays.joinToString { it.removePrefix("wss://").removeSuffix("/") },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = relay,
                onValueChange = {
                    relay = it
                    invalid = false
                },
                label = { Text(stringRes(Res.string.remote_signer_relay_label)) },
                placeholder = { Text("wss://") },
                singleLine = true,
                isError = invalid,
                supportingText = {
                    Text(stringRes(if (invalid) Res.string.relay_url_not_valid else Res.string.remote_signer_relay_hint))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { apply() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = apply, enabled = relay.isNotBlank()) {
                Text(stringRes(Res.string.remote_signer_relay_apply))
            }
        }
        if (!loginViewModel.usesDefaultNostrConnectRelays) {
            TextButton(onClick = loginViewModel::useDefaultNostrConnectRelays) {
                Text(stringRes(Res.string.remote_signer_default_relays))
            }
        }
    }
}
