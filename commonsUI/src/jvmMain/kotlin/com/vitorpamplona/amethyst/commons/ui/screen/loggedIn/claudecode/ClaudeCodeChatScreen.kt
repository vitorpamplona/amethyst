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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.claudecode.ChatError
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.ChatRow
import com.vitorpamplona.amethyst.commons.claudecode.ChatStatus
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChat
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChatState
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeChatViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.NostrRepoRef
import com.vitorpamplona.amethyst.commons.claudecode.NoticeKind
import com.vitorpamplona.amethyst.commons.claudecode.ReviewItem
import com.vitorpamplona.amethyst.commons.claudecode.ReviewLocation
import com.vitorpamplona.amethyst.commons.claudecode.ReviewPrompt
import com.vitorpamplona.amethyst.commons.claudecode.ToolKind
import com.vitorpamplona.amethyst.commons.claudecode.ToolView
import com.vitorpamplona.amethyst.commons.claudecode.ToolViews
import com.vitorpamplona.amethyst.commons.claudecode.toChatRows
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.verdictLabel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_address_review
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_dirs
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_mode
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_rule_project
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_rule_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_app_running
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_context_tooltip
import com.vitorpamplona.amethyst.commons.resources.claude_code_context_used
import com.vitorpamplona.amethyst.commons.resources.claude_code_deny
import com.vitorpamplona.amethyst.commons.resources.claude_code_end_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_exited
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_start_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_folder_gone
import com.vitorpamplona.amethyst.commons.resources.claude_code_fork
import com.vitorpamplona.amethyst.commons.resources.claude_code_fork_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_intro_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_loading_history
import com.vitorpamplona.amethyst.commons.resources.claude_code_message_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_accept_edits
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_accept_edits_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_auto
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_auto_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_default
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_default_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_plan
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_plan_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_new_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_not_installed
import com.vitorpamplona.amethyst.commons.resources.claude_code_notice_interrupted
import com.vitorpamplona.amethyst.commons.resources.claude_code_notice_process_ended
import com.vitorpamplona.amethyst.commons.resources.claude_code_notice_turn_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_elsewhere_readonly
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_create
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_edit
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_keys
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_keys_deny
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_run
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_proposal_closed
import com.vitorpamplona.amethyst.commons.resources.claude_code_proposal_merged
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose
import com.vitorpamplona.amethyst.commons.resources.claude_code_remove_anyway
import com.vitorpamplona.amethyst.commons.resources.claude_code_remove_worktree
import com.vitorpamplona.amethyst.commons.resources.claude_code_remove_worktree_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_resume_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_review_comments
import com.vitorpamplona.amethyst.commons.resources.claude_code_review_general
import com.vitorpamplona.amethyst.commons.resources.claude_code_review_inline
import com.vitorpamplona.amethyst.commons.resources.claude_code_review_inline_removed
import com.vitorpamplona.amethyst.commons.resources.claude_code_review_prompt_header
import com.vitorpamplona.amethyst.commons.resources.claude_code_review_verdict
import com.vitorpamplona.amethyst.commons.resources.claude_code_run_app
import com.vitorpamplona.amethyst.commons.resources.claude_code_run_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_session_missing
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_explain
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_review_branch
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_review_uncommitted
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_tests
import com.vitorpamplona.amethyst.commons.resources.claude_code_stop
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_agent
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_command
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_edit
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_other
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_read
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_search
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_web
import com.vitorpamplona.amethyst.commons.resources.claude_code_update_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_view_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_waiting_approval
import com.vitorpamplona.amethyst.commons.resources.claude_code_working
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.protocol.PermissionRequest
import com.vitorpamplona.claudecode.protocol.PermissionSuggestion
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** The widest a chat column gets: past this, lines are too long to read. */
internal val ChatColumnMaxWidth = 760.dp

private const val LIVE_REFRESH_MS = 5_000L

/** How many rows from the end the view may be and still follow new ones. */
private const val FOLLOW_SLACK = 3

