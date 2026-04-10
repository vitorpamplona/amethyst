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
package com.vitorpamplona.amethyst.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.drafts.DraftDisplayData
import com.vitorpamplona.amethyst.ios.drafts.DraftManager
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.service.upload.DEFAULT_BLOSSOM_SERVER
import com.vitorpamplona.amethyst.ios.service.upload.IosUploadOrchestrator
import com.vitorpamplona.amethyst.ios.service.upload.PickedImage
import com.vitorpamplona.amethyst.ios.service.upload.pickImageFromLibrary
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PublishState {
    IDLE,
    SIGNING,
    PUBLISHING,
    SUCCESS,
    ERROR,
}

private enum class UploadState {
    IDLE,
    PICKING,
    UPLOADING,
    ERROR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeNoteScreen(
    account: AccountState.LoggedIn,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    draftManager: DraftManager? = null,
    replyToNoteId: String? = null,
    initialDraft: String = "",
    initialDraftTag: String? = null,
    onDraftChanged: (String) -> Unit = {},
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    var noteText by remember { mutableStateOf(initialDraft) }
    var publishState by remember { mutableStateOf(PublishState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Draft management
    val currentDraftTag = remember { initialDraftTag ?: draftManager?.newDraftTag() }
    var showDraftsList by remember { mutableStateOf(false) }
    val savedDrafts = draftManager?.drafts?.collectAsState()?.value ?: emptyList()

    // Auto-save draft periodically when text changes
    LaunchedEffect(noteText) {
        try {
            if (draftManager != null && currentDraftTag != null && noteText.isNotBlank()) {
                delay(3000L) // debounce 3 seconds
                draftManager.saveDraft(
                    content = noteText,
                    dTag = currentDraftTag,
                    replyToNoteId = replyToNoteId,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            platform.Foundation.NSLog("Draft auto-save error: " + (e.message ?: "unknown"))
        }
    }

    // Media upload state
    var uploadState by remember { mutableStateOf(UploadState.IDLE) }
    var attachedImageUrls by remember { mutableStateOf(listOf<String>()) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }

    val uploadOrchestrator = remember { IosUploadOrchestrator() }

    val isPublishing = publishState == PublishState.SIGNING || publishState == PublishState.PUBLISHING
    val isUploading = uploadState == UploadState.UPLOADING || uploadState == UploadState.PICKING
    val canPost = noteText.isNotBlank() && !isPublishing && !isUploading

    // Look up the parent note for reply display
    val replyEvent =
        remember(replyToNoteId) {
            if (replyToNoteId != null) {
                localCache.getNoteIfExists(replyToNoteId)?.event
            } else {
                null
            }
        }

    fun insertImageUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) {
            attachedImageUrls = attachedImageUrls + trimmed
            // Insert the URL into the note text
            val separator = if (noteText.isNotEmpty() && !noteText.endsWith("\n")) "\n" else ""
            noteText = noteText + separator + trimmed
            onDraftChanged(noteText)
        }
    }

    fun removeAttachedImage(url: String) {
        attachedImageUrls = attachedImageUrls - url
        // Remove the URL from the note text
        noteText = noteText.replace(url, "").trim()
        onDraftChanged(noteText)
    }

    fun pickAndUploadImage() {
        scope.launch {
            uploadState = UploadState.PICKING
            try {
                val picked: PickedImage? =
                    withContext(Dispatchers.Main) {
                        pickImageFromLibrary()
                    }

                if (picked == null) {
                    uploadState = UploadState.IDLE
                    return@launch
                }

                uploadState = UploadState.UPLOADING

                val result =
                    withContext(Dispatchers.IO) {
                        uploadOrchestrator.upload(
                            imageData = picked.data,
                            mimeType = picked.mimeType,
                            alt = "Image from Amethyst iOS",
                            serverBaseUrl = DEFAULT_BLOSSOM_SERVER,
                            signer = account.signer,
                        )
                    }

                val imageUrl = result.blossom.url
                if (imageUrl != null) {
                    insertImageUrl(imageUrl)
                    snackbarHostState.showSnackbar("Image uploaded!")
                } else {
                    snackbarHostState.showSnackbar("Upload succeeded but no URL returned")
                }
                uploadState = UploadState.IDLE
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                uploadState = UploadState.ERROR
                snackbarHostState.showSnackbar("Upload failed: ${e.message}")
                uploadState = UploadState.IDLE
            }
        }
    }

    fun publishNote() {
        if (account.isReadOnly) {
            errorMessage = "Cannot post in read-only mode"
            return
        }

        scope.launch {
            try {
                publishState = PublishState.SIGNING
                errorMessage = null

                val signedEvent: TextNoteEvent =
                    withContext(Dispatchers.IO) {
                        val template =
                            TextNoteEvent.build(noteText) {
                                if (replyEvent != null) {
                                    val etag = ETag(replyEvent.id)
                                    etag.relay = null
                                    etag.author = replyEvent.pubKey
                                    eTag(etag)
                                    pTag(PTag(replyEvent.pubKey, relayHint = null))
                                }
                                hashtags(findHashtags(noteText))
                                references(findURLs(noteText))
                            }

                        account.signer.sign(template)
                    }

                publishState = PublishState.PUBLISHING

                relayManager.broadcastToAll(signedEvent as Event)
                localCache.consume(signedEvent, null)

                // Delete the draft after successful publish
                if (draftManager != null && currentDraftTag != null) {
                    try {
                        draftManager.deleteDraft(currentDraftTag)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        platform.Foundation.NSLog("Draft delete error: " + (e.message ?: "unknown"))
                    }
                }

                publishState = PublishState.SUCCESS
                onPublished()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                publishState = PublishState.ERROR
                errorMessage = e.message ?: "Failed to publish note"
                snackbarHostState.showSnackbar(errorMessage ?: "Unknown error")
            }
        }
    }

    // URL input dialog
    if (showUrlDialog) {
        InsertImageUrlDialog(
            onDismiss = { showUrlDialog = false },
            onInsert = { url ->
                insertImageUrl(url)
                showUrlDialog = false
            },
        )
    }

    // Attach menu dialog
    if (showAttachMenu) {
        AttachMenuDialog(
            onDismiss = { showAttachMenu = false },
            onPickFromLibrary = {
                showAttachMenu = false
                pickAndUploadImage()
            },
            onPasteUrl = {
                showAttachMenu = false
                showUrlDialog = true
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (replyToNoteId != null) "Reply" else "New Note",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isPublishing) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        // Drafts button
                        if (draftManager != null && savedDrafts.isNotEmpty()) {
                            TextButton(
                                onClick = { showDraftsList = !showDraftsList },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Text("📄 ${savedDrafts.size}")
                            }
                        }
                        Button(
                            onClick = { publishNote() },
                            enabled = canPost,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Post")
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Show parent note when replying
            if (replyEvent != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Replying to:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        replyEvent.content.take(200) +
                            if (replyEvent.content.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Drafts list panel
            if (showDraftsList && savedDrafts.isNotEmpty()) {
                DraftsListPanel(
                    drafts = savedDrafts,
                    onResume = { draft ->
                        scope.launch {
                            try {
                                val content = draftManager?.getDraftContent(draft.dTag)
                                if (content != null) {
                                    noteText = content
                                    onDraftChanged(content)
                                }
                                showDraftsList = false
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                platform.Foundation.NSLog("Resume draft error: " + (e.message ?: "unknown"))
                            }
                        }
                    },
                    onDelete = { draft ->
                        scope.launch {
                            try {
                                draftManager?.deleteDraft(draft.dTag)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                platform.Foundation.NSLog("Delete draft error: " + (e.message ?: "unknown"))
                            }
                        }
                    },
                    onDismiss = { showDraftsList = false },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Compose text field
            TextField(
                value = noteText,
                onValueChange = {
                    noteText = it
                    onDraftChanged(it)
                },
                placeholder = {
                    Text(
                        if (replyToNoteId != null) "Write your reply..." else "What's on your mind?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 8.dp),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                textStyle = MaterialTheme.typography.bodyLarge,
                enabled = !isPublishing,
            )

            // Upload progress indicator
            if (isUploading) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (uploadState == UploadState.PICKING) "Selecting image..." else "Uploading to Blossom...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Attached image previews
            if (attachedImageUrls.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Attached media",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    attachedImageUrls.forEach { url ->
                        AttachedImagePreview(
                            url = url,
                            onRemove = { removeAttachedImage(url) },
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Compose toolbar with attachment button
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Attachment button
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = !isPublishing && !isUploading,
                    ) {
                        Text("📷", style = MaterialTheme.typography.titleLarge)
                    }

                    // Add URL directly
                    IconButton(
                        onClick = { showUrlDialog = true },
                        enabled = !isPublishing && !isUploading,
                    ) {
                        Text("🔗", style = MaterialTheme.typography.titleLarge)
                    }
                }

                // Character count
                Text(
                    "${noteText.length} characters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error with retry
            if (publishState == PublishState.ERROR && errorMessage != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        errorMessage ?: "Failed to publish",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { publishNote() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachedImagePreview(
    url: String,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Attached image",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        CircleShape,
                    ),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InsertImageUrlDialog(
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Image URL") },
        text = {
            Column {
                Text(
                    "Paste a direct image URL to attach it to your note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Image URL") },
                    placeholder = { Text("https://example.com/image.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInsert(url) },
                enabled = url.isNotBlank(),
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AttachMenuDialog(
    onDismiss: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onPasteUrl: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach Media") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = onPickFromLibrary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📷", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Photo Library", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Pick an image and upload to Blossom",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onPasteUrl,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔗", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Paste URL", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Insert a direct image link",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DraftsListPanel(
    drafts: List<DraftDisplayData>,
    onResume: (DraftDisplayData) -> Unit,
    onDelete: (DraftDisplayData) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Saved Drafts",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }

        Spacer(Modifier.height(4.dp))

        drafts.forEach { draft ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onResume(draft) }
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        draft.content.take(80) + if (draft.content.length > 80) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                    Text(
                        "Draft",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = { onDelete(draft) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete draft",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
