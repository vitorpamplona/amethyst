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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var showRawData by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = onDismiss,
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
                        .verticalScroll(scrollState)
                        .padding(vertical = 24.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        info.appletTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.napplet_consent_wants_to, info.operationSummary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val hasContent = info.contentPreview.isNotBlank() || info.rawData.isNotBlank()
                if (hasContent) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier =
                            Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (info.contentPreview.isNotBlank()) {
                                Text(
                                    "“${info.contentPreview}”",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (info.rawData.isNotBlank()) {
                                if (showRawData) {
                                    Spacer(Modifier.height(8.dp))
                                    SelectionContainer {
                                        Text(
                                            info.rawData,
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { showRawData = !showRawData },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        if (showRawData) {
                                            stringResource(R.string.napplet_consent_see_less)
                                        } else {
                                            stringResource(R.string.napplet_consent_see_more)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    info.coordinate,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                ConsentActionButton(
                    text = stringResource(R.string.napplet_signer_allow_once),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onGrant(SignerOpGrant.AllowOnce) },
                )
                ConsentActionButton(
                    text = stringResource(R.string.napplet_signer_allow_op, info.operationSummary),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                )
                ConsentActionButton(
                    text = stringResource(R.string.napplet_signer_allow_all),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onGrant(SignerOpGrant.AllowAll) },
                )

                HorizontalDivider()

                ConsentActionButton(
                    text = stringResource(R.string.napplet_signer_deny_once),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onGrant(SignerOpGrant.DenyOnce) },
                )
                ConsentActionButton(
                    text = stringResource(R.string.napplet_signer_deny_op, info.operationSummary),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onGrant(SignerOpGrant.DenyForOp(info.op)) },
                )
            }
        }
    }
}

@Composable
private fun ConsentActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text,
            color = color,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}
