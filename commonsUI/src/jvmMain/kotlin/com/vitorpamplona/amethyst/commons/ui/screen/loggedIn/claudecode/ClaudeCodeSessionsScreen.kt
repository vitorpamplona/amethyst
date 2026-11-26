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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.agents.AgentBackend
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.amethyst.commons.androiddevices.AndroidDevices
import com.vitorpamplona.amethyst.commons.androiddevices.ui.DeviceChatFocus
import com.vitorpamplona.amethyst.commons.claudecode.AgentWorkspace
import com.vitorpamplona.amethyst.commons.claudecode.AgentWorkspaces
import com.vitorpamplona.amethyst.commons.claudecode.ChatStatus
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeSessionsViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeSessionRow
import com.vitorpamplona.amethyst.commons.claudecode.GitWorktrees
import com.vitorpamplona.amethyst.commons.claudecode.NewChatError
import com.vitorpamplona.amethyst.commons.claudecode.NostrWorkspaces
import com.vitorpamplona.amethyst.commons.claudecode.RepoCandidate
import com.vitorpamplona.amethyst.commons.claudecode.RepoGroup
import com.vitorpamplona.amethyst.commons.claudecode.expandHome
import com.vitorpamplona.amethyst.commons.claudecode.workstreamModeOf
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ai_code_title
import com.vitorpamplona.amethyst.commons.resources.android_devices_title
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.claude_code_advanced
import com.vitorpamplona.amethyst.commons.resources.claude_code_agent
import com.vitorpamplona.amethyst.commons.resources.claude_code_archive
import com.vitorpamplona.amethyst.commons.resources.claude_code_browse
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_new_workstream
import com.vitorpamplona.amethyst.commons.resources.claude_code_no_matches
import com.vitorpamplona.amethyst.commons.resources.claude_code_no_sessions
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_elsewhere_warning
import com.vitorpamplona.amethyst.commons.resources.claude_code_permissions
import com.vitorpamplona.amethyst.commons.resources.claude_code_pick_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_badge_open
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_badge_requested
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_closed
import com.vitorpamplona.amethyst.commons.resources.claude_code_pr_state_merged
import com.vitorpamplona.amethyst.commons.resources.claude_code_rename_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_repo_add
import com.vitorpamplona.amethyst.commons.resources.claude_code_repo_source_github
import com.vitorpamplona.amethyst.commons.resources.claude_code_repo_source_local
import com.vitorpamplona.amethyst.commons.resources.claude_code_repo_source_nostr
import com.vitorpamplona.amethyst.commons.resources.claude_code_repo_uses
import com.vitorpamplona.amethyst.commons.resources.claude_code_search_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_section_archived
import com.vitorpamplona.amethyst.commons.resources.claude_code_section_workspaces
import com.vitorpamplona.amethyst.commons.resources.claude_code_start
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_asks
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_finished
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_in_terminal
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_needs_approval
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_starting
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_terminal_busy
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_terminal_idle
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_terminal_waiting
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_working
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_your_turn
import com.vitorpamplona.amethyst.commons.resources.claude_code_storage
import com.vitorpamplona.amethyst.commons.resources.claude_code_switch_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_unarchive
import com.vitorpamplona.amethyst.commons.resources.claude_code_where
import com.vitorpamplona.amethyst.commons.resources.claude_code_where_clones
import com.vitorpamplona.amethyst.commons.resources.claude_code_where_folder
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_add
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_add_repo
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_clone_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_delete
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_delete_body
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_delete_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_forget_group
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_freed
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_group_name
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_last_used
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_remove_repo
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_repos
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_save_group
import com.vitorpamplona.amethyst.commons.resources.claude_code_workstream_name
import com.vitorpamplona.amethyst.commons.resources.claude_code_workstream_name_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_workstream_name_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_worktree
import com.vitorpamplona.amethyst.commons.resources.claude_code_worktree_desc
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.resources.rename
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.components.FormFieldShape
import com.vitorpamplona.amethyst.commons.ui.components.FormTextField
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.LabeledField
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.formFieldColors
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverLazyListState
import com.vitorpamplona.amethyst.commons.ui.layouts.HorizontalTwoPane
import com.vitorpamplona.amethyst.commons.ui.layouts.WidthClass
import com.vitorpamplona.amethyst.commons.ui.layouts.widthClassOf
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.DetailPaneNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.TwoPaneNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberDetailPaneSelection
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.showModalDialog
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.CollapseOpenChatToFullScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.PaneScopedChat
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.listDetailSplit
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.claudecode.ClaudeBackend
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/**
 * Every Claude Code session on this computer, grouped by the repository it worked on (worktrees
 * included), newest first, with the chats this app runs marked by what they are doing.
 */
