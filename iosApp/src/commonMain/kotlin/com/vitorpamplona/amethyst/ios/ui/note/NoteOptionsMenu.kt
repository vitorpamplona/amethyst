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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.ui.NoteDisplayData

/**
 * Overflow "..." menu for a note card.
 *
 * Provides: Copy note ID, Copy note text, Copy author npub, Share, Mute user, Report.
 */
@Composable
fun NoteOptionsMenu(
    note: NoteDisplayData,
    modifier: Modifier = Modifier,
    onCopyNoteId: ((String) -> Unit)? = null,
    onCopyNoteText: ((String) -> Unit)? = null,
    onCopyAuthorNpub: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
    onMuteUser: ((String) -> Unit)? = null,
    onReport: ((String, String) -> Unit)? = null, // (noteId, authorPubKeyHex)
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        onCopyNoteId?.let { callback ->
            DropdownMenuItem(
                text = { Text("Copy note ID") },
                onClick = {
                    callback(note.id)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        onCopyNoteText?.let { callback ->
            DropdownMenuItem(
                text = { Text("Copy note text") },
                onClick = {
                    callback(note.content)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        onCopyAuthorNpub?.let { callback ->
            DropdownMenuItem(
                text = { Text("Copy author npub") },
                onClick = {
                    callback(note.pubKeyHex)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        onShare?.let { callback ->
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    callback(note.id)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        onMuteUser?.let { callback ->
            DropdownMenuItem(
                text = {
                    Text(
                        "Mute user",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    callback(note.pubKeyHex)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        onReport?.let { callback ->
            DropdownMenuItem(
                text = {
                    Text(
                        "Report",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    callback(note.id, note.pubKeyHex)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}
