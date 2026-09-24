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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.CoordinatorHealth
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorServerInfo
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.launch

/**
 * The coordinators this account talks to.
 *
 * ## Why a coordinator is a first-class thing and not a relay setting
 *
 * Relays are interchangeable and redundant; losing one costs nothing. A
 * coordinator is the sole authority for the groups it serves (`spec/00.md`
 * §4) — losing it loses the ordering, and a second one does not mirror the
 * first. So this screen lists named things a user chose, with what each one
 * costs and two very different ways to stop using one.
 *
 * ## Remove and purge are different decisions
 *
 * Removing closes the session and leaves the MLS state on disk, so re-adding
 * brings the groups back. Purging deletes the ratchet trees, the cursors and
 * the KeyPackage private halves, after which those groups can only be
 * re-entered by a fresh invitation — MLS state cannot be rebuilt from
 * anywhere else. The confirmation says that, because "delete" normally means
 * the reversible one.
 *
 * Neither tells the coordinator anything. It keeps what it already had; this
 * screen is about the device, and implying otherwise would be selling a
 * deletion nobody can perform.
 */
@Composable
fun CordnCoordinatorsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_coordinators_title), nav) },
    ) { padding ->
        if (runtime == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringRes(R.string.cordn_group_unavailable))
            }
            return@Scaffold
        }

        val coordinators by runtime.coordinators.collectAsStateWithLifecycle()

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(R.string.cordn_coordinators_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            coordinators.forEach { config ->
                CoordinatorCard(config, runtime)
            }

            if (coordinators.isEmpty()) {
                Text(
                    text = stringRes(R.string.cordn_coordinators_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            AddCoordinator(runtime)
        }
    }
}

@Composable
private fun CoordinatorCard(
    config: CoordinatorConfig,
    runtime: CordnRuntime,
) {
    val scope = rememberCoroutineScope()
    var info by remember(config.pubKey) { mutableStateOf<CoordinatorServerInfo?>(null) }
    var infoChecked by remember(config.pubKey) { mutableStateOf(false) }
    var confirmingPurge by remember(config.pubKey) { mutableStateOf(false) }

    val health = runtime.health(config.pubKey)?.collectAsStateWithLifecycle()?.value

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = config.label ?: stringRes(R.string.cordn_coordinators_unnamed),
                style = MaterialTheme.typography.titleMedium,
            )
            SelectionContainer {
                Text(config.pubKey, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = config.relays.joinToString("\n") { it.url },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            health?.let { HealthLine(it) }

            // Only ever what a call of ours already observed — nothing here
            // polls. The handshake below is a call the user asked for.
            if (infoChecked) {
                Text(
                    text =
                        info?.let {
                            stringRes(
                                R.string.cordn_coordinators_server,
                                listOfNotNull(it.name, it.version, it.protocolVersion).joinToString(" · "),
                            )
                        } ?: stringRes(R.string.cordn_coordinators_server_silent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // Said next to it, every time, because a name on a settings
                    // screen reads as verified and this one is not: §8.5 makes
                    // the pubkey the identity and nothing else.
                    text = stringRes(R.string.cordn_coordinators_server_claim),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = {
                    scope.launch {
                        info = runCatching { runtime.serverInfo(config.pubKey) }.getOrNull()
                        infoChecked = true
                    }
                }) {
                    Text(stringRes(R.string.cordn_coordinators_identify))
                }
                TextButton(onClick = { scope.launch { runtime.forget(config.pubKey) } }) {
                    Text(stringRes(R.string.cordn_coordinators_remove))
                }
                TextButton(onClick = { confirmingPurge = true }) {
                    Text(stringRes(R.string.cordn_coordinators_purge), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmingPurge) {
        AlertDialog(
            onDismissRequest = { confirmingPurge = false },
            title = { Text(stringRes(R.string.cordn_coordinators_purge_title)) },
            text = { Text(stringRes(R.string.cordn_coordinators_purge_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingPurge = false
                    scope.launch { runtime.purge(config.pubKey) }
                }) {
                    Text(stringRes(R.string.cordn_coordinators_purge), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPurge = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * What this coordinator's own traffic has shown, and nothing more.
 *
 * `CoordinatorHealth` records what the calls the app was making anyway
 * observed; it never polls, because a poll is a call and every call is
 * metadata (§8). So "unknown" here means nothing has been asked of it yet,
 * which is genuinely different from "down" and is shown differently.
 */
@Composable
private fun HealthLine(state: CoordinatorHealth.State) {
    val text =
        when {
            state.isUnknown -> stringRes(R.string.cordn_coordinators_health_unknown)
            state.isDown -> stringRes(R.string.cordn_coordinators_health_down, state.consecutiveFailures)
            state.consecutiveFailures > 0 -> stringRes(R.string.cordn_coordinators_health_retrying)
            else -> stringRes(R.string.cordn_coordinators_health_ok)
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.isDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddCoordinator(runtime: CordnRuntime) {
    val scope = rememberCoroutineScope()
    var pubKeyInput by remember { mutableStateOf("") }
    var relaysInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val failed = stringRes(R.string.cordn_coordinators_add_failed)

    val config =
        remember(pubKeyInput, relaysInput, labelInput) {
            val pubKey = decodePublicKeyAsHexOrNull(pubKeyInput.trim())
            val relays = relaysInput.lines().mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
            if (pubKey == null || relays.isEmpty()) {
                null
            } else {
                CoordinatorConfig(pubKey, relays, CoordinatorConfig.Origin.MANUAL, labelInput.trim().ifEmpty { null })
            }
        }

    Text(stringRes(R.string.cordn_coordinators_add), style = MaterialTheme.typography.titleSmall)

    OutlinedTextField(
        value = pubKeyInput,
        onValueChange = {
            pubKeyInput = it
            error = null
        },
        label = { Text(stringRes(R.string.cordn_create_coordinator_pubkey)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = relaysInput,
        onValueChange = {
            relaysInput = it
            error = null
        },
        label = { Text(stringRes(R.string.cordn_create_coordinator_relays)) },
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = labelInput,
        onValueChange = { labelInput = it },
        label = { Text(stringRes(R.string.cordn_coordinators_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        // A label is ours, never theirs. A coordinator cannot prove a name and
        // this one is not asked for one.
        text = stringRes(R.string.cordn_coordinators_label_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = {
            val target = config ?: return@Button
            busy = true
            error = null
            scope.launch {
                try {
                    runtime.session(target)
                    pubKeyInput = ""
                    relaysInput = ""
                    labelInput = ""
                } catch (e: Exception) {
                    error = e.message ?: failed
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && config != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringRes(R.string.cordn_coordinators_add_action))
    }
}
