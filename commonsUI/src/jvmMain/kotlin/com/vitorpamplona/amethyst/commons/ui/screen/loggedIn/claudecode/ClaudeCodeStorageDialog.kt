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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.AgentWorkspaces
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeStorageViewModel
import com.vitorpamplona.amethyst.commons.claudecode.StorageResult
import com.vitorpamplona.amethyst.commons.claudecode.WorkspaceUse
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_close
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_auto
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_auto_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_clean_up
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_cleaned
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_finished
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_measuring
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_mirrors
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_mirrors_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_none_finished
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_tidied
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_tidied_none
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_tidy
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_tidy_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_unsaved
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_unused_mirrors
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage_workspaces
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.formatFileSize

/**
 * What the coding agents' workspaces and mirrors take on this computer, and the ways to give it
 * back: cleaning up the workspaces whose pull requests are all merged or closed (by hand, or a
 * day later on its own), and tidying the mirrors.
 */
@Composable
internal fun StorageDialog(onDismiss: () -> Unit) {
    val viewModel = rememberViewModel(key = "ClaudeCodeStorage") { ClaudeCodeStorageViewModel(ClaudeCodeService.shared) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = formDialogColor(),
        title = { Text(stringRes(Res.string.claude_code_storage)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FormGap)) {
                val current = state
                if (current == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(InlineGap))
                        Text(stringRes(Res.string.claude_code_storage_measuring), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // The totals, as one card.
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(InlineGap)) {
                            Text(pluralStringRes(Res.plurals.claude_code_storage_workspaces, current.workspaces.size, current.workspaces.size, formatFileSize(current.workspacesBytes)), style = MaterialTheme.typography.bodyLarge)
                            Text(pluralStringRes(Res.plurals.claude_code_storage_mirrors, current.mirrors, current.mirrors, formatFileSize(current.mirrorsBytes)), style = MaterialTheme.typography.bodyLarge)
                            if (current.unusedMirrors > 0) {
                                Text(pluralStringRes(Res.plurals.claude_code_storage_unused_mirrors, current.unusedMirrors, current.unusedMirrors, AgentWorkspaces.UNUSED_MIRROR_DAYS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                            }
                        }
                    }
                    ToggleRow(
                        title = stringRes(Res.string.claude_code_storage_auto),
                        description = stringRes(Res.string.claude_code_storage_auto_desc, AgentWorkspaces.UNUSED_MIRROR_DAYS),
                        checked = preferences.autoCleanup,
                        onChange = viewModel::setAutoCleanup,
                    )
                    FinishedWorkspaces(current.workspaces.filter { it.finished }, current.removable.sumOf { it.bytes }, current.removable.size, working, viewModel::cleanUpFinished)
                    Column(verticalArrangement = Arrangement.spacedBy(InlineGap)) {
                        Text(stringRes(Res.string.claude_code_storage_mirrors_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                        Text(stringRes(Res.string.claude_code_storage_tidy_desc, AgentWorkspaces.UNUSED_MIRROR_DAYS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(start = 4.dp))
                        OutlinedButton(enabled = !working && current.mirrors > 0, onClick = viewModel::tidyMirrors) { Text(stringRes(Res.string.claude_code_storage_tidy)) }
                    }
                }
                if (working) {
                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }
                }
                result?.let { done ->
                    Text(
                        when (done) {
                            is StorageResult.Cleaned -> pluralStringRes(Res.plurals.claude_code_storage_cleaned, done.workspaces, done.workspaces, formatFileSize(done.freed))
                            is StorageResult.Tidied if done.removedMirrors == 0 -> stringRes(Res.string.claude_code_storage_tidied_none, formatFileSize(done.freed))
                            is StorageResult.Tidied -> pluralStringRes(Res.plurals.claude_code_storage_tidied, done.removedMirrors, done.removedMirrors, formatFileSize(done.freed))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.claude_code_close)) } },
    )
}

/** The workspaces whose pull requests are all merged or closed, and the button that removes them. */
@Composable
private fun FinishedWorkspaces(
    finished: List<WorkspaceUse>,
    removableBytes: Long,
    removable: Int,
    working: Boolean,
    onCleanUp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(InlineGap)) {
        Text(stringRes(Res.string.claude_code_storage_finished), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        if (finished.isEmpty()) {
            Text(stringRes(Res.string.claude_code_storage_none_finished), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(start = 4.dp))
            return
        }
        finished.forEach { use ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(use.workspace.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(use.workspace.repoLabels.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (use.unsaved) {
                        Text(stringRes(Res.string.claude_code_storage_unsaved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(InlineGap))
                Text(formatFileSize(use.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            }
        }
        if (removable > 0) {
            FilledTonalButton(enabled = !working, onClick = onCleanUp) {
                Text(pluralStringRes(Res.plurals.claude_code_storage_clean_up, removable, removable, formatFileSize(removableBytes)))
            }
        }
    }
}
