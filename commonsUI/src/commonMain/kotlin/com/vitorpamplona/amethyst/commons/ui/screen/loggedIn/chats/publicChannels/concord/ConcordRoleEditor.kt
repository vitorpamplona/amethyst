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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordRoleRules
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.concord_perm_ban
import com.vitorpamplona.amethyst.commons.resources.concord_perm_create_invite
import com.vitorpamplona.amethyst.commons.resources.concord_perm_kick
import com.vitorpamplona.amethyst.commons.resources.concord_perm_manage_channels
import com.vitorpamplona.amethyst.commons.resources.concord_perm_manage_messages
import com.vitorpamplona.amethyst.commons.resources.concord_perm_manage_metadata
import com.vitorpamplona.amethyst.commons.resources.concord_perm_manage_roles
import com.vitorpamplona.amethyst.commons.resources.concord_perm_mention_everyone
import com.vitorpamplona.amethyst.commons.resources.concord_perm_pin_messages
import com.vitorpamplona.amethyst.commons.resources.concord_perm_view_audit_log
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_delete
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_delete_message
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_edit
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_empty
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_name
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_new
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_permissions
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_position
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_position_hint
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_position_label
import com.vitorpamplona.amethyst.commons.resources.concord_role_editor_title
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/** A role being edited: null [roleId] for a new one. */
private class RoleDraft(
    val roleId: String?,
    val name: String,
    val position: Long,
    val permissions: ConcordPermissions,
)

/**
 * The community's roles (CORD-04 §2), and the way to create, rename, re-rank or delete one. Only
 * the roles [authority] lets this viewer edit (strictly below their own rank) get the edit and
 * delete actions; the rest are listed for context. Saving goes through
 * [AccountViewModel.defineConcordRole], which refuses a Roles v2 community with a notice.
 */
@Composable
fun ConcordRoleEditorDialog(
    communityId: String,
    authority: AuthorityResolver,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val me = accountViewModel.account.signer.pubKey
    val myRank = authority.rank(me) ?: Long.MAX_VALUE
    val roles =
        remember(authority) {
            authority
                .roles()
                .entries
                .filter { !it.value.deleted }
                .sortedBy { it.value.position }
        }
    // The permissions a role may be given here: the ones this viewer holds.
    val offeredBits = remember(authority, me) { ConcordRoleRules.EDITABLE_BITS.filter { authority.hasPermission(me, it) } }

    var draft by remember { mutableStateOf<RoleDraft?>(null) }
    var toDelete by remember { mutableStateOf<Pair<String, RoleEntity>?>(null) }

    draft?.let { d ->
        ConcordRoleFormDialog(
            draft = d,
            myRank = myRank,
            offeredBits = offeredBits,
            onSave = { name, position, permissions ->
                accountViewModel.defineConcordRole(communityId, d.roleId, name, position, permissions)
                draft = null
            },
            onDismiss = { draft = null },
        )
        return
    }

    toDelete?.let { (id, role) ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringRes(Res.string.concord_role_editor_delete)) },
            text = { Text(stringRes(Res.string.concord_role_editor_delete_message, role.name)) },
            confirmButton = {
                TextButton(onClick = {
                    accountViewModel.deleteConcordRole(communityId, id)
                    toDelete = null
                }) { Text(stringRes(Res.string.concord_role_editor_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringRes(Res.string.cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_role_editor_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (roles.isEmpty()) {
                    Text(
                        stringRes(Res.string.concord_role_editor_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                roles.forEach { (id, role) ->
                    val editable = ConcordRoleRules.canPlace(myRank, role.position, role.position)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(role.name.ifBlank { id.take(8) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringRes(Res.string.concord_role_editor_position_label, role.position),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (editable) {
                            IconButton(onClick = { draft = RoleDraft(id, role.name, role.position, role.permissionBits()) }) {
                                SymbolIcon(symbol = MaterialSymbols.Edit, contentDescription = stringRes(Res.string.concord_role_editor_edit))
                            }
                            IconButton(onClick = { toDelete = id to role }) {
                                SymbolIcon(symbol = MaterialSymbols.Delete, contentDescription = stringRes(Res.string.concord_role_editor_delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lowest = roles.maxOfOrNull { it.value.position } ?: 0L
                draft = RoleDraft(null, "", maxOf(lowest + 1, ConcordRoleRules.highestPlaceable(myRank)), ConcordPermissions.NONE)
            }) { Text(stringRes(Res.string.concord_role_editor_new)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
private fun ConcordRoleFormDialog(
    draft: RoleDraft,
    myRank: Long,
    offeredBits: List<Int>,
    onSave: (String, Long, ConcordPermissions) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(draft.name) }
    var positionText by remember { mutableStateOf(draft.position.toString()) }
    var permissions by remember { mutableStateOf(draft.permissions) }
    val position = positionText.toLongOrNull()
    val valid =
        name.isNotBlank() &&
            position != null &&
            ConcordRoleRules.canPlace(myRank, if (draft.roleId == null) null else draft.position, position)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(if (draft.roleId == null) Res.string.concord_role_editor_new else Res.string.concord_role_editor_edit)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringRes(Res.string.concord_role_editor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = positionText,
                    onValueChange = { v -> positionText = v.filter { it.isDigit() }.take(6) },
                    label = { Text(stringRes(Res.string.concord_role_editor_position)) },
                    supportingText = { Text(stringRes(Res.string.concord_role_editor_position_hint, ConcordRoleRules.highestPlaceable(myRank))) },
                    isError = position != null && !ConcordRoleRules.canPlace(myRank, null, position),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringRes(Res.string.concord_role_editor_permissions), style = MaterialTheme.typography.titleSmall)
                offeredBits.forEach { bit ->
                    val checked = permissions.has(bit)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { permissions = if (checked) permissions.without(bit) else permissions.with(bit) }
                                .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(stringRes(permissionLabel(bit)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { if (position != null) onSave(name.trim(), position, permissions) }) {
                Text(stringRes(Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

private fun permissionLabel(bit: Int): StringResource =
    when (bit) {
        ConcordPermissions.MANAGE_ROLES -> Res.string.concord_perm_manage_roles
        ConcordPermissions.MANAGE_CHANNELS -> Res.string.concord_perm_manage_channels
        ConcordPermissions.MANAGE_METADATA -> Res.string.concord_perm_manage_metadata
        ConcordPermissions.KICK -> Res.string.concord_perm_kick
        ConcordPermissions.BAN -> Res.string.concord_perm_ban
        ConcordPermissions.MANAGE_MESSAGES -> Res.string.concord_perm_manage_messages
        ConcordPermissions.CREATE_INVITE -> Res.string.concord_perm_create_invite
        ConcordPermissions.VIEW_AUDIT_LOG -> Res.string.concord_perm_view_audit_log
        ConcordPermissions.MENTION_EVERYONE -> Res.string.concord_perm_mention_everyone
        else -> Res.string.concord_perm_pin_messages
    }
