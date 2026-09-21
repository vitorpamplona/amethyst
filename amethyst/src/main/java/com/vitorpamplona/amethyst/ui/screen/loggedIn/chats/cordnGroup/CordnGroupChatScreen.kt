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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.CordnGroupManager
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One cordn room.
 *
 * Deliberately minimal — text in, text out. Reactions, media, threading and
 * edits all exist in `spec/02.md` and in `CordnAnnotationIndex`, and none of
 * them are here yet: a composer that could send a kind the room cannot yet
 * render would produce messages this client shows as blanks.
 *
 * It renders the room's own envelopes rather than `Note`s, so nothing on this
 * screen goes through `LocalCache`. That is the point — a cordn message is not
 * a Nostr event that happens to be encrypted, it is an MLS payload that never
 * touched a relay, and giving it a Note would make it searchable and
 * notifiable alongside things that were actually published.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnGroupChatScreen(
    coordinatorPubKey: HexKey,
    gid: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val room = remember(coordinatorPubKey, gid) { runtime?.groups?.get(coordinatorPubKey, gid) }

    if (room == null) {
        // No room means no session for this coordinator, which is a real state
        // (it was forgotten, or never opened) and not an error to throw at the
        // user as a blank screen.
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringRes(R.string.cordn_group_unavailable), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    CordnGroupChat(room, accountViewModel, nav)
}

@Composable
private fun CordnGroupChat(
    room: CordnGroupChatroom,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val messages by room.messages.collectAsStateWithLifecycle()
    val annotations by room.annotations.collectAsStateWithLifecycle()
    val name by room.name.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val me = accountViewModel.account.signer.pubKey

    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CordnDeliveredMessage?>(null) }
    var editing by remember { mutableStateOf<CordnDeliveredMessage?>(null) }
    var acting by remember { mutableStateOf<CordnDeliveredMessage?>(null) }

    fun manager() =
        accountViewModel.account.cordnRuntime
            ?.sessionOrNull(room.coordinatorPubKey)
            ?.manager

    Scaffold(
        topBar = {
            CordnChatTopBar(
                title = name?.takeIf { it.isNotBlank() } ?: stringRes(R.string.cordn_group_untitled, room.gid.take(8)),
                onInfo = { nav.nav(Route.CordnGroupInfo(room.coordinatorPubKey, room.gid)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned messages sit above the conversation rather than inside it.
            // A pin is a claim about a message's importance, not a message, and
            // leaving it only in place means the thing someone pinned scrolls
            // away exactly like everything else.
            PinnedRibbon(annotations, room, scope, ::manager)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                items(messages, key = { it.envelope.id }) { message ->
                    CordnMessageRow(
                        message = message,
                        annotations = annotations,
                        // The edit if there is one, and nothing at all if the
                        // message was withdrawn: rendering the original text of
                        // a deleted message would defeat the deletion.
                        text = if (annotations.isDeleted(message.envelope.id)) null else annotations.contentOf(message.envelope.id),
                        isEdited = annotations.isEdited(message.envelope.id),
                        onClick = { acting = message },
                        onReact = { emoji ->
                            scope.launch { manager()?.post(room.gid, emoji, reactionTo = message.target()) }
                        },
                    )
                }
            }

            HorizontalDivider()

            val replyPreview = replyingTo
            if (replyPreview != null) {
                ComposerBanner(
                    label = stringRes(R.string.cordn_action_replying, annotations.contentOf(replyPreview.envelope.id).orEmpty().take(60)),
                    onCancel = { replyingTo = null },
                )
            }
            if (editing != null) {
                ComposerBanner(
                    label = stringRes(R.string.cordn_action_editing),
                    onCancel = {
                        editing = null
                        draft = ""
                    },
                )
            }

            CordnComposer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@CordnComposer

                    val reply = replyingTo
                    val edit = editing
                    draft = ""
                    replyingTo = null
                    editing = null

                    scope.launch {
                        manager()?.post(
                            gid = room.gid,
                            content = text,
                            replyTo = reply?.target(),
                            editTo = edit?.target(),
                        )
                    }
                },
            )
        }
    }

    val actingOn = acting
    if (actingOn != null) {
        val message = actingOn
        MessageActions(
            message = message,
            isMine = message.envelope.pubKey == me,
            isPinned = annotations.isPinned(message.envelope.id),
            onDismiss = { acting = null },
            onReply = {
                replyingTo = message
                editing = null
                acting = null
            },
            onEdit = {
                editing = message
                replyingTo = null
                draft = annotations.contentOf(message.envelope.id).orEmpty()
                acting = null
            },
            onDelete = {
                acting = null
                scope.launch { manager()?.post(room.gid, deleteTo = message.target()) }
            },
            onTogglePin = {
                val pinned = annotations.isPinned(message.envelope.id)
                acting = null
                scope.launch {
                    manager()?.post(
                        gid = room.gid,
                        pinTo = message.target(),
                        pinOp = if (pinned) CordnMessageReferences.PinOp.REMOVE else CordnMessageReferences.PinOp.ADD,
                    )
                }
            },
        )
    }
}