/** One Claude Code session: its history, the live turn, and the approvals it waits on. */
@Composable
fun ClaudeCodeChatScreen(
    key: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodeChat-$key") { ClaudeCodeChatViewModel(ClaudeCodeService.shared, key) }
    val chat = viewModel.chat
    if (chat == null) {
        Scaffold(topBar = { ChatTopBar(stringRes(Res.string.claude_code_new_session), null, nav) {} }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringRes(Res.string.claude_code_session_missing), color = MaterialTheme.colorScheme.grayText)
            }
        }
        return
    }
    val state by chat.state.collectAsStateWithLifecycle()
    val runningApp by viewModel.runningApp.collectAsStateWithLifecycle()
    val runError by viewModel.runError.collectAsStateWithLifecycle()
    val cleanupError by viewModel.cleanupError.collectAsStateWithLifecycle()
    val rows by ClaudeCodeService.shared.rows.collectAsStateWithLifecycle()
    // A fork still carries its source's id until it starts, but it is not that session.
    val openElsewhere = !state.forking && rows.any { it.sessionId != null && it.sessionId == state.sessionId && it.openElsewhere != null }
    val canRunApp = remember(state.cwd) { viewModel.canRunApp() }
    val folderGone = remember(state.cwd, state.status) { !File(state.cwd).isDirectory }

    LaunchedEffect(Unit) { chat.refreshGit() }
    // Whether a terminal holds the session decides the read-only bar: kept current while shown.
    LaunchedEffect(Unit) {
        while (true) {
            ClaudeCodeService.shared.refreshLive()
            delay(LIVE_REFRESH_MS)
        }
    }

    val composerText = rememberTextFieldState()
    val composerFocus = remember { FocusRequester() }
    // Typing goes straight to the composer, and the keys below work, without a click first.
    LaunchedEffect(Unit) { composerFocus.requestFocus() }
    val pending = state.pendingPermissions.firstOrNull()
    var switching by remember { mutableStateOf(false) }
    val keys =
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Ctrl/Cmd+K: jump to another session.
                event.key == Key.K && (event.isCtrlPressed || event.isMetaPressed) -> {
                    switching = true
                    true
                }
                // An approval waits: Esc denies; Enter, with nothing typed, gives the default answer.
                pending != null && event.key == Key.Escape -> {
                    viewModel.deny(pending)
                    true
                }
                pending != null && event.key == Key.Enter && !event.isShiftPressed && composerText.text.isBlank() -> {
                    if (pending.defaultToNo) viewModel.deny(pending) else viewModel.approve(pending, always = false)
                    true
                }
                // Otherwise Esc stops the turn, as in the terminal.
                pending == null && event.key == Key.Escape && state.isBusy -> {
                    viewModel.interrupt()
                    true
                }
                else -> false
            }
        }

    if (switching) {
        SessionSwitcher(
            rows = rows.filter { it.key != key && !it.archived },
            onDismiss = { switching = false },
            onPick = { row ->
                switching = false
                nav.popBack()
                nav.nav(Route.ClaudeCodeChat(row.key))
            },
        )
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = state.title?.takeIf { it.isNotBlank() } ?: firstPrompt(state) ?: stringRes(Res.string.claude_code_new_session),
                subtitle = subtitle(state),
                nav = nav,
            ) {
                val git = state.git
                if (git != null && git.hasChanges) {
                    // A narrow window keeps the title readable: the counts alone stand for the button.
                    val wide = LocalScreenLayout.current.isLargeScreen
                    HoverTooltip(stringRes(Res.string.claude_code_changes)) {
                        TextButton(onClick = { nav.nav(Route.ClaudeCodeChanges(key)) }) {
                            if (wide) {
                                Text(stringRes(Res.string.claude_code_changes))
                                Spacer(Modifier.width(6.dp))
                            }
                            DiffStat(git.insertions, git.deletions)
                        }
                    }
                    // A merged or closed proposal takes no new version.
                    val ended = git.proposalId?.let { proposalEnded(it) } ?: false
                    if (git.repo != null && git.ahead > 0 && !ended) {
                        FilledTonalButton(onClick = { nav.nav(Route.ClaudeCodePropose(key)) }) {
                            Text(stringRes(if (git.proposalId != null) Res.string.claude_code_update_proposal else Res.string.claude_code_propose))
                        }
                    }
                }
                ChatMenu(
                    canRunApp = canRunApp && !runningApp,
                    canEnd = state.status != ChatStatus.NOT_STARTED && state.status != ChatStatus.ENDED,
                    onRunApp = { viewModel.runApp() },
                    onEnd = { viewModel.endSession() },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .then(keys),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = ChatColumnMaxWidth).fillMaxSize()) {
                state.error?.let { ErrorBanner(it) }
                if (runningApp) InlineNote(stringRes(Res.string.claude_code_app_running))
                runError?.let { Banner(stringRes(Res.string.claude_code_run_failed, it)) }

                state.git?.proposalId?.let { proposalId ->
                    ProposalActivity(
                        proposalId = proposalId,
                        chat = chat,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onRemoveWorktree = { viewModel.removeWorktree { nav.popBack() } },
                    )
                }
                cleanupError?.let {
                    Banner(stringRes(Res.string.claude_code_remove_worktree_failed, it), stringRes(Res.string.claude_code_remove_anyway)) {
                        viewModel.removeWorktree(force = true) { nav.popBack() }
                    }
                }

                Messages(
                    state = state,
                    readOnly = openElsewhere || folderGone,
                    nav = nav,
                    modifier = Modifier.weight(1f),
                    onStarter = {
                        composerText.setTextAndPlaceCursorAtEnd(it)
                        composerFocus.requestFocus()
                    },
                )

                state.pendingPermissions.firstOrNull()?.let { request ->
                    PermissionCard(
                        request = request,
                        cwd = state.cwd,
                        onAllow = { viewModel.approve(request, always = false) },
                        onAllowAlways = { viewModel.approve(request, always = true) },
                        onDeny = { viewModel.deny(request) },
                    )
                }

                if (folderGone && !state.isBusy) {
                    // A removed worktree: its history reads, but there is nowhere to continue it.
                    InlineNote(stringRes(Res.string.claude_code_folder_gone))
                } else if (openElsewhere && !state.isBusy) {
                    // Two processes writing one session would interleave their turns: this one reads.
                    OpenElsewhereBar {
                        viewModel.fork { forked ->
                            nav.popBack()
                            nav.nav(Route.ClaudeCodeChat(forked))
                        }
                    }
                } else {
                    Composer(
                        text = composerText,
                        focusRequester = composerFocus,
                        busy = state.isBusy,
                        onSend = { viewModel.send(it) },
                        onStop = { viewModel.interrupt() },
                    )
                    ComposerFooter(
                        state = state,
                        onMode = { viewModel.setPermissionMode(it) },
                    )
                }
            }
        }
    }
}

