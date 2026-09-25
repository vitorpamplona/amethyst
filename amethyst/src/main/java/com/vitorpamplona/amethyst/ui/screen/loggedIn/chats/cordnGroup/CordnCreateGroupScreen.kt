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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorDiscovery
import com.vitorpamplona.amethyst.commons.cordn.DiscoveredCoordinator
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.model.cordn.CordnCoverage
import com.vitorpamplona.amethyst.model.cordn.CordnGroupCreation
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.cordn.CoordinatorIdentityRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.cordn.coordinatorDisplayName
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.TimeUtils
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

        val me = accountViewModel.account.signer.pubKey

        var selected by remember { mutableStateOf<String?>(null) }

        // Who the group is FOR, named before the coordinator is chosen: the
        // roster is what turns "which coordinator do I trust" into "which one
        // can reach these people", which is a question with an answer.
        var roster by remember { mutableStateOf<List<HexKey>>(emptyList()) }
        var coAdmins by remember { mutableStateOf<Set<HexKey>>(emptySet()) }
        var egalitarian by remember { mutableStateOf(false) }
        var coordinatorOpen by remember { mutableStateOf(false) }
        var userPicked by remember { mutableStateOf(false) }
        var creation by remember { mutableStateOf<CordnGroupCreation?>(null) }
        var memberSearch by remember { mutableStateOf("") }
        val userSuggestions = remember { UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder()) }
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
        var showStale by remember { mutableStateOf(false) }
        var showAllLive by remember { mutableStateOf(false) }
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

        // Asked only of coordinators this account already has a session with.
        // Probing a discovery offer would mean opening one, and opening one is
        // what commits to it -- so an offer stays uncovered until it is picked.
        val coverage by
            produceState<Map<HexKey, CordnCoverage>>(emptyMap(), runtime, known, roster) {
                value =
                    if (roster.isEmpty()) {
                        emptyMap()
                    } else {
                        val target = roster.toSet()
                        known.associate { it.pubKey to runtime.coverage(it.pubKey, target) }
                    }
            }

        // The best-covering coordinator is offered, not imposed: the moment the
        // user picks one themselves it stops moving under them, even if the
        // roster later changes and a different one would now reach more people.
        LaunchedEffect(coverage, userPicked) {
            if (userPicked || coverage.isEmpty()) return@LaunchedEffect
            val best = coverage.values.filter { it.answered }.maxByOrNull { it.reachable.size }
            if (best != null && best.reachable.isNotEmpty()) selected = best.coordinatorPubKey
        }

        val chosenCoverage = selected?.let { coverage[it] }

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

            StepHeading(1, stringRes(R.string.cordn_create_step_name))

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

            StepHeading(2, stringRes(R.string.cordn_create_step_people))

            CordnRoster(
                roster = roster,
                coAdmins = coAdmins,
                egalitarian = egalitarian,
                coverage = coverage,
                userSuggestions = userSuggestions,
                search = memberSearch,
                onSearchChange = {
                    memberSearch = it
                    if (it.length > 2) userSuggestions.processCurrentWord(it) else userSuggestions.reset()
                },
                onAdd = { user ->
                    memberSearch = ""
                    userSuggestions.reset()
                    if (user.pubkeyHex != me && user.pubkeyHex !in roster) roster = roster + user.pubkeyHex
                },
                onRemove = { pubKey ->
                    roster = roster - pubKey
                    coAdmins = coAdmins - pubKey
                },
                onToggleAdmin = { pubKey ->
                    coAdmins = if (pubKey in coAdmins) coAdmins - pubKey else coAdmins + pubKey
                },
                busy = busy,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            StepHeading(3, stringRes(R.string.cordn_create_step_where))

            // Collapsed to the one coordinator that will be used, because with a
            // roster in hand there is usually nothing left to decide. The whole
            // list is still one tap away for the times there is.
            CoordinatorSummary(
                config = config,
                coverage = chosenCoverage,
                rosterSize = roster.size,
                expanded = coordinatorOpen,
                onToggle = { coordinatorOpen = !coordinatorOpen },
                accountViewModel = accountViewModel,
                nav = nav,
            )

            if (coordinatorOpen) {
                known.forEach { coordinator ->
                    CoordinatorChoice(
                        label = coordinatorDisplayName(coordinator.pubKey, coordinator.label, accountViewModel),
                        pubKey = coordinator.pubKey,
                        selected = selected == coordinator.pubKey,
                        onSelect = {
                            selected = coordinator.pubKey
                            userPicked = true
                        },
                        relays = relayLabel(coordinator.relays),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                // Split on staleness rather than listing everything flat. A run
                // against a public relay returns a long tail of coordinators that
                // last announced months ago, and creating a group on one that has
                // gone away fails at the first call -- so the live ones come first
                // and the rest sit behind a count.
                val cutoff = TimeUtils.now() - TimeUtils.ONE_MONTH
                val live = offers.filter { it.announcedAt >= cutoff }
                val stale = offers.filter { it.announcedAt < cutoff }
                val names = offers.map { resolvedName(it, accountViewModel) }

                // Bounded rather than lazy. The obvious fix for a long list is a
                // LazyColumn, and it is the wrong one here: this screen is a form,
                // and a lazy list disposes what scrolls off it -- which for the
                // text fields below would throw away focus and IME state mid-typing.
                // Capping the rows keeps composition bounded without putting a form
                // inside a recycler.
                val shownLive = if (showAllLive) live else live.take(LIVE_PREVIEW)

                shownLive.forEach { offer ->
                    CoordinatorChoice(
                        // Its own word for itself, and only that: nothing here has
                        // verified the name a coordinator announces.
                        label = disambiguate(resolvedName(offer, accountViewModel), offer.pubKey, names),
                        pubKey = offer.pubKey,
                        selected = selected == offer.pubKey,
                        onSelect = {
                            selected = offer.pubKey
                            userPicked = true
                        },
                        detail = announcedLabel(offer.announcedAt),
                        relays = relayLabel(offer.relays),
                        about = offer.surface.about?.takeIf { it.isNotBlank() },
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                if (live.size > LIVE_PREVIEW) {
                    TextButton(onClick = { showAllLive = !showAllLive }) {
                        Text(
                            if (showAllLive) {
                                stringRes(R.string.cordn_coordinators_show_fewer)
                            } else {
                                stringRes(R.string.cordn_coordinators_show_all, live.size)
                            },
                        )
                    }
                }

                if (stale.isNotEmpty()) {
                    TextButton(onClick = { showStale = !showStale }) {
                        Text(
                            if (showStale) {
                                stringRes(R.string.cordn_coordinators_hide_older)
                            } else {
                                stringRes(R.string.cordn_coordinators_show_older, stale.size)
                            },
                        )
                    }
                }

                if (showStale && stale.isNotEmpty()) {
                    Text(
                        text = stringRes(R.string.cordn_coordinators_stale_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stale.forEach { offer ->
                        CoordinatorChoice(
                            label = disambiguate(resolvedName(offer, accountViewModel), offer.pubKey, names),
                            pubKey = offer.pubKey,
                            selected = selected == offer.pubKey,
                            onSelect = {
                                selected = offer.pubKey
                                userPicked = true
                            },
                            detail = announcedLabel(offer.announcedAt),
                            relays = relayLabel(offer.relays),
                            about = offer.surface.about?.takeIf { it.isNotBlank() },
                            dimmed = true,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                CoordinatorChoice(
                    label = stringRes(R.string.cordn_create_coordinator_new),
                    pubKey = null,
                    selected = selected == null,
                    onSelect = {
                        selected = null
                        userPicked = true
                    },
                    accountViewModel = accountViewModel,
                    nav = nav,
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
            }

            StepHeading(4, stringRes(R.string.cordn_create_step_admins))

            // spec/01.md §5.3: leaving the admin list empty is not "set it up
            // later", it is choosing egalitarian permanently -- so it is offered
            // as a decision here rather than described as one.
            //
            // With a roster in hand the choice is the real one the web client
            // makes: which of the people being added can add and remove others.
            // Choosing anybody at all must include yourself, or the group is
            // born unadministrable, so "you" is never one of the toggles.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(R.string.cordn_create_egalitarian), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text =
                            if (egalitarian) {
                                stringRes(R.string.cordn_create_egalitarian_note)
                            } else if (coAdmins.isEmpty()) {
                                stringRes(R.string.cordn_create_admin_only_me_on)
                            } else {
                                pluralStringRes(LocalContext.current, R.plurals.cordn_create_admin_count, coAdmins.size + 1, coAdmins.size + 1)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = egalitarian, onCheckedChange = { egalitarian = it })
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
                            // Only the people this coordinator can reach are
                            // attempted. An invitation is a Welcome left against
                            // a KeyPackage the invitee published *here*, so
                            // asking for one it does not hold is a call that can
                            // only fail -- and the roster keeps them either way,
                            // for the link the outcome offers.
                            val reachable = chosenCoverage?.reachable
                            val invitees = roster.filter { reachable == null || it in reachable }
                            val result =
                                runtime.createGroupAndInvite(
                                    config = target,
                                    metadata =
                                        CordnGroupMetadata(
                                            name = name.trim(),
                                            description = description.trim(),
                                            adminPubkeys = if (egalitarian) emptyList() else listOf(me) + coAdmins.toList(),
                                        ),
                                    invitees = invitees,
                                )
                            // Straight through when there is nothing to report:
                            // a dialog that only ever says "all five went out"
                            // is one nobody reads the sixth time.
                            if (result.failed.isEmpty() && invitees.size == roster.size) {
                                nav.nav(Route.CordnGroupChat(target.pubKey, result.gid))
                            } else {
                                creation = result
                            }
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
                Text(
                    if (roster.isEmpty()) {
                        stringRes(R.string.cordn_create_action)
                    } else {
                        pluralStringRes(LocalContext.current, R.plurals.cordn_create_action_invite, roster.size, roster.size)
                    },
                )
            }
        }

        creation?.let { done ->
            CordnCreationOutcome(
                creation = done,
                roster = roster,
                coordinatorPubKey = config?.pubKey,
                accountViewModel = accountViewModel,
                nav = nav,
                onDismiss = { creation = null },
            )
        }
    }
}

@Composable
private fun CoordinatorChoice(
    label: String,
    pubKey: HexKey?,
    selected: Boolean,
    onSelect: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
    detail: String? = null,
    /** Where it answers. A coordinator has no address beyond its pubkey (§8.5). */
    relays: String? = null,
    /** Its own sentence about itself, if it published one. */
    about: String? = null,
    /** Dimmed for a coordinator that stopped announcing long ago. */
    dimmed: Boolean = false,
) {
    val fade = if (dimmed) 0.6f else 1f
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        // Null on the "use a different one" row, which names no coordinator
        // yet, so there is no identity to show for it.
        if (pubKey != null) {
            UserPicture(
                userHex = pubKey,
                size = 24.dp,
                pictureModifier = Modifier.padding(end = 8.dp),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            relays?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            about?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                )
            }
        }
    }
}

/** How many live coordinators a discovery run shows before it asks. */
private const val LIVE_PREVIEW = 8

/** Its announced name, or null when it published none. */
private fun DiscoveredCoordinator.announcedName(): String? = surface.name?.takeIf { it.isNotBlank() }

/**
 * What to call an offer: its announced name, else its profile, else a short
 * npub -- never a hex prefix. The announcement still wins, because in a
 * discovery list the CEP-6 surface is the thing being offered.
 */
@Composable
private fun resolvedName(
    offer: DiscoveredCoordinator,
    accountViewModel: AccountViewModel,
): String = coordinatorDisplayName(offer.pubKey, offer.announcedName(), accountViewModel)

/**
 * How long ago a coordinator last announced, in words that stay words.
 *
 * `timeAgoNoDot` answers with a span for something recent and an absolute date
 * for anything past a month, and the caller used one template for both — so
 * every stale coordinator on the discovery list read "Last announced Aug 7
 * ago". The date branch gets a template with no "ago" in it.
 */
@Composable
private fun announcedLabel(announcedAt: Long): String =
    if (TimeUtils.now() - announcedAt > TimeUtils.ONE_MONTH) {
        stringRes(R.string.cordn_coordinators_discover_seen_on, timeAgoNoDot(announcedAt).trim())
    } else {
        stringRes(R.string.cordn_coordinators_discover_seen, timeAgoNoDot(announcedAt).trim())
    }

/** The hosts it answers on, the first two and a count of the rest. */
@Composable
private fun relayLabel(relays: List<NormalizedRelayUrl>): String? {
    if (relays.isEmpty()) return null
    val hosts = relays.map { it.url.substringAfter("://").trim('/') }.distinct()
    val shown = hosts.take(2).joinToString(", ")
    return if (hosts.size <= 2) {
        shown
    } else {
        stringRes(R.string.cordn_coordinators_relays_more, shown, hosts.size - 2)
    }
}

/**
 * Names are the coordinator's own word for itself and collide constantly — the
 * reference server ships as "My coordinator", so a discovery run returns a
 * dozen rows with that name and nothing to tell them apart. A pubkey prefix is
 * added only to the ones that actually clash, so the common case stays clean.
 */
internal fun disambiguate(
    name: String,
    pubKey: HexKey,
    allNames: List<String>,
): String = if (allNames.count { it == name } > 1) "$name \u00b7 ${pubKey.take(8)}" else name

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

/** A numbered step label, so the form reads as an order rather than a pile. */
@Composable
private fun StepHeading(
    step: Int,
    title: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = step.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = title, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Who the group is for, named before the coordinator is chosen.
 *
 * The order is the point. cordn can only add somebody by spending a KeyPackage
 * they published to the coordinator being used, so a roster turns an
 * unanswerable question ("which coordinator do I trust?") into a countable one
 * ("which one can reach these five?"). Everything below this section is ranked
 * on what is entered here.
 *
 * Reachability is per person and across every coordinator this account already
 * uses, not the one currently selected: somebody reachable nowhere cannot be
 * helped by changing the choice below, and the caption says which case it is.
 */
@Composable
private fun CordnRoster(
    roster: List<HexKey>,
    coAdmins: Set<HexKey>,
    egalitarian: Boolean,
    coverage: Map<HexKey, CordnCoverage>,
    userSuggestions: UserSuggestionState,
    search: String,
    onSearchChange: (String) -> Unit,
    onAdd: (User) -> Unit,
    onRemove: (HexKey) -> Unit,
    onToggleAdmin: (HexKey) -> Unit,
    busy: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text(stringRes(R.string.cordn_create_add_member)) },
            placeholder = { Text(stringRes(R.string.cordn_info_add_member_placeholder)) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringRes(R.string.cordn_create_people_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!busy && search.length > 2) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = onAdd,
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightChat,
                onEmpty = {
                    Text(
                        text = stringRes(R.string.cordn_info_add_member_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                },
                trailingContent = { user ->
                    IconButton(onClick = { onAdd(user) }) {
                        Icon(
                            symbol = MaterialSymbols.PersonAdd,
                            contentDescription = stringRes(R.string.cordn_create_add_member),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }

        roster.forEach { member ->
            // Counted across every coordinator that answered. An unanswered one
            // contributes nothing rather than a zero, so an unreachable
            // coordinator never reads as "this person has no key anywhere".
            val answered = coverage.values.count { it.answered }
            val reaching = coverage.values.count { it.answered && member in it.reachable }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UserPicture(userHex = member, size = 32.dp, accountViewModel = accountViewModel, nav = nav)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = observeUserNameByHex(member, accountViewModel),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            when {
                                answered == 0 -> stringRes(R.string.cordn_create_reach_unknown)
                                reaching > 0 ->
                                    pluralStringRes(LocalContext.current, R.plurals.cordn_create_reach_count, reaching, reaching)
                                else -> stringRes(R.string.cordn_create_reach_none)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (answered > 0 && reaching == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                // Hidden in egalitarian mode: there the admin list is empty by
                // definition, so a per-person toggle would offer a choice that
                // cannot be recorded.
                if (!egalitarian) {
                    val isAdmin = member in coAdmins
                    TextButton(onClick = { onToggleAdmin(member) }, enabled = !busy) {
                        Text(
                            text = stringRes(R.string.cordn_create_admin),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (isAdmin) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }

                IconButton(onClick = { onRemove(member) }, enabled = !busy) {
                    Icon(
                        symbol = MaterialSymbols.PersonRemove,
                        contentDescription = stringRes(R.string.cordn_info_remove_member),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The coordinator that will be used, and why, in one row.
 *
 * Collapsed by default because with a roster entered there is usually nothing
 * left to decide -- one coordinator reaches everybody and the rest do not. The
 * full list is one tap away for when that is not true.
 */
@Composable
private fun CoordinatorSummary(
    config: CoordinatorConfig?,
    coverage: CordnCoverage?,
    rosterSize: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (config == null) {
            Text(
                text = stringRes(R.string.cordn_create_no_coordinator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            CoordinatorIdentityRow(
                pubKey = config.pubKey,
                label = config.label,
                accountViewModel = accountViewModel,
                nav = nav,
                size = 32.dp,
                modifier = Modifier.weight(1f),
            ) { name ->
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    if (rosterSize > 0) {
                        // Says which of the three it is: reaches everybody,
                        // leaves somebody out, or was never asked. The third is
                        // not the second.
                        val reached = coverage?.reachable?.size
                        Text(
                            text =
                                when {
                                    coverage == null || !coverage.answered -> stringRes(R.string.cordn_create_coverage_unknown)
                                    reached == rosterSize -> stringRes(R.string.cordn_create_coverage_all, rosterSize)
                                    else -> stringRes(R.string.cordn_create_coverage_partial, reached ?: 0, rosterSize)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (coverage != null && coverage.answered && reached != rosterSize) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }

        TextButton(onClick = onToggle) {
            Text(
                if (expanded) {
                    stringRes(R.string.cordn_create_coordinator_hide)
                } else {
                    stringRes(R.string.cordn_create_coordinator_change)
                },
            )
        }
    }
}

/**
 * What happened after Create, when it was not simply "everything".
 *
 * Shown instead of a thrown error because the group exists either way: a
 * failed invitation is one row, not a failed creation. Every unfinished person
 * gets the action that would actually work for them -- a retry where the
 * coordinator refused, a link where it holds no KeyPackage at all and no retry
 * ever could.
 */
@Composable
private fun CordnCreationOutcome(
    creation: CordnGroupCreation,
    roster: List<HexKey>,
    coordinatorPubKey: HexKey?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val open: () -> Unit = {
        onDismiss()
        coordinatorPubKey?.let { nav.nav(Route.CordnGroupChat(it, creation.gid)) }
        Unit
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.cordn_create_outcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringRes(R.string.cordn_create_outcome_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val attempted = creation.outcomes.associateBy { it.pubKey }
                roster.forEach { member ->
                    val outcome = attempted[member]
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserPicture(userHex = member, size = 26.dp, accountViewModel = accountViewModel, nav = nav)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = observeUserNameByHex(member, accountViewModel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text =
                                    when {
                                        outcome == null -> stringRes(R.string.cordn_create_outcome_skipped)
                                        outcome.sent -> stringRes(R.string.cordn_create_outcome_waiting)
                                        else -> outcome.failure?.message ?: stringRes(R.string.cordn_create_outcome_failed)
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (outcome?.sent == true) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                        }
                        // Nobody was told anything, so the only thing that can
                        // reach somebody with no KeyPackage here is a link they
                        // open themselves -- which needs the group to exist,
                        // and now it does.
                        if (outcome == null) {
                            TextButton(onClick = open) { Text(stringRes(R.string.cordn_create_outcome_link)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = open) { Text(stringRes(R.string.cordn_create_outcome_open)) } },
    )
}
