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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.CordnGroupManager
import com.vitorpamplona.amethyst.commons.cordn.CordnMentions
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_group_untitled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.model.cordn.CordnMediaService
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordingResult
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceMessageRecorder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnBlobUpload
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnMediaAttachment
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One cordn room.
 *
 * Every kind `spec/02.md` defines can now be both sent and read here: text,
 * thread replies, reactions, edits, deletions and pins, plus encrypted
 * attachments and voice notes. The rule that got us here is worth keeping —
 * a composer must never be able to send a kind this room cannot render, or
 * the sender's own messages appear as blanks to them.
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

    val draft by room.draft.collectAsStateWithLifecycle()
    var replyingTo by remember { mutableStateOf<CordnDeliveredMessage?>(null) }
    var editing by remember { mutableStateOf<CordnDeliveredMessage?>(null) }
    var attaching by remember { mutableStateOf(false) }
    var attachError by remember { mutableStateOf<String?>(null) }

    // Why sending says anything at all when it fails: `manager()` is null-safe
    // all the way down, so a room whose coordinator has no open session
    // swallowed every send, reaction, edit, delete and pin without a word —
    // and the composer had already cleared the draft, so the text went with it.
    // From the outside that is indistinguishable from a message that was sent
    // and simply never arrived.
    var sendError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val uploadFailed = stringRes(R.string.cordn_media_upload_failed)
    val sendFailed = stringRes(R.string.cordn_send_failed)
    val noSession = stringRes(R.string.cordn_send_no_session)

    fun manager() =
        accountViewModel.account.cordnRuntime
            ?.sessionOrNull(room.coordinatorPubKey)
            ?.manager

    // One place for every outbound action, so none of them can go quiet again.
    // Returns false when it failed, which is what lets the composer put the
    // draft back rather than eat it.
    suspend fun trySend(block: suspend (CordnGroupManager) -> CordnDeliveredMessage?): Boolean {
        val manager = manager()
        if (manager == null) {
            sendError = noSession
            return false
        }
        return try {
            // Shown the moment the coordinator takes it, rather than when the
            // echo comes back — which, for your own traffic, it never does as
            // a message: the sync loop recognises it by cursor and reports it
            // as Delivery.Echo, whose branch adds nothing to the room. So a
            // sent message used to leave no trace in the room that sent it.
            //
            // add() is keyed on the envelope id and idempotent, so a later
            // re-sync that does hand the message back cannot double it.
            block(manager)?.let { room.add(it) }
            sendError = null
            true
        } catch (e: Exception) {
            Log.w("CordnGroupChat", "send failed in ${room.gid}: ${e.message}", e)
            sendError = e.message ?: sendFailed
            false
        }
    }

    val runtime = accountViewModel.account.cordnRuntime

    // Opening a room is what marks it read, and the read position and draft
    // are written when leaving it. Saving on every keystroke would rewrite an
    // encrypted file per character; saving on dispose loses nothing a process
    // death would not have lost anyway.
    DisposableEffect(room) {
        onDispose {
            room.markRead()
            runtime?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    it.saveRoomState(room.coordinatorPubKey, room.gid)
                }
            }
        }
    }

    LaunchedEffect(room) {
        runtime?.restoreRoomState(room.coordinatorPubKey, room.gid)
        room.markRead()
    }

    Scaffold(
        topBar = {
            CordnChatTopBar(
                title = name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, room.gid.take(8)),
                onInfo = { nav.nav(Route.CordnGroupInfo(room.coordinatorPubKey, room.gid)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned messages sit above the conversation rather than inside it.
            // A pin is a claim about a message's importance, not a message, and
            // leaving it only in place means the thing someone pinned scrolls
            // away exactly like everything else.
            PinnedRibbon(annotations, scope) { message ->
                trySend {
                    it.post(
                        gid = room.gid,
                        pinTo = message.target(),
                        pinOp = CordnMessageReferences.PinOp.REMOVE,
                    )
                }
            }

            LazyColumn(
                state = listState,
                // Anchored at the bottom like every other chat: a room opens on
                // its newest message, and an arrival while you sit at the
                // bottom keeps you there instead of pushing the conversation up
                // out of view. `messages` is oldest-first, so the rows are
                // reversed to match.
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                // Reversed once, into a val: `asReversed()` is a view, and
                // indexing it per item to find the neighbour is how a list
                // like this quietly becomes quadratic.
                val rows = messages.asReversed()

                itemsIndexed(rows, key = { _, it -> it.envelope.id }) { index, message ->
                    // `rows` runs newest-first and the list is reverse-laid-out,
                    // so the next index is the older message and renders above.
                    val older = rows.getOrNull(index + 1)
                    val newer = rows.getOrNull(index - 1)

                    CordnMessageRow(
                        message = message,
                        room = room,
                        annotations = annotations,
                        me = me,
                        // Grouped when the same person keeps talking inside the
                        // shared chat window: one avatar and name per burst
                        // instead of per line, and the bubbles of a burst square
                        // off against each other — which is most of what makes a
                        // wall of messages readable.
                        groupPosition =
                            remember(newer?.envelope?.id, message.envelope.id, older?.envelope?.id) {
                                cordnGroupPositionFor(newer, message, older)
                            },
                        // The edit if there is one, and nothing at all if the
                        // message was withdrawn: rendering the original text of
                        // a deleted message would defeat the deletion.
                        text = if (annotations.isDeleted(message.envelope.id)) null else annotations.contentOf(message.envelope.id),
                        isEdited = annotations.isEdited(message.envelope.id),
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onReply = {
                            replyingTo = message
                            editing = null
                        },
                        onEdit = {
                            editing = message
                            replyingTo = null
                            room.draft.value = annotations.contentOf(message.envelope.id).orEmpty()
                        },
                        onDelete = {
                            // Through trySend like the rest: a deletion is an
                            // annotation, and an annotation of your own comes back
                            // as an Echo too, so deleting your own message used to
                            // look like nothing had happened until someone else's
                            // traffic refreshed the fold.
                            scope.launch { trySend { it.post(room.gid, deleteTo = message.target()) } }
                        },
                        onTogglePin = {
                            val pinned = annotations.isPinned(message.envelope.id)
                            scope.launch {
                                trySend {
                                    it.post(
                                        gid = room.gid,
                                        pinTo = message.target(),
                                        pinOp = if (pinned) CordnMessageReferences.PinOp.REMOVE else CordnMessageReferences.PinOp.ADD,
                                    )
                                }
                            }
                        },
                        onReact = { emoji ->
                            scope.launch { trySend { it.post(room.gid, emoji, reactionTo = message.target()) } }
                        },
                    )

                    // Drawn under the first message of each day, which in a
                    // reversed list means comparing against the older row.
                    if (!message.sameDayAs(older)) {
                        DaySeparator(message.envelope.createdAt)
                    }
                }
            }

            HorizontalDivider()

            (attachError ?: sendError)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

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
                        room.draft.value = ""
                    },
                )
            }

            CordnComposer(
                draft = draft,
                onAttach = { uri ->
                    scope.launch {
                        attaching = true
                        attachError = null
                        try {
                            sendAttachment(context, accountViewModel, room, uri)
                        } catch (e: Exception) {
                            attachError = e.message ?: uploadFailed
                        } finally {
                            attaching = false
                        }
                    }
                },
                onVoiceNote = { recording ->
                    scope.launch {
                        attaching = true
                        attachError = null
                        try {
                            sendVoiceNote(context, accountViewModel, room, recording)
                        } catch (e: Exception) {
                            attachError = e.message ?: uploadFailed
                        } finally {
                            attaching = false
                        }
                    }
                },
                attaching = attaching,
                onDraftChange = { room.draft.value = it },
                onSend = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@CordnComposer

                    val reply = replyingTo
                    val edit = editing
                    room.draft.value = ""
                    replyingTo = null
                    editing = null

                    scope.launch {
                        val sent =
                            trySend {
                                it.post(
                                    gid = room.gid,
                                    content = text,
                                    replyTo = reply?.target(),
                                    editTo = edit?.target(),
                                )
                            }
                        if (!sent) {
                            // Hand the message back rather than lose it, and put
                            // the reply or edit it belonged to back with it, so
                            // trying again means pressing send and nothing else.
                            room.draft.value = text
                            replyingTo = reply
                            editing = edit
                        }
                    }
                },
            )
        }
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
    scope: CoroutineScope,
    // Hoisted rather than handed a manager: unpinning has to go through the
    // caller's trySend so it reports a failure and lands in the room, and a
    // ribbon that posted for itself could do neither.
    onUnpin: suspend (CordnDeliveredMessage) -> Unit,
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
                        onUnpin(message)
                    }
                }) {
                    Text(stringRes(R.string.cordn_action_unpin), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
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
        TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel), style = MaterialTheme.typography.labelSmall) }
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
private fun CordnComposer(
    draft: String,
    attaching: Boolean,
    onAttach: (Uri) -> Unit,
    onVoiceNote: (RecordingResult) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(onAttach)
        }

    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { picker.launch("*/*") }, enabled = !attaching) {
            Icon(MaterialSymbols.AttachFile, contentDescription = stringRes(R.string.cordn_media_attach))
        }
        VoiceNoteButton(enabled = !attaching, onRecorded = onVoiceNote)
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

