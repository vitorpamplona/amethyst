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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeWorkspaceViewModel
import com.vitorpamplona.amethyst.commons.claudecode.WorkspaceRepoRow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_ask_commit
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_panel_create
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_panel_details
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_panel_more
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_panel_number
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_send_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_closed
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_draft
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_merged
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_open
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_view
import com.vitorpamplona.amethyst.commons.resources.claude_code_published_section
import com.vitorpamplona.amethyst.commons.resources.claude_code_session_panel
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/** How wide the panel starts, how far it can be dragged, and how wide the screen must be to have it. */
internal val PullRequestsPanelWidth = 320.dp
internal val PullRequestsPanelWidthRange = 240f..560f
internal val PullRequestsPanelMinScreen = 1000.dp

/** The narrowest the chat gets beside the panel. */
internal val ChatMinWidth = 480.dp

/** The purple GitHub gives a merged pull request. */
private val MergedColor = Color(0xFFA371F7)

/**
 * A workspace chat's repositories beside the conversation: each one's pull request (title, number,
 * size) or what its branch has so far. Opening a pull request takes a title and description, so
 * that still happens on the full screen, one click away.
 */
@Composable
internal fun PullRequestsPanel(
    key: String,
    busy: Boolean,
    nav: INav,
    width: Dp,
    /** The chat's rows: what the session put on claude.ai is listed under the repositories. */
    items: List<ChatItem>,
    cwd: String,
    modifier: Modifier = Modifier,
) {
    val published = rememberSessionArtifacts(items, cwd)
    val open = rememberArtifactOpener()
    val viewModel = rememberViewModel(key = "ClaudeCodePullRequestsPanel-$key") { ClaudeCodeWorkspaceViewModel(ClaudeCodeService.shared, key) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    // A turn that ends may have committed, pushed by the user's hand meanwhile, or both: look again.
    var wasBusy by remember { mutableStateOf(busy) }
    LaunchedEffect(busy) {
        if (wasBusy && !busy) viewModel.refresh()
        wasBusy = busy
    }

    Column(modifier.width(width).fillMaxHeight().padding(top = 4.dp)) {
        // What the session made: its repositories' changes, and what it put on claude.ai.
        Text(
            stringRes(Res.string.claude_code_session_panel),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 6.dp, bottom = 6.dp),
        )
        // The repositories' part: its refresh and its pull requests page are git's, so they head it.
        Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelSectionLabel(stringRes(Res.string.claude_code_changes), Modifier.weight(1f))
            HoverTooltip(stringRes(Res.string.refresh)) {
                IconButton(onClick = { viewModel.refresh() }, modifier = Modifier.size(32.dp)) {
                    Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.refresh), tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                }
            }
            HoverTooltip(stringRes(Res.string.claude_code_pr_panel_details)) {
                IconButton(onClick = { nav.nav(Route.ClaudeCodePullRequests(key)) }, modifier = Modifier.size(32.dp)) {
                    Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = stringRes(Res.string.claude_code_pr_panel_details), tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                }
            }
        }
        // The line keeps its height either way, so the list doesn't jump on each refresh.
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { it.name }) { row ->
                PanelRow(
                    row = row,
                    onChanges = { nav.nav(Route.ClaudeCodeChanges(key, row.name)) },
                    onOpenPullRequest = { nav.nav(Route.ClaudeCodePullRequests(key)) },
                    onPropose = { nav.nav(Route.ClaudeCodePropose(key, row.name)) },
                    onAskToCommit = { viewModel.askToCommit(row.name) },
                )
            }
            // The session's other output: pages and docs on claude.ai, newest first.
            if (published.isNotEmpty()) {
                item(key = "published-title") {
                    PanelSectionLabel(
                        stringRes(Res.string.claude_code_published_section),
                        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(published, key = { "published-" + it.url }) { artifact ->
                    val url = artifact.url ?: return@items
                    PublishedArtifactRow(artifact, Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { open.link(url) }
                }
            }
        }
        open.failed?.let {
            Text(
                stringRes(Res.string.claude_code_artifact_open_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )
        }
    }
}

/** A part of the panel: "Changes", "On claude.ai". */
@Composable
private fun PanelSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText, modifier = modifier)
}

/**
 * One repository. With a pull request, the pull request: its title, then `repo #N`, its size and its
 * state. Without one, the repository with what its branch changed and the button that proposes it.
 */