private fun firstPrompt(state: ClaudeChatState): String? =
    (state.items.firstOrNull { it is ChatItem.UserText } as? ChatItem.UserText)
        ?.text
        ?.lineSequence()
        ?.first()

/** `amethyst · pr/feat/x` when the folder is a checkout, else the folder's name. */
private fun subtitle(state: ClaudeChatState): String {
    val git = state.git ?: return folderName(state.cwd)
    val name = git.repo?.identifier ?: folderName(git.root)
    return listOfNotNull(name, git.branch).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    title: String,
    subtitle: String?,
    nav: INav,
    actions: @Composable RowScope.() -> Unit,
) {
    ShorterTopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            if (nav.canPop()) IconButton(onClick = nav::popBack) { ArrowBackIcon() }
        },
        actions = actions,
    )
}

@Composable
internal fun DiffStat(
    insertions: Int,
    deletions: Int,
) {
    Text("+" + compactCount(insertions.toLong()), color = AddedColor, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.width(4.dp))
    Text("−" + compactCount(deletions.toLong()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
}

internal val AddedColor = Color(0xFF3FB950)

@Composable
private fun Messages(
    state: ClaudeChatState,
    readOnly: Boolean,
    nav: INav,
    modifier: Modifier,
    onStarter: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val rows = remember(state.items) { state.items.toChatRows() }
    val busy = state.isBusy
    val count = rows.size + (if (state.streamingText.isNotEmpty()) 1 else 0) + (if (busy) 1 else 0)
    // Follows the conversation while its end is in view; someone scrolled up to read is left there.
    LaunchedEffect(count, state.streamingText.length / 200) {
        if (count == 0) return@LaunchedEffect
        val lastVisible =
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        if (lastVisible == null || lastVisible >= count - FOLLOW_SLACK) listState.animateScrollToItem(count - 1)
    }
    val uriHandler = LocalUriHandler.current
    val onLink = remember(uriHandler) { { url: String -> uriHandler.openUri(url) } }
    val markdownStyle = rememberChatMarkdownStyle()
    val textStyle = ChatTextStyle
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.historyLoaded) {
            item { CenterNote(stringRes(Res.string.claude_code_loading_history)) }
        }
        if (state.historyLoaded && state.items.isEmpty() && !busy && state.streamingText.isEmpty()) {
            item(key = "intro") { EmptyChatIntro(state, onStarter) }
        }
        // A call the user stopped never gets a result: it only spins while the turn runs, and
        // not while it waits for the user.
        val turnRunning = state.status == ChatStatus.WORKING
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.ToolGroup -> ToolGroupRow(row, state.cwd, turnRunning)
                is ChatRow.Single ->
                    when (val item = row.item) {
                        is ChatItem.UserText -> UserBubble(item.text, textStyle, onLink)
                        is ChatItem.AssistantText -> RenderMarkdown(item.text, onLink, Modifier.fillMaxWidth(), style = markdownStyle, textStyle = textStyle)
                        is ChatItem.ToolCall ->
                            if (ToolKind.of(item.name) == ToolKind.EDIT) {
                                FileChangeRow(item, state.cwd, turnRunning)
                            } else {
                                ToolCallRow(item, state.cwd, turnRunning)
                            }
                        is ChatItem.Notice -> NoticeRow(item)
                    }
            }
        }
        if (state.streamingText.isNotEmpty()) {
            // Plain until the message is complete: markdown would re-parse on every delta.
            item(key = "streaming") { SelectionContainer { Text(state.streamingText, style = textStyle) } }
        }
        if (busy) {
            item(key = "status") { LiveStatus(state) }
        }
        if (state.status == ChatStatus.NOT_STARTED && state.historyLoaded && state.items.isNotEmpty() && !readOnly) {
            // A fork looks like its source until it starts: say what the first message does.
            item(key = "resume") { CenterNote(stringRes(if (state.forking) Res.string.claude_code_fork_hint else Res.string.claude_code_resume_hint)) }
        }
    }
}