/** The target fields `CordnMessageReferences` needs, straight off a delivery. */
private fun CordnDeliveredMessage.target() =
    CordnMessageReferences.Target(
        id = envelope.id,
        pubKey = envelope.pubKey,
        kind = envelope.kind,
        // The target's own tags, so replying to a reply keeps the original
        // thread root instead of starting a new thread at the reply.
        tags = envelope.tags,
    )

@Composable
private fun PinnedRibbon(
    annotations: CordnAnnotationIndex,
    room: CordnGroupChatroom,
    scope: CoroutineScope,
    manager: () -> CordnGroupManager?,
) {
    val pinned = annotations.pinnedIds().mapNotNull { annotations.byId[it] }
    if (pinned.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        pinned.forEach { message ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    // A deleted message that is still pinned shows as deleted,
                    // not as its old text: a pin must not outlive the
                    // withdrawal of what it points at.
                    text =
                        if (annotations.isDeleted(message.envelope.id)) {
                            stringRes(R.string.cordn_message_deleted)
                        } else {
                            annotations.contentOf(message.envelope.id).orEmpty()
                        },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                TextButton(onClick = {
                    scope.launch {
                        manager()?.post(
                            gid = room.gid,
                            pinTo = message.target(),
                            pinOp = CordnMessageReferences.PinOp.REMOVE,
                        )
                    }
                }) {
                    Text(stringRes(R.string.cordn_action_unpin), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(
    message: CordnDeliveredMessage,
    isMine: Boolean,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            ActionRow(stringRes(R.string.cordn_action_reply), onReply)
            // Pinning is any member's (§5.1), so it is offered on every
            // message rather than only on your own.
            ActionRow(
                label = stringRes(if (isPinned) R.string.cordn_action_unpin else R.string.cordn_action_pin),
                onClick = onTogglePin,
            )
            // Edit and delete are author-only, and the manager refuses them
            // for anyone else. Hiding them here is the same rule, stated
            // where it stops being a surprise.
            if (isMine) {
                ActionRow(stringRes(R.string.cordn_action_edit), onEdit)
                ActionRow(stringRes(R.string.cordn_action_delete), onDelete, isDestructive = true)
            }
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun ComposerBanner(
    label: String,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onCancel) { Text(stringRes(R.string.cancel), style = MaterialTheme.typography.labelSmall) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnChatTopBar(
    title: String,
    onInfo: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onInfo) {
                Icon(MaterialSymbols.Info, contentDescription = stringRes(R.string.cordn_group_info))
            }
        },
    )
}

@Composable
private fun CordnMessageRow(
    message: CordnDeliveredMessage,
    annotations: CordnAnnotationIndex,
    text: String?,
    isEdited: Boolean,
    onClick: () -> Unit,
    onReact: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = message.envelope.pubKey.take(8),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val thread = CordnMessageReferences.thread(message.envelope.tags)
        val parent = thread?.let { annotations.byId[it.parentId] }
        if (parent != null) {
            Text(
                text = stringRes(R.string.cordn_action_in_reply_to, annotations.contentOf(parent.envelope.id).orEmpty().take(60)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (text == null) {
            Text(
                text = stringRes(R.string.cordn_message_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (isEdited) {
                Text(
                    text = stringRes(R.string.cordn_message_edited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Reactions(annotations.reactions[message.envelope.id].orEmpty(), onReact)
    }
}

/**
 * The reaction chips, plus the one quick way to add one.
 *
 * A reaction is a set of pubkeys per emoji in the fold, so the count is the
 * set size — a member who reacted twice with the same emoji counts once, which
 * is what the index already guarantees and what a naive message count would
 * get wrong on a re-sync.
 */
@Composable
private fun Reactions(
    reactions: Map<String, Set<String>>,
    onReact: (String) -> Unit,
) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { (emoji, who) ->
            AssistChip(
                onClick = { onReact(emoji) },
                label = { Text("$emoji ${who.size}", style = MaterialTheme.typography.labelSmall) },
            )
        }
        if (reactions.isEmpty()) {
            TextButton(onClick = { onReact(DEFAULT_REACTION) }) {
                Text(DEFAULT_REACTION, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** What the one-tap reaction sends. Anything else goes through a picker later. */
private const val DEFAULT_REACTION = "\uD83D\uDC4D"

@Composable
private fun CordnComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(stringRes(R.string.cordn_composer_hint)) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = stringRes(R.string.cordn_send))
        }
    }
}
