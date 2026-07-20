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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
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
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
            ) {
                // Centered header: icon + app name + capability category + coordinate
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
                        info.capabilityLabel,
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

                // Operation detail box (may include content preview), plus the full event behind a
                // toggle: the summary truncates content and cannot spell out every tag, so for kinds
                // whose payload IS the tags (3, 5, 10000, 10002) this is the only complete disclosure.
                if (info.operationSummary.isNotBlank() || info.rawData.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (info.operationSummary.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        info.operationSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            // A single-account follow/mute change: show who, so the user recognizes
                            // the face rather than parsing a name they may not read carefully.
                            info.subject?.let { subject ->
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RobohashFallbackAsyncImage(
                                        robot = subject.pubKey,
                                        model = subject.pictureUrl,
                                        contentDescription = subject.name,
                                        modifier = Modifier.size(36.dp).clip(CircleShape),
                                        loadProfilePicture = true,
                                        loadRobohash = true,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        subject.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (info.rawData.isNotBlank()) {
                                var showRawData by remember { mutableStateOf(false) }
                                if (showRawData) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(modifier = Modifier.horizontalScroll(rememberScrollState())) {
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
                                TextButton(onClick = { showRawData = !showRawData }) {
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

                if (info.allowAlways) {
                    Button(
                        onClick = { onDecision(GrantState.ALLOW_ALWAYS) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_consent_allow_always))
                    }
                    OutlinedButton(
                        onClick = { onDecision(GrantState.ALLOW_ONCE) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_consent_allow_once))
                    }
                } else {
                    Button(
                        onClick = { onDecision(GrantState.ALLOW_ONCE) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_consent_allow_once))
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onDecision(GrantState.ASK) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text(
                        stringResource(R.string.napplet_consent_not_now),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(
                    onClick = { onDecision(GrantState.DENY) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        stringResource(R.string.napplet_consent_deny_always),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
