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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PublishState {
    IDLE,
    SIGNING,
    PUBLISHING,
    SUCCESS,
    ERROR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeNoteScreen(
    account: AccountState.LoggedIn,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    replyToNoteId: String? = null,
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    var noteText by remember { mutableStateOf("") }
    var publishState by remember { mutableStateOf(PublishState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isPublishing = publishState == PublishState.SIGNING || publishState == PublishState.PUBLISHING
    val canPost = noteText.isNotBlank() && !isPublishing

    // Look up the parent note for reply display
    val replyEvent =
        remember(replyToNoteId) {
            if (replyToNoteId != null) {
                localCache.getNoteIfExists(replyToNoteId)?.event
            } else {
                null
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
                        // Build the event template following the desktop pattern
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

                        // Sign the event
                        account.signer.sign(template)
                    }

                publishState = PublishState.PUBLISHING

                // Broadcast to all connected relays
                relayManager.broadcastToAll(signedEvent as Event)

                // Add to local cache so it appears immediately
                localCache.consume(signedEvent, null)

                publishState = PublishState.SUCCESS
                onPublished()
            } catch (e: Exception) {
                publishState = PublishState.ERROR
                errorMessage = e.message ?: "Failed to publish note"
                snackbarHostState.showSnackbar(errorMessage ?: "Unknown error")
            }
        }
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

            // Compose text field
            TextField(
                value = noteText,
                onValueChange = { noteText = it },
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

            // Character count
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
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
