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
package com.vitorpamplona.amethyst.ios.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import kotlinx.coroutines.launch

/**
 * Screen for editing an existing short note (kind 1010 — TextNoteModificationEvent).
 *
 * Creates a content modification event that references the original note.
 * Uses existing quartz TextNoteModificationEvent for event signing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    noteId: String,
    account: AccountState.LoggedIn,
    localCache: IosLocalCache,
    relayManager: IosRelayConnectionManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val originalNote = localCache.getNoteIfExists(noteId)
    val originalContent = originalNote?.event?.content ?: ""
    val originalAuthor = originalNote?.event?.pubKey

    var editedContent by remember { mutableStateOf(originalContent) }
    var editSummary by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Only allow editing your own notes
    val canEdit = originalAuthor == account.pubKeyHex && !account.isReadOnly

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            if (!canEdit) {
                Text(
                    text = if (originalAuthor != account.pubKeyHex) "You can only edit your own notes" else "Read-only account",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = "Edit content:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    enabled = !isSending,
                    placeholder = { Text("Edit your note...") },
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = editSummary,
                    onValueChange = { editSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending,
                    label = { Text("Edit summary (optional)") },
                    placeholder = { Text("What changed?") },
                    singleLine = true,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        isSending = true
                        scope.launch {
                            try {
                                val editEvent =
                                    TextNoteModificationEvent.create(
                                        content = editedContent,
                                        eventId = noteId,
                                        notify = null,
                                        summary = editSummary.ifBlank { null },
                                        signer = account.signer,
                                    )
                                relayManager.broadcastToAll(editEvent)
                                localCache.consume(editEvent, null)
                                snackbarHostState.showSnackbar("✏️ Edit published!")
                                onBack()
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                snackbarHostState.showSnackbar("Failed to publish edit: " + (e.message ?: "unknown"))
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending && editedContent != originalContent && editedContent.isNotBlank(),
                ) {
                    Text(if (isSending) "Publishing..." else "Publish Edit")
                }
            }
        }
    }
}