/**
 * What happened to the branch's proposal on nostr since the session last looked: reviewers'
 * comments (sent to Claude on request) and a merge (the worktree can go).
 */
@Composable
private fun ProposalActivity(
    proposalId: String,
    chat: ClaudeChat,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemoveWorktree: () -> Unit,
) {
    // Keeps the proposal's thread (comments, statuses, updates) loading while the chat is open.
    ThreadFilterAssemblerSubscription(proposalId, accountViewModel)
    val note = remember(proposalId) { LocalCache.getOrCreateNote(proposalId) }
    val replies by remember(note) { note.flow().replies.stateFlow }.collectAsStateWithLifecycle()
    val status = observeGitStatus(note)
    val reviewed by chat.reviewed.collectAsStateWithLifecycle()
    val me = accountViewModel.account.signer.pubKey
    val repo =
        chat.state.value.git
            ?.repo
    val comments =
        remember(replies, reviewed, repo) {
            // A reply goes to Claude as a review only from the repository's maintainers or someone
            // the user follows: anyone can reply to a proposal, and the text becomes a prompt.
            val trusted = trustedReviewers(repo)
            note.replies.filter { reply ->
                val event = reply.event ?: return@filter false
                if (event.pubKey == me || reply.idHex in reviewed) return@filter false
                if (event.pubKey !in trusted && !accountViewModel.account.isFollowing(event.pubKey)) return@filter false
                when (event) {
                    is CommentEvent, is GitReplyEvent -> event.content.isNotBlank()
                    // A verdict counts when it asks for something or says why.
                    is GitReviewEvent -> event.content.isNotBlank() || event.verdict() in ACTIONABLE_VERDICTS
                    else -> false
                }
            }
        }

    when (status) {
        is GitStatusAppliedEvent ->
            ActionBanner(stringRes(Res.string.claude_code_proposal_merged), stringRes(Res.string.claude_code_remove_worktree), onRemoveWorktree)
        is GitStatusClosedEvent ->
            ActionBanner(stringRes(Res.string.claude_code_proposal_closed), stringRes(Res.string.claude_code_view_proposal)) { nav.nav(Route.Note(proposalId)) }
        else -> {}
    }
    if (comments.isNotEmpty()) {
        val header = stringRes(Res.string.claude_code_review_prompt_header)
        // Worded here, in composition; the code each inline comment is about is read when sent.
        val items = comments.map { reviewItem(it) }
        val scope = rememberCoroutineScope()
        ActionBanner(
            pluralStringRes(Res.plurals.claude_code_review_comments, comments.size, comments.size),
            stringRes(Res.string.claude_code_address_review),
        ) {
            scope.launch {
                val prompt = ReviewPrompt.render(File(chat.state.value.cwd), header, items)
                chat.addressReview(items.map { it.id }, prompt)
            }
        }
    }
}

