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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ChatStatus
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeSessionsViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeSessionRow
import com.vitorpamplona.amethyst.commons.claudecode.NewChatError
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.claude_code_delete
import com.vitorpamplona.amethyst.commons.resources.claude_code_delete_body
import com.vitorpamplona.amethyst.commons.resources.claude_code_delete_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_not_a_folder
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_not_a_repository
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_worktree
import com.vitorpamplona.amethyst.commons.resources.claude_code_folder
import com.vitorpamplona.amethyst.commons.resources.claude_code_install_body
import com.vitorpamplona.amethyst.commons.resources.claude_code_install_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_new_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_no_matches
import com.vitorpamplona.amethyst.commons.resources.claude_code_no_sessions
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_elsewhere_warning
import com.vitorpamplona.amethyst.commons.resources.claude_code_permissions
import com.vitorpamplona.amethyst.commons.resources.claude_code_rename_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_search_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_start
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_needs_approval
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_ready
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_starting
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_working
import com.vitorpamplona.amethyst.commons.resources.claude_code_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_worktree
import com.vitorpamplona.amethyst.commons.resources.claude_code_worktree_desc
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.resources.rename
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.claudecode.protocol.PermissionMode
import java.io.File

/**
 * Every Claude Code session on this computer, grouped by the repository it worked on (worktrees
 * included), newest first, with the chats this app runs marked by what they are doing.
 */
