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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ChangesState
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeChangesViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.GitWorkInfo
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_commits
import com.vitorpamplona.amethyst.commons.resources.claude_code_no_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_uncommitted
import com.vitorpamplona.amethyst.commons.resources.claude_code_untracked
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/** Everything a session's branch changed since its base: the commits, then the diff, uncommitted work included. */
@Composable
fun ClaudeCodeChangesScreen(
    key: String,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodeChanges-$key") { ClaudeCodeChangesViewModel(ClaudeCodeService.shared, key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val info = (state as? ChangesState.Loaded)?.info

    Scaffold(
        topBar = {
            ChatTopBar(
                title = stringRes(Res.string.claude_code_changes),
                subtitle = info?.let { listOfNotNull(it.branch, it.base?.let { b -> "← $b" }).joinToString(" ") },
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
                        s.info?.let { CommitList(it) }
                        if (s.untracked.isNotEmpty()) {
                            SectionTitle(stringRes(Res.string.claude_code_untracked))
                            s.untracked.forEach { Mono(it) }
                        }
                        if (s.patch.hasDiff) {
                            GitDiffView(s.patch)
                        } else if (s.untracked.isEmpty()) {
                            Text(stringRes(Res.string.claude_code_no_changes), color = MaterialTheme.colorScheme.grayText)
                        }
                    }
                }
        }
    }
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