@Composable
fun ClaudeCodeSessionsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Above the size switch, so the open chat outlives a collapse to one pane (handed over below).
    val selection = rememberDetailPaneSelection()
    // Opened from the list's toolbar, or from the empty pane beside it.
    val showNew = remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthClass = widthClassOf(maxWidth)
        if (widthClass == WidthClass.Compact) {
            CollapseOpenChatToFullScreen(selection, nav)
            SessionsPane(nav, accountViewModel, selectedKey = null, inPane = false, showNew = showNew)
        } else {
            // The sessions beside the open chat, as Messages lays out its chats: a click on a
            // session opens it on the right; Changes, Propose and the rest still take the screen.
            val scope = rememberCoroutineScope()
            val twoPaneNav = remember(nav, selection) { TwoPaneNav(nav, scope, { it is Route.ClaudeCodeChat }, selection) }
            val detailNav = remember(twoPaneNav) { DetailPaneNav(twoPaneNav) }
            val split = listDetailSplit(widthClass, maxWidth.value)
            val open = selection.value as? Route.ClaudeCodeChat
            HorizontalTwoPane(
                first = { SessionsPane(twoPaneNav, accountViewModel, selectedKey = open?.key, inPane = true, showNew = showNew) },
                second = {
                    if (open != null) {
                        PaneScopedChat(open) { ClaudeCodeChatScreen(open.key, accountViewModel, detailNav) }
                    } else {
                        NoChatOpen(onNew = { showNew.value = true })
                    }
                },
                splitFraction = split.fraction,
                modifier = Modifier.fillMaxSize(),
                onDividerDrag = split.onDrag,
            )
        }
    }
}

/** The pane beside the list with no chat open: a way to start one, and to jump to one. */
@Composable
private fun NoChatOpen(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(MaterialSymbols.Assistant, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(PartGap))
        Text(stringRes(Res.string.claude_code_pick_session), color = MaterialTheme.colorScheme.grayText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(GroupGap))
        FilledTonalButton(onClick = onNew) {
            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(InlineGap))
            Text(stringRes(Res.string.claude_code_new_workstream))
        }
    }
}

/** The sessions list with its top bar; [selectedKey] is the chat open beside it, if any. */
@Composable
private fun SessionsPane(
    nav: INav,
    accountViewModel: AccountViewModel,
    selectedKey: String?,
    inPane: Boolean,
    showNew: MutableState<Boolean>,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodeSessions") { ClaudeCodeSessionsViewModel(ClaudeCodeService.shared) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val executable by viewModel.executable.collectAsStateWithLifecycle()

    // Polls while the list is on screen; a chat opened on top stops it.
    LaunchedEffect(viewModel) { viewModel.poll() }

    var searching by remember { mutableStateOf(false) }
    var showNew by showNew
    var renaming by remember { mutableStateOf<ClaudeSessionRow?>(null) }
    var deleting by remember { mutableStateOf<ClaudeSessionRow?>(null) }
    var deletingWorkspace by remember { mutableStateOf<ClaudeSessionRow?>(null) }
    var showStorage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ChatTopBar(stringRes(Res.string.ai_code_title), null, nav) {
                HoverTooltip(stringRes(Res.string.claude_code_search_hint)) {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) viewModel.search("")
                    }) { Icon(MaterialSymbols.Search, contentDescription = stringRes(Res.string.claude_code_search_hint)) }
                }
                // The emulators, phones and simulators the chats test on, where an Android SDK or Xcode is installed.
                if (remember { AndroidDevices.supported }) {
                    HoverTooltip(stringRes(Res.string.android_devices_title)) {
                        IconButton(onClick = { nav.nav(Route.AndroidDevices()) }) { Icon(MaterialSymbols.PhoneAndroid, contentDescription = stringRes(Res.string.android_devices_title)) }
                    }
                }
                HoverTooltip(stringRes(Res.string.claude_code_storage)) {
                    IconButton(onClick = { showStorage = true }) { Icon(MaterialSymbols.Storage, contentDescription = stringRes(Res.string.claude_code_storage)) }
                }
                HoverTooltip(stringRes(Res.string.refresh)) {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.refresh)) }
                }
                HoverTooltip(stringRes(Res.string.claude_code_new_workstream)) {
                    IconButton(onClick = { showNew = true }) { Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.claude_code_new_workstream)) }
                }
            }
        },
        // Alone on a narrow window it is a top-level screen like the others: the app's bottom bar
        // is the way to the rest of the app (it has no back arrow when opened from the drawer).
        bottomBar = {
            if (!inPane) {
                AppBottomBar(Route.ClaudeCode, nav, accountViewModel) { route ->
                    if (route != Route.ClaudeCode) nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Alone it keeps a readable column; beside a chat it fills its pane.
            Column(Modifier.then(if (inPane) Modifier else Modifier.widthIn(max = ChatColumnMaxWidth)).fillMaxSize()) {
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
                    else -> SessionList(rows, nav, selectedKey, onRename = { renaming = it }, onDelete = { deleting = it }, onDeleteWorkspace = { deletingWorkspace = it }, onArchive = viewModel::archive)
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
                    // The dock's device panel stops following the chat that is gone.
                    DeviceChatFocus.forget(row.key)
                    deleting = null
                }) { Text(stringRes(Res.string.claude_code_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringRes(Res.string.cancel)) } },
        )
    }

    if (showStorage) StorageDialog(onDismiss = { showStorage = false })

    deletingWorkspace?.let { row ->
        DeleteWorkspaceDialog(
            onDismiss = { deletingWorkspace = null },
            onConfirm = {
                viewModel.deleteWorkspace(row)
                deletingWorkspace = null
            },
        )
    }
}

