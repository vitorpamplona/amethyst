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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.nip46

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.nip46Signer.Nip46SignerState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

@Composable
fun Nip46SignerScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val signer = account.nip46Signer
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val enabled by account.settings.nip46SignerEnabled.collectAsStateWithLifecycle()
    val secret by account.settings.nip46BunkerSecret.collectAsStateWithLifecycle()
    val relays by signer.listeningRelays.collectAsStateWithLifecycle()
    val writeable = remember { account.signer.isWriteable() }

    var bunkerUri by remember { mutableStateOf<String?>(null) }
    // Recompute the advertised bunker URI whenever it is being shown or its inputs change.
    LaunchedEffect(enabled, secret, relays) {
        bunkerUri = if (enabled && writeable && relays.isNotEmpty()) signer.bunkerUri() else null
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringResource(R.string.nip46_signer_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard()

            if (!writeable) {
                Text(
                    stringResource(R.string.nip46_signer_readonly),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            EnableRow(enabled = enabled, onToggle = { signer.setEnabled(it) })

            if (enabled) {
                if (relays.isEmpty()) {
                    Text(
                        stringResource(R.string.nip46_signer_status_no_relays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        pluralStringResource(R.plurals.nip46_signer_status_listening, relays.size, relays.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                bunkerUri?.let { uri ->
                    BunkerAddressCard(
                        uri = uri,
                        onCopy = {
                            clipboard.setText(AnnotatedString(uri))
                            Toast.makeText(context, R.string.nip46_signer_copied, Toast.LENGTH_SHORT).show()
                        },
                        onRegenerate = {
                            signer.regenerateSecret()
                            Toast.makeText(context, R.string.nip46_signer_regenerated, Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                HorizontalDivider()

                ConnectAppSection(
                    onConnect = { uri ->
                        scope.launch {
                            val result = signer.connectViaNostrConnect(uri.trim())
                            Toast.makeText(context, describe(context, result), Toast.LENGTH_SHORT).show()
                        }
                    },
                )

                OutlinedButton(
                    onClick = { nav.nav(Route.ConnectedApps) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nip46_signer_manage_apps))
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                MaterialSymbols.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(
                stringResource(R.string.nip46_signer_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EnableRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.nip46_signer_enable),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun BunkerAddressCard(
    uri: String,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.nip46_signer_bunker_uri_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                uri,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.nip46_signer_bunker_uri_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nip46_signer_copy))
                }
                FilledTonalButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nip46_signer_regenerate))
                }
            }
        }
    }
}

@Composable
private fun ConnectAppSection(onConnect: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.nip46_signer_connect_label),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.nip46_signer_connect_hint)) },
        )
        Button(
            onClick = {
                if (input.isNotBlank()) {
                    onConnect(input)
                    input = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank(),
        ) {
            Text(stringResource(R.string.nip46_signer_connect_button))
        }
    }
}

private fun describe(
    context: Context,
    result: Nip46SignerState.ConnectResult,
): String =
    when (result) {
        is Nip46SignerState.ConnectResult.Connected ->
            result.name?.let { context.getString(R.string.nip46_signer_connected_named, it) }
                ?: context.getString(R.string.nip46_signer_connected_ok)
        Nip46SignerState.ConnectResult.InvalidUri -> context.getString(R.string.nip46_signer_connect_invalid)
        Nip46SignerState.ConnectResult.NoRelays -> context.getString(R.string.nip46_signer_connect_no_relays)
        Nip46SignerState.ConnectResult.NotWriteable -> context.getString(R.string.nip46_signer_readonly)
        is Nip46SignerState.ConnectResult.Failed -> context.getString(R.string.nip46_signer_connect_failed, result.reason)
    }
