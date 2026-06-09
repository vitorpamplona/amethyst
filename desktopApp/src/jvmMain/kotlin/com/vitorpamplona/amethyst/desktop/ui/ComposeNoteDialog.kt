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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.upload.UploadResult
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.ImageCompressionStore
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.upload.DesktopUploadTracker
import com.vitorpamplona.amethyst.desktop.ui.compose.ComposeRelayPicker
import com.vitorpamplona.amethyst.desktop.ui.compose.RelayPickerState
import com.vitorpamplona.amethyst.desktop.ui.media.ClipboardPasteHandler
import com.vitorpamplona.amethyst.desktop.ui.media.CompressionPreviewDialog
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopFilePicker
import com.vitorpamplona.amethyst.desktop.ui.media.MediaAttachmentRow
import com.vitorpamplona.amethyst.desktop.ui.media.PreviewItem
import com.vitorpamplona.amethyst.desktop.ui.media.QualitySelectorChip
import com.vitorpamplona.amethyst.desktop.ui.media.buildPreview
import com.vitorpamplona.amethyst.desktop.ui.media.cleanupPreviewTemps
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quote
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private val MEDIA_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "avif", "mp4", "webm", "mov", "mp3", "ogg", "wav", "flac")

private val IMAGE_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "avif")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ComposeNoteDialog(
    onDismiss: () -> Unit,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    localCache: com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache? = null,
    replyTo: Event? = null,
    quoteOf: Event? = null,
) {
    val initialContent =
        remember(quoteOf) {
            if (quoteOf != null) {
                val relays = relayManager.connectedRelays.value.take(3)
                val nevent = NEvent.create(quoteOf.id, quoteOf.pubKey, quoteOf.kind, relays)
                "\nnostr:$nevent"
            } else {
                ""
            }
        }
    var contentField by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialContent,
                selection = TextRange(initialContent.length),
            ),
        )
    }
    val content = contentField.text
    var isPosting by remember { mutableStateOf(false) }

    // Mention autocomplete state
    var mentionSuggestions by remember { mutableStateOf<List<User>>(emptyList()) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionWordStart by remember { mutableStateOf(0) }

    LaunchedEffect(contentField) {
        val cursor = contentField.selection.end
        val text = contentField.text
        if (cursor > 0 && localCache != null) {
            // Find the word at cursor
            val wordStart = text.lastIndexOf(' ', cursor - 1) + 1
            val wordAtCursor = text.substring(wordStart, cursor)
            if (wordAtCursor.startsWith("@") && wordAtCursor.length > 1) {
                val query = wordAtCursor.removePrefix("@")
                mentionQuery = query
                mentionWordStart = wordStart
                mentionSuggestions = localCache.findUsersStartingWith(query, 5)
            } else {
                mentionQuery = null
                mentionSuggestions = emptyList()
            }
        } else {
            mentionQuery = null
            mentionSuggestions = emptyList()
        }
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val attachedFiles = remember { mutableStateListOf<File>() }
    val uploadTracker = remember { DesktopUploadTracker() }
    val uploadState by uploadTracker.state.collectAsState()
    val orchestrator = remember { UploadOrchestrator() }
    var selectedServer by remember { mutableStateOf(DesktopPreferences.preferredBlossomServer) }
    var postAsPicture by remember { mutableStateOf(false) }

    // Image compression: global default + optional per-post override.
    // Override resets after every successful send so the next post
    // starts from the saved default again.
    val defaultQuality by ImageCompressionStore.quality.collectAsState()
    val stripExifSetting by ImageCompressionStore.stripExif.collectAsState()
    var perPostQualityOverride by remember { mutableStateOf<CompressionQuality?>(null) }
    val activeQuality = perPostQualityOverride ?: defaultQuality

    // Preview-then-publish state. When images are attached, the
    // publish button reads "Preview" and a click here triggers the
    // CompressionPreviewDialog: every attachment is run through the
    // ImageReencoder eagerly, the user reviews the result, and the
    // actual upload uses the cached temp files (preCompressed).
    var pendingPreview by remember { mutableStateOf<List<PreviewItem>?>(null) }
    val hasImages =
        attachedFiles.any {
            it.extension.lowercase() in IMAGE_EXTENSIONS
        }

    // Relay picker state
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val allRelays by relayManager.availableRelays.collectAsState()
    val pickerState =
        remember(allRelays, connectedRelays) {
            RelayPickerState(allRelays = allRelays, connectedRelays = connectedRelays)
        }
    var selectedRelays by remember(connectedRelays) { mutableStateOf(connectedRelays) }

    // Drag-and-drop state
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
                        attachedFiles.addAll(files.filter { it.extension.lowercase() in MEDIA_EXTENSIONS })
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

    // Shared upload + publish flow. Called by either the main button
    // (when there are no images, hence no preview gate) or by the
    // CompressionPreviewDialog's Publish button (when the user has
    // confirmed the preview). The parameter is the filtered list of
    // items the user actually wants to upload (skipped rows have
    // already been cleaned up by the dialog), or null for the
    // no-preview path.
    val runPublish: (List<PreviewItem>?) -> Unit = { selectedItems ->
        scope.launch {
            isPosting = true
            errorMessage = null
            try {
                pendingPreview = null
                val uploadResults = mutableListOf<UploadResult>()

                val total = selectedItems?.size ?: attachedFiles.size
                val sources: List<Pair<File, PreviewItem?>> =
                    selectedItems?.map { it.source to it }
                        ?: attachedFiles.map { it to null }

                for ((idx, pair) in sources.withIndex()) {
                    val (file, item) = pair
                    val n = idx + 1
                    val prefix = if (total > 1) "$n/$total: " else ""
                    uploadTracker.startUpload("$prefix${file.name}")
                    val result =
                        when (item) {
                            is PreviewItem.Reencoded ->
                                orchestrator.upload(
                                    file = file,
                                    alt = null,
                                    serverBaseUrl = selectedServer,
                                    signer = account.signer,
                                    stripExif = stripExifSetting,
                                    quality = activeQuality,
                                    preCompressed = item.compressedFile,
                                )
                            is PreviewItem.Failed ->
                                orchestrator.upload(
                                    file = file,
                                    alt = null,
                                    serverBaseUrl = selectedServer,
                                    signer = account.signer,
                                    stripExif = stripExifSetting,
                                    quality = activeQuality,
                                    bypassReencode = true,
                                )
                            else ->
                                orchestrator.upload(
                                    file = file,
                                    alt = null,
                                    serverBaseUrl = selectedServer,
                                    signer = account.signer,
                                    stripExif = stripExifSetting,
                                    quality = activeQuality,
                                )
                        }
                    uploadTracker.onSuccess(result)
                    uploadResults.add(result)
                }

                perPostQualityOverride = null

                val finalContent =
                    buildString {
                        append(content)
                        for (result in uploadResults) {
                            result.blossom.url?.let { url ->
                                if (isNotBlank()) append("\n")
                                append(url)
                            }
                        }
                    }

                if (postAsPicture) {
                    val pictureMetas = buildPictureMetas(uploadResults)
                    publishPicture(
                        description = content,
                        images = pictureMetas,
                        account = account,
                        relayManager = relayManager,
                        relays = selectedRelays,
                    )
                } else {
                    val imetaTags = buildIMetaTags(uploadResults)
                    publishNote(
                        content = finalContent,
                        account = account,
                        relayManager = relayManager,
                        replyTo = replyTo,
                        quoteOf = quoteOf,
                        imetaTags = imetaTags,
                        relays = selectedRelays,
                    )
                }
                onDismiss()
            } catch (e: Exception) {
                errorMessage = "Failed: ${e.message}"
                uploadTracker.onError(e.message ?: "Unknown error")
            } finally {
                isPosting = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .width(780.dp)
                    .padding(16.dp)
                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
                    .then(
                        if (isDragOver) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    when {
                        replyTo != null -> "Reply"
                        quoteOf != null -> "Quote"
                        else -> "New Note"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                replyTo?.let { reply ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Replying to: ${reply.content.take(50)}${if (reply.content.length > 50) "..." else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                quoteOf?.let { quoted ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Quoting: ${quoted.content.take(50)}${if (quoted.content.length > 50) "..." else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Box {
                    OutlinedTextField(
                        value = if (postAsPicture) TextFieldValue("") else contentField,
                        onValueChange = {
                            contentField = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth().height(if (postAsPicture) 60.dp else 200.dp),
                        label = {
                            Text(
                                if (postAsPicture) "Text disabled for picture posts" else "What's on your mind?",
                            )
                        },
                        placeholder = { Text(if (postAsPicture) "" else "Write your note... (type @ to mention)") },
                        enabled = !isPosting && !postAsPicture,
                        maxLines = if (postAsPicture) 1 else 10,
                    )

                    // Mention autocomplete dropdown
                    if (mentionSuggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(mentionSuggestions, key = { it.pubkeyHex }) { user ->
                                    MentionSuggestionRow(
                                        user = user,
                                        onClick = {
                                            val npub = user.pubkeyNpub()
                                            val replacement = "nostr:$npub "
                                            val cursorEnd = contentField.selection.end
                                            val newText =
                                                contentField.text.replaceRange(
                                                    mentionWordStart,
                                                    cursorEnd,
                                                    replacement,
                                                )
                                            val newCursor = mentionWordStart + replacement.length
                                            contentField = TextFieldValue(newText, TextRange(newCursor))
                                            mentionSuggestions = emptyList()
                                            mentionQuery = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                MediaAttachmentRow(
                    attachedFiles = attachedFiles,
                    isUploading = uploadState.isUploading,
                    onAttach = {
                        val files = DesktopFilePicker.pickMediaFiles()
                        attachedFiles.addAll(files)
                    },
                    onPaste = {
                        val files = ClipboardPasteHandler.getClipboardFiles()
                        attachedFiles.addAll(files)
                    },
                    onRemove = { attachedFiles.remove(it) },
                )

                // Server selector + per-post quality + post type — shown when files are attached
                if (attachedFiles.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        ServerSelector(
                            selectedServer = selectedServer,
                            onServerSelected = { selectedServer = it },
                        )

                        // Quality override chip — only when images are attached
                        // (no point picking a JPEG preset for a video upload).
                        if (hasImages) {
                            QualitySelectorChip(
                                activeQuality = activeQuality,
                                isOverride = perPostQualityOverride != null,
                                onSelect = { perPostQualityOverride = it },
                                onReset = { perPostQualityOverride = null },
                            )
                        }

                        // Post type toggle — only when images are attached
                        if (hasImages) {
                            PostTypeSelector(
                                isPicture = postAsPicture,
                                onToggle = { postAsPicture = it },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Character count
                Text(
                    "${content.length} characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                errorMessage?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                uploadState.error?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            "Upload error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                ComposeRelayPicker(
                    pickerState = pickerState,
                    selectedRelays = selectedRelays,
                    onToggleRelay = { url ->
                        selectedRelays =
                            if (url in selectedRelays) {
                                selectedRelays - url
                            } else {
                                selectedRelays + url
                            }
                    },
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isPosting,
                    ) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (content.isBlank() && attachedFiles.isEmpty()) {
                                errorMessage = "Note cannot be empty"
                                return@Button
                            }
                            // When there are images and we don't have a
                            // preview yet, the publish button acts as a
                            // "Preview" gate — build the preview, show it,
                            // and let the user confirm before any upload.
                            if (hasImages && pendingPreview == null) {
                                scope.launch {
                                    isPosting = true
                                    errorMessage = null
                                    try {
                                        pendingPreview =
                                            buildPreview(
                                                attachments = attachedFiles.toList(),
                                                imageExtensions = IMAGE_EXTENSIONS,
                                                quality = activeQuality,
                                            )
                                    } finally {
                                        isPosting = false
                                    }
                                }
                                return@Button
                            }
                            runPublish(null)
                        },
                        enabled = !isPosting && (content.isNotBlank() || attachedFiles.isNotEmpty()),
                    ) {
                        Text(
                            when {
                                isPosting -> if (pendingPreview == null && hasImages) "Compressing…" else "Publishing…"
                                hasImages && pendingPreview == null -> "Preview"
                                else -> "Publish"
                            },
                        )
                    }
                }
            }
        }
    }

    pendingPreview?.let { items ->
        CompressionPreviewDialog(
            items = items,
            stripExifSetting = stripExifSetting,
            onPublish = { included -> runPublish(included) },
            onCancel = {
                cleanupPreviewTemps(items)
                pendingPreview = null
                isPosting = false
            },
        )
    }
}

private fun buildIMetaTags(results: List<UploadResult>): List<IMetaTag> =
    results.mapNotNull { result ->
        val url = result.blossom.url ?: return@mapNotNull null
        val meta = result.metadata
        val props = mutableMapOf<String, List<String>>()
        props["m"] = listOf(meta.mimeType)
        props["x"] = listOf(meta.sha256)
        props["size"] = listOf(meta.size.toString())
        if (meta.width != null && meta.height != null) {
            props["dim"] = listOf("${meta.width}x${meta.height}")
        }
        meta.blurhash?.let { props["blurhash"] = listOf(it) }
        meta.thumbhash?.let { props["thumbhash"] = listOf(it) }
        IMetaTag(url = url, properties = props)
    }

@Composable
private fun ServerSelector(
    selectedServer: String,
    onServerSelected: (String) -> Unit,
) {
    val servers = DesktopPreferences.blossomServers
    if (servers.size <= 1) {
        // Only one server — just show label, no dropdown
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "Upload to: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                selectedServer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            "Upload to: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Box {
            androidx.compose.material3.TextButton(onClick = { expanded = true }) {
                Text(
                    selectedServer,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                servers.forEach { server ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(server, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onServerSelected(server)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostTypeSelector(
    isPicture: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            "Post as: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            androidx.compose.material3.TextButton(onClick = { expanded = true }) {
                Text(
                    if (isPicture) "Picture" else "Note",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Note", style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onToggle(false)
                        expanded = false
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Picture", style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onToggle(true)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun buildPictureMetas(results: List<UploadResult>): List<com.vitorpamplona.quartz.nip68Picture.PictureMeta> =
    results.mapNotNull { result ->
        val url = result.blossom.url ?: return@mapNotNull null
        val meta = result.metadata
        com.vitorpamplona.quartz.nip68Picture.PictureMeta(
            url = url,
            mimeType = meta.mimeType,
            blurhash = meta.blurhash,
            dimension =
                meta.width?.let { w ->
                    meta.height?.let { h ->
                        com.vitorpamplona.quartz.nip94FileMetadata.tags
                            .DimensionTag(w, h)
                    }
                },
            hash = meta.sha256,
            size = meta.size.toInt(),
            thumbhash = meta.thumbhash,
        )
    }

private suspend fun publishPicture(
    description: String,
    images: List<com.vitorpamplona.quartz.nip68Picture.PictureMeta>,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    relays: Set<NormalizedRelayUrl>,
) {
    withContext(Dispatchers.IO) {
        if (account.isReadOnly) {
            throw IllegalStateException("Cannot post in read-only mode")
        }

        val template =
            com.vitorpamplona.quartz.nip68Picture.PictureEvent.build(
                images = images,
                description = description,
            ) {
                hashtags(findHashtags(description))
            }

        val signedEvent = account.signer.sign(template)
        relayManager.publish(signedEvent, relays)
    }
}

private suspend fun publishNote(
    content: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    replyTo: Event?,
    quoteOf: Event? = null,
    imetaTags: List<IMetaTag> = emptyList(),
    relays: Set<NormalizedRelayUrl>,
) {
    withContext(Dispatchers.IO) {
        if (account.isReadOnly) {
            throw IllegalStateException("Cannot post in read-only mode")
        }

        val template =
            TextNoteEvent.build(content) {
                if (replyTo != null) {
                    val etag = ETag(replyTo.id)
                    etag.relay = null
                    etag.author = replyTo.pubKey
                    eTag(etag)
                    pTag(PTag(replyTo.pubKey, relayHint = null))
                }
                if (quoteOf != null) {
                    quote(QEventTag(quoteOf.id, relayHint = null, authorPubKeyHex = quoteOf.pubKey))
                    pTag(PTag(quoteOf.pubKey, relayHint = null))
                }
                hashtags(findHashtags(content))
                references(findURLs(content))
                for (imeta in imetaTags) {
                    add(imeta.toTagArray())
                }
            }

        val signedEvent = account.signer.sign(template)
        relayManager.publish(signedEvent, relays)
    }
}

@Composable
private fun MentionSuggestionRow(
    user: User,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserAvatar(
            userHex = user.pubkeyHex,
            pictureUrl = user.profilePicture(),
            size = 28.dp,
            contentDescription = null,
        )
        Column {
            Text(
                text = user.toBestDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = user.pubkeyNpub().take(24) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
