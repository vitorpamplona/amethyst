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
package com.vitorpamplona.amethyst.napplet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.napplet.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class NappletConnectActivity : ComponentActivity() {
    private var token: String? = null
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(NappletConnectCoordinator.EXTRA_TOKEN)
        this.token = token
        val info = token?.let { NappletConnectCoordinator.infoFor(it) }
        if (token == null || info == null) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                NappletConnectScreen(
                    info = info,
                    onConnect = { policy ->
                        decided = true
                        NappletConnectCoordinator.complete(token, AppConnectResult.Connected(policy))
                        finish()
                    },
                    onBlock = {
                        decided = true
                        NappletConnectCoordinator.complete(token, AppConnectResult.Blocked)
                        finish()
                    },
                    onCancel = {
                        decided = true
                        NappletConnectCoordinator.complete(token, AppConnectResult.Cancelled)
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        if (!decided) token?.let { NappletConnectCoordinator.cancel(it) }
        super.finish()
    }
}

@Composable
private fun NappletConnectScreen(
    info: NappletConnectInfo,
    onConnect: (AppSignerPolicy) -> Unit,
    onBlock: () -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf(AppSignerPolicy.REASONABLE) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.napplet_connect_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))

            // App card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {}
                    Column {
                        Text(info.appletTitle, style = MaterialTheme.typography.titleMedium)
                        Text(info.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.napplet_connect_how_handle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            // Trust level options
            PolicyOption(
                selected = selected == AppSignerPolicy.FULL_TRUST,
                icon = "❤",
                label = stringResource(R.string.napplet_policy_full_trust),
                description = stringResource(R.string.napplet_policy_full_trust_desc),
                onClick = { selected = AppSignerPolicy.FULL_TRUST },
            )
            Spacer(Modifier.height(8.dp))
            PolicyOption(
                selected = selected == AppSignerPolicy.REASONABLE,
                icon = "👍",
                label = stringResource(R.string.napplet_policy_reasonable),
                description = stringResource(R.string.napplet_policy_reasonable_desc),
                onClick = { selected = AppSignerPolicy.REASONABLE },
            )
            Spacer(Modifier.height(8.dp))
            PolicyOption(
                selected = selected == AppSignerPolicy.PARANOID,
                icon = "🕶",
                label = stringResource(R.string.napplet_policy_paranoid),
                description = stringResource(R.string.napplet_policy_paranoid_desc),
                onClick = { selected = AppSignerPolicy.PARANOID },
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = { onConnect(selected) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.napplet_connect_button))
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBlock) {
                Text(
                    stringResource(R.string.napplet_connect_block, info.domain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PolicyOption(
    selected: Boolean,
    icon: String,
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
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
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