/** Asks before a workspace's clones go: commits never pushed go with them. */
@Composable
internal fun DeleteWorkspaceDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.claude_code_workspace_delete_title)) },
        text = { Text(stringRes(Res.string.claude_code_workspace_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringRes(Res.string.claude_code_workspace_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
private fun SessionList(
    rows: List<ClaudeSessionRow>,
    nav: INav,
    selectedKey: String?,
    onRename: (ClaudeSessionRow) -> Unit,
    onDelete: (ClaudeSessionRow) -> Unit,
    onDeleteWorkspace: (ClaudeSessionRow) -> Unit,
    onArchive: (ClaudeSessionRow, Boolean) -> Unit,
) {
    // The sessions in workspaces of their own, newest first; then a section per project, in the
    // order of its newest session; sessions whose folder is gone last, folded. One waiting on the
    // user stays in its section: its row's mark and status say so.
    val archived = remember(rows) { rows.filter { it.archived } }
    val active = remember(rows) { rows.filter { !it.archived } }
    val inWorkspaces = remember(active) { active.filter { it.workspace != null } }
    val sections =
        remember(active) {
            active
                .filter { it.workspace == null }
                .groupBy { it.project }
                .toList()
        }
    var showArchived by remember { mutableStateOf(false) }
    // Kept while the app runs: opening a chat and coming back finds the list where it was.
    val listState = rememberForeverLazyListState("ClaudeCodeSessions")
    // Where the open chat's row sits in the list, headers counted, as laid out below.
    val selectedIndex =
        remember(inWorkspaces, sections, archived, showArchived, selectedKey) {
            selectedKey ?: return@remember -1
            val groups =
                buildList {
                    if (inWorkspaces.isNotEmpty()) add(inWorkspaces)
                    sections.forEach { add(it.second) }
                    if (archived.isNotEmpty()) add(if (showArchived) archived else emptyList())
                }
            var index = 0
            for (group in groups) {
                index++ // its header
                val at = group.indexOfFirst { it.key == selectedKey }
                if (at >= 0) return@remember index + at
                index += group.size
            }
            -1
        }
    // A list kept its place when rows arrive above it: a workstream just started would sit out of
    // sight. When the open chat changes, or its row first shows up, it scrolls into view.
    LaunchedEffect(selectedKey, selectedIndex >= 0) {
        if (selectedIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val shown = visible.any { it.index == selectedIndex && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
        if (!shown) listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }

    @Composable
    fun Item(row: ClaudeSessionRow) =
        SessionRow(
            row = row,
            selected = row.key == selectedKey,
            onOpen = { nav.nav(Route.ClaudeCodeChat(row.key)) },
            onRename = { onRename(row) },
            onDelete = { onDelete(row) },
            onDeleteWorkspace = { onDeleteWorkspace(row) },
            onArchive = { onArchive(row, !row.userArchived) },
        )

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = Gutter)) {
        // The first header sits under the top bar; every later one starts a section, a clear break.
        var first = true

        fun headerTop(): Dp = (if (first) 8.dp else SectionHeaderTop).also { first = false }
        if (inWorkspaces.isNotEmpty()) {
            val top = headerTop()
            item(key = "section-workspaces") { SectionHeader(stringRes(Res.string.claude_code_section_workspaces), top = top) }
            items(inWorkspaces, key = { "ws-" + it.key }) { Item(it) }
        }
        sections.forEach { (project, sessions) ->
            val top = headerTop()
            item(key = "section-$project") { SectionHeader(project, top = top) }
            items(sessions, key = { it.key }) { Item(it) }
        }
        if (archived.isNotEmpty()) {
            val top = headerTop()
            item(key = "section-archived") {
                SectionHeader(
                    pluralStringRes(Res.plurals.claude_code_section_archived, archived.size, archived.size),
                    top = top,
                    expanded = showArchived,
                    onClick = { showArchived = !showArchived },
                )
            }
            if (showArchived) items(archived, key = { "archived-" + it.key }) { Item(it) }
        }
    }
}

/** The break above a section of the list: wider than the gap between its rows. */
private val SectionHeaderTop = 28.dp

/** A quiet label over a group of sessions; a foldable one when it has [onClick]. */
@Composable
private fun SectionHeader(
    text: String,
    color: Color = MaterialTheme.colorScheme.grayText,
    top: Dp = SectionHeaderTop,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    // Nothing below: the first row's own padding is the gap, tighter than the break above.
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = Gutter, end = Gutter, top = top),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f, fill = false))
        if (expanded != null) {
            Icon(if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SessionRow(
    row: ClaudeSessionRow,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteWorkspace: () -> Unit,
    onArchive: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .hoverable(hover)
            .clickable(onClick = onOpen)
            .padding(start = Gutter, top = RowPaddingV, bottom = RowPaddingV, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMark(row)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title.ifBlank { stringRes(Res.string.claude_code_new_session) },
                style = MaterialTheme.typography.bodyLarge,
                // Like an unread message: the agent finished and the user hasn't looked yet.
                fontWeight = if (row.needsYou) FontWeight.SemiBold else null,
                color = if (row.archived) MaterialTheme.colorScheme.grayText else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The PR pills stand taller than the line around them: a gap keeps their border off the title.
            Spacer(Modifier.height(3.dp))
            // What matters first: what the session is doing, then when, then where. The branch is
            // the one that gives way when the line runs out, not the status.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val status = statusLabel(row)
                status?.let { (label, color) ->
                    Text("$label • ", style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
                }
                // Sessions with an agent other than the default say which.
                if (row.agentId.isNotEmpty() && row.agentId != ClaudeBackend.ID) {
                    Text(row.agentName + " • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                Text(
                    timeAgo(row.lastModified / 1000, prefix = ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
                // What the workspace's clones take on disk, once measured.
                row.workspaceSize?.let { size ->
                    Text(" • " + formatFileSize(size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1)
                }
                // Its clones were freed: the next message (or Unarchive) makes them again.
                if (row.workspace?.isFreed == true) {
                    Text(" • " + stringRes(Res.string.claude_code_workspace_freed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1)
                }
                row.workspace?.let {
                    PublishRequestBadge(it)
                    PullRequestBadge(it)
                }
                // A workspace's branch is the app's own name for it: its repositories say more.
                val where = row.workspace?.repoLabels?.joinToString(", ") ?: row.gitBranch
                where?.let {
                    Text(
                        " • $it",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (row.workspace == null) FontFamily.Monospace else null,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        // Shown on hover (or while open); still there to click or tab to without a mouse.
        Box(Modifier.alpha(if (hovered || menu) 1f else 0f)) {
            // 40 dp, not the default 48: the row's height is its text's, not the hidden button's.
            Dense { IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(MaterialSymbols.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.grayText) } }
            RoundedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                RoundedMenuSection {
                    // A workspace no session works in yet has only itself to remove.
                    if (!row.key.startsWith(ClaudeCodeService.WORKSPACE_KEY)) {
                        MenuRow(stringRes(Res.string.rename)) {
                            menu = false
                            onRename()
                        }
                        // Archived sessions stay, under Archived; the agent keeps them too.
                        if (row.sessionId != null) {
                            MenuRow(stringRes(if (row.userArchived) Res.string.claude_code_unarchive else Res.string.claude_code_archive)) {
                                menu = false
                                onArchive()
                            }
                        }
                        MenuRow(stringRes(Res.string.claude_code_delete)) {
                            menu = false
                            onDelete()
                        }
                    }
                    if (row.workspace != null) {
                        MenuRow(stringRes(Res.string.claude_code_workspace_delete)) {
                            menu = false
                            onDeleteWorkspace()
                        }
                    }
                }
            }
        }
    }
}

/** The agent asked the user to open a pull request, and it waits on the Pull requests screen. */
@Composable
private fun PublishRequestBadge(workspace: AgentWorkspace) {
    if (workspace.publishRequests.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    Text(
        stringRes(Res.string.claude_code_pr_badge_requested),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Where the workspace's pull requests stand, as last seen: how many are open, else merged or
 * closed. Nothing before the first one is opened.
 */
@Composable
private fun PullRequestBadge(workspace: AgentWorkspace) {
    val prs = workspace.pullRequests.values
    if (prs.isEmpty()) return
    val open = prs.count { it.isOpen }
    val (label, color) =
        when {
            open > 0 -> pluralStringRes(Res.plurals.claude_code_pr_badge_open, open, open) to AddedColor
            prs.any { it.isMerged } -> stringRes(Res.string.claude_code_pr_state_merged) to MaterialTheme.colorScheme.primary
            else -> stringRes(Res.string.claude_code_pr_state_closed) to MaterialTheme.colorScheme.error
        }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * What the session is doing, at a glance: a spinner while it works, a filled mark when it waits
 * on the user (an hourglass for an approval, a check for a turn they haven't seen end), a dot
 * when it is open here or in a terminal and it's the user's turn, a ring when it rests. A
 * terminal's session says the same in the terminal's color.
 */
@Composable
private fun StatusMark(row: ClaudeSessionRow) {
    val status = row.appStatus
    val terminal = row.openElsewhere
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            status == ChatStatus.WORKING || status == ChatStatus.STARTING ->
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            status == ChatStatus.NEEDS_APPROVAL -> FilledMark(MaterialSymbols.HourglassEmpty, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            row.lastTurnFailed -> FilledMark(MaterialSymbols.Warning, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            row.needsYou -> FilledMark(MaterialSymbols.Check, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            status == ChatStatus.READY -> Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            terminal != null ->
                HoverTooltip(stringRes(Res.string.claude_code_open_elsewhere_warning)) {
                    when (terminal.status) {
                        TERMINAL_BUSY -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                        TERMINAL_WAITING -> FilledMark(MaterialSymbols.HourglassEmpty, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                        else -> Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                    }
                }
            else -> Box(Modifier.size(10.dp).border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

@Composable
private fun FilledMark(
    symbol: MaterialSymbol,
    background: Color,
    tint: Color,
) {
    Box(Modifier.size(18.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
    }
}

/** What Claude Code writes as a running session's `status` in its registry (`~/.claude/sessions`). */
internal const val TERMINAL_BUSY = "busy"
internal const val TERMINAL_IDLE = "idle"

/** Stopped on a prompt in the terminal: a permission, a question. */
internal const val TERMINAL_WAITING = "waiting"

@Composable
private fun statusLabel(row: ClaudeSessionRow): Pair<String, Color>? =
    when (row.appStatus) {
        ChatStatus.STARTING -> stringRes(Res.string.claude_code_status_starting) to MaterialTheme.colorScheme.primary
        ChatStatus.WORKING -> stringRes(Res.string.claude_code_status_working) to MaterialTheme.colorScheme.primary
        ChatStatus.NEEDS_APPROVAL -> stringRes(if (row.asking) Res.string.claude_code_status_asks else Res.string.claude_code_status_needs_approval) to MaterialTheme.colorScheme.error
        ChatStatus.READY, ChatStatus.ENDED, ChatStatus.NOT_STARTED, null ->
            when {
                row.lastTurnFailed -> stringRes(Res.string.claude_code_status_failed) to MaterialTheme.colorScheme.error
                row.needsYou -> stringRes(Res.string.claude_code_status_finished) to MaterialTheme.colorScheme.primary
                row.appStatus == ChatStatus.READY -> stringRes(Res.string.claude_code_status_your_turn) to MaterialTheme.colorScheme.primary
                row.openElsewhere != null ->
                    when (row.openElsewhere?.status) {
                        TERMINAL_BUSY -> stringRes(Res.string.claude_code_status_terminal_busy)
                        TERMINAL_IDLE -> stringRes(Res.string.claude_code_status_terminal_idle)
                        TERMINAL_WAITING -> stringRes(Res.string.claude_code_status_terminal_waiting)
                        else -> stringRes(Res.string.claude_code_status_in_terminal)
                    } to MaterialTheme.colorScheme.tertiary
                else -> null
            }
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

/**
 * A new workstream: pick the repositories, name it, go. Each workstream gets fresh clones of its own, so the
 * agent runs hands-off ([workstreamModeOf]). A faint button in the corner opens the rest: a folder
 * already on this computer instead of clones, the agent, and the permission mode.
 */
@Composable
private fun NewSessionDialog(
    viewModel: ClaudeCodeSessionsViewModel,
    suggestedFolders: List<String>,
    onDismiss: () -> Unit,
    onStarted: (String) -> Unit,
) {
    val error by viewModel.newChatError.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    // The agent the last session started with, until the user picks another (in Advanced).
    var agentId by remember { mutableStateOf(viewModel.preferredAgentId()) }
    val agent = installed.firstOrNull { it.id == agentId } ?: installed.firstOrNull()
    val modes = agent?.capabilities?.modes?.filter { it in PICKABLE_MODES } ?: PICKABLE_MODES
    // Fresh clones, unless the user picks a folder of their own.
    var inFolder by remember { mutableStateOf(false) }
    // Null until the user picks one: then the way a workstream or a folder starts on this agent.
    var pickedMode by remember { mutableStateOf<PermissionMode?>(null) }
    val mode = pickedMode?.takeIf { it in modes } ?: if (inFolder) defaultModeOf(modes) else workstreamModeOf(modes)
    val groups by viewModel.repoGroups.collectAsStateWithLifecycle()
    // The repositories of the last workstream to start with: most workstreams continue where the last left off.
    val repos = remember { mutableStateListOf<String>().apply { addAll((groups.firstOrNull { it.name == ClaudeCodeService.LAST_GROUP } ?: groups.firstOrNull())?.urls.orEmpty()) } }
    var adding by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    var folder by remember { mutableStateOf(suggestedFolders.firstOrNull() ?: System.getProperty("user.home")) }
    // A folder in a repository starts on its own branch in a worktree, keeping the user's checkout
    // as it is; the switch is there to work in the folder itself.
    var worktree by remember { mutableStateOf(true) }
    // Null while git looks. A folder outside any repository has no worktree to offer.
    val inRepository by produceState<Boolean?>(null, folder) {
        value = null
        value = GitWorktrees.repositoryRoot(File(expandHome(folder))) != null
    }

    val candidates by viewModel.repoCandidates.collectAsStateWithLifecycle()
    val searchingGitHub by viewModel.searchingGitHub.collectAsStateWithLifecycle()
    // Lists the mirrors and runs `gh`: once, when the dialog opens.
    LaunchedEffect(Unit) { viewModel.loadRepoCatalog() }
    // With the last workstream's repositories already in, the name is all that is left to type.
    LaunchedEffect(Unit) { if (repos.isNotEmpty()) nameFocus.requestFocus() }

    // What Go clones: the chosen ones, and a URL still in the field (not a search).
    fun pendingRepos(): List<String> = (repos + if (looksLikeUrl(adding)) splitUrls(adding) else emptyList()).distinct()

    fun addTyped() {
        if (looksLikeUrl(adding)) splitUrls(adding).forEach { if (it !in repos) repos.add(it) }
        adding = ""
        viewModel.searchRepos("")
        viewModel.clearNewChatError()
    }

    // Where is always a choice in the open: working in one's own folder is common, not advanced.
    val folderMode = inFolder
    val canGo = !creating && if (folderMode) folder.isNotBlank() else pendingRepos().isNotEmpty() && name.isNotBlank()

    fun go() {
        if (!canGo) return
        if (folderMode) {
            viewModel.newChat(folder, worktree && inRepository != false, mode, agent?.id, onStarted)
        } else {
            addTyped()
            // Simple: the workstream's own hands-off mode; Advanced: the one the user chose.
            viewModel.newWorkstream(repos.toList(), name, agent?.id, mode.takeIf { advanced }, onStarted)
        }
    }

    val errorText = error?.let { newChatErrorText(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = formDialogColor(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(Res.string.claude_code_new_workstream), modifier = Modifier.weight(1f))
                AdvancedToggle(advanced) {
                    advanced = !advanced
                    viewModel.clearNewChatError()
                }
            }
        },
        text = {
            // Scrolls on a short window: the Start button must never be pushed off screen.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FormGap)) {
                LabeledField(Res.string.claude_code_where) {
                    SegmentedChoice(
                        options = listOf(false, true),
                        selected = inFolder,
                        label = { stringRes(if (it) Res.string.claude_code_where_folder else Res.string.claude_code_where_clones) },
                        onSelect = {
                            inFolder = it
                            viewModel.clearNewChatError()
                        },
                    )
                }
                if (folderMode) {
                    FolderField(viewModel, folder, { folder = it }, suggestedFolders, loaded, errorText)
                    if (inRepository != false) {
                        ToggleRow(
                            title = stringRes(Res.string.claude_code_worktree),
                            description = stringRes(Res.string.claude_code_worktree_desc),
                            checked = worktree,
                            onChange = { worktree = it },
                        )
                    }
                } else {
                    LabeledField(Res.string.claude_code_workspace_repos, helper = errorText, isError = errorText != null) {
                        RepoPicker(
                            repos = repos,
                            candidates = candidates,
                            groups = groups,
                            searchingGitHub = searchingGitHub,
                            adding = adding,
                            onAddingChange = {
                                adding = it
                                viewModel.searchRepos(it)
                                viewModel.clearNewChatError()
                            },
                            onAdd = ::addTyped,
                            onPick = { url ->
                                if (url !in repos) repos.add(url)
                                viewModel.clearNewChatError()
                            },
                            onRemove = { repos.remove(it) },
                            onUseGroup = { group ->
                                repos.clear()
                                repos.addAll(group.urls)
                                viewModel.clearNewChatError()
                            },
                            onForgetGroup = { viewModel.deleteGroup(it.name) },
                            onSaveGroup = { groupName ->
                                addTyped()
                                viewModel.saveGroup(groupName, repos.toList())
                            },
                        )
                    }
                    LabeledField(Res.string.claude_code_workstream_name, helper = stringRes(Res.string.claude_code_workstream_name_desc)) {
                        FormTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = stringRes(Res.string.claude_code_workstream_name_hint),
                            modifier = Modifier.focusRequester(nameFocus),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { go() }),
                        )
                    }
                }
                if (advanced) {
                    // Which agent works on it, when more than one is installed; remembered for the next workstream.
                    AgentPicker(installed, agent?.id) { agentId = it }
                    PermissionField(modes, mode) { pickedMode = it }
                }
            }
        },
        confirmButton = {
            // One verb for both: the same thing happens, a chat starts.
            TextButton(enabled = canGo, onClick = ::go) { Text(stringRes(Res.string.claude_code_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

/** The way to the rest of the options: faint until hovered, lit while they show. */
@Composable
private fun AdvancedToggle(
    on: Boolean,
    onClick: () -> Unit,
) {
    val label = stringRes(Res.string.claude_code_advanced)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    HoverTooltip(label) {
        Dense {
            IconButton(onClick = onClick, interactionSource = hover, modifier = Modifier.size(32.dp)) {
                Icon(
                    MaterialSymbols.Tune,
                    contentDescription = label,
                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText.copy(alpha = if (hovered) 1f else 0.45f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A folder already on this computer: typed, browsed to, or one sessions ran in before. */
@Composable
private fun FolderField(
    viewModel: ClaudeCodeSessionsViewModel,
    folder: String,
    onFolder: (String) -> Unit,
    suggestedFolders: List<String>,
    loaded: Boolean,
    error: String?,
) {
    val scope = rememberCoroutineScope()
    val browseTitle = stringRes(Res.string.claude_code_browse)
    LabeledField(Res.string.claude_code_folder, helper = error, isError = error != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(
                value = folder,
                onValueChange = {
                    onFolder(it)
                    viewModel.clearNewChatError()
                },
                isError = error != null,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = {
                    HoverTooltip(browseTitle) {
                        IconButton(onClick = {
                            scope.launch {
                                pickFolder(browseTitle, folder)?.let {
                                    onFolder(it)
                                    viewModel.clearNewChatError()
                                }
                            }
                        }) { Icon(MaterialSymbols.Folder, contentDescription = browseTitle) }
                    }
                },
            )
            // The folders sessions ran in, one row that scrolls sideways. Its height is kept
            // while the list loads, so the dialog doesn't jump under the pointer.
            if (!loaded) {
                Spacer(Modifier.height(32.dp))
            } else if (suggestedFolders.size > 1) {
                Dense {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InlineGap)) {
                        suggestedFolders.take(12).forEach { cwd ->
                            HoverTooltip(cwd) {
                                FilterChip(selected = folder == cwd, onClick = { onFolder(cwd) }, label = { Text(folderName(cwd), maxLines = 1) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Why a new session didn't start, in the user's words. */
@Composable
private fun newChatErrorText(error: NewChatError): String =
    when (error) {
        NewChatError.NotAFolder -> stringRes(Res.string.claude_code_error_not_a_folder)
        NewChatError.NotARepository -> stringRes(Res.string.claude_code_error_not_a_repository)
        is NewChatError.WorktreeFailed -> stringRes(Res.string.claude_code_error_worktree, error.message ?: "")
        is NewChatError.CloneFailed -> stringRes(Res.string.claude_code_workspace_clone_failed, error.message ?: "")
    }

/** The permission mode, as segments, with what the chosen one does under them. */
@Composable
internal fun PermissionField(
    modes: List<PermissionMode>,
    mode: PermissionMode,
    onMode: (PermissionMode) -> Unit,
) {
    // The segment says the mode in a word; the line under it, what that means.
    LabeledField(Res.string.claude_code_permissions, helper = modeDescription(mode)) {
        SegmentedChoice(options = modes, selected = mode, label = { modeShortLabel(it) }, onSelect = onMode)
    }
}

/** URLs typed or pasted into the field: one or many, split on spaces, commas and new lines. */
private fun splitUrls(text: String): List<String> = text.split(Regex("[\\s,]+")).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The repositories to clone: the chosen ones as chips (a tap removes one), the saved groups and
 * "Save as group" as actions, a field to search or paste a URL, and what fits it as rows to add.
 * With nothing typed, the rows are the repositories cloned here before, most used first.
 */
@Composable
private fun RepoPicker(
    repos: List<String>,
    candidates: List<RepoCandidate>,
    groups: List<RepoGroup>,
    searchingGitHub: Boolean,
    adding: String,
    onAddingChange: (String) -> Unit,
    onAdd: () -> Unit,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUseGroup: (RepoGroup) -> Unit,
    onForgetGroup: (RepoGroup) -> Unit,
    onSaveGroup: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    val chosen = remember(repos) { repos.map { NostrWorkspaces.normalize(it) }.toSet() }
    // Names for the chips: the catalog knows them even when the search no longer shows them.
    val names = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(candidates) { candidates.forEach { names[NostrWorkspaces.normalize(it.url)] = it.label } }
    val offered = remember(candidates, chosen) { candidates.filter { NostrWorkspaces.normalize(it.url) !in chosen }.take(OFFERED_REPOS) }

    fun saveGroup() {
        if (groupName.isBlank()) return
        onSaveGroup(groupName)
        naming = false
        groupName = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (repos.isNotEmpty()) {
            Dense {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(InlineGap), verticalArrangement = Arrangement.spacedBy(InlineGap)) {
                    repos.forEach { url ->
                        val label = names[NostrWorkspaces.normalize(url)] ?: AgentWorkspaces.ownerAndName(url)
                        val remove = stringRes(Res.string.claude_code_workspace_remove_repo, label)
                        HoverTooltip(url) {
                            InputChip(
                                selected = true,
                                onClick = { onRemove(url) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = { Icon(MaterialSymbols.Close, contentDescription = remove, modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(50),
                            )
                        }
                    }
                }
            }
        }
        RepoGroupActions(
            groups = groups,
            canSave = repos.isNotEmpty() && !naming,
            onUse = onUseGroup,
            onForget = onForgetGroup,
            onSave = { naming = true },
        )
        if (naming) {
            // Typing goes to the name as soon as the field appears; Enter saves.
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            FormTextField(
                value = groupName,
                onValueChange = { groupName = it },
                placeholder = stringRes(Res.string.claude_code_workspace_group_name),
                leadingIcon = MaterialSymbols.BookmarkAdd,
                trailingIcon = { TextButton(enabled = groupName.isNotBlank(), onClick = ::saveGroup) { Text(stringRes(Res.string.save)) } },
                modifier =
                    Modifier.focusRequester(focus).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && groupName.isNotBlank()) {
                            saveGroup()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        FormTextField(
            value = adding,
            onValueChange = onAddingChange,
            placeholder = stringRes(Res.string.claude_code_workspace_add_repo),
            leadingIcon = MaterialSymbols.Search,
            // Only when it has something to show: an empty slot still takes the placeholder's room.
            trailingIcon =
                when {
                    searchingGitHub -> {
                        { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }
                    }
                    looksLikeUrl(adding) -> {
                        { TextButton(onClick = onAdd) { Text(stringRes(Res.string.claude_code_workspace_add)) } }
                    }
                    else -> null
                },
            modifier =
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.Enter || adding.isBlank()) return@onPreviewKeyEvent false
                    // A URL is added as it is; a word adds its first match.
                    if (looksLikeUrl(adding)) onAdd() else offered.firstOrNull()?.let { onPick(it.url) }
                    true
                },
        )
        if (offered.isNotEmpty()) {
            Column {
                offered.forEach { candidate -> RepoSuggestionRow(candidate) { onPick(candidate.url) } }
            }
        }
    }
}

/** How many unchosen repositories the picker lists at once: past that, typing narrows it. */
private const val OFFERED_REPOS = 6

/**
 * The saved groups as actions that fill the list ("Last used" first), then "Save as group":
 * action chips with icons, not filters, since a tap replaces the list rather than marking a state.
 */
@Composable
private fun RepoGroupActions(
    groups: List<RepoGroup>,
    canSave: Boolean,
    onUse: (RepoGroup) -> Unit,
    onForget: (RepoGroup) -> Unit,
    onSave: () -> Unit,
) {
    if (groups.isEmpty() && !canSave) return
    val last = stringRes(Res.string.claude_code_workspace_last_used)
    Dense {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InlineGap), verticalAlignment = Alignment.CenterVertically) {
            groups.sortedByDescending { it.name == ClaudeCodeService.LAST_GROUP }.forEach { group ->
                val isLast = group.name == ClaudeCodeService.LAST_GROUP
                HoverTooltip(group.urls.joinToString("\n") { AgentWorkspaces.ownerAndName(it) }) {
                    AssistChip(
                        onClick = { onUse(group) },
                        label = { Text(if (isLast) last else group.name, maxLines = 1) },
                        leadingIcon = { Icon(if (isLast) MaterialSymbols.History else MaterialSymbols.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon =
                            if (isLast) {
                                null
                            } else {
                                {
                                    val forget = stringRes(Res.string.claude_code_workspace_forget_group, group.name)
                                    Icon(MaterialSymbols.Close, contentDescription = forget, modifier = Modifier.size(16.dp).clip(CircleShape).clickable { onForget(group) })
                                }
                            },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
            if (canSave) {
                AssistChip(
                    onClick = onSave,
                    label = { Text(stringRes(Res.string.claude_code_workspace_save_group), maxLines = 1) },
                    leadingIcon = { Icon(MaterialSymbols.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                )
            }
        }
    }
}

/** Whether the field holds a repository URL (or several) rather than words to search for. */
private fun looksLikeUrl(text: String): Boolean = splitUrls(text).let { parts -> parts.isNotEmpty() && parts.all { it.contains("://") || it.contains('@') || it.startsWith("/") || it.matches(Regex("^[A-Za-z]:\\\\.*")) } }

/** Where a repository is known from, and how often it was cloned here: the row's second line. */
@Composable
private fun repoDetail(candidate: RepoCandidate): String {
    val source =
        when (candidate.source) {
            RepoCandidate.Source.LOCAL ->
                if (candidate.uses > 0) {
                    pluralStringRes(Res.plurals.claude_code_repo_uses, candidate.uses, candidate.uses)
                } else {
                    stringRes(Res.string.claude_code_repo_source_local)
                }
            RepoCandidate.Source.GITHUB -> stringRes(Res.string.claude_code_repo_source_github)
            // Its tag already says Nostr: the server it clones from says more.
            RepoCandidate.Source.NOSTR -> candidate.url.substringAfter("://").substringBefore('/')
        }
    return listOfNotNull(source, candidate.description).joinToString(" · ")
}

/** A repository to add: its name, where it is known from, and a plus; the whole row adds it. */
@Composable
private fun RepoSuggestionRow(
    candidate: RepoCandidate,
    onAdd: () -> Unit,
) {
    val add = stringRes(Res.string.claude_code_repo_add, candidate.label)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = add, onClick = onAdd)
            // The plus under the search field's magnifier: both sit 14 dp in.
            .padding(start = 14.dp, end = RowPaddingH, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // A NIP-34 repository: its changes can go back as a proposal.
                if (candidate.nostr != null) {
                    Text(
                        stringRes(Res.string.claude_code_repo_source_nostr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(repoDetail(candidate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The agent to work with, as segments; nothing when only one is installed. */
@Composable
internal fun AgentPicker(
    installed: List<AgentBackend>,
    selected: String?,
    onPick: (String) -> Unit,
) {
    if (installed.size < 2) return
    LabeledField(Res.string.claude_code_agent) {
        SegmentedChoice(
            options = installed.map { it.id },
            selected = selected ?: installed.first().id,
            label = { id -> installed.firstOrNull { it.id == id }?.displayName ?: id },
            onSelect = onPick,
        )
    }
}

@Composable
internal fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    // The whole name selected, so typing replaces it.
    var title by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = formDialogColor(),
        title = { Text(stringRes(Res.string.claude_code_rename_title)) },
        text = {
            // The form look's field, kept as a plain field for the selected text it starts with.
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = FormFieldShape,
                colors = formFieldColors(),
                modifier =
                    Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                        // Enter saves.
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && title.text.isNotBlank()) {
                            onSave(title.text)
                            true
                        } else {
                            false
                        }
                    },
            )
        },
        confirmButton = { TextButton(enabled = title.text.isNotBlank(), onClick = { onSave(title.text) }) { Text(stringRes(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
internal fun Banner(
    text: String,
    action: String? = null,
    /** Its place in the column: the gutter by default, nothing inside a screen that pads already. */
    modifier: Modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
    onAction: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = CardPadding, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(InlineGap))
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.let {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

internal fun folderName(path: String): String = File(path).name.ifEmpty { path }

/** A quick jump between sessions (Ctrl/Cmd+K): type to filter, Enter opens the first. */
@Composable
internal fun SessionSwitcher(
    rows: List<ClaudeSessionRow>,
    onDismiss: () -> Unit,
    onPick: (ClaudeSessionRow) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val matches =
        remember(rows, query) {
            rows
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.project.contains(query, ignoreCase = true) || it.gitBranch?.contains(query, ignoreCase = true) == true }
                // The ones waiting on the user first.
                .sortedByDescending { it.needsYou }
                .take(12)
        }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.claude_code_switch_title)) },
        text = {
            Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringRes(Res.string.claude_code_search_hint)) },
                    leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = null) },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                matches.firstOrNull()?.let(onPick)
                                true
                            } else {
                                false
                            }
                        },
                )
                matches.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(row) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusMark(row)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.title.ifBlank { stringRes(Res.string.claude_code_new_session) }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(row.project, row.gitBranch).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

/**
 * The system's folder chooser: Finder's on macOS (an AWT dialog switched to folders), Swing's
 * elsewhere, where AWT can't pick a folder. Null when cancelled.
 */
internal suspend fun pickFolder(
    title: String,
    start: String?,
): String? =
    showModalDialog {
        val from = start?.let { File(expandHome(it)) }?.takeIf { it.isDirectory }
        if (System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply { directory = from?.absolutePath }
                dialog.isVisible = true
                dialog.file?.let { File(dialog.directory, it).absolutePath }
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        } else {
            val chooser =
                JFileChooser(from).apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
        }
    }