/**
 * A message's text, with `nostr:` mentions rendered as names.
 *
 * Deliberately not Amethyst's rich-text renderer: that one is built on `Note`,
 * and a `Note` comes from `LocalCache`. Nothing a cordn room receives may go
 * in there — see the screen KDoc. `CordnMentions` splits the envelope's own
 * content instead, and this walks the result.
 *
 * Looking a mentioned pubkey up for a display name is a different thing and a
 * safe one: a profile is public relay data the cache already holds, and
 * reading one puts no part of this conversation into it.
 */
@Composable
internal fun MessageBody(
    text: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val segments = remember(text) { CordnMentions.segment(text) }

    if (segments.none { it is CordnMentions.Segment.Mention }) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        return
    }

    FlowRow(verticalArrangement = Arrangement.Center) {
        segments.forEach { segment ->
            when (segment) {
                is CordnMentions.Segment.Text ->
                    Text(segment.value, style = MaterialTheme.typography.bodyMedium)

                is CordnMentions.Segment.Mention -> {
                    val name by observeUserName(LocalCache.getOrCreateUser(segment.pubKey), accountViewModel)
                    Text(
                        text = "@$name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { nav.nav(Route.Profile(segment.pubKey)) },
                    )
                }
            }
        }
    }
}

/**
 * Encrypts the picked file and sends it as an attachment on an empty message.
 *
 * Reading the bytes into memory rather than streaming: the codec authenticates
 * the whole file with one AEAD tag and hashes the plaintext, both of which
 * need every byte anyway, and a group chat attachment is not a video archive.
 */
