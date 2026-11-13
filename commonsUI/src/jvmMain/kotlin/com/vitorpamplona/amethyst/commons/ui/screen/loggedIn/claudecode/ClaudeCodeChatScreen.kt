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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
import com.vitorpamplona.amethyst.commons.claudecode.NoticeKind
import com.vitorpamplona.amethyst.commons.claudecode.ReviewItem
import com.vitorpamplona.amethyst.commons.claudecode.ReviewLocation
import com.vitorpamplona.amethyst.commons.claudecode.ReviewPrompt
import com.vitorpamplona.amethyst.commons.claudecode.ToolKind
import com.vitorpamplona.amethyst.commons.claudecode.toChatRows
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_cost
import com.vitorpamplona.amethyst.commons.resources.claude_code_deny
import com.vitorpamplona.amethyst.commons.resources.claude_code_end_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_exited
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_start_failed
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_elsewhere_short
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_elsewhere_warning
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_stop
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_agent
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_command
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_edit
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_other
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_read
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_search
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_web
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_input
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_output
import com.vitorpamplona.amethyst.commons.resources.claude_code_update_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_view_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_working
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
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
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** The widest a chat column gets: past this, lines are too long to read. */
internal val ChatColumnMaxWidth = 760.dp

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
    val openElsewhere = rows.any { it.sessionId != null && it.sessionId == state.sessionId && it.openElsewhere != null }
    val canRunApp = remember(state.cwd) { viewModel.canRunApp() }

    LaunchedEffect(Unit) { chat.refreshGit() }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = state.title?.takeIf { it.isNotBlank() } ?: firstPrompt(state) ?: stringRes(Res.string.claude_code_new_session),
                subtitle = subtitle(state),
                nav = nav,
            ) {
                val git = state.git
                if (git != null && git.hasChanges) {
                    TextButton(onClick = { nav.nav(Route.ClaudeCodeChanges(key)) }) {
                        Text(stringRes(Res.string.claude_code_changes))
                        Spacer(Modifier.width(6.dp))
                        DiffStat(git.insertions, git.deletions)
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
        Box(Modifier.padding(padding).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
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

                Messages(state, nav, Modifier.weight(1f))

                state.pendingPermissions.firstOrNull()?.let { request ->
                    PermissionCard(
                        request = request,
                        onAllow = { viewModel.approve(request, always = false) },
                        onAllowAlways = { viewModel.approve(request, always = true) },
                        onDeny = { viewModel.deny(request) },
                    )
                }

                Composer(
                    busy = state.isBusy,
                    onSend = { viewModel.send(it) },
                    onStop = { viewModel.interrupt() },
                )
                ComposerFooter(
                    state = state,
                    openElsewhere = openElsewhere,
                    onMode = { viewModel.setPermissionMode(it) },
                )
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
    Text("+$insertions", color = AddedColor, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.width(4.dp))
    Text("−$deletions", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
}

internal val AddedColor = Color(0xFF3FB950)

@Composable
private fun Messages(
    state: ClaudeChatState,
    nav: INav,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val rows = remember(state.items) { state.items.toChatRows() }
    val busy = state.isBusy
    val count = rows.size + (if (state.streamingText.isNotEmpty()) 1 else 0) + (if (busy) 1 else 0)
    LaunchedEffect(count, state.streamingText.length / 200) {
        if (count > 0) listState.animateScrollToItem(count - 1)
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
        // A call the user stopped never gets a result: it only runs while the turn does.
        val turnRunning = state.status == ChatStatus.WORKING || state.status == ChatStatus.NEEDS_APPROVAL
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.ToolGroup -> ToolGroupRow(row, turnRunning)
                is ChatRow.Single ->
                    when (val item = row.item) {
                        is ChatItem.UserText -> UserBubble(item.text, textStyle, onLink)
                        is ChatItem.AssistantText -> RenderMarkdown(item.text, onLink, Modifier.fillMaxWidth(), style = markdownStyle, textStyle = textStyle)
                        is ChatItem.ToolCall -> ToolCallRow(item, turnRunning)
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
        if (state.status == ChatStatus.NOT_STARTED && state.historyLoaded && state.items.isNotEmpty()) {
            item(key = "resume") { CenterNote(stringRes(Res.string.claude_code_resume_hint)) }
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
    val comments =
        remember(replies, reviewed) {
            note.replies.filter { reply ->
                val event = reply.event ?: return@filter false
                if (event.pubKey == me || reply.idHex in reviewed) return@filter false
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            listOfNotNull(state.activity ?: stringRes(Res.string.claude_code_working), seconds?.let { elapsed(it) }).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun elapsed(seconds: Long): String = if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"

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

private fun ToolKind.symbol(): MaterialSymbol =
    when (this) {
        ToolKind.COMMAND -> MaterialSymbols.Code
        ToolKind.READ -> MaterialSymbols.Visibility
        ToolKind.EDIT -> MaterialSymbols.Edit
        ToolKind.SEARCH -> MaterialSymbols.Search
        ToolKind.WEB -> MaterialSymbols.Language
        ToolKind.AGENT -> MaterialSymbols.Assistant
        ToolKind.OTHER -> MaterialSymbols.Apps
    }

/** A run of tool calls as one quiet line: "Ran 6 commands · read 2 files", opened on click. */
@Composable
private fun ToolGroupRow(
    group: ChatRow.ToolGroup,
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
                running.summary ?: running.name,
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
                group.calls.forEach { ToolCallRow(it, turnRunning) }
            }
        }
    }
}

/** One tool call: its name and what it acted on; opened, its input and output. */
@Composable
private fun ToolCallRow(
    call: ChatItem.ToolCall,
    turnRunning: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ToolLine(
            symbol = ToolKind.of(call.name).symbol(),
            text = listOfNotNull(call.name, call.summary).joinToString("  "),
            running = turnRunning && !call.isDone,
            error = call.isError,
            expanded = expanded,
            monospace = true,
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(stringRes(Res.string.claude_code_tool_input))
                CodeBlock(call.input)
                call.result?.let {
                    Label(stringRes(Res.string.claude_code_tool_output))
                    CodeBlock(it.take(RESULT_SHOWN), if (call.isError) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        }
    }
}

@Composable
private fun ToolLine(
    symbol: MaterialSymbol,
    text: String,
    running: Boolean,
    error: Boolean,
    expanded: Boolean,
    monospace: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip8()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.grayText
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (running) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(if (error) MaterialSymbols.Error else symbol, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun Modifier.clip8() = clip(RoundedCornerShape(6.dp))

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
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

@Composable
private fun PermissionCard(
    request: PermissionRequest,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringRes(Res.string.claude_code_permission_title, request.displayName ?: request.toolName),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            CodeBlock(request.summary, maxHeight = 160)
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
            }
            // The rule can be a whole command line: one line, cut, with the full text in a tooltip.
            if (always != null) {
                HoverTooltip(always) {
                    TextButton(onClick = onAllowAlways) { Text(always, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** The app's chat composer: a rounded field with the send button inside; Stop while a turn runs. */
@Composable
private fun Composer(
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val text = rememberTextFieldState()
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
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp).onPreviewKeyEvent { event ->
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

/** Under the composer: the permission mode, a note when another Claude Code has the session, the cost. */
@Composable
private fun ComposerFooter(
    state: ClaudeChatState,
    openElsewhere: Boolean,
    onMode: (PermissionMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeChip(state.permissionMode, onMode)
        if (openElsewhere) {
            Spacer(Modifier.width(10.dp))
            HoverTooltip(stringRes(Res.string.claude_code_open_elsewhere_warning)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(stringRes(Res.string.claude_code_open_elsewhere_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        state.costUsd?.let {
            Text(stringRes(Res.string.claude_code_cost, "%.2f".format(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
        }
    }
}

@Composable
private fun ModeChip(
    mode: PermissionMode,
    onMode: (PermissionMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.Security, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(modeLabel(mode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
            Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(14.dp))
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

private const val RESULT_SHOWN = 20_000
