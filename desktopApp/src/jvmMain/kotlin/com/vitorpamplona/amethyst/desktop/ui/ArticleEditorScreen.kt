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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.compose.editor.MarkdownToolbar
import com.vitorpamplona.amethyst.commons.compose.editor.MetadataPanel
import com.vitorpamplona.amethyst.commons.compose.markdown.ArticleMediaRenderer
import com.vitorpamplona.amethyst.commons.compose.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.model.nip23LongContent.LongFormPublishAction
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.drafts.DesktopDraftStore
import com.vitorpamplona.amethyst.desktop.service.drafts.DraftMetadata
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

@Composable
fun ArticleEditorScreen(
    draftSlug: String?,
    draftStore: DesktopDraftStore,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var bannerUrl by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var slug by remember { mutableStateOf(draftSlug ?: "") }
    var contentField by remember { mutableStateOf(TextFieldValue("")) }
    var publishing by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Load existing draft
    LaunchedEffect(draftSlug) {
        if (draftSlug != null) {
            val meta = draftStore.loadMetadata(draftSlug)
            val body = draftStore.loadContent(draftSlug)
            if (meta != null) {
                title = meta.title
                summary = meta.summary ?: ""
                bannerUrl = meta.image ?: ""
                tags = meta.tags
                slug = draftSlug
            }
            if (body != null) {
                contentField = TextFieldValue(body)
            }
        }
    }

    // Auto-generate slug from title if creating a new draft
    LaunchedEffect(title) {
        if (draftSlug == null && title.isNotBlank()) {
            slug = draftStore.slugFromTitle(title)
        }
    }

    val mediaRenderer =
        remember {
            object : ArticleMediaRenderer {
                @Composable
                override fun renderImage(
                    url: String,
                    alt: String?,
                ) {
                    // Simple text placeholder for editor preview
                    Text(
                        "[Image: ${alt ?: url}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                override fun onLinkClick(url: String) {
                    try {
                        Desktop.getDesktop().browse(URI(url))
                    } catch (_: Exception) {
                        // Ignore unsupported or malformed URLs
                    }
                }
            }
        }

    fun saveDraft() {
        if (slug.isBlank()) return
        scope.launch {
            draftStore.saveDraft(
                slug = slug,
                content = contentField.text,
                metadata =
                    DraftMetadata(
                        title = title,
                        summary = summary.ifBlank { null },
                        image = bannerUrl.ifBlank { null },
                        tags = tags,
                    ),
            )
            saveMessage = "Saved"
        }
    }

    fun publishArticle() {
        if (publishing) return
        publishing = true
        scope.launch {
            try {
                val event =
                    LongFormPublishAction.publish(
                        title = title,
                        content = contentField.text,
                        summary = summary.ifBlank { null },
                        image = bannerUrl.ifBlank { null },
                        tags = tags,
                        dTag = slug.ifBlank { draftStore.slugFromTitle(title) },
                        signer = account.signer,
                    )
                relayManager.send(event)
                draftStore.markPublished(slug)
                onPublished()
            } catch (e: Exception) {
                saveMessage = "Publish failed: ${e.message}"
            } finally {
                publishing = false
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Ctrl/Cmd+S to save
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.S &&
                        (if (isMacOS) event.isMetaPressed else event.isMetaPressed)
                    ) {
                        saveDraft()
                        true
                    } else {
                        false
                    }
                },
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saveMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                OutlinedButton(onClick = { saveDraft() }) {
                    Text("Save")
                }
                Button(
                    onClick = { publishArticle() },
                    enabled = !publishing && title.isNotBlank() && contentField.text.isNotBlank(),
                ) {
                    Text(if (publishing) "Publishing..." else "Publish")
                }
            }
        }

        // Metadata panel (collapsible)
        MetadataPanel(
            title = title,
            onTitleChange = { title = it },
            summary = summary,
            onSummaryChange = { summary = it },
            bannerUrl = bannerUrl,
            onBannerUrlChange = { bannerUrl = it },
            tags = tags,
            onTagsChange = { tags = it },
            slug = slug,
            onSlugChange = { slug = it },
        )

        Spacer(Modifier.height(8.dp))

        // Markdown toolbar
        MarkdownToolbar(
            onInsert = { prefix, suffix ->
                val selection = contentField.selection
                val text = contentField.text
                val newText =
                    text.substring(0, selection.start) +
                        prefix +
                        text.substring(selection.start, selection.end) +
                        suffix +
                        text.substring(selection.end)
                val newCursorPos = selection.start + prefix.length + (selection.end - selection.start)
                contentField =
                    TextFieldValue(
                        text = newText,
                        selection =
                            androidx.compose.ui.text
                                .TextRange(newCursorPos),
                    )
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Split pane: source left, preview right
        Row(
            modifier = Modifier.fillMaxSize().weight(1f),
        ) {
            // Source editor
            TextField(
                value = contentField,
                onValueChange = {
                    contentField = it
                    saveMessage = null
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                placeholder = { Text("Write your article in markdown...") },
            )

            VerticalDivider()

            // Preview
            SelectionContainer {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(modifier = Modifier.widthIn(max = 680.dp)) {
                        if (contentField.text.isNotBlank()) {
                            RenderMarkdown(
                                content = contentField.text,
                                mediaRenderer = mediaRenderer,
                            )
                        } else {
                            Text(
                                "Preview will appear here...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