@Composable
fun ClaudeCodeSessionsScreen(nav: INav) {
    val viewModel = rememberViewModel(key = "ClaudeCodeSessions") { ClaudeCodeSessionsViewModel(ClaudeCodeService.shared) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val executable by viewModel.executable.collectAsStateWithLifecycle()

    var searching by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ClaudeSessionRow?>(null) }
    var deleting by remember { mutableStateOf<ClaudeSessionRow?>(null) }

    Scaffold(
        topBar = {
            ChatTopBar(stringRes(Res.string.claude_code_title), null, nav) {
                HoverTooltip(stringRes(Res.string.claude_code_search_hint)) {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) viewModel.search("")
                    }) { Icon(MaterialSymbols.Search, contentDescription = stringRes(Res.string.claude_code_search_hint)) }
                }
                HoverTooltip(stringRes(Res.string.refresh)) {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.refresh)) }
                }
                HoverTooltip(stringRes(Res.string.claude_code_new_session)) {
                    IconButton(onClick = { showNew = true }) { Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.claude_code_new_session)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ChatColumnMaxWidth).fillMaxSize()) {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::search,
                        placeholder = { Text(stringRes(Res.string.claude_code_search_hint)) },
                        leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                when {
                    !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    executable == null && rows.isEmpty() -> InstallState()
                    rows.isEmpty() -> EmptyState(stringRes(if (query.isBlank()) Res.string.claude_code_no_sessions else Res.string.claude_code_no_matches))
                    else -> SessionList(rows, nav, onRename = { renaming = it }, onDelete = { deleting = it })
                }
            }
        }
    }

    if (showNew) {
        NewSessionDialog(
            viewModel = viewModel,
            // Removed worktrees stay in the history; only folders that still exist are offered.
            suggestedFolders = remember(projects) { projects.map { it.cwd }.filter { File(it).isDirectory } },
            onDismiss = {
                showNew = false
                viewModel.clearNewChatError()
            },
            onStarted = { key ->
                showNew = false
                nav.nav(Route.ClaudeCodeChat(key))
            },
        )
    }

    renaming?.let { row ->
        RenameDialog(
            initial = row.title,
            onDismiss = { renaming = null },
            onSave = {
                viewModel.rename(row, it)
                renaming = null
            },
        )
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringRes(Res.string.claude_code_delete_title)) },
            text = { Text(stringRes(Res.string.claude_code_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(row)
                    deleting = null
                }) { Text(stringRes(Res.string.claude_code_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun SessionList(
    rows: List<ClaudeSessionRow>,
    nav: INav,
    onRename: (ClaudeSessionRow) -> Unit,
    onDelete: (ClaudeSessionRow) -> Unit,
) {
    // Sections in the order of their newest session; sessions newest first inside each.
    val sections = remember(rows) { rows.groupBy { it.project }.toList() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        sections.forEach { (project, sessions) ->
            item(key = "section-$project") {
                Text(
                    project,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            items(sessions, key = { it.key }) { row ->
                SessionRow(
                    row = row,
                    onOpen = { nav.nav(Route.ClaudeCodeChat(row.key)) },
                    onRename = { onRename(row) },
                    onDelete = { onDelete(row) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    row: ClaudeSessionRow,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusAvatar(row)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title.ifBlank { stringRes(Res.string.claude_code_new_session) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.gitBranch?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    timeAgo(row.lastModified / 1000, prefix = if (row.gitBranch != null) " • " else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
                row.appStatus?.let { statusLabel(it) }?.let {
                    Text(" • $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
        }
        if (row.openElsewhere != null) {
            HoverTooltip(stringRes(Res.string.claude_code_open_elsewhere_warning)) {
                Box(Modifier.padding(horizontal = 8.dp).size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(MaterialSymbols.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.grayText) }
            RoundedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                RoundedMenuSection {
                    MenuRow(stringRes(Res.string.rename), enabled = row.sessionId != null) {
                        menu = false
                        onRename()
                    }
                    MenuRow(stringRes(Res.string.claude_code_delete)) {
                        menu = false
                        onDelete()
                    }
                }
            }
        }
    }
}

/** A round mark showing what the session is doing: working, waiting on the user, or resting. */
@Composable
private fun StatusAvatar(row: ClaudeSessionRow) {
    val status = row.appStatus
    val active = status == ChatStatus.WORKING || status == ChatStatus.STARTING
    val waiting = status == ChatStatus.NEEDS_APPROVAL
    val background =
        when {
            waiting -> MaterialTheme.colorScheme.errorContainer
            active || status == ChatStatus.READY -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    Box(Modifier.size(36.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        when {
            active -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            waiting -> Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            else ->
                Icon(
                    MaterialSymbols.Assistant,
                    contentDescription = null,
                    tint = if (status == ChatStatus.READY) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.size(18.dp),
                )
        }
    }
}

@Composable
private fun statusLabel(status: ChatStatus): String? =
    when (status) {
        ChatStatus.STARTING -> stringRes(Res.string.claude_code_status_starting)
        ChatStatus.WORKING -> stringRes(Res.string.claude_code_status_working)
        ChatStatus.NEEDS_APPROVAL -> stringRes(Res.string.claude_code_status_needs_approval)
        ChatStatus.READY -> stringRes(Res.string.claude_code_status_ready)
        ChatStatus.NOT_STARTED, ChatStatus.ENDED -> null
    }

@Composable
private fun InstallState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.Assistant, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(28.dp))
        }
        Text(stringRes(Res.string.claude_code_install_title), style = MaterialTheme.typography.titleMedium)
        Text(stringRes(Res.string.claude_code_install_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NewSessionDialog(
    viewModel: ClaudeCodeSessionsViewModel,
    suggestedFolders: List<String>,
    onDismiss: () -> Unit,
    onStarted: (String) -> Unit,
) {
    val error by viewModel.newChatError.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    var folder by remember { mutableStateOf(suggestedFolders.firstOrNull() ?: System.getProperty("user.home")) }
    var worktree by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(PermissionMode.DEFAULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.claude_code_new_session)) },
        text = {
            Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = folder,
                    onValueChange = {
                        folder = it
                        viewModel.clearNewChatError()
                    },
                    label = { Text(stringRes(Res.string.claude_code_folder)) },
                    leadingIcon = { Icon(MaterialSymbols.Folder, contentDescription = null) },
                    singleLine = true,
                    isError = error != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestedFolders.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestedFolders.take(6).forEach { cwd ->
                            FilterChip(selected = folder == cwd, onClick = { folder = cwd }, label = { Text(folderName(cwd)) })
                        }
                    }
                }
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { worktree = !worktree }, verticalAlignment = Alignment.Top) {
                    Checkbox(checked = worktree, onCheckedChange = { worktree = it })
                    Column(Modifier.padding(top = 12.dp, end = 8.dp)) {
                        Text(stringRes(Res.string.claude_code_worktree), style = MaterialTheme.typography.bodyMedium)
                        Text(stringRes(Res.string.claude_code_worktree_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringRes(Res.string.claude_code_permissions), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PICKABLE_MODES.forEach { option ->
                            FilterChip(selected = option == mode, onClick = { mode = option }, label = { Text(modeLabel(option)) })
                        }
                    }
                    Text(modeDescription(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }
                error?.let {
                    Text(
                        when (it) {
                            NewChatError.NotAFolder -> stringRes(Res.string.claude_code_error_not_a_folder)
                            NewChatError.NotARepository -> stringRes(Res.string.claude_code_error_not_a_repository)
                            is NewChatError.WorktreeFailed -> stringRes(Res.string.claude_code_error_worktree, it.message ?: "")
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !creating && folder.isNotBlank(), onClick = { viewModel.newChat(folder, worktree, mode, onStarted) }) {
                Text(stringRes(Res.string.claude_code_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.claude_code_rename_title)) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title) }) { Text(stringRes(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
internal fun Banner(
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.let {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

internal fun folderName(path: String): String = File(path).name.ifEmpty { path }
