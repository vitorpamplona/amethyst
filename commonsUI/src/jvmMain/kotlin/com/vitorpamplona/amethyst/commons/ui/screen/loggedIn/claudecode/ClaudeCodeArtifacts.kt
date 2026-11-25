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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.ChatRow
import com.vitorpamplona.amethyst.commons.claudecode.PublishedArtifact
import com.vitorpamplona.amethyst.commons.claudecode.PublishedArtifacts
import com.vitorpamplona.amethyst.commons.claudecode.ToolViews
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_copy_link
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_copy_link_tooltip
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_created
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_creating
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_creating_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_kind_document
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_kind_page
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_kind_typed
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_link_copied
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_list_created
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_list_published
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_list_shown
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_list_updated
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_list_version
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_no_link
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_no_link_created
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_on_claude
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open_file
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open_file_tooltip
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open_link
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_opened_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_private
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_published
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_publishing
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_publishing_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_stopped
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_stopped_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_untitled_document
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_untitled_page
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_untitled_typed
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_updated
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_updated_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_updated_line_version
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_updated_version
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_waiting_create_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_waiting_publish_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_in_browser
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_output
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.onSurface65
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "ClaudeCodeArtifacts"

/** How long "Link copied" stays on the button. */
private const val COPIED_MS = 1_500L

/**
 * [artifact] with its page's `<title>` once the file is read, off the main thread: a page Claude
 * published names itself there, not in the call.
 */
@Composable
internal fun rememberTitled(
    artifact: PublishedArtifact,
    cwd: String,
): PublishedArtifact {
    val titled by produceState(artifact, artifact, cwd) {
        value = withContext(Dispatchers.IO) { PublishedArtifacts.withPageTitle(artifact, File(cwd)) }
    }
    return titled
}

/** What a card calls [artifact]: its title, its file's name, or what kind of thing it is. */
@Composable
internal fun artifactTitle(artifact: PublishedArtifact): String =
    artifact.title ?: PublishedArtifacts.fallbackTitle(artifact) ?: stringRes(
        when (artifact.kind) {
            PublishedArtifact.Kind.PAGE -> Res.string.claude_code_artifact_untitled_page
            PublishedArtifact.Kind.TYPED, PublishedArtifact.Kind.UNKNOWN -> Res.string.claude_code_artifact_untitled_typed
            PublishedArtifact.Kind.DOCUMENT -> Res.string.claude_code_artifact_untitled_document
        },
    )

internal fun PublishedArtifact.Kind.symbol(): MaterialSymbol =
    when (this) {
        PublishedArtifact.Kind.PAGE -> MaterialSymbols.Language
        PublishedArtifact.Kind.TYPED -> MaterialSymbols.Dashboard
        PublishedArtifact.Kind.DOCUMENT -> MaterialSymbols.AutoMirrored.Article
        PublishedArtifact.Kind.UNKNOWN -> MaterialSymbols.Link
    }

@Composable
private fun kindLabel(kind: PublishedArtifact.Kind): String =
    stringRes(
        when (kind) {
            PublishedArtifact.Kind.PAGE -> Res.string.claude_code_artifact_kind_page
            PublishedArtifact.Kind.TYPED, PublishedArtifact.Kind.UNKNOWN -> Res.string.claude_code_artifact_kind_typed
            PublishedArtifact.Kind.DOCUMENT -> Res.string.claude_code_artifact_kind_document
        },
    )

/** Where it stands: "Published to claude.ai", "Updated on claude.ai · version 3". */
@Composable
private fun statusLabel(artifact: PublishedArtifact): String =
    when (artifact.change) {
        PublishedArtifact.Change.CREATED ->
            stringRes(if (artifact.kind == PublishedArtifact.Kind.PAGE) Res.string.claude_code_artifact_published else Res.string.claude_code_artifact_created)
        PublishedArtifact.Change.UPDATED ->
            artifact.version?.let { stringRes(Res.string.claude_code_artifact_updated_version, it) } ?: stringRes(Res.string.claude_code_artifact_updated)
        PublishedArtifact.Change.OPENED -> stringRes(Res.string.claude_code_artifact_on_claude)
    }

