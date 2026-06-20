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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

/**
 * The capability-consent dialog, shown in the **main** process (the only place trusted to
 * make a grant). It renders the [NappletConsentInfo] for a pending request and reports the
 * user's [GrantState] back through [NappletConsentCoordinator]. The untrusted applet never
 * sees or drives this UI.
 */
class NappletConsentActivity : ComponentActivity() {
    private var token: String? = null
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(NappletConsentCoordinator.EXTRA_TOKEN)
        this.token = token
        val info = token?.let { NappletConsentCoordinator.infoFor(it) }

        if (token == null || info == null) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                NappletConsentDialog(
                    info = info,
                    onDecision = { grant ->
                        decided = true
                        NappletConsentCoordinator.complete(token, grant)
                        finish()
                    },
                    onDismiss = {
                        decided = true
                        NappletConsentCoordinator.cancel(token)
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        // If the system tears us down before the user chose, fail closed.
        if (!decided) token?.let { NappletConsentCoordinator.cancel(it) }
        super.finish()
    }
}

@Composable
private fun NappletConsentDialog(
    info: NappletConsentInfo,
    onDecision: (GrantState) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.appletTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info.operationSummary)
                Text(
                    stringResource(R.string.napplet_consent_capability, info.capabilityLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    info.coordinate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (info.allowAlways) {
                    TextButton(
                        onClick = { onDecision(GrantState.ALLOW_ALWAYS) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(stringResource(R.string.napplet_consent_allow_always)) }
                }
                TextButton(
                    onClick = { onDecision(GrantState.ALLOW_ONCE) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_consent_allow_once)) }
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onDecision(GrantState.DENY) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_consent_deny_always)) }
                TextButton(
                    onClick = { onDecision(GrantState.ASK) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_consent_not_now)) }
            }
        },
    )
}
