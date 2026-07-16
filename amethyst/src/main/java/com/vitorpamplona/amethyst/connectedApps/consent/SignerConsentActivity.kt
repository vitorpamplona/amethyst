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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.utils.TimeUtils

class SignerConsentActivity : ComponentActivity() {
    private var token: String? = null
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(SignerConsentCoordinator.EXTRA_TOKEN)
        this.token = token
        val info = token?.let { SignerConsentCoordinator.infoFor(it) }
        if (token == null || info == null) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                SignerConsentDialog(
                    info = info,
                    onGrant = { grant ->
                        decided = true
                        SignerConsentCoordinator.complete(token, grant)
                        finish()
                    },
                    onDismiss = {
                        decided = true
                        SignerConsentCoordinator.cancel(token)
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        if (!decided) token?.let { SignerConsentCoordinator.cancel(it) }
        super.finish()
    }
}

@Composable
private fun SignerConsentDialog(
    info: SignerConsentInfo,
    onGrant: (SignerOpGrant) -> Unit,
    onDismiss: () -> Unit,
) {
    var showRawData by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
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
                // Centered header: icon + title + description
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
                        stringResource(R.string.napplet_consent_wants_to, info.operationSummary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        info.coordinate.substringAfter(':', "").ifBlank { info.coordinate.substringBefore(':').take(12) + "…" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
                                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        SelectionContainer {
                                            Text(
                                                info.rawData,
                                                style =
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { showRawData = !showRawData },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        if (showRawData) {
                                            stringResource(R.string.napplet_consent_hide_event)
                                        } else {
                                            stringResource(R.string.napplet_consent_show_event)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Primary: always allow this op
                Button(
                    onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text(stringResource(R.string.napplet_consent_allow_always))
                }

                // Secondary: allow just once
                OutlinedButton(
                    onClick = { onGrant(SignerOpGrant.AllowOnce) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text(stringResource(R.string.napplet_signer_allow_once))
                }

                // "More options" toggle: session and time-bound grants
                TextButton(
                    onClick = { showMoreOptions = !showMoreOptions },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (showMoreOptions) {
                                stringResource(R.string.napplet_consent_fewer_options)
                            } else {
                                stringResource(R.string.napplet_consent_more_options)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Icon(
                            if (showMoreOptions) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (showMoreOptions) {
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowForSession(info.op)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_session))
                    }
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowUntil(info.op, TimeUtils.now() + 86_400L)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_24h))
                    }
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowUntil(info.op, TimeUtils.now() + 30L * 86_400L)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_30d))
                    }
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowAll) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_all))
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onGrant(SignerOpGrant.DenyOnce) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.napplet_signer_deny_once))
                }
                OutlinedButton(
                    onClick = { onGrant(SignerOpGrant.DenyForOp(info.op)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.napplet_signer_deny_op, info.operationSummary))
                }
            }
        }
    }
}