private suspend fun sendAttachment(
    context: Context,
    accountViewModel: AccountViewModel,
    room: CordnGroupChatroom,
    uri: Uri,
) {
    val session = accountViewModel.account.cordnRuntime?.sessionOrNull(room.coordinatorPubKey) ?: return
    val group = session.manager.group(room.gid) ?: return

    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: CordnBlobUpload.OPAQUE
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: return

    val tag = CordnMediaService(accountViewModel.account).upload(bytes, mime, name, context) ?: return
    // Into the room as well, for the same reason every other send is: an
    // attachment of your own echoes back as an Echo and would otherwise be
    // invisible to the person who sent it.
    room.add(session.manager.send(room.gid, content = "", tags = arrayOf(tag)))
}

/**
 * One attachment, fetched and decrypted on demand.
 *
 * Never automatically. A cordn attachment lives on a blob host that is not the
 * coordinator and not a relay, and fetching one tells that host a specific
 * person opened a specific message at a specific time. Auto-loading would make
 * that happen for every message that scrolls past, which is exactly the leak
 * an end-to-end encrypted group is supposed to avoid — so the first tap is the
 * user's.
 */
@Composable
internal fun CordnAttachment(
    attachment: CordnMediaAttachment,
    room: CordnGroupChatroom,
    accountViewModel: AccountViewModel,
) {
    val scope = rememberCoroutineScope()
    var bytes by remember(attachment.url) { mutableStateOf<ByteArray?>(null) }
    var loading by remember(attachment.url) { mutableStateOf(false) }
    var error by remember(attachment.url) { mutableStateOf<String?>(null) }
    val failed = stringRes(R.string.cordn_media_download_failed)

    val image = remember(bytes) { bytes?.takeIf { attachment.isImage }?.toImageBitmapOrNull() }

    Column(Modifier.padding(top = 6.dp)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.filename,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val session = accountViewModel.account.cordnRuntime?.sessionOrNull(room.coordinatorPubKey)
                            val group = session?.manager?.group(room.gid)
                            if (group == null) {
                                error = failed
                            } else {
                                bytes = CordnMediaService(accountViewModel.account).download(attachment)
                            }
                        } catch (e: Exception) {
                            error = e.message ?: failed
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
            ) {
                Icon(MaterialSymbols.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = stringRes(R.string.cordn_media_open, attachment.filename),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        val audio = bytes
        if (audio != null && attachment.isAudio) {
            VoiceNotePlayer(audio, attachment.filename)
        } else if (bytes != null && image == null) {
            // Nothing to render for an arbitrary file, and claiming success
            // with nothing on screen reads as a broken message.
            Text(
                text = stringRes(R.string.cordn_media_opened, attachment.filename),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Decoded bytes as a bitmap, or null when they are not an image this device reads. */
private fun ByteArray.toImageBitmapOrNull(): ImageBitmap? =
    try {
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

/**
 * Hold to record, release to send.
 *
 * The permission is requested on the first press rather than when the room
 * opens: opening a chat is not consent to use the microphone, and a dialog
 * that appears before anyone reached for it trains people to dismiss it.
 */
@Composable
private fun VoiceNoteButton(
    enabled: Boolean,
    onRecorded: (RecordingResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceMessageRecorder() }
    var recording by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasMicPermission(context)) }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    IconButton(
        enabled = enabled,
        onClick = {
            if (!granted) {
                permission.launch(Manifest.permission.RECORD_AUDIO)
                return@IconButton
            }
            if (recording) {
                recording = false
                // Null when the press was too short to be a message. Dropping
                // it silently is right: an accidental tap should not send a
                // zero-second voice note to a group.
                recorder.stop()?.let(onRecorded)
            } else {
                recording = true
                recorder.start(context, scope)
            }
        },
    ) {
        Icon(
            symbol = if (recording) MaterialSymbols.Stop else MaterialSymbols.Mic,
            contentDescription = stringRes(if (recording) R.string.cordn_voice_stop else R.string.cordn_voice_record),
            tint = if (recording) MaterialTheme.colorScheme.error else LocalContentColor.current,
        )
    }
}

private fun hasMicPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Sends a recording through exactly the same encrypted path as any other file.
 *
 * A voice note is a file: same codec, same Blossom upload, same `imeta`
 * descriptor inside the envelope. The only cordn-specific part is deleting
 * the cache file afterwards — the recorder writes plaintext audio to
 * `cacheDir`, and leaving it there would keep an unencrypted copy of a
 * message that was end-to-end encrypted everywhere else.
 */
private suspend fun sendVoiceNote(
    context: Context,
    accountViewModel: AccountViewModel,
    room: CordnGroupChatroom,
    recording: RecordingResult,
) {
    val session = accountViewModel.account.cordnRuntime?.sessionOrNull(room.coordinatorPubKey) ?: return
    val group = session.manager.group(room.gid) ?: return

    try {
        val bytes = withContext(Dispatchers.IO) { recording.file.readBytes() }
        val tag =
            CordnMediaService(accountViewModel.account)
                .upload(bytes, recording.mimeType, recording.file.name, context) ?: return
        // Into the room as well, for the same reason every other send is: an
        // attachment of your own echoes back as an Echo and would otherwise be
        // invisible to the person who sent it.
        room.add(session.manager.send(room.gid, content = "", tags = arrayOf(tag)))
    } finally {
        withContext(Dispatchers.IO) { recording.file.delete() }
    }
}

/**
 * Plays decrypted audio from memory, via a cache file the player can open.
 *
 * `MediaPlayer` cannot take a byte array, so the plaintext has to touch disk.
 * It goes to a file this composable owns and deletes on dispose, rather than
 * anywhere durable: the whole point of the codec above is that the only
 * lasting copy of this audio is the ciphertext on the blob host.
 */
@Composable
private fun VoiceNotePlayer(
    bytes: ByteArray,
    filename: String,
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }

    val scratch =
        remember(bytes) {
            File(context.cacheDir, "cordn-voice")
                .apply { mkdirs() }
                .let { File(it, "${bytes.contentHashCode()}-$filename") }
                .also { it.writeBytes(bytes) }
        }

    val player =
        remember(scratch) {
            MediaPlayer().apply {
                setDataSource(scratch.absolutePath)
                prepare()
                setOnCompletionListener { playing = false }
            }
        }

    DisposableEffect(player) {
        onDispose {
            player.release()
            scratch.delete()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) {
                player.pause()
                playing = false
            } else {
                player.start()
                playing = true
            }
        }) {
            Icon(
                symbol = if (playing) MaterialSymbols.Stop else MaterialSymbols.PlayArrow,
                contentDescription = stringRes(R.string.cordn_voice_play),
            )
        }
        Text(filename, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
