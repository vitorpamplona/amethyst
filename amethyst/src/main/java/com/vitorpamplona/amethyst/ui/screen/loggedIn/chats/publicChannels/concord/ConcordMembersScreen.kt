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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordMembership
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The members roster of one Concord community — the analog of NIP-29's
 * `RelayGroupMembersScreen`. Concord has no relay-signed roster (membership is key
 * possession), so this shows the *privileged* roster derivable from the folded
 * Control Plane: the owner, every role-holder (admins/moderators), and banned
 * users. The overflow menu offers promote/demote (owner) and ban/unban, gated on
 * the viewer's authority exactly as the write path enforces on fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordMembersScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account

    // Self-sufficient: mount the Concord plane subscription so the community folds even when this
    // screen is opened directly (deep link), not only from the hub.
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    // Page every channel's bounded history once so the roster includes observed authors who only posted
    // outside the live tail (CORD-02 §5) — the difference between a handful of recent posters and the
    // real membership.
    ConcordMemberHarvest(communityId, accountViewModel)

    // Re-resolve the session as sessions are created/folded (revision-keyed), so a deep link that
    // lands before the community's session exists still picks it up once it does.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(account, communityId, revision) { account.concordSessions.sessionFor(communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()

    // The Guestbook membership (self-signed joins) plus everyone seen publishing a channel message
    // (observed authors, CORD-02 §5) — most members never post a Join, so without the latter the
    // roster collapses to just the owner + privileged roles.
    val guestbookMembers by (session?.members ?: remember { MutableStateFlow(emptySet<HexKey>()) }).collectAsStateWithLifecycle()
    val observedAuthors by (session?.observedAuthors ?: remember { MutableStateFlow(emptySet<HexKey>()) }).collectAsStateWithLifecycle()

    val myPubKey = account.signer.pubKey
    val roster =
        remember(state, guestbookMembers, observedAuthors) {
            val s = state ?: return@remember emptyList<RosterEntry>()
            val authority = s.authority
            val pubkeys =
                (listOf(s.ownerPubKey) + authority.roleHolders() + authority.bannedMembers() + guestbookMembers + observedAuthors)
                    .map { it.lowercase() }
                    .distinct()
            pubkeys
                .map {
                    // The member's most-privileged role name (lowest position ranks highest), so the
                    // roster shows the real "Admin"/"Moderator"/custom label instead of a coarse badge.
                    val roleName =
                        authority
                            .rolesFor(it)
                            .minByOrNull { r -> r.position }
                            ?.name
                            ?.takeIf { n -> n.isNotBlank() }
                    RosterEntry(it, ConcordMembership.of(authority, it), roleName, authority.rolesOf(it))
                }.sortedWith(compareBy({ it.membership.sortRank() }, { it.pubkey }))
        }

    val iAmOwner = state?.authority?.isOwner(myPubKey) == true
    val iCanBan = state?.let { it.authority.isOwner(myPubKey) || it.authority.effectivePermissions(myPubKey).has(ConcordPermissions.BAN) } == true
    val iCanManageRoles = state?.authority?.hasPermission(myPubKey, ConcordPermissions.MANAGE_ROLES) == true

    // The roles this viewer may actually hand out. The fold drops a grant whose granter does
    // not *strictly* outrank every assigned role, so offering a role at or above our own
    // position would publish an edition that every client then silently discards. The owner
    // sits at rank 0 and no role may claim position 0, so this admits everything for them.
    val assignableRoles =
        remember(state, myPubKey) {
            val authority = state?.authority ?: return@remember emptyList<AssignableRole>()
            val myRank = authority.rank(myPubKey) ?: return@remember emptyList()
            authority
                .roles()
                .filter { (_, role) -> myRank < role.position }
                .map { (id, role) -> AssignableRole(id, role.name, role.position) }
                .sortedBy { it.position }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringRes(R.string.concord_members_title), maxLines = 1)
                        state?.metadata?.name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (roster.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(R.string.concord_members_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(roster, key = { it.pubkey }) { entry ->
                    ConcordMemberRow(
                        entry = entry,
                        communityId = communityId,
                        isSelf = entry.pubkey.equals(myPubKey, ignoreCase = true),
                        viewerIsOwner = iAmOwner,
                        viewerCanBan = iCanBan,
                        // Ban/Remove are rank-gated the same way Roles… is. The owner short-circuits
                        // because canActOn begins at hasPermission, which is false while banned, and
                        // a rogue BAN holder can currently banlist the owner — see the note on
                        // Account.concordBanTarget.
                        canBanTarget =
                            iAmOwner ||
                                state?.authority?.canActOn(myPubKey, entry.pubkey, ConcordPermissions.BAN) == true,
                        viewerCanManageRoles = iCanManageRoles,
                        // canActOn folds the whole rank rule for us: we hold MANAGE_ROLES, we're not
                        // banned, the target isn't the owner (unremovable), and we strictly outrank
                        // them — which also rules out acting on ourselves (equal cannot act on equal).
                        canManageRolesOnTarget = state?.authority?.canActOn(myPubKey, entry.pubkey, ConcordPermissions.MANAGE_ROLES) == true,
                        assignableRoles = assignableRoles,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ConcordMemberRow(
    entry: RosterEntry,
    communityId: String,
    isSelf: Boolean,
    viewerIsOwner: Boolean,
    viewerCanBan: Boolean,
    canBanTarget: Boolean,
    viewerCanManageRoles: Boolean,
    canManageRolesOnTarget: Boolean,
    assignableRoles: List<AssignableRole>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val user = remember(entry.pubkey) { accountViewModel.checkGetOrCreateUser(entry.pubkey) }
    val isOwnerTarget = entry.membership == ConcordMembership.OWNER
    val isBanned = entry.membership == ConcordMembership.BANNED
    val isAdmin = entry.membership == ConcordMembership.ADMIN

    // Owner can promote/demote anyone but the owner; ban is available to owner + BAN holders that
    // strictly outrank the target, never against the owner or yourself. A banned user only offers
    // "unban" — and unban is rank-gated too, so whoever cannot ban you cannot lift your ban either.
    val canToggleAdmin = viewerIsOwner && !isOwnerTarget && !isBanned && !isSelf
    val canBan = viewerCanBan && canBanTarget && !isOwnerTarget && !isSelf
    // Hard removal (CORD-06 Refounding) rotates the community key; same authority as ban.
    val canRemove = viewerCanBan && canBanTarget && !isOwnerTarget && !isSelf
    // Shown to any MANAGE_ROLES holder, but disabled with a reason when this particular
    // member (or every defined role) is out of our reach — a grant we don't outrank
    // publishes fine and is then dropped by every client's fold, so a silently no-op
    // control would be worse than none. The owner's own row never offers it: the owner
    // is unremovable and outranks everyone, so canManageRolesOnTarget is false there.
    val rolesBlockedReason =
        when {
            !canManageRolesOnTarget -> stringRes(R.string.concord_members_roles_out_of_reach)
            assignableRoles.isEmpty() -> stringRes(R.string.concord_members_roles_none_assignable)
            else -> null
        }
    val hasMenu = canToggleAdmin || canBan || canRemove || viewerCanManageRoles

    var editRoles by remember { mutableStateOf(false) }
    if (editRoles) {
        ConcordRolesDialog(
            assignable = assignableRoles,
            current = entry.roleIds,
            onConfirm = { selected ->
                accountViewModel.setConcordRoles(communityId, entry.pubkey, selected)
                editRoles = false
            },
            onDismiss = { editRoles = false },
        )
    }

    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        ConcordRemoveMemberDialog(
            onConfirm = {
                accountViewModel.removeConcordMember(communityId, entry.pubkey)
                confirmRemove = false
            },
            onDismiss = { confirmRemove = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(entry.pubkey, Size35dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            if (user != null) {
                UsernameDisplay(user, accountViewModel = accountViewModel)
            } else {
                Text(entry.pubkey.take(8), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MemberBadge(entry.membership, entry.roleName)
        if (hasMenu) {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }) {
                SymbolIcon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(R.string.more_options))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (canToggleAdmin) {
                    DropdownMenuItem(
                        text = { Text(stringRes(if (isAdmin) R.string.concord_members_remove_admin else R.string.concord_members_make_admin)) },
                        onClick = {
                            accountViewModel.setConcordAdmin(communityId, entry.pubkey, makeAdmin = !isAdmin)
                            expanded = false
                        },
                    )
                }
                if (viewerCanManageRoles) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringRes(R.string.concord_members_roles))
                                rolesBlockedReason?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        enabled = rolesBlockedReason == null,
                        onClick = {
                            editRoles = true
                            expanded = false
                        },
                    )
                }
                if (canBan) {
                    DropdownMenuItem(
                        text = { Text(stringRes(if (isBanned) R.string.concord_members_unban else R.string.concord_members_ban)) },
                        onClick = {
                            accountViewModel.setConcordBan(communityId, entry.pubkey, ban = !isBanned)
                            expanded = false
                        },
                    )
                }
                if (canRemove) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringRes(R.string.concord_members_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            confirmRemove = true
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** A small pill labelling the member's standing (owner / role name / banned; plain members render nothing). */
@Composable
private fun MemberBadge(
    membership: ConcordMembership,
    roleName: String?,
) {
    val label =
        when {
            membership == ConcordMembership.BANNED -> stringRes(R.string.concord_role_banned)
            membership == ConcordMembership.OWNER -> stringRes(R.string.concord_role_owner)
            // Show the actual granted role ("Admin", "Moderator", or a custom role) rather than a
            // one-size-fits-all badge; fall back to the generic "Admin" label if a role-holder's
            // role name somehow didn't resolve.
            roleName != null -> roleName
            membership == ConcordMembership.ADMIN -> stringRes(R.string.concord_role_admin)
            else -> return
        }
    val container = if (membership == ConcordMembership.BANNED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (membership == ConcordMembership.BANNED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Multi-select over the roles the viewer may assign (CORD-04 role grant).
 *
 * A grant REPLACES the member's role set rather than merging into it, so the box starts
 * checked on everything they already hold — otherwise saving would silently strip the
 * roles that weren't re-checked. Every currently-held role is guaranteed to appear in
 * [assignable]: the caller only opens this when it strictly outranks the member, and the
 * member's rank is the *lowest* position they hold, so all of their roles sit strictly
 * below us too. Like "Make admin", saving applies immediately — no extra confirmation.
 */
@Composable
private fun ConcordRolesDialog(
    assignable: List<AssignableRole>,
    current: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(current) { mutableStateListOf<String>().apply { addAll(current) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.concord_members_roles_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringRes(R.string.concord_members_roles_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                assignable.forEach { role ->
                    val checked = role.id in selected
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(role.id) else selected.add(role.id)
                                }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(role.name.ifBlank { role.id.take(8) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }) {
                Text(stringRes(R.string.concord_members_roles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

/** Confirms a hard removal — spells out that it rotates the community key (CORD-06). */
@Composable
private fun ConcordRemoveMemberDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.concord_members_remove_title)) },
        text = { Text(stringRes(R.string.concord_members_remove_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.concord_members_remove_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

private class RosterEntry(
    val pubkey: HexKey,
    val membership: ConcordMembership,
    /** The member's most-privileged role name (e.g. "Admin"/"Moderator"), null for a plain member. */
    val roleName: String?,
    /** Every role id the member currently holds — the preselection for the role picker. */
    val roleIds: Set<String>,
)

/** One role the viewer is allowed to hand out, ordered by [position] (lower ranks higher). */
private class AssignableRole(
    val id: String,
    val name: String,
    val position: Long,
)

/** Owner first, then admins, then plain members, then banned last. */
private fun ConcordMembership.sortRank(): Int =
    when (this) {
        ConcordMembership.OWNER -> 0
        ConcordMembership.ADMIN -> 1
        ConcordMembership.MEMBER -> 2
        ConcordMembership.NONE -> 3
        ConcordMembership.BANNED -> 4
    }
