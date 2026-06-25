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
import com.vitorpamplona.amethyst.commons.napplet.signers.SignerOpGrant
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class NappletSignerConsentActivity : ComponentActivity() {
    private var token: String? = null
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(NappletSignerConsentCoordinator.EXTRA_TOKEN)
        this.token = token
        val info = token?.let { NappletSignerConsentCoordinator.infoFor(it) }
        if (token == null || info == null) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                NappletSignerConsentDialog(
                    info = info,
                    onGrant = { grant ->
                        decided = true
                        NappletSignerConsentCoordinator.complete(token, grant)
                        finish()
                    },
                    onDismiss = {
                        decided = true
                        NappletSignerConsentCoordinator.cancel(token)
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        if (!decided) token?.let { NappletSignerConsentCoordinator.cancel(it) }
        super.finish()
    }
}

@Composable
private fun NappletSignerConsentDialog(
    info: NappletSignerConsentInfo,
    onGrant: (SignerOpGrant) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.appletTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info.operationSummary)
                if (info.contentPreview.isNotBlank()) {
                    Text(
                        "“${info.contentPreview}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    info.coordinate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onGrant(SignerOpGrant.AllowOnce) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_signer_allow_once)) }
                TextButton(
                    onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_signer_allow_op, info.operationSummary)) }
                TextButton(
                    onClick = { onGrant(SignerOpGrant.AllowAll) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_signer_allow_all)) }
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onGrant(SignerOpGrant.DenyOnce) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_signer_deny_once)) }
                TextButton(
                    onClick = { onGrant(SignerOpGrant.DenyForOp(info.op)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                ) { Text(stringResource(R.string.napplet_signer_deny_op, info.operationSummary)) }
            }
        },
    )
}
