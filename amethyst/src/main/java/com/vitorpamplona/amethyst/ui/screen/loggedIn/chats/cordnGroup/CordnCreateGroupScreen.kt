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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.launch

/**
 * Starting a cordn group: pick the coordinator, name it, create it.
 *
 * ## The coordinator is the first field, not a setting
 *
 * `spec/00.md` §4 makes the coordinator the sole authority for a group's
 * stream, and a `gid` means nothing outside the one that issued it. So
 * "which coordinator" is not a preference that could sensibly default — it is
 * half of the group's identity, chosen once and unchangeable afterwards. It is
 * asked first, in the open, with the exposure card underneath saying what
 * choosing it costs.
 *
 * ## Creating tells the coordinator nothing
 *
 * `CordnRuntime.createGroup` is local MLS work. The coordinator hears about
 * the group when the first Commit or message is posted, which is why a group
 * of one is still entirely private and why this screen works against a
 * coordinator that is down. The exposure card is therefore about what will
 * become true once someone is invited, not about what just happened.
 *
 * Inviting is not here. `CordnGroupManager.invite` exists and works, but its
 * user-facing half — key-package discovery, share links, join requests — is
 * Stage C of `amethyst/plans/2026-09-19-cordn-ui.md`. A member picker that
 * could only offer accounts that happen to have published a KeyPackage to this
 * exact coordinator would promise more than it can do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnCreateGroupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
                title = { Text(stringRes(R.string.cordn_create_title)) },
            )
        },
    ) { padding ->
        if (runtime == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringRes(R.string.cordn_group_unavailable))
            }
            return@Scaffold
        }

        val known by runtime.coordinators.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        var selected by remember { mutableStateOf<String?>(null) }
        var pubKeyInput by remember { mutableStateOf("") }
        var relaysInput by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val failureFallback = stringRes(R.string.cordn_create_failed)
        var busy by remember { mutableStateOf(false) }

        val config = remember(selected, pubKeyInput, relaysInput, known) { resolve(known, selected, pubKeyInput, relaysInput) }

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(R.string.cordn_create_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringRes(R.string.cordn_create_coordinator),
                style = MaterialTheme.typography.titleSmall,
            )

            known.forEach { coordinator ->
                CoordinatorChoice(
                    label = coordinator.label ?: coordinator.pubKey.take(16),
                    selected = selected == coordinator.pubKey,
                    onSelect = { selected = coordinator.pubKey },
                )
            }

            CoordinatorChoice(
                label = stringRes(R.string.cordn_create_coordinator_new),
                selected = selected == null,
                onSelect = { selected = null },
            )

            if (selected == null) {
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
                    // A coordinator has no address beyond its pubkey (§8.5), so
                    // the relays are how it is reached and more than one is
                    // ordinary. One per line rather than comma-separated,
                    // because a relay URL can contain a comma and a newline
                    // cannot be mistyped into one.
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringRes(R.string.cordn_create_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringRes(R.string.cordn_create_description)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                // spec/01.md §5.3: leaving the admin list empty is not "set it
                // up later", it is choosing egalitarian permanently. Saying so
                // here is the only moment it is still a decision.
                text = stringRes(R.string.cordn_create_egalitarian_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            config?.let {
                CordnExposureCard(
                    GroupExposure(
                        coordinator = it.pubKey,
                        // This group, plus whatever this coordinator already
                        // serves for this account: the disclosure is about what
                        // one coordinator can correlate, so a second group on
                        // the same one widens it.
                        linkedGroupCount =
                            1 +
                                runtime.groups.all.value
                                    .count { room -> room.coordinatorPubKey == it.pubKey },
                        joinedFromShareLink = false,
                        publishedKeyPackage = false,
                        encryptionPinned = true,
                    ),
                )
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val target = config ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val gid =
                                runtime.createGroup(
                                    config = target,
                                    metadata = CordnGroupMetadata(name = name.trim(), description = description.trim()),
                                )
                            nav.nav(Route.CordnGroupChat(target.pubKey, gid))
                        } catch (e: Exception) {
                            error = e.message ?: failureFallback
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && config != null && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.cordn_create_action))
            }
        }
    }
}

@Composable
private fun CoordinatorChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The coordinator this screen would create against, or null while the form
 * cannot name one.
 *
 * Null rather than a thrown error: an unfinished form is the normal state of a
 * form, and the button is disabled off this rather than the user being told
 * they are wrong while they type. `CoordinatorConfig` itself rejects a bad
 * pubkey or an empty relay list in its `init`, so the checks here are only
 * about not constructing one yet.
 */
private fun resolve(
    known: List<CoordinatorConfig>,
    selected: String?,
    pubKeyInput: String,
    relaysInput: String,
): CoordinatorConfig? {
    if (selected != null) return known.firstOrNull { it.pubKey == selected }

    // npub as well as hex: a coordinator pubkey is shared the same ways any
    // other Nostr pubkey is, and refusing the bech32 form would just move the
    // conversion to the user.
    val pubKey = decodePublicKeyAsHexOrNull(pubKeyInput.trim()) ?: return null
    val relays = relaysInput.lines().mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
    if (relays.isEmpty()) return null

    return CoordinatorConfig(pubKey, relays, CoordinatorConfig.Origin.MANUAL)
}