@Composable
private fun PanelRow(
    row: WorkspaceRepoRow,
    onChanges: () -> Unit,
    onOpenPullRequest: () -> Unit,
    onPropose: () -> Unit,
    onAskToCommit: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val status = row.status
    val pr = row.pullRequest
    val hasChanges = status.ahead > 0 || status.uncommitted > 0
    // A NIP-34 repository takes a signed proposal; any other, a GitHub pull request from the full page.
    val onCreate: (() -> Unit)? =
        when {
            pr != null || status.ahead == 0 -> null
            row.nostr != null -> onPropose
            else -> onOpenPullRequest
        }
    val menu: @Composable (close: () -> Unit) -> Unit = { close ->
        fun pick(action: () -> Unit): () -> Unit =
            {
                close()
                action()
            }
        if (pr != null) MenuRow(stringRes(Res.string.claude_code_pr_view), onClick = pick { uriHandler.openUri(pr.url) })
        if (row.nostr != null && status.ahead > 0) MenuRow(stringRes(Res.string.claude_code_pr_send_proposal), onClick = pick(onPropose))
        if (hasChanges) MenuRow(stringRes(Res.string.claude_code_changes), onClick = pick(onChanges))
        if (status.uncommitted > 0) MenuRow(stringRes(Res.string.claude_code_pr_ask_commit), onClick = pick(onAskToCommit))
        MenuRow(stringRes(Res.string.claude_code_pr_panel_details), onClick = pick(onOpenPullRequest))
    }

    if (pr != null) {
        val (stateLabel, stateColor) =
            when {
                pr.isMerged -> stringRes(Res.string.claude_code_pr_state_merged) to MergedColor
                pr.isOpen && pr.isDraft -> stringRes(Res.string.claude_code_pr_state_draft) to MaterialTheme.colorScheme.grayText
                pr.isOpen -> stringRes(Res.string.claude_code_pr_state_open) to AddedColor
                else -> stringRes(Res.string.claude_code_pr_state_closed) to MaterialTheme.colorScheme.error
            }
        RowFrame(onClick = { uriHandler.openUri(pr.url) }, twoLines = true) {
            Icon(if (pr.isMerged) MaterialSymbols.CallMerge else MaterialSymbols.AltRoute, contentDescription = stateLabel, tint = stateColor, modifier = Modifier.padding(top = 2.dp).size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pr.title ?: row.draftTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pr.number?.let { stringRes(Res.string.claude_code_pr_panel_number, row.name, it) } ?: row.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val additions = pr.additions
                    val deletions = pr.deletions
                    if (additions != null && deletions != null) {
                        Spacer(Modifier.width(6.dp))
                        DiffStat(additions, deletions)
                    }
                }
            }
            // The menu at the title's height; the state's dot under it, as GitHub's list has it.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MoreMenu(row.name, menu)
                HoverTooltip(stateLabel) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(stateColor),
                    )
                }
            }
        }
    } else {
        RowFrame(onClick = if (hasChanges) onChanges else null, twoLines = false) {
            Icon(MaterialSymbols.Difference, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            // The name and its size take the free width; the button or menu keeps to the end.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasChanges) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (status.insertions > 0 || status.deletions > 0) {
                    Spacer(Modifier.width(8.dp))
                    DiffStat(status.insertions, status.deletions)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (onCreate != null) {
                CreateButton(row.name, onCreate, menu)
            } else {
                MoreMenu(row.name, menu)
            }
        }
    }
}

/** A row's hover and click area, inset from the panel's edges. */
@Composable
private fun RowFrame(
    onClick: (() -> Unit)?,
    twoLines: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 8.dp, end = 4.dp, top = if (twoLines) 8.dp else 4.dp, bottom = if (twoLines) 8.dp else 4.dp),
        verticalAlignment = if (twoLines) Alignment.Top else Alignment.CenterVertically,
        content = content,
    )
}

/** `Create PR | ▾`: one outlined button, the arrow holding the repository's other actions. */
@Composable
private fun CreateButton(
    name: String,
    onCreate: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(30.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).clip(shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().clickable(onClick = onCreate).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(stringRes(Res.string.claude_code_pr_panel_create), style = MaterialTheme.typography.labelLarge)
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box {
            val more = stringRes(Res.string.claude_code_pr_panel_more, name)
            Box(Modifier.fillMaxHeight().clickable { open = true }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = more, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
            }
            RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                RoundedMenuSection { menu { open = false } }
            }
        }
    }
}

@Composable
private fun MoreMenu(
    name: String,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        val more = stringRes(Res.string.claude_code_pr_panel_more, name)
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(MaterialSymbols.MoreHoriz, contentDescription = more, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection { menu { open = false } }
        }
    }
}
