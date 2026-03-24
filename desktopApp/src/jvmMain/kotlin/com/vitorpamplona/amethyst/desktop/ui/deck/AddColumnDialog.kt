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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val COLUMN_OPTIONS =
    listOf(
        DeckColumnType.HomeFeed,
        DeckColumnType.Notifications,
        DeckColumnType.Messages,
        DeckColumnType.Search,
        DeckColumnType.Reads,
        DeckColumnType.Drafts,
        DeckColumnType.Bookmarks,
        DeckColumnType.GlobalFeed,
        DeckColumnType.MyProfile,
        DeckColumnType.Chess,
    )

@Composable
fun AddColumnDialog(
    onDismiss: () -> Unit,
    onAdd: (DeckColumnType) -> Unit,
) {
    var hashtagInput by remember { mutableStateOf("") }
    var showHashtagInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Column") },
        text = {
            Column {
                if (showHashtagInput) {
                    Text(
                        "Enter hashtag:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hashtagInput,
                        onValueChange = { hashtagInput = it.removePrefix("#") },
                        label = { Text("Hashtag") },
                        placeholder = { Text("bitcoin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    COLUMN_OPTIONS.forEach { type ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(type) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Icon(
                                imageVector = type.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                type.title(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { showHashtagInput = true }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = DeckColumnType.Hashtag("").icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Hashtag...",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showHashtagInput) {
                Button(
                    onClick = {
                        if (hashtagInput.isNotBlank()) {
                            onAdd(DeckColumnType.Hashtag(hashtagInput.trim()))
                        }
                    },
                    enabled = hashtagInput.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showHashtagInput) {
                    showHashtagInput = false
                } else {
                    onDismiss()
                }
            }) {
                Text(if (showHashtagInput) "Back" else "Cancel")
            }
        },
    )
}