/**
 * Who may review [repo]'s proposals: its confirmed maintainers, as the repository screen counts
 * them, or the announcement's own owner and list until those load.
 */
private fun trustedReviewers(repo: NostrRepoRef?): Set<String> {
    if (repo == null) return emptySet()
    val address = Address(repo.kind, repo.pubKeyHex, repo.identifier)
    val confirmed = LocalCache.gitAuthority.membership(address)?.confirmedMaintainers
    if (!confirmed.isNullOrEmpty()) return confirmed.toSet()
    val announcement = LocalCache.getAddressableNoteIfExists(address)?.event as? GitRepositoryEvent
    return (listOf(repo.pubKeyHex) + announcement?.maintainers().orEmpty()).toSet()
}

/** Whether the proposal was merged or closed. */
@Composable
private fun proposalEnded(proposalId: String): Boolean {
    val note = remember(proposalId) { LocalCache.getOrCreateNote(proposalId) }
    val status = observeGitStatus(note)
    return status is GitStatusAppliedEvent || status is GitStatusClosedEvent
}

private val ACTIONABLE_VERDICTS = setOf(GitReviewEvent.Verdict.NACK, GitReviewEvent.Verdict.CONCEPT_NACK, GitReviewEvent.Verdict.CHANGES_REQUIRED)

/** A reviewer's comment, headed by who wrote it and, for an inline one, the file and lines. */
@Composable
private fun reviewItem(note: Note): ReviewItem {
    val event = note.event
    val name = note.author?.toBestDisplayName() ?: event?.pubKey?.take(8).orEmpty()
    val location = (event as? CommentEvent)?.gitCodeLocation()
    val heading =
        when {
            event is GitReviewEvent && event.verdict() != null -> stringRes(Res.string.claude_code_review_verdict, name, verdictLabel(event.verdict()!!))
            location != null -> {
                val start = location.startLine ?: location.endLine ?: 0
                val end = location.endLine ?: start
                val lines = if (end > start) "$start\u2013$end" else "$start"
                stringRes(
                    if (location.removedSide) Res.string.claude_code_review_inline_removed else Res.string.claude_code_review_inline,
                    name,
                    location.path,
                    lines,
                )
            }
            else -> stringRes(Res.string.claude_code_review_general, name)
        }
    return ReviewItem(
        id = note.idHex,
        heading = heading,
        body = event?.content.orEmpty(),
        location =
            location?.let {
                val start = it.startLine ?: it.endLine ?: 1
                ReviewLocation(it.path, start, it.endLine ?: start, it.removedSide, it.commit)
            },
        createdAt = note.createdAt() ?: 0,
    )
}

