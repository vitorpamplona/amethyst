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

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The channel list of one Concord community (the "server" view). Reads the folded
 * Control Plane from the community session and renders one row per channel; tapping
 * opens that channel's [ConcordChannelScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordChannelListScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    val account = accountViewModel.account
    // Re-resolve on each revision so a deep link that lands before the session exists picks it up.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(account, communityId, revision) { account.concordSessions.sessionFor(communityId) }
    val state by (session?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) })
        .collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var inviteLink by remember { mutableStateOf<String?>(null) }
    var minting by remember { mutableStateOf(false) }

    // Channel create/rename/delete are gated on MANAGE_CHANNELS (or owner) — the same predicate the
    // fold enforces, so an unauthorized action would be a silent no-op we shouldn't even offer.
    val canManageChannels =
        state?.authority?.let {
            it.isOwner(account.signer.pubKey) ||
                it.effectivePermissions(account.signer.pubKey).has(ConcordPermissions.MANAGE_CHANNELS)
        } == true

    // channelIdHex == null → create; else → rename that channel.
    var channelEditor by remember { mutableStateOf<ConcordChannelEditor?>(null) }
    var channelToDelete by remember { mutableStateOf<ConcordChannelEditor?>(null) }

    inviteLink?.let { link ->
        InviteLinkDialog(link = link, onDismiss = { inviteLink = null })
    }

    channelEditor?.let { editor ->
        ConcordChannelEditDialog(
            initialName = editor.initialName,
            isCreate = editor.channelIdHex == null,
            onDismiss = { channelEditor = null },
            onConfirm = { newName ->
                channelEditor = null
                scope.launch {
                    if (editor.channelIdHex == null) {
                        account.createConcordChannel(communityId, newName)
                    } else {
                        account.renameConcordChannel(communityId, editor.channelIdHex, newName)
                    }
                }
            },
        )
    }

    channelToDelete?.let { target ->
        val id = target.channelIdHex ?: return@let
        AlertDialog(
            onDismissRequest = { channelToDelete = null },
            title = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_delete_title)) },
            text = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_delete_message, target.initialName)) },
            confirmButton = {
                TextButton(onClick = {
                    channelToDelete = null
                    scope.launch { account.deleteConcordChannel(communityId, id, target.initialName) }
                }) {
                    Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToDelete = null }) {
                    Text(stringRes(com.vitorpamplona.amethyst.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.metadata?.name ?: stringRes(com.vitorpamplona.amethyst.R.string.app_name), fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.back))
                    }
                },
                actions = {
                    val canEdit =
                        state?.authority?.let {
                            it.isOwner(account.signer.pubKey) ||
                                it.effectivePermissions(account.signer.pubKey).has(ConcordPermissions.MANAGE_METADATA)
                        } == true

                    IconButton(onClick = { nav.nav(Route.ConcordMembers(communityId)) }) {
                        SymbolIcon(symbol = MaterialSymbols.Group, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.concord_members_title))
                    }
                    if (canEdit) {
                        IconButton(onClick = { nav.nav(Route.ConcordEdit(communityId)) }) {
                            SymbolIcon(symbol = MaterialSymbols.Edit, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.concord_edit_title))
                        }
                    }
                    IconButton(
                        enabled = !minting,
                        onClick = {
                            minting = true
                            scope.launch {
                                try {
                                    inviteLink = account.mintConcordInvite(communityId)
                                } finally {
                                    // Always clear the flag — a thrown mint would otherwise leave the
                                    // button disabled until the screen is recreated.
                                    minting = false
                                }
                            }
                        },
                    ) {
                        SymbolIcon(symbol = MaterialSymbols.PersonAdd, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.concord_invite_action))
                    }
                },
            )
        },
        floatingActionButton = {
            if (canManageChannels) {
                FloatingActionButton(onClick = { channelEditor = ConcordChannelEditor(channelIdHex = null, initialName = "") }) {
                    SymbolIcon(symbol = MaterialSymbols.Add, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_create))
                }
            }
        },
    ) { padding ->
        val channels =
            state
                ?.channels
                ?.entries
                ?.toList()
                .orEmpty()
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(com.vitorpamplona.amethyst.R.string.concord_channels_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(channels, key = { it.key }) { entry ->
                    val def = entry.value.definition
                    val name = def?.name ?: entry.key
                    val icon =
                        when {
                            def?.voice == true -> MaterialSymbols.Mic
                            def?.private == true -> MaterialSymbols.Lock
                            else -> MaterialSymbols.Tag
                        }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.nav(Route.Concord(communityId, entry.key)) }
                            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (canManageChannels) 4.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SymbolIcon(
                            symbol = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                        if (canManageChannels) {
                            ConcordChannelRowMenu(
                                onRename = { channelEditor = ConcordChannelEditor(channelIdHex = entry.key, initialName = name) },
                                onDelete = { channelToDelete = ConcordChannelEditor(channelIdHex = entry.key, initialName = name) },
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** A pending channel create ([channelIdHex] null) or rename target. */
private data class ConcordChannelEditor(
    val channelIdHex: String?,
    val initialName: String,
)

/** The per-channel-row overflow menu (rename / delete), shown only to channel managers. */
@Composable
private fun ConcordChannelRowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            SymbolIcon(
                symbol = MaterialSymbols.MoreVert,
                contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Name-entry dialog for creating a new channel or renaming an existing one. */
@Composable
private fun ConcordChannelEditDialog(
    initialName: String,
    isCreate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringRes(
                    if (isCreate) {
                        com.vitorpamplona.amethyst.R.string.concord_channel_create
                    } else {
                        com.vitorpamplona.amethyst.R.string.concord_channel_rename
                    },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
            ) {
                Text(
                    stringRes(
                        if (isCreate) {
                            com.vitorpamplona.amethyst.R.string.concord_channel_create
                        } else {
                            com.vitorpamplona.amethyst.R.string.concord_channel_rename_save
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.cancel))
            }
        },
    )
}

/** Shows a freshly minted invite link as a QR code with copy + share actions. */
@Composable
private fun InviteLinkDialog(
    link: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_invite_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrCodeDrawer(contents = link, modifier = Modifier.size(220.dp))
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send =
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                    }
                context.startActivity(Intent.createChooser(send, stringRes(context, com.vitorpamplona.amethyst.R.string.concord_invite_title)))
                onDismiss()
            }) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.quick_action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch { clipboard.setText(link) }
                onDismiss()
            }) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.copy_to_clipboard))
            }
        },
    )
}
