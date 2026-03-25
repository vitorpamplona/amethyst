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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.compose.editor.MarkdownEditorState
import com.vitorpamplona.amethyst.commons.compose.editor.MarkdownToolbar
import com.vitorpamplona.amethyst.commons.compose.editor.MetadataPanel
import com.vitorpamplona.amethyst.commons.compose.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.model.nip23LongContent.LongFormPublishAction
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.drafts.DesktopDraftStore
import com.vitorpamplona.amethyst.desktop.service.drafts.DraftMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

private val ALLOWED_SCHEMES = setOf("https", "http", "nostr", "lightning")

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
    val editorState = remember { MarkdownEditorState() }
    var publishing by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var debouncedContent by remember { mutableStateOf("") }

    LaunchedEffect(editorState.text) {
        delay(300)
        debouncedContent = editorState.text
    }

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
                editorState.loadContent(body)
            }
        }
    }

    // Auto-generate slug from title if creating a new draft
    LaunchedEffect(title) {
        if (draftSlug == null && title.isNotBlank()) {
            slug = draftStore.slugFromTitle(title)
        }
    }

    val onLinkClick: (String) -> Unit =
        remember {
            { url: String ->
                val scheme = url.substringBefore(":").lowercase()
                if (scheme in ALLOWED_SCHEMES) {
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
                content = editorState.text,
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
                        content = editorState.text,
                        summary = summary.ifBlank { null },
                        image = bannerUrl.ifBlank { null },
                        tags = tags,
                        dTag = slug.ifBlank { draftStore.slugFromTitle(title) },
                        signer = account.signer,
                    )
                // TODO: send() is fire-and-forget; markPublished runs before relay ack.
                //  Consider waiting for relay OK response before marking as published.
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
                    if (event.type == KeyEventType.KeyDown && event.isMetaPressed) {
                        when (event.key) {
                            Key.S -> {
                                saveDraft()
                                true
                            }

                            Key.B -> {
                                editorState.toggleBold()
                                true
                            }

                            Key.I -> {
                                editorState.toggleItalic()
                                true
                            }

                            Key.E -> {
                                editorState.toggleInlineCode()
                                true
                            }

                            Key.K -> {
                                editorState.insertLink()
                                true
                            }

                            else -> {
                                false
                            }
                        }
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
                    enabled = !publishing && title.isNotBlank() && editorState.text.isNotBlank(),
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

        // Markdown toolbar — selection-aware toggle behavior
        MarkdownToolbar(state = editorState)

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Split pane: source left, preview right
        Row(
            modifier = Modifier.fillMaxSize().weight(1f),
        ) {
            // Source editor
            TextField(
                value = editorState.value,
                onValueChange = {
                    editorState.onValueChange(it)
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
                        if (debouncedContent.isNotBlank()) {
                            RenderMarkdown(
                                content = debouncedContent,
                                onLinkClick = onLinkClick,
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
