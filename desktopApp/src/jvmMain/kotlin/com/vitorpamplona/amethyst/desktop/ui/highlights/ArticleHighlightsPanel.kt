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
package com.vitorpamplona.amethyst.desktop.ui.highlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.highlights.HighlightData
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.launch

@Composable
fun ArticleHighlightsPanel(
    highlights: List<HighlightData>,
    highlightStore: DesktopHighlightStore,
    articleContent: String,
    signer: NostrSigner?,
    relayManager: DesktopRelayConnectionManager?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var editTarget by remember { mutableStateOf<HighlightData?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            highlights.forEach { highlight ->
                HighlightPanelCard(
                    highlight = highlight,
                    onDelete = {
                        scope.launch { highlightStore.removeHighlight(highlight.id) }
                    },
                    onEditNote = { editTarget = highlight },
                    onPublish =
                        if (!highlight.published && signer != null && relayManager != null) {
                            {
                                scope.launch {
                                    val context =
                                        HighlightPublishAction.extractContext(
                                            articleContent,
                                            highlight.text,
                                        )
                                    val event =
                                        HighlightPublishAction.publish(
                                            highlightText = highlight.text,
                                            articleAddressTag = highlight.articleAddressTag,
                                            note = highlight.note,
                                            context = context,
                                            signer = signer,
                                        )
                                    relayManager.broadcastToAll(event)
                                    highlightStore.markPublished(highlight.id, event.id)
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }

    editTarget?.let { highlight ->
        HighlightAnnotationDialog(
            selectedText = highlight.text,
            onConfirm = { note ->
                scope.launch { highlightStore.updateNote(highlight.id, note) }
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

@Composable
private fun HighlightPanelCard(
    highlight: HighlightData,
    onDelete: () -> Unit,
    onEditNote: () -> Unit,
    onPublish: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "\u201C${highlight.text}\u201D",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val noteText = highlight.note
            if (!noteText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Published status
                Icon(
                    symbol = if (highlight.published) MaterialSymbols.Public else MaterialSymbols.Lock,
                    contentDescription = if (highlight.published) "Published" else "Private",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (highlight.published) "Published" else "Private",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )

                Spacer(Modifier.weight(1f))

                // Publish button
                if (onPublish != null) {
                    IconButton(onClick = onPublish, modifier = Modifier.size(32.dp)) {
                        Icon(
                            MaterialSymbols.Public,
                            contentDescription = "Publish to relays",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                IconButton(onClick = onEditNote, modifier = Modifier.size(32.dp)) {
                    Icon(
                        MaterialSymbols.Edit,
                        contentDescription = "Edit note",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        MaterialSymbols.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
