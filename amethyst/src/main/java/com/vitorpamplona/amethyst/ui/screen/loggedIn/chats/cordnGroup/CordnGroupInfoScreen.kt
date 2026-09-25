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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.CordnGroupManager
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_group_untitled
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SectionCollapse
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SectionExpand
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec00Coordinator.JoinRequest
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.launch

/**
 * What this room is, and what the coordinator can see of it.
 *
 * The **exposure card's real home**. The link screen shows the same disclosure
 * before joining, when it is a decision; this shows it after, when it is a
 * fact worth being able to check. §8 is only meaningful if it is available at
 * both moments — a privacy property nobody can look up again is a claim, not a
 * property.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnGroupInfoScreen(
    coordinatorPubKey: HexKey,
    gid: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val room = remember(coordinatorPubKey, gid) { runtime?.groups?.get(coordinatorPubKey, gid) }

    if (room == null) {
        Scaffold(topBar = { InfoTopBar(nav) }) { padding ->
            // The app's own empty state, centred and titled, rather than a
            // sentence stranded in the top-left corner.
            EmptyState(
                title = stringRes(R.string.cordn_group_unavailable),
                description = stringRes(R.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    CordnGroupInfo(room, coordinatorPubKey, accountViewModel, nav)
}

/**
 * The bar both states share, with a slot for the one action only a loaded
 * group can offer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoTopBar(
    nav: INav,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { nav.popBack() }) {
                Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
            }
        },
        title = { Text(stringRes(R.string.cordn_group_info)) },
        actions = actions,
    )
}

@Composable
private fun CordnGroupInfo(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val name by room.name.collectAsStateWithLifecycle()
    val description by room.description.collectAsStateWithLifecycle()
    val members by room.members.collectAsStateWithLifecycle()
    val admins by room.adminPubkeys.collectAsStateWithLifecycle()
    val epoch by room.epoch.collectAsStateWithLifecycle()

    val me = accountViewModel.account.signer.pubKey
    // The same rule the policy enforces, asked here so the screen offers only
    // what would actually go through: empty is egalitarian and everyone
    // administers, otherwise it is the named set.
    val canAdminister = admins.isEmpty() || me in admins

    val scope = rememberCoroutineScope()
    val manager =
        accountViewModel.account.cordnRuntime
            ?.sessionOrNull(coordinatorPubKey)
            ?.manager
    var adminError by remember(room.gid) { mutableStateOf<String?>(null) }
    var busy by remember(room.gid) { mutableStateOf(false) }
    var renaming by remember(room.gid) { mutableStateOf(false) }
    var removing by remember(room.gid) { mutableStateOf<HexKey?>(null) }
    val failed = stringRes(R.string.cordn_admin_action_failed)
    val noSession = stringRes(R.string.cordn_send_no_session)

    /**
     * Runs an admin commit, reporting rather than swallowing what it costs.
     *
     * A room whose coordinator has no open session cannot commit anything, and
     * saying so beats a button that does nothing.
     */
    fun runAdmin(block: suspend (CordnGroupManager) -> Unit) {
        val target = manager
        if (target == null) {
            adminError = noSession
            return
        }
        busy = true
        adminError = null
        scope.launch {
            try {
                block(target)
            } catch (e: Exception) {
                adminError = e.message ?: failed
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            InfoTopBar(nav) {
                // Where the sibling group-info screen keeps it. It was the only
                // action on the page, so it was also the only reason the body
                // held a button between the roster and the share block.
                if (canAdminister) {
                    IconButton(onClick = { renaming = true }, enabled = !busy) {
                        Icon(
                            symbol = MaterialSymbols.Edit,
                            contentDescription = stringRes(R.string.cordn_info_edit_details),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, room.gid.take(8)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // A subtitle, not a labelled row above a list that is itself the count.
            Text(
                text = pluralStringRes(LocalContext.current, R.plurals.cordn_member_count, members.size, members.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text(stringRes(R.string.cordn_info_members), style = MaterialTheme.typography.titleMedium)

            // Said once, and only when it is true. An empty admin set is not "none
            // configured" -- spec/01.md makes it permanently egalitarian, so the
            // roster's missing badges are a decision rather than a group waiting to
            // be set up. With admins present the badges below say who they are, and
            // a count of them adds nothing.
            if (admins.isEmpty()) {
                Text(
                    text = stringRes(R.string.cordn_info_egalitarian),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }

            members.forEach { member ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UserPicture(userHex = member, size = 28.dp, accountViewModel = accountViewModel, nav = nav)
                    Text(
                        text = observeUserNameByHex(member, accountViewModel),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (member in admins) {
                        Text(
                            text = stringRes(R.string.cordn_info_admin_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Never against yourself: cordn has no self-removal, and the
                    // manager refuses it, so offering the button would only be a
                    // way to be told no.
                    if (canAdminister && member != me) {
                        IconButton(onClick = { removing = member }, enabled = !busy) {
                            Icon(
                                symbol = MaterialSymbols.PersonRemove,
                                contentDescription = stringRes(R.string.cordn_info_remove_member),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            adminError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (renaming) {
                EditGroupDetailsDialog(
                    name = name.orEmpty(),
                    description = description.orEmpty(),
                    admins = admins,
                    onDismiss = { renaming = false },
                    onSave = { newName, newDescription ->
                        renaming = false
                        runAdmin {
                            // The whole metadata travels together, admin list
                            // included, because the extension is replaced whole.
                            it.updateGroupMetadata(
                                room.gid,
                                CordnGroupMetadata(name = newName, description = newDescription, adminPubkeys = admins),
                            )
                        }
                    },
                )
            }

            removing?.let { target ->
                // A plain confirm rather than the shared quick-action dialog: that
                // one offers "don't ask again", which for an irreversible removal
                // would be a setting nobody should be nudged into.
                AlertDialog(
                    onDismissRequest = { removing = null },
                    title = { Text(stringRes(R.string.cordn_info_remove_confirm_title)) },
                    text = {
                        Text(
                            stringRes(
                                R.string.cordn_info_remove_confirm_body,
                                observeUserNameByHex(target, accountViewModel),
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            removing = null
                            runAdmin { it.removeMember(room.gid, target) }
                        }) {
                            Text(
                                text = stringRes(R.string.cordn_info_remove_member),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { removing = null }) { Text(stringRes(Res.string.cancel)) }
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            ShareGroup(room, coordinatorPubKey, accountViewModel)

            // Only where they could be accepted. The manager already asks the
            // coordinator only for groups this account administers, so a non-admin
            // would otherwise see an empty list that reads as "nobody has asked"
            // rather than "these are not yours to answer".
            if (canAdminister) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                JoinRequests(room, coordinatorPubKey, accountViewModel, nav)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Computed, never asserted. Hardcoding these made the card look like a
            // disclosure while reporting the same four values for every group --
            // which is exactly the regression §7 risk 4 of the plan warns about.
            val runtime = accountViewModel.account.cordnRuntime
            val exposure by
                produceState<GroupExposure?>(null, runtime, coordinatorPubKey, room.gid) {
                    value =
                        runtime
                            ?.sessionOrNull(coordinatorPubKey)
                            ?.runCatching { exposure(room.gid) }
                            ?.getOrNull()
                }

            exposure?.let { CordnExposureCard(it) }

            // The coordinator key, the group id and the epoch are what you need
            // to file a bug or tell two devices apart, and nothing you need to
            // read a message. They sit beside the exposure card, which is the
            // page's other protocol-literate block, instead of above the people.
            TechnicalDetails(coordinatorPubKey, room.gid, epoch)
        }
    }
}

/**
 * The three values that only matter when something is wrong.
 *
 * Collapsed by default and selectable when open: the reason to look at a
 * 64-character coordinator key is to copy it somewhere else, never to read it.
 */
@Composable
private fun TechnicalDetails(
    coordinatorPubKey: HexKey,
    gid: String,
    epoch: Long,
) {
    var expanded by remember { mutableStateOf(false) }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(R.string.cordn_info_technical),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AnimatedVisibility(visible = expanded, enter = SectionExpand, exit = SectionCollapse) {
        SelectionContainer {
            Column {
                InfoRow(stringRes(R.string.cordn_info_coordinator), coordinatorPubKey)
                InfoRow(stringRes(R.string.cordn_info_gid), gid)
                InfoRow(stringRes(R.string.cordn_info_epoch), epoch.toString())
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Who is asking to get in, and the two buttons that answer them.
 *
 * ## Admins only, because the commit would be refused anyway
 *
 * `CordnGroupPolicy.authorizeCommit` rejects an `add` from a non-admin once a
 * group carries `admin_pubkeys`, in both directions — so a non-admin pressing
 * accept would build a commit the other members drop on receipt. The manager
 * matches it: `pendingJoinRequests` only asks the coordinator about groups
 * this account administers. An empty admin set is egalitarian, which makes
 * every member an admin and shows this to everyone.
 *
 * ## Fetched on a tap, never polled
 *
 * Same reason as the invitations screen: every call to a coordinator is
 * metadata (`spec/00.md` §8), so a live count of pending requests would be a
 * steady heartbeat telling the coordinator this group is open on someone's
 * screen.
 */
@Composable
private fun JoinRequests(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime ?: return
    val scope = rememberCoroutineScope()
    val failed = stringRes(R.string.cordn_requests_failed)

    var requests by remember { mutableStateOf<List<JoinRequest>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        busy = true
        error = null
        try {
            requests = runtime.joinRequests(coordinatorPubKey, room.gid)
        } catch (e: Exception) {
            error = e.message ?: failed
        } finally {
            busy = false
        }
    }

    Text(stringRes(R.string.cordn_requests_title), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringRes(R.string.cordn_requests_admin_only),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !busy) {
        Text(stringRes(R.string.cordn_requests_check))
    }

    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    val loaded = requests
    if (loaded != null && !busy) {
        if (loaded.isEmpty()) {
            Text(
                text = stringRes(R.string.cordn_requests_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        loaded.forEach { request ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserPicture(userHex = request.pubKey, size = 28.dp, accountViewModel = accountViewModel, nav = nav)
                Text(
                    // Admitting someone to an encrypted group off sixteen hex
                    // characters is not a decision anyone can actually make.
                    text = observeUserNameByHex(request.pubKey, accountViewModel),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    scope.launch {
                        try {
                            runtime.acceptJoinRequest(coordinatorPubKey, request)
                        } catch (e: Exception) {
                            error = e.message ?: failed
                        }
                        reload()
                    }
                }) {
                    Text(stringRes(R.string.cordn_requests_accept))
                }
                TextButton(onClick = {
                    scope.launch {
                        try {
                            runtime.declineJoinRequest(coordinatorPubKey, request)
                        } catch (e: Exception) {
                            error = e.message ?: failed
                        }
                        reload()
                    }
                }) {
                    Text(stringRes(R.string.cordn_requests_decline))
                }
            }
        }
    }
}

/**
 * The group's `cordn1…` ref, as text and as a QR.
 *
 * The ref is built from the live MLS group (`CordnGroupManager.shareRef`), so
 * it carries the `gid` and the coordinator that actually serves it rather than
 * whatever this screen was navigated with. It is public by design — `spec/02.md`
 * gives it no secret — and holding one makes nobody a member: the most it does
 * is let someone ask, which is what the line under it says.
 *
 * A QR because a ref is a long bech32 string nobody wants to read aloud, and
 * because the person you are handing a group to is usually in the room.
 */
@Composable
private fun ShareGroup(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
) {
    val runtime = accountViewModel.account.cordnRuntime ?: return
    val ref =
        remember(room.gid, coordinatorPubKey) {
            runtime
                .sessionOrNull(coordinatorPubKey)
                ?.manager
                ?.runCatching { shareRef(room.gid).encode() }
                ?.getOrNull()
        } ?: return

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Text(stringRes(R.string.cordn_share_title), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringRes(R.string.cordn_share_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SelectionContainer {
        Text(ref, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    }

    QrCodeDrawer(ref, Modifier.size(220.dp))

    OutlinedButton(
        onClick = {
            clipboard.setText(AnnotatedString(ref))
            copied = true
        },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringRes(if (copied) R.string.cordn_share_copied else R.string.cordn_share_copy))
    }
}

/**
 * Renames a group, or rewrites its description.
 *
 * [admins] rides through untouched. A GroupContextExtensions proposal replaces
 * the whole `cordn_group_metadata` extension, so a save that dropped the admin
 * list would quietly turn an administered group egalitarian — which nobody can
 * undo from inside an egalitarian group's own rules.
 */
@Composable
private fun EditGroupDetailsDialog(
    name: String,
    description: String,
    admins: List<HexKey>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var draftName by remember { mutableStateOf(name) }
    var draftDescription by remember { mutableStateOf(description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.cordn_info_edit_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text(stringRes(R.string.cordn_create_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    label = { Text(stringRes(R.string.cordn_create_description)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (admins.isNotEmpty()) {
                    Text(
                        text = pluralStringRes(LocalContext.current, R.plurals.cordn_info_admins_kept, admins.size, admins.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draftName.trim(), draftDescription.trim()) },
                enabled = draftName.isNotBlank(),
            ) {
                Text(stringRes(R.string.cordn_info_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}
