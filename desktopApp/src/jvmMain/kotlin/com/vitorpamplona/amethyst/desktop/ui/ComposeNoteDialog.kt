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
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.upload.DesktopUploadOrchestrator
import com.vitorpamplona.amethyst.desktop.service.upload.DesktopUploadTracker
import com.vitorpamplona.amethyst.desktop.service.upload.UploadResult
import com.vitorpamplona.amethyst.desktop.ui.media.ClipboardPasteHandler
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopFilePicker
import com.vitorpamplona.amethyst.desktop.ui.media.MediaAttachmentRow
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
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
    replyTo: Event? = null,
) {
    var content by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val attachedFiles = remember { mutableStateListOf<File>() }
    val uploadTracker = remember { DesktopUploadTracker() }
    val uploadState by uploadTracker.state.collectAsState()
    val orchestrator = remember { DesktopUploadOrchestrator() }
    var selectedServer by remember { mutableStateOf(DesktopPreferences.preferredBlossomServer) }
    var postAsPicture by remember { mutableStateOf(false) }

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

    Dialog(onDismissRequest = { if (!isPosting) onDismiss() }) {
        Card(
            modifier =
                Modifier
                    .width(600.dp)
                    .padding(16.dp)
                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
                    .then(
                        if (isDragOver) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (replyTo != null) "Reply" else "New Note",
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

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = if (postAsPicture) "" else content,
                    onValueChange = {
                        content = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth().height(if (postAsPicture) 60.dp else 200.dp),
                    label = {
                        Text(
                            if (postAsPicture) "Text disabled for picture posts" else "What's on your mind?",
                        )
                    },
                    placeholder = { Text(if (postAsPicture) "" else "Write your note...") },
                    enabled = !isPosting && !postAsPicture,
                    maxLines = if (postAsPicture) 1 else 10,
                )

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

                // Server selector + post type — shown when files are attached
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

                        // Post type toggle — only when images are attached
                        val hasImages =
                            attachedFiles.any {
                                it.extension.lowercase() in IMAGE_EXTENSIONS
                            }
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
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                uploadState.error?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Upload error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(16.dp))

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

                            scope.launch {
                                isPosting = true
                                errorMessage = null

                                try {
                                    // Upload attached files and collect results
                                    val uploadResults = mutableListOf<UploadResult>()
                                    for (file in attachedFiles) {
                                        uploadTracker.startUpload(file.name)
                                        val result =
                                            orchestrator.upload(
                                                file = file,
                                                alt = null,
                                                serverBaseUrl = selectedServer,
                                                signer = account.signer,
                                            )
                                        uploadTracker.onSuccess(result)
                                        uploadResults.add(result)
                                    }

                                    // Append uploaded URLs to content
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
                                        )
                                    } else {
                                        val imetaTags = buildIMetaTags(uploadResults)
                                        publishNote(
                                            content = finalContent,
                                            account = account,
                                            relayManager = relayManager,
                                            replyTo = replyTo,
                                            imetaTags = imetaTags,
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
                        },
                        enabled = !isPosting && (content.isNotBlank() || attachedFiles.isNotEmpty()),
                    ) {
                        Text(if (isPosting) "Publishing..." else "Publish")
                    }
                }
            }
        }
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
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Post as:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.FilterChip(
            selected = !isPicture,
            onClick = { onToggle(false) },
            label = { Text("Note", style = MaterialTheme.typography.labelSmall) },
        )
        androidx.compose.material3.FilterChip(
            selected = isPicture,
            onClick = { onToggle(true) },
            label = { Text("Picture", style = MaterialTheme.typography.labelSmall) },
        )
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
                if (meta.width != null && meta.height != null) {
                    com.vitorpamplona.quartz.nip94FileMetadata.tags
                        .DimensionTag(meta.width, meta.height)
                } else {
                    null
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
        relayManager.broadcastToAll(signedEvent)
    }
}

private suspend fun publishNote(
    content: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    replyTo: Event?,
    imetaTags: List<IMetaTag> = emptyList(),
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
                hashtags(findHashtags(content))
                references(findURLs(content))
                for (imeta in imetaTags) {
                    add(imeta.toTagArray())
                }
            }

        val signedEvent = account.signer.sign(template)
        relayManager.broadcastToAll(signedEvent)
    }
}
