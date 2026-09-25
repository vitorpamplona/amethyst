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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorDiscovery
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
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
                        Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                    }
                },
                title = { Text(stringRes(R.string.cordn_create_title)) },
            )
        },
    ) { padding ->
        if (runtime == null) {
            // The app's own empty state, centred and titled, rather than a
            // sentence stranded in the top-left corner.
            EmptyState(
                title = stringRes(R.string.cordn_group_unavailable),
                description = stringRes(R.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val known by runtime.coordinators.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        var selected by remember { mutableStateOf<String?>(null) }
        var adminOnlyMe by remember { mutableStateOf(false) }
        var pubKeyInput by remember { mutableStateOf("") }
        var relaysInput by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val failureFallback = stringRes(R.string.cordn_create_failed)
        var busy by remember { mutableStateOf(false) }

        // Coordinators this account has never used, from their CEP-6
        // announcements. Not added to the account by looking: picking one here
        // is what commits to it, and `createGroup` opens the session.
        var discovered by remember { mutableStateOf<CordnCoordinatorDiscovery.Result?>(null) }
        var discovering by remember { mutableStateOf(false) }
        val discoverFailed = stringRes(R.string.cordn_coordinators_discover_failed)

        // Offers this account already holds are not offers; they are the choices
        // above, and listing them twice would let the same coordinator be picked
        // from two places with different relays.
        val offers =
            remember(discovered, known) {
                val knownKeys = known.mapTo(mutableSetOf()) { it.pubKey }
                discovered?.coordinators?.filter { it.pubKey !in knownKeys }.orEmpty()
            }

        val choices = remember(known, offers) { known + offers.map { it.toConfig() } }

        // A re-discovery can drop the coordinator that was picked -- it stopped
        // announcing, or it is now in `known` and therefore not an offer. Left
        // alone, `selected` would name a coordinator no row shows: no radio
        // checked, no exposure card, Create disabled, and nothing saying why.
        // Falling back to the manual entry is the one state that explains itself,
        // because its fields appear.
        LaunchedEffect(choices) {
            if (selected != null && choices.none { it.pubKey == selected }) selected = null
        }

        val config = remember(selected, pubKeyInput, relaysInput, choices) { resolve(choices, selected, pubKeyInput, relaysInput) }

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

            offers.forEach { offer ->
                CoordinatorChoice(
                    // Its own word for itself, and only that: nothing here has
                    // verified the name a coordinator announces.
                    label = offer.surface.name?.takeIf { it.isNotBlank() } ?: offer.pubKey.take(16),
                    selected = selected == offer.pubKey,
                    onSelect = { selected = offer.pubKey },
                    // Staleness decides whether this choice can work at all: a
                    // coordinator that announced itself two years ago and went
                    // away takes the group creation down with it.
                    detail = stringRes(R.string.cordn_coordinators_discover_seen, timeAgoNoDot(offer.announcedAt).trim()),
                )
            }

            CoordinatorChoice(
                label = stringRes(R.string.cordn_create_coordinator_new),
                selected = selected == null,
                onSelect = { selected = null },
            )

            // Asks relays, never a coordinator: an announcement is an ordinary
            // event, so looking costs nothing with any of them and tells none
            // of them anything.
            OutlinedButton(
                onClick = {
                    discovering = true
                    error = null
                    scope.launch {
                        try {
                            discovered = runtime.discover(accountViewModel.account.outboxRelays.flow.value)
                        } catch (e: Exception) {
                            error = e.message ?: discoverFailed
                        } finally {
                            discovering = false
                        }
                    }
                },
                enabled = !discovering && !busy,
            ) {
                Text(stringRes(R.string.cordn_create_coordinator_discover))
            }

            discovered?.takeIf { offers.isEmpty() }?.let { result ->
                Text(
                    text =
                        if (result.unreachable.isEmpty()) {
                            stringRes(R.string.cordn_coordinators_discover_none)
                        } else {
                            // "Nobody is announcing" and "we were not told" are
                            // different answers and only one of them is final.
                            // Reporting the first for the second sends someone
                            // off to paste a pubkey by hand over a timeout.
                            stringRes(R.string.cordn_coordinators_discover_unheard, result.unreachable.size)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

            // spec/01.md §5.3: leaving the admin list empty is not "set it up
            // later", it is choosing egalitarian permanently — so it is offered
            // as a decision here rather than described as one.
            //
            // The web client picks admins out of the members being added. This
            // screen adds nobody — a cordn group starts with its creator and
            // invites follow — so at this moment the only admin there could be
            // is you, and the choice collapses to one switch. The same rule the
            // web client states holds either way: choosing any admin at all
            // must include yourself, or the group is born unadministrable.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(R.string.cordn_create_admin_only_me), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text =
                            if (adminOnlyMe) {
                                stringRes(R.string.cordn_create_admin_only_me_on)
                            } else {
                                stringRes(R.string.cordn_create_egalitarian_note)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = adminOnlyMe, onCheckedChange = { adminOnlyMe = it })
            }

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
                                    metadata =
                                        CordnGroupMetadata(
                                            name = name.trim(),
                                            description = description.trim(),
                                            adminPubkeys = if (adminOnlyMe) listOf(accountViewModel.account.signer.pubKey) else emptyList(),
                                        ),
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
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
