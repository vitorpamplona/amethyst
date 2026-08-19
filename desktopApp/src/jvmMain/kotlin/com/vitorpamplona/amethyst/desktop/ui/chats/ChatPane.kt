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
package com.vitorpamplona.amethyst.desktop.ui.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.viewmodels.ChatNewMessageState
import com.vitorpamplona.amethyst.commons.viewmodels.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.desktop.ImageCompressionStore
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DEFAULT_BLOSSOM_SERVER
import com.vitorpamplona.amethyst.desktop.ui.LocalBlossomServers
import com.vitorpamplona.amethyst.desktop.ui.chats.composer.EmojiPickerPanel
import com.vitorpamplona.amethyst.desktop.ui.chats.composer.EmojiSuggestion
import com.vitorpamplona.amethyst.desktop.ui.chats.composer.EmojiSuggestionStrip
import com.vitorpamplona.amethyst.desktop.ui.chats.composer.GifPickerPanel
import com.vitorpamplona.amethyst.desktop.ui.chats.composer.MentionSuggestions
import com.vitorpamplona.amethyst.desktop.ui.chats.composer.standardEmojis
import com.vitorpamplona.amethyst.desktop.ui.components.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopFilePicker
import com.vitorpamplona.amethyst.desktop.ui.media.MediaAttachmentRow
import com.vitorpamplona.amethyst.desktop.ui.media.QualitySelectorChip
import com.vitorpamplona.amethyst.desktop.ui.note.DesktopRichText
import com.vitorpamplona.amethyst.desktop.ui.note.RichTextCallbacks
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.messages.changeSubject
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

private val MEDIA_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "avif", "mp4", "webm", "mov", "mp3", "ogg", "wav", "flac")