/** Where it stands, in a list headed "On claude.ai": "Published", "Version 2". */
@Composable
private fun shortStatus(artifact: PublishedArtifact): String =
    when (artifact.change) {
        PublishedArtifact.Change.CREATED ->
            stringRes(if (artifact.kind == PublishedArtifact.Kind.PAGE) Res.string.claude_code_artifact_list_published else Res.string.claude_code_artifact_list_created)
        PublishedArtifact.Change.UPDATED ->
            artifact.version?.let { stringRes(Res.string.claude_code_artifact_list_version, it) } ?: stringRes(Res.string.claude_code_artifact_list_updated)
        PublishedArtifact.Change.OPENED -> stringRes(Res.string.claude_code_artifact_list_shown)
    }

/** A chat row for a call that put something on claude.ai: the card, or one line under an earlier card. */
@Composable
internal fun PublishedRow(
    row: ChatRow.Published,
    cwd: String,
    /** The turn is still on: running, or waiting on the user for this or another call. */
    turnLive: Boolean,
    waitingForApproval: Boolean,
) {
    val artifact = rememberTitled(row.artifact, cwd)
    val running = !row.call.isDone
    when {
        // The approval card under the chat shows what it would publish: here it is one line, as a pending edit is.
        running && waitingForApproval -> PublishedLine(artifact, LineState.WAITING)
        // A page published again: a line from the start, so no card shows up and folds away.
        row.repeat ->
            PublishedLine(
                artifact,
                if (!running) {
                    LineState.DONE
                } else if (turnLive) {
                    LineState.RUNNING
                } else {
                    LineState.STOPPED
                },
            )
        else -> ArtifactCard(row.call, artifact, cwd, turnLive)
    }
}

/**
 * The card: what it is and where it went, with the way to see it. The page opens on claude.ai,
 * where it lives (private, behind the user's login); a page published from a file here can also
 * be opened from that file. A click on the card opens what the tool said, as a diff card opens.
 */
@Composable
internal fun ArtifactCard(
    call: ChatItem.ToolCall,
    artifact: PublishedArtifact,
    cwd: String,
    /** The turn is still on: running, or waiting on the user for another call. Over, a call with no result was stopped. */
    turnLive: Boolean,
) {
    val open = rememberArtifactOpener()
    var details by remember(call.id) { mutableStateOf(false) }
    val url = artifact.url
    val running = !call.isDone
    val localPage = rememberLocalPage(artifact.file, cwd)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (call.result != null) Modifier.clickable { details = !details } else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (url != null || localPage != null) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (running && turnLive) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    // The status line under the title already says what kind it is.
                    Icon(artifact.kind.symbol(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(artifactTitle(artifact), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status =
                    when {
                        // A call the user stopped never gets a result: it says so rather than spin.
                        running && !turnLive -> stringRes(Res.string.claude_code_artifact_stopped)
                        running && artifact.kind == PublishedArtifact.Kind.PAGE -> stringRes(Res.string.claude_code_artifact_publishing)
                        running -> stringRes(Res.string.claude_code_artifact_creating)
                        url == null ->
                            stringRes(if (artifact.kind == PublishedArtifact.Kind.PAGE) Res.string.claude_code_artifact_no_link else Res.string.claude_code_artifact_no_link_created)
                        else -> kindLabel(artifact.kind) + " · " + statusLabel(artifact)
                    }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                artifact.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (url != null) {
                HoverTooltip(stringRes(Res.string.claude_code_artifact_open_link, url)) {
                    FilledTonalButton(onClick = { open.link(url) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.claude_code_artifact_open))
                    }
                }
            }
        }
        // The other ways to it, at the card's end: the link to keep, the file it was made from.
        if (url != null || localPage != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (url != null) CopyLinkButton(url)
                if (localPage != null) {
                    HoverTooltip(stringRes(Res.string.claude_code_artifact_open_file_tooltip, ToolViews.relative(localPage.path, File(cwd)))) {
                        TextButton(onClick = { open.file(localPage) }) {
                            Icon(MaterialSymbols.AutoMirrored.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringRes(Res.string.claude_code_artifact_open_file))
                        }
                    }
                }
            }
        }
        open.failed?.let {
            Text(
                stringRes(Res.string.claude_code_artifact_open_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }
        AnimatedVisibility(details) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringRes(Res.string.claude_code_tool_output), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
                CodeBlock(call.result.orEmpty().take(RESULT_SHOWN), maxHeight = 200)
            }
        }
    }
}