@Composable
private fun ActionBanner(
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Spinner, what the CLI says it is doing, and for how long. */
@Composable
private fun LiveStatus(state: ClaudeChatState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.turnStartedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = state.turnStartedAt?.let { ((now - it) / 1000).coerceAtLeast(0) }
    // While a call waits for an answer, the CLI's last activity is stale: the turn is on the user.
    val waiting = state.status == ChatStatus.NEEDS_APPROVAL
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        if (waiting) {
            Icon(MaterialSymbols.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        } else {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (waiting) {
                stringRes(Res.string.claude_code_waiting_approval)
            } else {
                listOfNotNull(state.activity ?: stringRes(Res.string.claude_code_working), seconds?.let { elapsed(it) }).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun elapsed(seconds: Long): String = if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"

/** A new chat: where it will work, and a few ways to begin that fill the composer. */
@Composable
private fun EmptyChatIntro(
    state: ClaudeChatState,
    onStarter: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.Assistant, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(24.dp))
        }
        Text(stringRes(Res.string.claude_code_intro_title, subtitle(state)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            listOf(ToolViews.relative(state.cwd, File(System.getProperty("user.home"))).let { if (it != state.cwd) "~/$it" else it }, modeLabel(state.permissionMode)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.grayText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(6.dp))
        val starters =
            buildList {
                add(stringRes(Res.string.claude_code_starter_explain))
                add(stringRes(Res.string.claude_code_starter_tests))
                val git = state.git
                if (git != null && git.ahead > 0) add(stringRes(Res.string.claude_code_starter_review_branch, git.base ?: "main"))
                if (git != null && git.uncommitted > 0) add(stringRes(Res.string.claude_code_starter_review_uncommitted))
            }
        starters.forEach { text ->
            Surface(
                onClick = { onStarter(text) },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

/** What the user wrote: a quiet rounded block on the right, in the reply's own type. */
@Composable
private fun UserBubble(
    text: String,
    textStyle: TextStyle,
    onLink: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, start = 48.dp), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(18.dp),
        ) {
            val padding = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            // Typed text stays as typed (markdown would join its lines); pasted code and review
            // prompts carry fences, which read better as code blocks.
            if (text.contains("```")) {
                val style = rememberChatMarkdownStyle(codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest)
                RenderMarkdown(text, onLink, padding, style = style, textStyle = textStyle)
            } else {
                SelectionContainer { Text(text, padding, style = textStyle) }
            }
        }
    }
}

/** A run of tool calls as one quiet line: "Ran 6 commands · read 2 files", opened on click. */
@Composable
private fun ToolGroupRow(
    group: ChatRow.ToolGroup,
    cwd: String,
    turnRunning: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val parts =
        group.counts.map { (kind, n) ->
            pluralStringRes(
                when (kind) {
                    ToolKind.COMMAND -> Res.plurals.claude_code_tool_group_command
                    ToolKind.READ -> Res.plurals.claude_code_tool_group_read
                    ToolKind.EDIT -> Res.plurals.claude_code_tool_group_edit
                    ToolKind.SEARCH -> Res.plurals.claude_code_tool_group_search
                    ToolKind.WEB -> Res.plurals.claude_code_tool_group_web
                    ToolKind.AGENT -> Res.plurals.claude_code_tool_group_agent
                    ToolKind.OTHER -> Res.plurals.claude_code_tool_group_other
                },
                n,
                n,
            )
        }
    val failed = group.failed.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.claude_code_tool_group_failed, it, it) }
    val summary = (parts + listOfNotNull(failed)).joinToString(" · ").replaceFirstChar { it.uppercase() }
    Column {
        ToolLine(
            symbol = ToolKind.of(group.calls.first().name).symbol(),
            text = summary,
            running = turnRunning && group.running != null,
            error = false,
            expanded = expanded,
            monospace = false,
            onClick = { expanded = !expanded },
        )
        group.running?.takeIf { turnRunning && !expanded }?.let { running ->
            Text(
                running.summary?.let { ToolViews.relative(it, File(cwd)) } ?: running.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 26.dp),
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 14.dp)) {
                group.calls.forEach { ToolCallRow(it, cwd, turnRunning) }
            }
        }
    }
}

@Composable
internal fun CodeBlock(
    text: String,
    color: Color = Color.Unspecified,
    maxHeight: Int = 320,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color,
                modifier = Modifier.heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()).padding(8.dp),
            )
        }
    }
}

@Composable
private fun NoticeRow(notice: ChatItem.Notice) {
    val text =
        when (notice.kind) {
            NoticeKind.INTERRUPTED -> stringRes(Res.string.claude_code_notice_interrupted)
            NoticeKind.TURN_FAILED -> stringRes(Res.string.claude_code_notice_turn_failed) + (notice.detail?.let { ": $it" } ?: "")
            NoticeKind.PROCESS_ENDED -> stringRes(Res.string.claude_code_notice_process_ended)
            NoticeKind.CLI_NOTE -> notice.detail ?: ""
        }
    CenterNote(text)
}

@Composable
private fun CenterNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.grayText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun InlineNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** What "don't ask again" will allow, in words, from the CLI's own suggestion. */
@Composable
private fun alwaysLabel(request: PermissionRequest): String? {
    if (request.suppressAlwaysAllow) return null
    val suggestion = request.parsedSuggestions.firstOrNull() ?: return null
    return when (suggestion) {
        is PermissionSuggestion.SetMode -> suggestion.mode?.let { stringRes(Res.string.claude_code_allow_mode, modeLabel(it)) }
        is PermissionSuggestion.AddRules -> {
            val rules = suggestion.rules.joinToString(", ")
            if (rules.isEmpty()) {
                null
            } else if (suggestion.destination == "session") {
                stringRes(Res.string.claude_code_allow_rule_session, rules)
            } else {
                stringRes(Res.string.claude_code_allow_rule_project, rules)
            }
        }
        is PermissionSuggestion.AddDirectories -> stringRes(Res.string.claude_code_allow_dirs, suggestion.directories.joinToString(", ") { folderName(it) })
        is PermissionSuggestion.Other -> null
    }
}

