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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
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

    // Live typing heartbeats (kind 23311) per channel, so a channel where someone is composing shows
    // "typing…" in place of its last-message preview. A single screen-level ticker re-applies the
    // freshness window for every row at once, and only spins while at least one heartbeat is live.
    val typingMap by (session?.typing ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<HexKey, Map<HexKey, Long>>()) })
        .collectAsStateWithLifecycle()
    var typingNow by remember { mutableLongStateOf(TimeUtils.now()) }
    LaunchedEffect(typingMap) {
        if (typingMap.values.all { it.isEmpty() }) return@LaunchedEffect
        while (true) {
            typingNow = TimeUtils.now()
            if (typingMap.values.all { per -> per.values.none { typingNow - it <= ConcordCommunitySession.TYPING_STALE_SECS } }) break
            delay(2000L)
        }
    }

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
                title = {
                    // Prefer the folded metadata name, then the stored community name from the list
                    // entry (always present from the join/create — this is what shows everywhere else).
                    // Fall back to the app name only if neither exists (should be unreachable), never as
                    // the normal "metadata hasn't folded yet" placeholder — that showed "Amy Debug".
                    val title =
                        state?.metadata?.name
                            ?: session?.entry?.name?.ifBlank { null }
                            ?: stringRes(com.vitorpamplona.amethyst.R.string.app_name)
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    // Back arrow only when pushed from elsewhere; as a bottom-nav tab the bar takes its place.
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.back))
                        }
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
        bottomBar = {
            // Renders only when this is a bottom-nav root (AppBottomBar hides itself when canPop),
            // so a pinned Concord community works both as a pushed detail and as a bottom-nav tab.
            AppBottomBar(Route.ConcordServer(communityId), nav, accountViewModel) { route ->
                if (route != Route.ConcordServer(communityId)) nav.navBottomBar(route)
            }
        },
        floatingActionButton = {
            if (canManageChannels) {
                FloatingActionButton(
                    onClick = { channelEditor = ConcordChannelEditor(channelIdHex = null, initialName = "") },
                    shape = CircleShape,
                ) {
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
                    val name = def.name.ifBlank { entry.key }
                    val icon =
                        when {
                            def.voice == true -> MaterialSymbols.Mic
                            def.private == true -> MaterialSymbols.Lock
                            else -> MaterialSymbols.Tag
                        }
                    val typingAuthors =
                        remember(typingMap, typingNow, entry.key) {
                            (typingMap[entry.key] ?: emptyMap())
                                .filterValues { typingNow - it <= ConcordCommunitySession.TYPING_STALE_SECS }
                                .keys
                                .sorted()
                        }
                    ConcordChannelListRow(
                        communityId = communityId,
                        channelKey = entry.key,
                        channelName = name,
                        icon = icon,
                        isVoice = def.voice == true,
                        typingAuthors = typingAuthors,
                        canManageChannels = canManageChannels,
                        accountViewModel = accountViewModel,
                        onClick = { nav.nav(Route.Concord(communityId, entry.key)) },
                        onRename = { channelEditor = ConcordChannelEditor(channelIdHex = entry.key, initialName = name) },
                        onDelete = { channelToDelete = ConcordChannelEditor(channelIdHex = entry.key, initialName = name) },
                    )
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/**
 * One channel row in the community's server view: its icon, name, a preview of the last message
 * (author + snippet), the relative time of that message, and an unread-message count badge — plus
 * the manager-only overflow menu. Name/icon come from the folded Control Plane definition; the
 * preview, time, and unread count are read reactively from the channel's own message store so they
 * fill in the moment messages fold in and clear as soon as the channel is opened (last-read advances).
 */
@Composable
private fun ConcordChannelListRow(
    communityId: String,
    channelKey: String,
    channelName: String,
    icon: MaterialSymbol,
    isVoice: Boolean,
    typingAuthors: List<HexKey>,
    canManageChannels: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val account = accountViewModel.account
    // getOrCreate (not getIfExists): a channel folded on the Control Plane may have no message note
    // yet; its notes flow then makes the preview/time/unread/facepile reactive as messages arrive.
    val channel = remember(communityId, channelKey) { LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelKey)) }
    val channelState by channel
        .flow()
        .notes.stateFlow
        .collectAsStateWithLifecycle()
    // The newest *timeline* message (not the raw lastNote): skips kind-1111 thread replies and
    // hidden authors so the preview + time match the channel feed and the unread badge below.
    val lastNote = remember(channelState) { channel.newestTimelineNote(account) }
    val unread by
        remember(communityId, channelKey) { concordChannelUnreadCountFlow(account, communityId, channelKey) }
            .collectAsStateWithLifecycle(0)
    val hasUnread = unread > 0
    // The recent posters' faces — recomputed as the channel's notes change (keyed on channelState).
    val faceAuthors = remember(channelState) { channel.recentAuthorHexes(FACEPILE_MAX) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (canManageChannels) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymbolIcon(
            symbol = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Line 1: channel name + the recent-posters facepile pushed to the right.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    channelName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ConcordAuthorFacepile(faceAuthors, accountViewModel)
            }
            // Line 2: the last-message preview (or a live "typing…"), then the time + unread badge.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ConcordChannelPreviewLine(lastNote, isVoice, typingAuthors, accountViewModel)
                }
                lastNote?.createdAt()?.let { ts ->
                    Text(
                        timeAgo(ts, LocalContext.current, prefix = ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                ConcordUnreadBadge(unread)
            }
        }
        if (canManageChannels) {
            ConcordChannelRowMenu(
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}

/** How many recent-poster avatars a channel row's facepile shows at most. */
private const val FACEPILE_MAX = 4

/**
 * The line under a channel name. When someone is composing it shows a live italic "X is typing…";
 * otherwise the last message's author + a snippet ("author: hello"), or a muted "No messages yet"
 * placeholder before anything has folded in. Author names resolve reactively (hex → profile name).
 */
@Composable
private fun ConcordChannelPreviewLine(
    lastNote: Note?,
    isVoice: Boolean,
    typingAuthors: List<HexKey>,
    accountViewModel: AccountViewModel,
) {
    if (typingAuthors.isNotEmpty()) {
        val label =
            when (typingAuthors.size) {
                1 -> stringRes(com.vitorpamplona.amethyst.R.string.concord_typing_one, rememberConcordDisplayName(typingAuthors[0], accountViewModel))
                2 ->
                    stringRes(
                        com.vitorpamplona.amethyst.R.string.concord_typing_two,
                        rememberConcordDisplayName(typingAuthors[0], accountViewModel),
                        rememberConcordDisplayName(typingAuthors[1], accountViewModel),
                    )
                else -> stringRes(com.vitorpamplona.amethyst.R.string.concord_typing_many)
            }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val event = lastNote?.event
    val author = lastNote?.author
    val preview: String =
        if (event != null && author != null) {
            val authorName by observeUserName(author, accountViewModel)
            val body = event.content.take(80)
            if (body.isBlank()) authorName else "$authorName: $body"
        } else if (event != null) {
            event.content.take(80)
        } else {
            // Voice channels never carry chat notes, so "No messages yet" would read oddly — leave blank.
            if (isVoice) return
            stringRes(com.vitorpamplona.amethyst.R.string.concord_channel_no_messages)
        }
    Text(
        preview,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Resolves [hex] to its best display name, reactively, falling back to a short hex. */
@Composable
private fun rememberConcordDisplayName(
    hex: HexKey,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(hex) { accountViewModel.checkGetOrCreateUser(hex) } ?: return remember(hex) { hex.take(8) }
    val name by observeUserName(user, accountViewModel)
    return name
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
