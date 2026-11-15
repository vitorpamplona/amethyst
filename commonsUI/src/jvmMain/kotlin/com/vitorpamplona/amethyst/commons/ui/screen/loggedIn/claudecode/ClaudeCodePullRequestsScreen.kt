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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.AgentWorkspaces
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeWorkspaceViewModel
import com.vitorpamplona.amethyst.commons.claudecode.PullRequestError
import com.vitorpamplona.amethyst.commons.claudecode.PullRequestInfo
import com.vitorpamplona.amethyst.commons.claudecode.WorkspaceRepoRow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_ahead
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_ask_commit
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_body
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_branch
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_details
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_hide_details
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_no_gh
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_nothing
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_open
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_open_draft
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_open_repo
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_pushed
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_rules
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_closed
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_draft
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_merged
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_open
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_uncommitted
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_view
import com.vitorpamplona.amethyst.commons.resources.claude_code_pull_requests
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_delete
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_none
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/**
 * A workspace session's repositories, each on its own branch: what each has to propose, and the
 * GitHub pull request the user opens for it. The agent can't push or open one; the user does,
 * here, through their own `gh`.
 */
@Composable
fun ClaudeCodePullRequestsScreen(
    key: String,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodePullRequests-$key") { ClaudeCodeWorkspaceViewModel(ClaudeCodeService.shared, key) }
    val workspace = viewModel.workspace
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ChatTopBar(stringRes(Res.string.claude_code_pull_requests), workspace?.title, nav) {
                if (workspace != null) {
                    HoverTooltip(stringRes(Res.string.refresh)) {
                        IconButton(onClick = { viewModel.refresh() }) { Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.refresh)) }
                    }
                    // Removing the workspace is rare and final: in the menu, not next to the work.
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(MaterialSymbols.MoreVert, contentDescription = null) }
                        RoundedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            RoundedMenuSection {
                                MenuRow(stringRes(Res.string.claude_code_workspace_delete)) {
                                    menu = false
                                    deleting = true
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (workspace == null) {
                Text(stringRes(Res.string.claude_code_workspace_none), color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(32.dp))
                return@Box
            }
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "rules") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringRes(Res.string.claude_code_pr_rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                        if (!viewModel.hasGh) Banner(stringRes(Res.string.claude_code_pr_no_gh))
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                items(rows, key = { it.name }) { row ->
                    RepoCard(
                        row = row,
                        canOpen = viewModel.hasGh && opening == null,
                        opening = opening == row.name,
                        error = errors[row.name],
                        onOpen = { title, body, draft -> viewModel.openPullRequest(row.name, title, body, draft) },
                        onChanges = { nav.nav(Route.ClaudeCodeChanges(key, row.name)) },
                        onAskToCommit = {
                            viewModel.askToCommit(row.name)
                            nav.popBack()
                        },
                    )
                }
                errors[""]?.let { error -> item(key = "delete-error") { Banner(error.reason) } }
            }
        }
    }

    if (deleting) {
        DeleteWorkspaceDialog(
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                // Back past the chat too: its folder is gone.
                viewModel.deleteWorkspace {
                    nav.popBack()
                    nav.popBack()
                }
            },
        )
    }
}

/** One repository: its branch, what it has to propose, and its pull request or the form to open one. */
@Composable
private fun RepoCard(
    row: WorkspaceRepoRow,
    canOpen: Boolean,
    opening: Boolean,
    error: PullRequestError?,
    onOpen: (title: String, body: String, draft: Boolean) -> Unit,
    onChanges: () -> Unit,
    onAskToCommit: () -> Unit,
) {
    val status = row.status
    var title by remember(row.draftTitle) { mutableStateOf(row.draftTitle) }
    var body by remember(row.draftBody) { mutableStateOf(row.draftBody) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // owner/name, as GitHub shows it, with a link to the repository's page.
            val label = AgentWorkspaces.ownerAndName(status.repo.url)
            val web = AgentWorkspaces.webUrl(status.repo.url)
            val uriHandler = LocalUriHandler.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (web != null) {
                    val open = stringRes(Res.string.claude_code_pr_open_repo, label)
                    HoverTooltip(open) {
                        IconButton(onClick = { uriHandler.openUri(web) }, modifier = Modifier.size(32.dp)) {
                            Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = open, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text(
                stringRes(Res.string.claude_code_pr_branch, status.repo.branch, status.repo.baseBranch),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.grayText,
            )
            val parts =
                listOfNotNull(
                    status.ahead.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.claude_code_pr_ahead, it, it) },
                    status.uncommitted.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.claude_code_pr_uncommitted, it, it) },
                )
            Text(parts.joinToString(" · ").ifEmpty { stringRes(Res.string.claude_code_pr_nothing) }, style = MaterialTheme.typography.bodyMedium)
            if (status.ahead > 0 || status.uncommitted > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // This repository's diff, before proposing it.
                    OutlinedButton(onClick = onChanges) { Text(stringRes(Res.string.claude_code_changes)) }
                    if (status.uncommitted > 0) {
                        OutlinedButton(onClick = onAskToCommit) { Text(stringRes(Res.string.claude_code_pr_ask_commit)) }
                    }
                }
            }
            val pr = row.pullRequest
            if (pr != null) {
                PullRequestLine(pr)
            } else if (status.ahead > 0) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringRes(Res.string.claude_code_pr_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text(stringRes(Res.string.claude_code_pr_body)) }, minLines = 3, maxLines = 10, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = canOpen && title.isNotBlank(), onClick = { onOpen(title, body, false) }) { Text(stringRes(Res.string.claude_code_pr_open)) }
                    OutlinedButton(enabled = canOpen && title.isNotBlank(), onClick = { onOpen(title, body, true) }) { Text(stringRes(Res.string.claude_code_pr_open_draft)) }
                    if (opening) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            error?.let { PullRequestErrorView(it, status.repo.branch) }
        }
    }
}

@Composable
private fun PullRequestLine(pr: PullRequestInfo) {
    val uriHandler = LocalUriHandler.current
    val (label, color) =
        when {
            pr.isMerged -> stringRes(Res.string.claude_code_pr_state_merged) to MaterialTheme.colorScheme.primary
            pr.isOpen && pr.isDraft -> stringRes(Res.string.claude_code_pr_state_draft) to MaterialTheme.colorScheme.grayText
            pr.isOpen -> stringRes(Res.string.claude_code_pr_state_open) to AddedColor
            else -> stringRes(Res.string.claude_code_pr_state_closed) to MaterialTheme.colorScheme.error
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        HoverTooltip(pr.url) {
            TextButton(onClick = { uriHandler.openUri(pr.url) }) { Text(stringRes(Res.string.claude_code_pr_view)) }
        }
    }
}

/**
 * Why the pull request didn't open: the first line of what git or `gh` said, the rest behind
 * "Details"; and, when the branch got pushed anyway, that it did, so the user knows only the
 * pull request is missing.
 */
@Composable
private fun PullRequestErrorView(
    error: PullRequestError,
    branch: String,
) {
    var details by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (error.pushed) {
            Text(stringRes(Res.string.claude_code_pr_pushed, branch), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringRes(Res.string.claude_code_pr_failed, error.summary), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (error.hasDetails) {
            TextButton(onClick = { details = !details }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringRes(if (details) Res.string.claude_code_pr_hide_details else Res.string.claude_code_pr_details), style = MaterialTheme.typography.labelMedium)
            }
            if (details) {
                SelectionContainer {
                    Text(
                        error.reason.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(8.dp),
                    )
                }
            }
        }
    }
}
