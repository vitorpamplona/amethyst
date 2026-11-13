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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ChangesScope
import com.vitorpamplona.amethyst.commons.claudecode.ChangesState
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeChangesViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.GitWorkInfo
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.claude_code_ahead_of
import com.vitorpamplona.amethyst.commons.resources.claude_code_ask_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_ask_placeholder
import com.vitorpamplona.amethyst.commons.resources.claude_code_ask_send
import com.vitorpamplona.amethyst.commons.resources.claude_code_ask_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_commits
import com.vitorpamplona.amethyst.commons.resources.claude_code_no_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_scope_branch
import com.vitorpamplona.amethyst.commons.resources.claude_code_scope_uncommitted
import com.vitorpamplona.amethyst.commons.resources.claude_code_uncommitted
import com.vitorpamplona.amethyst.commons.resources.claude_code_untracked
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffFile
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLine

/**
 * What a session changed: what isn't committed yet, the whole branch, or one commit. A line
 * can be asked about: the question goes to the chat with the line quoted.
 */
@Composable
fun ClaudeCodeChangesScreen(
    key: String,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodeChanges-$key") { ClaudeCodeChangesViewModel(ClaudeCodeService.shared, key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val info = (state as? ChangesState.Loaded)?.info
    var asking by remember { mutableStateOf<Pair<GitDiffFile, GitDiffLine>?>(null) }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = stringRes(Res.string.claude_code_changes),
                subtitle =
                    info?.let {
                        val base = it.base
                        if (base != null) {
                            pluralStringRes(Res.plurals.claude_code_ahead_of, it.ahead, it.ahead, base)
                        } else {
                            it.branch
                        }
                    },
                nav = nav,
            ) {
                IconButton(onClick = { viewModel.reload() }) { Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.refresh)) }
            }
        },
    ) { padding ->
        when (val s = state) {
            ChangesState.Loading ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ChangesState.Loaded ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ScopePicker(s.info, s.scope, viewModel::show)
                        if (s.patch.hasDiff) {
                            Text(stringRes(Res.string.claude_code_ask_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                            GitDiffView(s.patch, onLineClick = { file, line -> asking = file to line })
                        } else {
                            Text(stringRes(Res.string.claude_code_no_changes), color = MaterialTheme.colorScheme.grayText)
                        }
                        // Binary or large new files the diff can't show.
                        val unseen = s.untracked.filter { path -> s.patch.files.none { it.displayPath == path } }
                        if (unseen.isNotEmpty()) {
                            SectionTitle(stringRes(Res.string.claude_code_untracked))
                            unseen.forEach { Mono(it) }
                        }
                    }
                }
        }
    }

    asking?.let { (file, line) ->
        AskAboutLineDialog(
            path = file.displayPath,
            line = line.newNumber ?: line.oldNumber,
            code = line.content,
            onDismiss = { asking = null },
            onAsk = { question ->
                viewModel.askAbout(file.displayPath, line.newNumber ?: line.oldNumber, line.content, question)
                asking = null
                nav.popBack()
            },
        )
    }
}

/** Which changes to show: the uncommitted ones, the whole branch, or one of its commits. */
@Composable
private fun ScopePicker(
    info: GitWorkInfo?,
    scope: ChangesScope,
    onPick: (ChangesScope) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = scope == ChangesScope.Uncommitted,
                onClick = { onPick(ChangesScope.Uncommitted) },
                label = { Text(stringRes(Res.string.claude_code_scope_uncommitted)) },
            )
            FilterChip(
                selected = scope == ChangesScope.Branch,
                onClick = { onPick(ChangesScope.Branch) },
                label = { Text(stringRes(Res.string.claude_code_scope_branch)) },
            )
        }
        val commits = info?.commits.orEmpty()
        if (commits.isNotEmpty()) {
            SectionTitle(pluralStringRes(Res.plurals.claude_code_commits, commits.size, commits.size))
            commits.forEach { commit ->
                val selected = (scope as? ChangesScope.Commit)?.sha == commit.sha
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onPick(ChangesScope.Commit(commit.sha)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(commit.sha.take(7), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(commit.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A question about one line of the diff, sent to the chat with the line and where it is. */
@Composable
private fun AskAboutLineDialog(
    path: String,
    line: Int?,
    code: String,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.claude_code_ask_title)) },
        text = {
            Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (line != null) "$path:$line" else path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                CodeBlock(code, maxHeight = 120)
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text(stringRes(Res.string.claude_code_ask_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = question.isNotBlank(), onClick = { onAsk(question.trim()) }) { Text(stringRes(Res.string.claude_code_ask_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
internal fun CommitList(info: GitWorkInfo) {
    if (info.commits.isEmpty() && info.uncommitted == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (info.commits.isNotEmpty()) {
            SectionTitle(pluralStringRes(Res.plurals.claude_code_commits, info.commits.size, info.commits.size))
            info.commits.forEach { commit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(commit.sha.take(7), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(commit.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (info.uncommitted > 0) {
            Text(
                pluralStringRes(Res.plurals.claude_code_uncommitted, info.uncommitted, info.uncommitted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
}
