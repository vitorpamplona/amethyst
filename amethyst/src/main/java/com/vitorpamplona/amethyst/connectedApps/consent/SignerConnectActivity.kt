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
package com.vitorpamplona.amethyst.connectedApps.consent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class SignerConnectActivity : ComponentActivity() {
    private var token: String? = null
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(SignerConnectCoordinator.EXTRA_TOKEN)
        this.token = token
        val info = token?.let { SignerConnectCoordinator.infoFor(it) }
        if (token == null || info == null) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                SignerConnectScreen(
                    info = info,
                    onConnect = { policy ->
                        decided = true
                        SignerConnectCoordinator.complete(token, AppConnectResult.Connected(policy))
                        finish()
                    },
                    onBlock = {
                        decided = true
                        SignerConnectCoordinator.complete(token, AppConnectResult.Blocked)
                        finish()
                    },
                    onCancel = {
                        decided = true
                        SignerConnectCoordinator.complete(token, AppConnectResult.Cancelled)
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        if (!decided) token?.let { SignerConnectCoordinator.cancel(it) }
        super.finish()
    }
}

@Composable
private fun SignerConnectScreen(
    info: SignerConnectInfo,
    onConnect: (AppSignerPolicy) -> Unit,
    onBlock: () -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf(AppSignerPolicy.REASONABLE) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
            ) {
                // Centered header: icon + app name + connect subtitle
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isBrowser = info.coordinate.startsWith("browser:")
                    FavoriteAppIcon(
                        app =
                            if (isBrowser) {
                                FavoriteApp.WebApp(info.coordinate.substringAfter(':'), info.appletTitle, 0L, info.iconUrl)
                            } else {
                                FavoriteApp.NostrApp(info.coordinate, info.appletTitle, 0L, info.iconUrl)
                            },
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        info.appletTitle,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.napplet_connect_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    // Show WHICH account is being connected (avatar + name), not a raw pubkey.
                    if (info.accountName != null) {
                        ConnectedAccountRow(info.accountName, info.accountPicture, info.accountPubKey)
                    } else {
                        Text(
                            info.domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.napplet_connect_how_handle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))

                // Trust level options
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PolicyOption(
                        selected = selected == AppSignerPolicy.FULL_TRUST,
                        symbol = MaterialSymbols.LockOpen,
                        label = stringResource(R.string.napplet_policy_full_trust),
                        description = stringResource(R.string.napplet_policy_full_trust_desc),
                        onClick = { selected = AppSignerPolicy.FULL_TRUST },
                    )
                    PolicyOption(
                        selected = selected == AppSignerPolicy.REASONABLE,
                        symbol = MaterialSymbols.Shield,
                        label = stringResource(R.string.napplet_policy_reasonable),
                        description = stringResource(R.string.napplet_policy_reasonable_desc),
                        onClick = { selected = AppSignerPolicy.REASONABLE },
                    )
                    PolicyOption(
                        selected = selected == AppSignerPolicy.PARANOID,
                        symbol = MaterialSymbols.Lock,
                        label = stringResource(R.string.napplet_policy_paranoid),
                        description = stringResource(R.string.napplet_policy_paranoid_desc),
                        onClick = { selected = AppSignerPolicy.PARANOID },
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = { onConnect(selected) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.napplet_connect_button))
                    }
                }

                OutlinedButton(
                    onClick = onBlock,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        stringResource(R.string.napplet_connect_block, info.domain),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyOption(
    selected: Boolean,
    symbol: MaterialSymbol,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