/**
 * What an approval would put on claude.ai: its name, the file it comes from (which can be looked
 * at first, in the browser), and who can see it once it is up.
 */
@Composable
internal fun PublishPreview(
    publishing: PublishedArtifact,
    cwd: String,
) {
    val artifact = rememberTitled(publishing, cwd)
    val localPage = rememberLocalPage(artifact.file, cwd)
    val open = rememberArtifactOpener()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(artifactTitle(artifact), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            artifact.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            artifact.file?.let {
                Text(
                    ToolViews.relative(it, File(cwd)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // An update keeps whoever the page is already shared with.
                if (artifact.change == PublishedArtifact.Change.CREATED) {
                    Icon(MaterialSymbols.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringRes(Res.string.claude_code_artifact_private), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
                }
                Spacer(Modifier.weight(1f))
                if (localPage != null) {
                    TextButton(onClick = { open.file(localPage) }) {
                        Icon(MaterialSymbols.AutoMirrored.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.claude_code_artifact_open_file))
                    }
                }
            }
            open.failed?.let {
                Text(stringRes(Res.string.claude_code_artifact_open_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Where a one-line row stands: its call waits for the user, runs, was stopped, or is done. */
private enum class LineState { WAITING, RUNNING, STOPPED, DONE }

/**
 * One line for an artifact: a later publish or an opening of one a card above already shows, or
 * a publish waiting for the approval card. Done, it opens the artifact.
 */
@Composable
private fun PublishedLine(
    artifact: PublishedArtifact,
    state: LineState,
) {
    val open = rememberArtifactOpener()
    val url = artifact.url?.takeIf { state == LineState.DONE }
    val title = artifactTitle(artifact)
    val makes = artifact.kind != PublishedArtifact.Kind.PAGE && artifact.change == PublishedArtifact.Change.CREATED
    val text =
        when (state) {
            LineState.WAITING -> stringRes(if (makes) Res.string.claude_code_artifact_waiting_create_line else Res.string.claude_code_artifact_waiting_publish_line, title)
            LineState.RUNNING -> stringRes(if (makes) Res.string.claude_code_artifact_creating_line else Res.string.claude_code_artifact_publishing_line, title)
            LineState.STOPPED -> stringRes(Res.string.claude_code_artifact_stopped_line, title)
            LineState.DONE ->
                when (artifact.change) {
                    PublishedArtifact.Change.OPENED -> stringRes(Res.string.claude_code_artifact_opened_line, title)
                    else ->
                        artifact.version?.let { stringRes(Res.string.claude_code_artifact_updated_line_version, title, it) }
                            ?: stringRes(Res.string.claude_code_artifact_updated_line, title)
                }
        }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .then(if (url != null) Modifier.clickable { open.link(url) } else Modifier)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (state == LineState.RUNNING) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.onSurface65)
                } else {
                    Icon(artifact.kind.symbol(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface65, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface65, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (url != null) {
                HoverTooltip(stringRes(Res.string.claude_code_artifact_open_link, url)) {
                    TextButton(onClick = { open.link(url) }) {
                        Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.claude_code_artifact_open))
                    }
                }
            }
        }
        open.failed?.let {
            Text(stringRes(Res.string.claude_code_artifact_open_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 26.dp))
        }
    }
}

@Composable
private fun CopyLinkButton(url: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(url) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    // Only the user can open the link until they share the artifact: said before it is pasted somewhere.
    HoverTooltip(stringRes(Res.string.claude_code_artifact_copy_link_tooltip)) {
        TextButton(
            onClick = {
                scope.launch {
                    // Another app can hold the system clipboard (Windows), or there is none (headless).
                    try {
                        clipboard.setText(url)
                        copied = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not copy the link", e)
                    }
                }
            },
        ) {
            Icon(if (copied) MaterialSymbols.Check else MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(if (copied) Res.string.claude_code_artifact_link_copied else Res.string.claude_code_artifact_copy_link))
        }
    }
}

/**
 * What the session put on claude.ai, newest first, each called by its page's `<title>` once the
 * files are read. The same list for the top bar's menu and the workspace panel's section.
 */
@Composable
internal fun rememberSessionArtifacts(
    items: List<ChatItem>,
    cwd: String,
): List<PublishedArtifact> {
    val published = remember(items) { PublishedArtifacts.inSession(items) }
    val titled by produceState(published, published, cwd) {
        value = withContext(Dispatchers.IO) { published.map { PublishedArtifacts.withPageTitle(it, File(cwd)) } }
    }
    return titled
}

/** One artifact in a list: its kind, name and where it stands; a click opens it. */
@Composable
internal fun PublishedArtifactRow(
    artifact: PublishedArtifact,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quiet, like the repositories' rows beside it in the panel.
        Icon(artifact.kind.symbol(), contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(artifactTitle(artifact), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // The list's heading already says where: "Page · Version 2".
                kindLabel(artifact.kind) + " · " + shortStatus(artifact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(16.dp))
    }
}

/** A file Claude wrote that a browser shows as a page (HTML, SVG), while it is still there. */
@Composable
internal fun rememberLocalPage(
    path: String?,
    cwd: String,
): File? {
    val page by produceState<File?>(null, path, cwd) {
        value = path?.takeIf { PublishedArtifacts.isPage(it) }?.let { withContext(Dispatchers.IO) { PublishedArtifacts.localPage(it, File(cwd)) } }
    }
    return page
}

/** "Open in browser" for a page Claude wrote in the session's folder: under the file's diff card. */
@Composable
internal fun OpenPageButton(
    page: File,
    modifier: Modifier = Modifier,
) {
    val open = rememberArtifactOpener()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { open.file(page) }) {
            Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.claude_code_open_in_browser))
        }
        open.failed?.let {
            Text(stringRes(Res.string.claude_code_artifact_open_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Opens an artifact's link, or a page file, and remembers when nothing on this computer could. */
internal class ArtifactOpener(
    private val openLink: (String) -> Boolean,
    private val openFile: (File) -> Unit,
) {
    var failed by mutableStateOf<String?>(null)

    fun link(url: String) {
        failed = if (openLink(url)) null else url
    }

    fun file(page: File) = openFile(page)
}

@Composable
internal fun rememberArtifactOpener(): ArtifactOpener {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    return remember(uriHandler) {
        lateinit var opener: ArtifactOpener
        opener =
            ArtifactOpener(
                openLink = { url ->
                    // The window's handler throws when no app takes the link.
                    try {
                        uriHandler.openUri(url)
                        true
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "No app opened an artifact link", e)
                        false
                    }
                },
                openFile = { page ->
                    scope.launch {
                        val opened = withContext(Dispatchers.IO) { LocalPages.open(page) }
                        opener.failed = if (opened) null else page.path
                    }
                },
            )
        opener
    }
}

/**
 * Shows a page Claude wrote on this computer in the browser. The window's link handler opens no
 * `file:` links, since a note decides what reaches it; this is only for a file the session wrote
 * or published, and only for a page ([PublishedArtifacts.isPage]), so what opens it is the browser.
 */
internal object LocalPages {
    private val os = System.getProperty("os.name").orEmpty().lowercase()

    fun open(page: File): Boolean {
        // Judged by the file a link leads to, and that file is what opens.
        val target = PublishedArtifacts.localPage(page.path, null) ?: return false
        val uri = target.toURI()
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG) { "Could not open a local page: ${e::class.simpleName}" }
        }
        val command =
            when {
                os.contains("mac") -> listOf("open", target.path)
                os.contains("linux") -> listOf("xdg-open", target.path)
                else -> return false
            }
        return try {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            // xdg-open answers at once; a failure to find any handler comes back as its exit code.
            !process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() == 0
        } catch (e: Exception) {
            Log.w(TAG) { "Could not open a local page with ${command.first()}: ${e::class.simpleName}" }
            false
        }
    }
}