// Reencodable still-image types — gate the quality selector AND per-file
// reencoding on these. GIF is excluded (animated GIFs pass through unchanged);
// AVIF/HEIC are excluded because the reencoder rejects them (it would throw and
// abort the send) — they upload as-is instead.
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/**
 * Right panel of the DM split-pane layout (flexible width).
 *
 * Displays:
 * - ChatroomHeader at top (shared component from commons)
 * - Message list (LazyColumn, reversed - newest at bottom, auto-scroll)
 * - Message input at bottom with Send button and NIP-17 toggle
 *
 * @param roomKey The chatroom key for the selected conversation
 * @param account The user's account (IAccount)
 * @param cacheProvider The cache provider for user/note lookups
 * @param feedViewModel ChatroomFeedViewModel for message data
 * @param messageState ChatNewMessageState for composition
 * @param onNavigateToProfile Called when user clicks on a profile
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatPane(
    roomKey: ChatroomKey,
    account: IAccount,
    cacheProvider: ICacheProvider,
    feedViewModel: ChatroomFeedViewModel,
    messageState: ChatNewMessageState,
    emojiPacks: EmojiPackState? = null,
    attachedFiles: SnapshotStateList<File>,
    dmBroadcastStatus: DmBroadcastStatus = DmBroadcastStatus.Idle,
    onNavigateToProfile: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Account's Blossom media servers (kind 10063), read from context.
    val blossomServers = LocalBlossomServers.current
    val feedState by feedViewModel.feedState.feedContent.collectAsState()
    val messageText by messageState.message.collectAsState()
    val recipientsMissingRelays by messageState.recipientsMissingDmRelays.collectAsState()
    val replyTo by messageState.replyTo.collectAsState()

    // `:shortcode:` autocomplete: match the word under the caret against the
    // account's selected NIP-30 packs AND standard unicode emoji shortcodes.
    val myEmojis =
        if (emojiPacks != null) {
            emojiPacks.myEmojis.collectAsState().value
        } else {
            emptyList()
        }
    val currentWord = messageText.currentWord()
    val emojiSuggestions =
        remember(currentWord, myEmojis) {
            if (currentWord.startsWith(":") && currentWord.length > 1) {
                val code = currentWord.removePrefix(":")
                val custom =
                    myEmojis
                        .filter { it.code.startsWith(code, ignoreCase = true) }
                        .map { EmojiSuggestion(it.code, ":${it.code}:", previewUrl = it.link, previewGlyph = null) }
                val standard =
                    standardEmojis.mapNotNull { emoji ->
                        val alias = emoji.details.aliases.firstOrNull { it.startsWith(code, ignoreCase = true) }
                        alias?.let {
                            EmojiSuggestion(it, emoji.details.string, previewUrl = null, previewGlyph = emoji.details.string)
                        }
                    }
                (custom + standard).take(30)
            } else {
                emptyList()
            }
        }

    // @mention autocomplete: search the cache for users matching the @word under
    // the caret (done off the composition to avoid a cache scan every keystroke).
    var mentionSuggestions by remember { mutableStateOf<List<User>>(emptyList()) }
    LaunchedEffect(currentWord) {
        mentionSuggestions =
            if (currentWord.startsWith("@") && currentWord.length > 1) {
                cacheProvider.findUsersStartingWith(currentWord.removePrefix("@"), 5)
            } else {
                emptyList()
            }
    }

    // File attachment state is hoisted above the lock gate (see
    // DeckColumnContainer) so it survives a lock/unlock cycle.
    var isUploading by remember { mutableStateOf(false) }
    // True for the whole duration of a send (upload + publish) — drives the
    // send-button spinner.
    var isSending by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Image compression: shared global default + optional per-composer override,
    // reusing the same store the note composer uses. Override resets after send.
    val defaultQuality by ImageCompressionStore.quality.collectAsState()
    val stripExifSetting by ImageCompressionStore.stripExif.collectAsState()
    val declareRealMimeType by ImageCompressionStore.encryptedMediaRealType.collectAsState()
    var qualityOverride by remember { mutableStateOf<CompressionQuality?>(null) }
    val activeQuality = qualityOverride ?: defaultQuality
    val hasImageAttachment = attachedFiles.any { it.extension.lowercase() in IMAGE_EXTENSIONS }

    // Helper: attach files
    fun attachFiles(files: List<File>) {
        val mediaFiles = files.filter { it.extension.lowercase() in MEDIA_EXTENSIONS }
        if (mediaFiles.isEmpty()) return
        attachedFiles.addAll(mediaFiles)
    }

    // Drag-and-drop target for file attachments (NIP-17 only)
    var isDragOver by remember { mutableStateOf(false) }
    val dropTarget =
        remember {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isDragOver = false
                    val dropEvent = event.nativeEvent as? DropTargetDropEvent ?: return false
                    dropEvent.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dropEvent.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        attachFiles(files)
                        dropEvent.dropComplete(true)
                        return true
                    }
                    dropEvent.dropComplete(false)
                    return false
                }

                override fun onStarted(event: DragAndDropEvent) {
                    isDragOver = true
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isDragOver = false
                }
            }
        }

    // Resolve users for the header
    val users = roomKey.users.mapNotNull { cacheProvider.getUserIfExists(it) }
    val isGroup = users.size > 1

    // NIP-14 group subject/name, updated reactively as subject-tagged messages arrive.
    val subjectFlow = remember(roomKey) { account.chatroomList.getOrCreatePrivateChatroom(roomKey).subject }
    val subject by subjectFlow.collectAsState()
    var showSubjectDialog by remember { mutableStateOf(false) }

    // Load room into message state
    LaunchedEffect(roomKey) {
        messageState.load(roomKey)
    }

    if (showSubjectDialog) {
        GroupSubjectDialog(
            roomKey = roomKey,
            currentSubject = subject ?: "",
            account = account,
            cacheProvider = cacheProvider,
            onClose = { showSubjectDialog = false },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = dropTarget,
                    ).then(
                        if (isDragOver) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = "Back to conversations",
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isGroup) {
                        GroupChatroomHeader(
                            users = users,
                            subject = subject,
                            onClick = { users.firstOrNull()?.let { onNavigateToProfile(it.pubkeyHex) } },
                        )
                    } else {
                        users.firstOrNull()?.let { user ->
                            ChatroomHeader(
                                user = user,
                                onClick = { onNavigateToProfile(user.pubkeyHex) },
                            )
                        } ?: run {
                            // Fallback header with raw pubkey
                            Text(
                                text = roomKey.users.firstOrNull()?.take(20) ?: "Unknown",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }

                // Rename group (set NIP-14 subject) — groups only
                if (isGroup) {
                    IconButton(
                        onClick = { showSubjectDialog = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            MaterialSymbols.Edit,
                            contentDescription = "Rename group",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()

            // Broadcast status banner
            DmBroadcastBanner(status = dmBroadcastStatus)

            // Message list
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (feedState) {
                    is FeedState.Loading -> {
                        LoadingState("Loading messages...")
                    }

                    is FeedState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No messages yet. Send the first one!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is FeedState.Loaded -> {
                        val loaded = feedState as FeedState.Loaded
                        val loadedState by loaded.feed.collectAsState()
                        val messages = loadedState.list

                        MessageList(
                            messages = messages,
                            account = account,
                            cacheProvider = cacheProvider,
                            onAuthorClick = onNavigateToProfile,
                            onReaction = { note, emoji ->
                                scope.launch {
                                    try {
                                        sendWrappedReaction(note, emoji, roomKey, account)
                                    } catch (e: Exception) {
                                        println("Failed to send reaction: ${e.message}")
                                    }
                                }
                            },
                            onReply = { messageState.setReply(it) },
                        )
                    }

                    is FeedState.FeedError -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Error loading messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // File attachment row (only when NIP-17 and files attached)
            if (attachedFiles.isNotEmpty()) {
                MediaAttachmentRow(
                    attachedFiles = attachedFiles,
                    isUploading = isUploading,
                    onAttach = { attachFiles(DesktopFilePicker.pickMediaFiles()) },
                    onPaste = {},
                    onRemove = { attachedFiles.remove(it) },
                )
                // Compression quality selector — only meaningful for reencodable images.
                if (hasImageAttachment) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        QualitySelectorChip(
                            activeQuality = activeQuality,
                            isOverride = qualityOverride != null,
                            onSelect = { qualityOverride = it },
                            onReset = { qualityOverride = null },
                        )
                    }
                }
            }

            // @mention autocomplete dropdown
            MentionSuggestions(
                suggestions = mentionSuggestions,
                onPick = { user ->
                    onMentionPicked(messageState, messageText, user)
                    mentionSuggestions = emptyList()
                },
            )

            // `:shortcode:` autocomplete strip (custom packs + standard emoji)
            EmojiSuggestionStrip(
                suggestions = emojiSuggestions,
                onPick = { suggestion ->
                    messageState.updateMessage(messageText.replaceCurrentWord(suggestion.insertText))
                },
            )

            // Reply-quote preview (NIP-17 threaded reply target)
            replyTo?.let { replyNote ->
                ReplyPreviewBar(
                    note = replyNote,
                    account = account,
                    onDismiss = { messageState.clearReply() },
                )
            }

            // Message input
            MessageInput(
                messageText = messageText,
                recipientsMissingRelays = recipientsMissingRelays,
                canSend = messageState.canSend || attachedFiles.isNotEmpty(),
                isUploading = isUploading,
                isSending = isSending,
                hasAttachments = attachedFiles.isNotEmpty(),
                onMessageChange = { messageState.updateMessage(it) },
                onAttach = { attachFiles(DesktopFilePicker.pickMediaFiles()) },
                onSend = {
                    scope.launch {
                        isSending = true
                        try {
                            if (attachedFiles.isNotEmpty()) {
                                isUploading = true
                                try {
                                    sendEncryptedFiles(
                                        files = attachedFiles.toList(),
                                        roomKey = roomKey,
                                        account = account,
                                        cacheProvider = cacheProvider,
                                        blossomServers = blossomServers,
                                        quality = activeQuality,
                                        stripExif = stripExifSetting,
                                        declareRealMimeType = declareRealMimeType,
                                    )
                                    attachedFiles.clear()
                                    qualityOverride = null
                                } catch (e: Exception) {
                                    // Keep files in the attachment row for retry and
                                    // surface the reason (console-only failures were
                                    // invisible during testing).
                                    println("Encrypted file send failed: ${e.message}")
                                    val msg = e.message ?: e::class.simpleName.orEmpty()
                                    val hint =
                                        when {
                                            // Server inspects the bytes and rejects the encrypted blob
                                            // no matter what type we declare — it can't host private files.
                                            "does not match" in msg || "expected application/json" in msg ->
                                                " — this media server inspects uploads and can't host encrypted DM files. Switch to nostr.download in Media settings."
                                            "415" in msg || "media type" in msg || "file type" in msg ->
                                                " — this media server rejects private (opaque) uploads. Switch to one that accepts them (e.g. nostr.download), or enable “Reveal media type on encrypted DM uploads” in Media settings."
                                            else -> ""
                                        }
                                    snackbarHostState.showSnackbar("Couldn't send attachment: $msg$hint")
                                } finally {
                                    isUploading = false
                                }
                            }
                            // Also send text message if present. Wrap in try/catch:
                            // an uncaught throw here (e.g. a relay/signer error after
                            // the message was already published) previously skipped
                            // clear(), leaving the sent text/GIF stuck in the input.
                            if (messageState.canSend) {
                                try {
                                    if (messageState.send()) {
                                        messageState.clear()
                                    }
                                } catch (e: Exception) {
                                    println("DM text send failed: ${e.message}")
                                    // The message may already have been broadcast, so
                                    // clear the composer and report the error rather
                                    // than risk a duplicate on retry.
                                    messageState.clear()
                                    snackbarHostState.showSnackbar(
                                        "Message sent, but a relay reported: ${e.message ?: e::class.simpleName}",
                                    )
                                }
                            } else if (attachedFiles.isEmpty()) {
                                messageState.clear()
                            }
                        } finally {
                            isSending = false
                        }
                    }
                },
            )
        } // end Column

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    } // end Box
}

/**
 * Scrollable message list, reversed so newest messages appear at the bottom.
 */