/**
 * What Claude asks to do, shown as what it would do: the diff of an edit, the command it would
 * run. Enter answers with the default and Esc denies (see the screen's key handler).
 */
@Composable
private fun PermissionCard(
    request: PermissionRequest,
    cwd: String,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    val view = rememberToolView(request.toolName, request.input, cwd, pending = true)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ToolKind.of(request.toolName).symbol(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (view) {
                        is ToolView.FileChange ->
                            stringRes(if (view.newFile) Res.string.claude_code_permission_create else Res.string.claude_code_permission_edit, view.path)
                        is ToolView.Command -> stringRes(Res.string.claude_code_permission_run)
                        else -> stringRes(Res.string.claude_code_permission_title, request.displayName ?: request.toolName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (view) {
                is ToolView.FileChange ->
                    Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { FileChangeDiff(view) }
                is ToolView.Command -> {
                    view.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
                    CodeBlock("$ " + view.command, maxHeight = 200)
                }
                else -> CodeBlock(ToolViews.relative(request.summary, File(cwd)), maxHeight = 160)
            }
            request.decisionReason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            }
            val always = alwaysLabel(request)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (request.defaultToNo) {
                    Button(onClick = onDeny) { Text(stringRes(Res.string.claude_code_deny)) }
                    OutlinedButton(onClick = onAllow) { Text(stringRes(Res.string.claude_code_allow)) }
                } else {
                    Button(onClick = onAllow) { Text(stringRes(Res.string.claude_code_allow)) }
                    OutlinedButton(onClick = onDeny) { Text(stringRes(Res.string.claude_code_deny)) }
                }
                // The rule can be a whole command line: one line, cut, with the full text in a tooltip.
                Box(Modifier.weight(1f)) {
                    if (always != null) {
                        HoverTooltip(always) {
                            TextButton(onClick = onAllowAlways) { Text(always, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                Text(
                    stringRes(if (request.defaultToNo) Res.string.claude_code_permission_keys_deny else Res.string.claude_code_permission_keys),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The app's chat composer: a rounded field with the send button inside; Stop while a turn runs. */
@Composable
private fun Composer(
    text: TextFieldState,
    focusRequester: FocusRequester,
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val send = {
        val value = text.text.toString()
        if (value.isNotBlank()) {
            onSend(value)
            text.clearText()
        }
    }
    ThinPaddingTextField(
        state = text,
        modifier =
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp).focusRequester(focusRequester).onPreviewKeyEvent { event ->
                // Enter sends; Shift+Enter is a new line.
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                    send()
                    true
                } else {
                    false
                }
            },
        shape = EditFieldBorder,
        textStyle = ChatTextStyle,
        maxLines = 10,
        placeholder = { Text(stringRes(Res.string.claude_code_message_hint), color = MaterialTheme.colorScheme.placeholderText) },
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    HoverTooltip(stringRes(Res.string.claude_code_stop)) {
                        IconButton(onClick = onStop) {
                            Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(MaterialSymbols.Stop, contentDescription = stringRes(Res.string.claude_code_stop), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                ThinSendButton(isActive = text.text.isNotBlank(), modifier = Modifier) { send() }
            }
        },
    )
}

/** Under the composer: the permission mode, and how full the context is. */
@Composable
private fun ComposerFooter(
    state: ClaudeChatState,
    onMode: (PermissionMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeChip(state.permissionMode, onMode)
        Spacer(Modifier.weight(1f))
        val used = state.contextTokens
        val window = state.contextWindow
        if (used != null && window != null && window > 0) ContextMeter(used, window)
    }
}

/**
 * How much of the model's context the conversation fills. Near full, Claude Code summarizes
 * the conversation to go on, and details from early on can be lost.
 */
@Composable
private fun ContextMeter(
    used: Long,
    window: Long,
) {
    val fraction = (used.toFloat() / window).coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    val color =
        when {
            fraction >= 0.85f -> MaterialTheme.colorScheme.error
            fraction >= 0.6f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.grayText
        }
    HoverTooltip(stringRes(Res.string.claude_code_context_tooltip, compactCount(used), compactCount(window))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                progress = { fraction.coerceAtLeast(0.02f) },
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = color,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.claude_code_context_used, percent), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/** 950, 12.4k, 1.2M: counts that fit a narrow bar. */
internal fun compactCount(n: Long): String =
    when {
        n < 1_000 -> n.toString()
        n < 10_000 -> "%.1fk".format(n / 1_000.0).replace(".0k", "k")
        n < 1_000_000 -> "${n / 1_000}k"
        else -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    }

/** In place of the composer while a terminal holds the session: why, and the way to go on. */
@Composable
private fun OpenElsewhereBar(onFork: () -> Unit) {
    // Side by side on a wide window; stacked on a narrow one, where the text would wrap to a column.
    val wide = LocalScreenLayout.current.isLargeScreen
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val note: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringRes(Res.string.claude_code_open_elsewhere_readonly), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            }
        }
        val fork: @Composable () -> Unit = {
            FilledTonalButton(onClick = onFork) {
                Icon(MaterialSymbols.ForkRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringRes(Res.string.claude_code_fork))
            }
        }
        if (wide) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                note(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                fork()
            }
        } else {
            Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note(Modifier)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { fork() }
            }
        }
    }
}

@Composable
private fun ModeChip(
    mode: PermissionMode,
    onMode: (PermissionMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // The mode decides what Claude may do unasked: the more it may, the louder the chip.
    val color =
        when (mode) {
            PermissionMode.AUTO, PermissionMode.BYPASS -> MaterialTheme.colorScheme.error
            PermissionMode.ACCEPT_EDITS -> MaterialTheme.colorScheme.tertiary
            PermissionMode.PLAN -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.grayText
        }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.Security, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(modeLabel(mode), style = MaterialTheme.typography.labelMedium, color = color)
            Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection {
                PICKABLE_MODES.forEach { option ->
                    MenuRow(
                        title = modeLabel(option),
                        description = modeDescription(option),
                        selected = option == mode,
                    ) {
                        open = false
                        onMode(option)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuRow(
    title: String,
    description: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .widthIn(min = 220.dp, max = 340.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.placeholderText,
            )
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ChatMenu(
    canRunApp: Boolean,
    canEnd: Boolean,
    onRunApp: () -> Unit,
    onEnd: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(MaterialSymbols.MoreVert, contentDescription = null) }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection {
                MenuRow(stringRes(Res.string.claude_code_run_app), enabled = canRunApp) {
                    open = false
                    onRunApp()
                }
                MenuRow(stringRes(Res.string.claude_code_end_session), enabled = canEnd) {
                    open = false
                    onEnd()
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: ChatError) {
    when (error) {
        ChatError.NotInstalled -> Banner(stringRes(Res.string.claude_code_not_installed))
        is ChatError.StartFailed -> Banner(stringRes(Res.string.claude_code_error_start_failed, error.message ?: ""))
        ChatError.FolderGone -> Banner(stringRes(Res.string.claude_code_folder_gone))
        ChatError.HeldElsewhere -> Banner(stringRes(Res.string.claude_code_open_elsewhere_readonly))
        is ChatError.Exited ->
            Column {
                Banner(stringRes(Res.string.claude_code_error_exited, error.code?.toString() ?: "?"))
                if (error.stderr.isNotBlank()) Box(Modifier.padding(horizontal = 16.dp)) { CodeBlock(error.stderr, maxHeight = 160) }
            }
    }
}

internal val PICKABLE_MODES = listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN, PermissionMode.AUTO)

@Composable
internal fun modeLabel(mode: PermissionMode): String =
    when (mode) {
        PermissionMode.ACCEPT_EDITS -> stringRes(Res.string.claude_code_mode_accept_edits)
        PermissionMode.PLAN -> stringRes(Res.string.claude_code_mode_plan)
        PermissionMode.AUTO -> stringRes(Res.string.claude_code_mode_auto)
        else -> stringRes(Res.string.claude_code_mode_default)
    }

@Composable
internal fun modeDescription(mode: PermissionMode): String =
    when (mode) {
        PermissionMode.ACCEPT_EDITS -> stringRes(Res.string.claude_code_mode_accept_edits_desc)
        PermissionMode.PLAN -> stringRes(Res.string.claude_code_mode_plan_desc)
        PermissionMode.AUTO -> stringRes(Res.string.claude_code_mode_auto_desc)
        else -> stringRes(Res.string.claude_code_mode_default_desc)
    }
