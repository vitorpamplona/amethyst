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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.CordnLinkInspection
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_link_clear
import com.vitorpamplona.amethyst.commons.resources.cordn_link_coordinator
import com.vitorpamplona.amethyst.commons.resources.cordn_link_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_link_field
import com.vitorpamplona.amethyst.commons.resources.cordn_link_group_id
import com.vitorpamplona.amethyst.commons.resources.cordn_link_inspect
import com.vitorpamplona.amethyst.commons.resources.cordn_link_invalid
import com.vitorpamplona.amethyst.commons.resources.cordn_link_no_coordinator
import com.vitorpamplona.amethyst.commons.resources.cordn_link_not_joinable
import com.vitorpamplona.amethyst.commons.resources.cordn_link_paste
import com.vitorpamplona.amethyst.commons.resources.cordn_link_relays
import com.vitorpamplona.amethyst.commons.resources.cordn_link_section_input
import com.vitorpamplona.amethyst.commons.resources.cordn_link_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * "Someone sent me a `cordn1…` link" — what it points at, and what following it
 * would cost.
 *
 * This screen exists because of §8 of `quartz/plans/2026-09-17-cordn-interop.md`:
 * a cordn group and a Marmot group look the same and are not the same, and the
 * moment the difference can still change a decision is **before** joining. A
 * link is where that moment happens, so the disclosure lives here rather than
 * in a settings sub-page nobody opens.
 *
 * It deliberately does not join anything. Joining needs a live coordinator, and
 * Tier B of the interop plan is blocked (§7) — so the honest scope is "read the
 * link, tell the truth about it" rather than a join button whose other half has
 * never been run.
 *
 * All parsing is [CordnLinkInspection] in `commons`, which has its own tests;
 * everything here is drawing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnLinkScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var input by remember { mutableStateOf("") }
    var inspection by remember { mutableStateOf<CordnLinkInspection?>(null) }
    var requestState by remember { mutableStateOf<String?>(null) }
    var requesting by remember { mutableStateOf(false) }
    val runtime = accountViewModel.account.cordnRuntime
    val scope = rememberCoroutineScope()
    val requestFailed = stringRes(R.string.cordn_link_request_failed)
    val asked = stringRes(R.string.cordn_link_request_sent)
    var scanning by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_link_title), nav) },
    ) { insets ->
        Column(
            modifier =
                Modifier
                    .padding(insets)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.cordn_link_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The field, its buttons and the scanner are one thing to do; the
            // verdict below is another. Boxing the input says where the screen
            // asks something of you and where it answers.
            SettingsSection(Res.string.cordn_link_section_input) {
                SettingsFormBlock {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            // Clearing on edit rather than re-parsing per keystroke: a
                            // half-typed ref is always invalid, and showing that while
                            // someone is still pasting is noise, not feedback.
                            inspection = null
                            requestState = null
                        },
                        label = { Text(stringResource(Res.string.cordn_link_field)) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { inspection = CordnLinkInspection.of(input) },
                            enabled = input.isNotBlank(),
                        ) {
                            Text(stringResource(Res.string.cordn_link_inspect))
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.getText()?.text?.let {
                                    input = it
                                    inspection = CordnLinkInspection.of(it)
                                }
                            },
                        ) {
                            Text(stringResource(Res.string.cordn_link_paste))
                        }
                        OutlinedButton(onClick = { scanning = true }) {
                            Text(stringRes(R.string.cordn_link_scan))
                        }
                        if (input.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    input = ""
                                    inspection = null
                                },
                            ) {
                                Text(stringResource(Res.string.cordn_link_clear))
                            }
                        }
                    }
                }
            }

            if (scanning) {
                // A cordn ref is a long bech32 string nobody types twice.
                // Reusing the app's scanner rather than writing one: the
                // ref goes through exactly the same parse as a pasted link,
                // so a scanned link cannot take a shortcut a typed one
                // cannot.
                SimpleQrCodeScanner {
                    scanning = false
                    if (!it.isNullOrEmpty()) {
                        input = it
                        inspection = CordnLinkInspection.of(it)
                        requestState = null
                    }
                }
            }

            when (val result = inspection) {
                null -> Unit

                is CordnLinkInspection.Invalid ->
                    Text(
                        text = stringResource(Res.string.cordn_link_invalid, result.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                is CordnLinkInspection.Valid -> {
                    LabelledValue(stringResource(Res.string.cordn_link_group_id), result.ref.gid)

                    result.coordinator?.let { coordinator ->
                        LabelledValue(stringResource(Res.string.cordn_link_coordinator), coordinator.pubKey)
                        LabelledValue(
                            stringResource(Res.string.cordn_link_relays),
                            coordinator.relays.joinToString("\n") { it.url },
                        )
                    }

                    result.exposure?.let { CordnExposureCard(it) }

                    if (!result.isFollowable) {
                        Text(
                            text = stringResource(Res.string.cordn_link_no_coordinator),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        // Still true, and still worth saying: reading a link
                        // is not joining, and the button below is the only
                        // thing on this screen that tells anyone anything.
                        text = stringResource(Res.string.cordn_link_not_joinable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val coordinator = result.coordinator
                    if (coordinator != null && runtime != null) {
                        Button(
                            onClick = {
                                requesting = true
                                requestState = null
                                scope.launch {
                                    requestState =
                                        try {
                                            runtime.requestToJoin(coordinator, result.ref.gid)
                                            asked
                                        } catch (e: Exception) {
                                            e.message ?: requestFailed
                                        }
                                    requesting = false
                                }
                            },
                            enabled = !requesting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringRes(R.string.cordn_link_request))
                        }

                        Text(
                            // Said before the tap, because it is the part that
                            // cannot be undone: asking to join publishes a
                            // KeyPackage under this account's own key and tells
                            // the coordinator this account wants into this
                            // group, whether or not anyone ever answers (§8.4).
                            text = stringRes(R.string.cordn_link_request_disclosure),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        requestState?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (it == asked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A field the user may need to compare against something they were sent, so it
 * is selectable and never truncated.
 */
@Composable
private fun LabelledValue(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(text = value, style = MaterialTheme.typography.bodySmall)
        }
    }
}