@Composable
private fun MessageList(
    messages: List<Note>,
    account: IAccount,
    cacheProvider: ICacheProvider,
    onAuthorClick: (String) -> Unit,
    onReaction: (Note, String) -> Unit = { _, _ -> },
    onReply: (Note) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Message the user just jumped to via a reply preview — briefly highlighted.
    var highlightedNoteId by remember { mutableStateOf<String?>(null) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Scroll to the referenced message and flash it. No-op if it isn't loaded
    // in the current window (older than what's on screen).
    fun jumpToMessage(noteId: String) {
        val index = messages.indexOfFirst { it.idHex == noteId }
        if (index < 0) return
        scope.launch {
            listState.animateScrollToItem(index)
            highlightedNoteId = noteId
            delay(1600)
            if (highlightedNoteId == noteId) highlightedNoteId = null
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
    ) {
        items(messages, key = { it.idHex }) { note ->
            val isMe = note.author?.pubkeyHex == account.pubKey
            val isDraft = note.isDraft()

            MessageWithReactions(
                note = note,
                isMe = isMe,
                isDraft = isDraft,
                account = account,
                cacheProvider = cacheProvider,
                isHighlighted = note.idHex == highlightedNoteId,
                onAuthorClick = onAuthorClick,
                onReaction = { emoji -> onReaction(note, emoji) },
                onReply = { onReply(note) },
                onReplyContextClick = { parentId -> jumpToMessage(parentId) },
            )
        }
    }
}

/**
 * Common reaction emojis for quick access.
 */
private val QUICK_REACTIONS =
    listOf(
        "\uD83D\uDC4D", // thumbs up
        "\u2764\uFE0F", // red heart
        "\uD83D\uDE02", // face with tears of joy
        "\uD83D\uDD25", // fire
    )

/**
 * Wraps a ChatMessageCompose with reaction icon in the detail row
 * and displays existing reactions below the bubble.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MessageWithReactions(
    note: Note,
    isMe: Boolean,
    isDraft: Boolean,
    account: IAccount,
    cacheProvider: ICacheProvider,
    isHighlighted: Boolean = false,
    onAuthorClick: (String) -> Unit,
    onReaction: (String) -> Unit,
    onReply: () -> Unit = {},
    onReplyContextClick: (String) -> Unit = {},
) {
    var isHovered by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val showIcon = (isHovered || showPicker) && !isDraft

    // Reply-jump highlight: a brief bounce-in scale + background flash when the
    // user taps a reply preview that points at this message.
    val highlightScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "replyHighlightScale",
    )
    val highlightBg by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "replyHighlightBg",
    )

    // The DM cache doesn't link Note.replyTo for chat messages, so resolve the
    // replied-to parent from the event's reply e-tag directly.
    val replyParent =
        remember(note.idHex) {
            val replyId =
                when (val e = note.event) {
                    is ChatMessageEvent -> e.replyTo().lastOrNull()
                    is ChatMessageEncryptedFileHeaderEvent -> e.replyTo().lastOrNull()
                    else -> null
                }
            replyId?.let { cacheProvider.getNoteIfExists(it) }
        }

    // Decrypt NIP-04 content asynchronously; NIP-17 content is already plaintext
    val event = note.event
    var decryptedContent by remember(note.idHex) { mutableStateOf<String?>(null) }

    LaunchedEffect(note.idHex, event) {
        decryptedContent =
            when (event) {
                is PrivateDmEvent -> {
                    try {
                        event.decryptContent(account.signer)
                    } catch (_: Exception) {
                        null
                    }
                }

                else -> {
                    event?.content
                }
            }
    }

    // Observe reaction changes
    val reactions = note.reactions

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        Column(
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = highlightScale
                        scaleY = highlightScale
                    }.clip(RoundedCornerShape(8.dp))
                    .background(highlightBg),
        ) {
            ChatMessageCompose(
                note = note,
                isLoggedInUser = isMe,
                isDraft = isDraft,
                isComplete = true,
                hasDetailsToShow = reactions.isNotEmpty(),
                drawAuthorInfo = !isMe,
                onClick = { false },
                onAuthorClick = {
                    note.author?.pubkeyHex?.let { onAuthorClick(it) }
                },
                authorLine = {
                    note.author?.let { author ->
                        Text(
                            text = author.toBestDisplayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                detailRow = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Encryption badge
                        when (event) {
                            is PrivateDmEvent -> {
                                Icon(
                                    MaterialSymbols.LockOpen,
                                    contentDescription = "NIP-04 (legacy)",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                            }

                            is com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent,
                            is ChatMessageEncryptedFileHeaderEvent,
                            -> {
                                Icon(
                                    MaterialSymbols.Lock,
                                    contentDescription = "NIP-17 (encrypted)",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                )
                            }
                        }

                        // Timestamp
                        note.createdAt()?.let { timestamp ->
                            ToggleableTimeAgoText(
                                timestamp = timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }

                        // Reaction counts
                        reactions.forEach { (emoji, notes) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text =
                                        if (notes.size > 1) {
                                            "$emoji ${notes.size}"
                                        } else {
                                            emoji
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        // Reply icon: same reserve-space + fade-on-hover treatment as
                        // the reaction button. Sets the composer's reply target.
                        Box(modifier = Modifier.alpha(if (showIcon) 1f else 0f)) {
                            IconButton(
                                onClick = onReply,
                                enabled = showIcon,
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.Reply,
                                    contentDescription = "Reply",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // AddReaction icon: always laid out to reserve space (so hover
                        // doesn't reflow the detail row and shift the whole list);
                        // alpha fades in/out based on hover.
                        Box(modifier = Modifier.alpha(if (showIcon) 1f else 0f)) {
                            IconButton(
                                onClick = { showPicker = !showPicker },
                                enabled = showIcon,
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.AddReaction,
                                    contentDescription = "React",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (showPicker) {
                                Popup(
                                    alignment = Alignment.TopCenter,
                                    offset = IntOffset(0, -44),
                                    onDismissRequest = { showPicker = false },
                                    properties = PopupProperties(focusable = true),
                                ) {
                                    ReactionBar(
                                        onReaction = { emoji ->
                                            onReaction(emoji)
                                            showPicker = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { _ ->
                Column {
                    // Replied-to message preview (tap to jump + highlight).
                    replyParent?.let { parent ->
                        MessageReplyContext(
                            parent = parent,
                            account = account,
                            onClick = { onReplyContextClick(parent.idHex) },
                        )
                    }
                    when (note.event) {
                        is ChatMessageEncryptedFileHeaderEvent -> {
                            ChatFileAttachment(event = note.event as ChatMessageEncryptedFileHeaderEvent)
                        }

                        else -> {
                            val content = decryptedContent
                            if (content != null) {
                                // Rich rendering so nostr:npub mentions resolve to
                                // names, and image/GIF URLs (incl. picked GIFs) and
                                // custom emoji render inline instead of as raw text.
                                val tags =
                                    remember(note.idHex) {
                                        note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList
                                    }
                                val richState =
                                    remember(content, tags) {
                                        CachedRichTextParser.parseText(content, tags)
                                    }
                                DesktopRichText(
                                    content = content,
                                    state = richState,
                                    localCache = cacheProvider as? DesktopLocalCache,
                                    callbacks = RichTextCallbacks(onMentionClick = onAuthorClick),
                                )
                            } else {
                                Text(
                                    text = "Could not decrypt the message",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Small "replying to …" preview shown at the top of a reply message bubble.
 * Shows the quoted author + a snippet; tapping it asks the list to jump to and
 * highlight the original message. Mirrors the reply chips in Signal/WhatsApp/
 * Telegram.
 */
@Composable
private fun MessageReplyContext(
    parent: Note,
    account: IAccount,
    onClick: () -> Unit,
) {
    var preview by remember(parent.idHex) { mutableStateOf("") }
    LaunchedEffect(parent.idHex) {
        preview =
            when (val e = parent.event) {
                is ChatMessageEncryptedFileHeaderEvent -> "📎 Attachment"
                is PrivateDmEvent ->
                    try {
                        e.decryptContent(account.signer)
                    } catch (_: Exception) {
                        ""
                    }

                else -> e?.content ?: ""
            }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = parent.author?.toBestDisplayName() ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Sends a NIP-17 gift-wrapped reaction to a note within a group DM.
 */
private suspend fun sendWrappedReaction(
    note: Note,
    emoji: String,
    roomKey: ChatroomKey,
    account: IAccount,
) {
    val event = note.event
    if (event == null) {
        println("sendWrappedReaction: note.event is null for ${note.idHex}")
        return
    }
    val eventBundle = EventHintBundle(event)
    val recipients = roomKey.users.toList()
    println("sendWrappedReaction: sending '$emoji' to ${recipients.size} recipients for event ${event.id.take(8)}")

    val result =
        NIP17Factory().createReactionWithinGroup(
            content = emoji,
            originalNote = eventBundle,
            to = recipients,
            signer = account.signer,
        )

    println("sendWrappedReaction: created ${result.wraps.size} wraps, broadcasting...")
    account.sendGiftWraps(result.wraps)
}

/**
 * Floating row of quick emoji reaction buttons.
 */
@Composable
private fun ReactionBar(onReaction: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                TextButton(
                    onClick = { onReaction(emoji) },
                    modifier = Modifier.size(32.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(0.dp),
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Preview bar shown above the composer when a message is selected as the reply
 * target. Mirrors Android's DisplayReplyingToNote: quoted author + snippet +
 * dismiss. On send, [ChatNewMessageState] threads the reply via
 * ChatMessageEvent.reply().
 */
@Composable
private fun ReplyPreviewBar(
    note: Note,
    account: IAccount,
    onDismiss: () -> Unit,
) {
    val event = note.event
    var preview by remember(note.idHex) { mutableStateOf("") }
    LaunchedEffect(note.idHex, event) {
        preview =
            when (event) {
                is ChatMessageEncryptedFileHeaderEvent -> "📎 Attachment"
                is PrivateDmEvent ->
                    try {
                        event.decryptContent(account.signer)
                    } catch (_: Exception) {
                        ""
                    }

                else -> event?.content ?: ""
            }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Reply,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.author?.toBestDisplayName() ?: "Unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = "Cancel reply",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Message input area at the bottom of the chat pane.
 */
@Composable
private fun MessageInput(
    messageText: TextFieldValue,
    recipientsMissingRelays: Boolean,
    canSend: Boolean,
    isUploading: Boolean = false,
    isSending: Boolean = false,
    hasAttachments: Boolean = false,
    onMessageChange: (TextFieldValue) -> Unit,
    onAttach: () -> Unit = {},
    onSend: () -> Unit,
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Paperclip attach button — always visible, auto-switches to NIP-17 on send
            IconButton(
                onClick = onAttach,
                enabled = !isUploading,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    MaterialSymbols.AttachFile,
                    contentDescription = "Attach file",
                    tint =
                        if (isUploading) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }

            // BasicTextField + DecorationBox with custom contentPadding lets the
            // field sit at a compact ~40dp minimum on desktop (M3's default
            // OutlinedTextField has ~32dp vertical contentPadding baked in, which
            // would clip the bodyMedium placeholder at any height below ~52dp).
            val messageInteraction =
                remember {
                    androidx.compose.foundation.interaction
                        .MutableInteractionSource()
                }
            androidx.compose.foundation.text.BasicTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            // Cmd+Enter (Mac) or Ctrl+Enter to send
                            val hasModifier = if (isMacOS) event.isMetaPressed else event.isCtrlPressed
                            if (event.key == Key.Enter && hasModifier) {
                                if (canSend) onSend()
                                true
                            } else {
                                false
                            }
                        },
                textStyle =
                    MaterialTheme.typography.bodyMedium
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush =
                    androidx.compose.ui.graphics
                        .SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                interactionSource = messageInteraction,
                decorationBox = { innerTextField ->
                    androidx.compose.material3.OutlinedTextFieldDefaults.DecorationBox(
                        value = messageText.text,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = false,
                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                        interactionSource = messageInteraction,
                        placeholder = {
                            Text(
                                "Message\u2026 (${if (isMacOS) "\u2318" else "Ctrl"}+Enter to send)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 8.dp,
                            ),
                        container = {
                            androidx.compose.material3.OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = false,
                                interactionSource = messageInteraction,
                                shape = RoundedCornerShape(10.dp),
                            )
                        },
                    )
                },
            )

            // Emoji picker
            Box {
                IconButton(
                    onClick = { showEmojiPicker = !showEmojiPicker },
                    enabled = !isUploading,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.EmojiEmotions,
                        contentDescription = "Emoji",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (showEmojiPicker) {
                    Popup(
                        alignment = Alignment.BottomEnd,
                        offset = IntOffset(0, -48),
                        onDismissRequest = { showEmojiPicker = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        EmojiPickerPanel(
                            onPick = { emoji ->
                                onMessageChange(insertAtCursor(messageText, emoji))
                            },
                        )
                    }
                }
            }

            // GIF picker (Nostr-native, NIP-94)
            Box {
                TextButton(
                    onClick = { showGifPicker = !showGifPicker },
                    enabled = !isUploading,
                    modifier = Modifier.size(width = 44.dp, height = 40.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(0.dp),
                ) {
                    Text(
                        text = "GIF",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (showGifPicker) {
                    Popup(
                        alignment = Alignment.BottomEnd,
                        offset = IntOffset(0, -48),
                        onDismissRequest = { showGifPicker = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        GifPickerPanel(
                            onPick = { url ->
                                onMessageChange(messageText.insertUrlAtCursor(url))
                                showGifPicker = false
                            },
                        )
                    }
                }
            }

            IconButton(
                onClick = onSend,
                enabled = canSend && !isSending,
                modifier = Modifier.size(40.dp),
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        MaterialSymbols.AutoMirrored.Send,
                        contentDescription = "Send",
                        tint =
                            if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                    )
                }
            }
        }

        // NIP-17 indicator / recipient warning
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Lock,
                contentDescription = "NIP-17 (encrypted)",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "NIP-17",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (recipientsMissingRelays) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Recipient has no DM relay list — messages cannot be delivered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Insert [insert] at the caret (replacing any selection) and place the caret
 * immediately after it. Shared by the emoji picker, custom-emoji / mention
 * autocomplete, and GIF picker to write into the composer's TextFieldValue.
 */
internal fun insertAtCursor(
    current: TextFieldValue,
    insert: String,
): TextFieldValue {
    val sel = current.selection
    val newText = current.text.replaceRange(sel.min, sel.max, insert)
    val cursor = sel.min + insert.length
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}

/**
 * Replaces the `@word` under the caret with a `nostr:npub…` reference (plus a
 * trailing space), mirroring the note composer's mention insertion.
 */
private fun onMentionPicked(
    messageState: ChatNewMessageState,
    current: TextFieldValue,
    user: User,
) {
    messageState.updateMessage(current.replaceCurrentWord("nostr:${user.pubkeyNpub()} "))
}

/**
 * Encrypts and uploads files, then sends each as a ChatMessageEncryptedFileHeaderEvent (kind 15)
 * wrapped in GiftWrap for each recipient.
 */
private suspend fun sendEncryptedFiles(
    files: List<File>,
    roomKey: ChatroomKey,
    account: IAccount,
    cacheProvider: ICacheProvider,
    blossomServers: StateFlow<List<String>>?,
    quality: CompressionQuality? = null,
    stripExif: Boolean = true,
    declareRealMimeType: Boolean = false,
) {
    val orchestrator = UploadOrchestrator()
    val server = blossomServers?.value?.firstOrNull() ?: DEFAULT_BLOSSOM_SERVER
    val recipients = roomKey.users.mapNotNull { cacheProvider.getUserIfExists(it) }.map { it.toPTag() }

    for (file in files) {
        val cipher = AESGCM()
        // Only re-encode genuinely reencodable raster images; everything else
        // (video, audio, GIF, AVIF/HEIC, unknown) uploads unchanged so a single
        // unsupported file can't abort the whole send.
        val fileQuality = if (file.extension.lowercase() in IMAGE_EXTENSIONS) quality else null
        val result =
            orchestrator.uploadEncrypted(file, cipher, server, account.signer, stripExif, fileQuality, declareRealMimeType)
        val url = result.blossom.url ?: continue

        val template =
            ChatMessageEncryptedFileHeaderEvent.build(
                to = recipients,
                url = url,
                cipher = cipher,
                mimeType = result.metadata.mimeType,
                hash = result.encryptedHash,
                size = result.encryptedSize,
                dimension =
                    result.metadata.width?.let { w ->
                        result.metadata.height?.let { h -> DimensionTag(w, h) }
                    },
                blurhash = result.metadata.blurhash,
                thumbhash = result.metadata.thumbhash,
                originalHash = result.metadata.sha256,
            )
        account.sendNip17EncryptedFile(template)
    }
}

/**
 * Dialog to set or change a group's NIP-14 subject (name). Mirrors Android's
 * NewChatroomSubjectDialog: it sends a normal NIP-17 message carrying a
 * `subject` tag (plus an optional accompanying message) to every room member,
 * so all participants pick up the new name.
 */
@Composable
private fun GroupSubjectDialog(
    roomKey: ChatroomKey,
    currentSubject: String,
    account: IAccount,
    cacheProvider: ICacheProvider,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var groupName by remember { mutableStateOf(currentSubject) }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Group name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Subject") },
                    placeholder = { Text("A name for this group") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    label = { Text("Message (optional)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = groupName.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            val pTags = roomKey.users.mapNotNull { cacheProvider.getUserIfExists(it)?.toPTag() }
                            val template =
                                ChatMessageEvent.build(message, pTags) {
                                    groupName.ifBlank { null }?.let { changeSubject(it) }
                                }
                            account.sendNip17PrivateMessage(template)
                        } catch (e: Exception) {
                            println("Failed to set group subject: ${e.message}")
                        }
                    }
                    onClose()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
    )
}
