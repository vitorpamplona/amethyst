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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.PermissionSuggestion
import com.vitorpamplona.agents.SlashCommand
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.claudecode.AccountToolServer
import com.vitorpamplona.amethyst.commons.claudecode.AgentWorkspace
import com.vitorpamplona.amethyst.commons.claudecode.ChatError
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.ChatKind
import com.vitorpamplona.amethyst.commons.claudecode.ChatRow
import com.vitorpamplona.amethyst.commons.claudecode.ChatStatus
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChat
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChatState
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeChatViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.GitHubPullRequests
import com.vitorpamplona.amethyst.commons.claudecode.GitWorkInfo
import com.vitorpamplona.amethyst.commons.claudecode.NostrRepoRef
import com.vitorpamplona.amethyst.commons.claudecode.NoticeKind
import com.vitorpamplona.amethyst.commons.claudecode.PublishedArtifact
import com.vitorpamplona.amethyst.commons.claudecode.PublishedArtifacts
import com.vitorpamplona.amethyst.commons.claudecode.ReviewItem
import com.vitorpamplona.amethyst.commons.claudecode.ReviewLocation
import com.vitorpamplona.amethyst.commons.claudecode.ReviewPrompt
import com.vitorpamplona.amethyst.commons.claudecode.ScreenToolServer
import com.vitorpamplona.amethyst.commons.claudecode.SentImage
import com.vitorpamplona.amethyst.commons.claudecode.ToolKind
import com.vitorpamplona.amethyst.commons.claudecode.ToolView
import com.vitorpamplona.amethyst.commons.claudecode.ToolViews
import com.vitorpamplona.amethyst.commons.claudecode.firstPromptLine
import com.vitorpamplona.amethyst.commons.claudecode.hasOpenWork
import com.vitorpamplona.amethyst.commons.claudecode.toChatRows
import com.vitorpamplona.amethyst.commons.devices.Devices
import com.vitorpamplona.amethyst.commons.devices.ui.ChatFocus
import com.vitorpamplona.amethyst.commons.devices.ui.DeviceChatFocus
import com.vitorpamplona.amethyst.commons.devices.ui.DeviceUpBadge
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.verdictLabel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amethyst_assistant
import com.vitorpamplona.amethyst.commons.resources.amethyst_assistant_desc
import com.vitorpamplona.amethyst.commons.resources.amethyst_assistant_permission_act
import com.vitorpamplona.amethyst.commons.resources.amethyst_assistant_permission_screen
import com.vitorpamplona.amethyst.commons.resources.claude_code_address_review
import com.vitorpamplona.amethyst.commons.resources.claude_code_ahead_of
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_dirs
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_mode
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_rule_project
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_rule_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_app_running
import com.vitorpamplona.amethyst.commons.resources.claude_code_artifact_open_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_attach
import com.vitorpamplona.amethyst.commons.resources.claude_code_attach_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_attach_pick
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_context_tooltip
import com.vitorpamplona.amethyst.commons.resources.claude_code_context_used
import com.vitorpamplona.amethyst.commons.resources.claude_code_context_used_little
import com.vitorpamplona.amethyst.commons.resources.claude_code_create_pr
import com.vitorpamplona.amethyst.commons.resources.claude_code_deny
import com.vitorpamplona.amethyst.commons.resources.claude_code_edit_message
import com.vitorpamplona.amethyst.commons.resources.claude_code_end_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_exited
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_start_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_folder_gone
import com.vitorpamplona.amethyst.commons.resources.claude_code_fork
import com.vitorpamplona.amethyst.commons.resources.claude_code_fork_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_intro_question
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_change_this
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_create_artifact
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_create_doc
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_create_this
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_keys
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_keys_deny
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_publish
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_republish
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_run
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_setting
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_proposal_closed
import com.vitorpamplona.amethyst.commons.resources.claude_code_proposal_merged
import com.vitorpamplona.amethyst.commons.resources.claude_code_published_count
import com.vitorpamplona.amethyst.commons.resources.claude_code_published_section
import com.vitorpamplona.amethyst.commons.resources.claude_code_pull_requests
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_waiting
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_session_panel
import com.vitorpamplona.amethyst.commons.resources.claude_code_settings_starter_data
import com.vitorpamplona.amethyst.commons.resources.claude_code_settings_starter_notifications
import com.vitorpamplona.amethyst.commons.resources.claude_code_settings_starter_privacy
import com.vitorpamplona.amethyst.commons.resources.claude_code_settings_starter_text
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_explain
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_explain_repos
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_review_branch
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_review_uncommitted
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_tests
import com.vitorpamplona.amethyst.commons.resources.claude_code_starter_tests_repos
import com.vitorpamplona.amethyst.commons.resources.claude_code_stop
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_agent
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_command
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_edit
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_other
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_read
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_search
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_settings
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_group_web
import com.vitorpamplona.amethyst.commons.resources.claude_code_turn_done
import com.vitorpamplona.amethyst.commons.resources.claude_code_turn_done_after
import com.vitorpamplona.amethyst.commons.resources.claude_code_turn_done_terminal
import com.vitorpamplona.amethyst.commons.resources.claude_code_uncommitted_files
import com.vitorpamplona.amethyst.commons.resources.claude_code_update_pr
import com.vitorpamplona.amethyst.commons.resources.claude_code_view_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_waiting_approval
import com.vitorpamplona.amethyst.commons.resources.claude_code_working
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_clone_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_workspace_subtitle
import com.vitorpamplona.amethyst.commons.resources.claude_code_workstream_cloning
import com.vitorpamplona.amethyst.commons.resources.device_panel_toggle
import com.vitorpamplona.amethyst.commons.resources.device_panel_toggle_desc
import com.vitorpamplona.amethyst.commons.resources.rename
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.pickFiles
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverLazyListState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.PaneSplitter
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.LocalDockController
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableImageDialog
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The widest a chat column gets: past this, lines are too long to read. */
internal val ChatColumnMaxWidth = 760.dp

private const val LIVE_REFRESH_MS = 5_000L

/** How long the agent must leave the device alone before its next action brings the panel up again. */
private const val DEVICE_RUN_GAP_MS = 60_000L

/** How fresh the device list behind the top bar's green dot is kept. */
private const val DEVICE_DOT_REFRESH_MS = 10_000L

/** The agent a chat talks to, as its sentences name it. */
internal val LocalAgentName = staticCompositionLocalOf { "Claude" }

/** How many rows from the end the view may be and still follow new ones. */
private const val FOLLOW_SLACK = 3

/**
 * Past the last item's top, clamped to the list's end: a tall last row (an error banner, a long
 * reply) shows whole instead of stopping at its first line.
 */
private const val TO_ITS_END = 100_000

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
    // However it was opened (the list, a notification, after a restart), the assistant reaches this account.
    if (state.kind == ChatKind.SETTINGS) BindAssistantTools(accountViewModel, nav)
    val runningApp by viewModel.runningApp.collectAsStateWithLifecycle()
    val runError by viewModel.runError.collectAsStateWithLifecycle()
    val cleanupError by viewModel.cleanupError.collectAsStateWithLifecycle()
    val rows by ClaudeCodeService.shared.rows.collectAsStateWithLifecycle()
    // A fork still carries its source's id until it starts, but it is not that session.
    // The terminal that has the session open, if one does: what it is doing shows at the chat's end.
    val terminal = if (state.forking) null else rows.firstOrNull { it.sessionId != null && it.sessionId == state.sessionId && it.openElsewhere != null }?.openElsewhere
    val openElsewhere = terminal != null
    val canRunApp = remember(state.cwd) { viewModel.canRunApp() }
    val folderGone = remember(state.cwd, state.status) { !File(state.cwd).isDirectory }

    LaunchedEffect(Unit) { chat.refreshGit() }
    // On screen, how a turn ends is seen: the list doesn't flag it as waiting to be read.
    DisposableEffect(chat) {
        val release = chat.viewing()
        onDispose { release() }
    }
    // Whether a terminal holds the session decides the read-only bar: kept current while shown.
    LaunchedEffect(Unit) {
        while (true) {
            ClaudeCodeService.shared.refreshLive()
            delay(LIVE_REFRESH_MS)
        }
    }

    // Starts from what was typed and not sent wherever this chat was drawn before, and keeps it there.
    val composerText = rememberTextFieldState(viewModel.unsent)
    LaunchedEffect(composerText) {
        snapshotFlow { composerText.text.toString() }.collect { viewModel.unsent = it }
    }
    val attachments = remember { ComposerAttachments() }
    var dropHover by remember { mutableStateOf(false) }
    val dropTarget = rememberImageDropTarget(attachments) { dropHover = it }
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // A message opened to be asked again arrives with its text, ready to change.
        viewModel.takeDraft()?.let { composerText.setTextAndPlaceCursorAtEnd(it) }
        // The settings assistant has no commands of its own to offer.
        if (state.kind == ChatKind.CODE) viewModel.loadCliInfo()
    }
    // Typing goes straight to the composer, and the keys below work, without a click first.
    LaunchedEffect(Unit) { composerFocus.requestFocus() }
    val pending = state.pendingPermissions.firstOrNull()
    // The answers being filled in for [pending]'s questions: the key handler reads them too.
    val questionForm = remember(pending?.requestId) { pending?.questions?.let { QuestionForm(it) } }
    // Sends the failed message again; a saved session's message with images can't be, so its text
    // goes back into the composer to send with them by hand.
    val retry: (() -> Unit)? =
        if (openElsewhere || folderGone) {
            null
        } else {
            {
                if (viewModel.retry() == false) {
                    (state.items.lastOrNull { it is ChatItem.UserText } as? ChatItem.UserText)?.let { composerText.setTextAndPlaceCursorAtEnd(it.text) }
                    composerFocus.requestFocus()
                }
            }
        }
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
                // Questions wait: Esc dismisses them, unless the user has started answering. Enter
                // belongs to the card's own fields.
                pending?.questions != null && event.key == Key.Escape -> {
                    if (questionForm?.hasInput != true) viewModel.dismissQuestions(pending)
                    true
                }
                // An approval waits: Esc denies; Enter, with nothing typed, gives the default answer.
                pending != null && pending.questions == null && event.key == Key.Escape -> {
                    viewModel.deny(pending)
                    true
                }
                pending != null && pending.questions == null && event.key == Key.Enter && !event.isShiftPressed && composerText.text.isBlank() -> {
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

    var renaming by remember { mutableStateOf(false) }
    val shownTitle = state.title?.takeIf { it.isNotBlank() } ?: state.items.firstPromptLine() ?: stringRes(Res.string.claude_code_new_session)
    if (renaming) {
        RenameDialog(
            // A chat with no name yet starts from an empty field, not its placeholder.
            initial = state.title?.takeIf { it.isNotBlank() } ?: state.items.firstPromptLine().orEmpty(),
            onDismiss = { renaming = false },
            onSave = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }
    // A workspace of fresh clones: its name and repositories stand for the folder's id.
    // From the service's own list, not the disk; a new workstream is in it once its clones are.
    val workspace = remember(state.cwd, state.cloning) { ClaudeCodeService.shared.workspaceAt(state.cwd) }
    // The dock's device panel follows the coding chat in front: its workstream's device, its folder
    // for Run, and its composer for a screenshot. Where an Android SDK or Xcode is installed.
    val canDevice = remember(state.kind) { state.kind == ChatKind.CODE && Devices.supported }
    val deviceOwner = remember(key, state.cwd, workspace) { ClaudeCodeService.shared.deviceOwner(key, state.cwd) }
    val dock = LocalDockController.current
    val chatScope = rememberCoroutineScope()
    if (canDevice) {
        DisposableEffect(key, deviceOwner, workspace, state.cwd) {
            val release =
                DeviceChatFocus.publish(
                    ChatFocus(
                        chatKey = key,
                        owner = deviceOwner,
                        ownerLabel = workspace?.title ?: state.title,
                        folder = File(state.cwd),
                        agentName = chat.backend.shortName,
                        attach = { file -> chatScope.launch { attachments.add(listOf(file)) } },
                    ),
                )
            onDispose(release)
        }
        // When the agent starts using this chat's device, the panel comes up to show what it does:
        // once per run of work, so a user who folds it while the agent goes on keeps it folded.
        LaunchedEffect(key, deviceOwner, dock) {
            var lastAt = 0L
            Devices.shared.activity.collect { a ->
                if (Devices.shared.selection.value[deviceOwner] != a.serial) return@collect
                val now = System.currentTimeMillis()
                if (now - lastAt > DEVICE_RUN_GAP_MS) dock?.show(PanelId.DEVICES)
                lastAt = now
            }
        }
        // The top bar's green dot reads the device list: kept fresh while the chat is shown, even
        // with the panel (which polls it closely) folded.
        LaunchedEffect(Unit) {
            while (true) {
                Devices.shared.refreshIfStale(DEVICE_DOT_REFRESH_MS)
                delay(DEVICE_DOT_REFRESH_MS)
            }
        }
    }
    // Sentences in the chat name the agent ("Codex wants to run a command").
    CompositionLocalProvider(LocalAgentName provides chat.backend.shortName, LocalWorkspace provides workspace) {
        BoxWithConstraints {
            // A wide window has the workspace's changes and pull requests beside the chat; a narrow one
            // reaches them from the top bar.
            val showPanel = workspace != null && state.kind == ChatKind.CODE && maxWidth >= PullRequestsPanelMinScreen
            val paneWidths = LocalAppServices.current.paneWidthPrefs
            val savedWidth by paneWidths.flow.collectAsStateWithLifecycle()
            // What the user dragged it to, as far as the chat keeps room to read.
            val panelWidth =
                (savedWidth.changesPanel ?: PullRequestsPanelWidth.value)
                    .coerceIn(PullRequestsPanelWidthRange.start, maxOf(PullRequestsPanelWidthRange.start, minOf(PullRequestsPanelWidthRange.endInclusive, maxWidth.value - ChatMinWidth.value)))
                    .dp
            Scaffold(
                topBar = {
                    ChatTopBar(
                        title = shownTitle,
                        subtitle = if (state.kind == ChatKind.SETTINGS) null else subtitle(state, workspace),
                        nav = nav,
                        onTitleClick = { renaming = true },
                    ) {
                        // With the panel, the session's output is in it, not here. Without it, one
                        // button holds it all: the branch's changes, the workspace's pull requests and
                        // what the session put on claude.ai, so the title keeps its room.
                        if (!showPanel) {
                            SessionMenu(
                                git = state.git?.takeIf { it.hasChanges },
                                hasPullRequests = workspace != null,
                                items = state.items,
                                cwd = state.cwd,
                                onChanges = { nav.nav(Route.ClaudeCodeChanges(key)) },
                                onPullRequests = { nav.nav(Route.ClaudeCodePullRequests(key)) },
                            )
                            // Proposing is an action, not something the session made: it keeps its button.
                            val git = state.git
                            if (git != null && git.hasChanges && git.repo != null && git.ahead > 0) {
                                // A merged or closed proposal takes no new version.
                                val ended = git.proposalId?.let { proposalEnded(it) } ?: false
                                if (!ended) {
                                    FilledTonalButton(onClick = { nav.nav(Route.ClaudeCodePropose(key)) }) {
                                        Text(stringRes(if (git.proposalId != null) Res.string.claude_code_update_pr else Res.string.claude_code_create_pr))
                                    }
                                }
                            }
                        }
                        if (canDevice) {
                            HoverTooltip(stringRes(Res.string.device_panel_toggle_desc)) {
                                // The dock's device panel; a window without a dock opens the devices screen.
                                IconButton(onClick = { dock?.toggle(PanelId.DEVICES) ?: nav.nav(Route.Devices(key)) }) {
                                    // A green dot while this chat's device is up, panel open or not.
                                    val devices by Devices.shared.state.collectAsStateWithLifecycle()
                                    val picks by Devices.shared.selection.collectAsStateWithLifecycle()
                                    val up = remember(devices, picks, deviceOwner) { Devices.shared.selected(deviceOwner)?.isOnline == true }
                                    Box {
                                        Icon(
                                            MaterialSymbols.PhoneAndroid,
                                            contentDescription = stringRes(Res.string.device_panel_toggle),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        if (up) DeviceUpBadge(Modifier.align(Alignment.TopEnd))
                                    }
                                }
                            }
                        }
                        ChatMenu(
                            canRunApp = canRunApp && !runningApp,
                            canEnd = state.status != ChatStatus.NOT_STARTED && state.status != ChatStatus.ENDED,
                            onRename = { renaming = true },
                            onRunApp = { viewModel.runApp() },
                            onEnd = { viewModel.endSession() },
                        )
                    }
                },
            ) { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .imePadding()
                            .then(keys)
                            .dragAndDropTarget(shouldStartDragAndDrop = { it.hasFiles() }, target = dropTarget),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(Modifier.widthIn(max = ChatColumnMaxWidth).fillMaxSize()) {
                            // A start that failed can be tried again; a gone folder or a failed clone can't.
                            state.error?.let { ErrorBanner(it, onRetry = retry?.takeIf { _ -> state.lastTurnFailed && !state.isBusy }) }
                            state.cloning?.let { CloningNote(it) }
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
                                chatKey = chat.key,
                                state = state,
                                accountViewModel = accountViewModel,
                                readOnly = openElsewhere || folderGone,
                                terminalStatus = terminal?.status,
                                nav = nav,
                                modifier = Modifier.weight(1f),
                                onEditFrom =
                                    if (state.kind == ChatKind.CODE && state.sessionId != null && !folderGone && chat.backend.capabilities.editFromMessage) {
                                        { item -> viewModel.editFrom(item) { key -> nav.nav(Route.ClaudeCodeChat(key)) } }
                                    } else {
                                        null
                                    },
                                onStarter = {
                                    composerText.setTextAndPlaceCursorAtEnd(it)
                                    composerFocus.requestFocus()
                                },
                                onRetry = retry,
                            )

                            state.pendingPermissions.firstOrNull()?.let { request ->
                                if (questionForm != null && request.requestId == pending?.requestId) {
                                    QuestionCard(
                                        form = questionForm,
                                        onAnswer = { viewModel.answerQuestions(request, it) },
                                        onDismiss = { viewModel.dismissQuestions(request) },
                                    )
                                    return@let
                                }
                                PermissionCard(
                                    request = request,
                                    note = state.permissionNotes[request.requestId],
                                    cwd = state.cwd,
                                    onAllow = { viewModel.approve(request, always = false) },
                                    onAllowAlways = { viewModel.approve(request, always = true) },
                                    onDeny = { viewModel.deny(request) },
                                )
                            }

                            // The agent's plan stays in view while there is work left in it.
                            if (state.tasks.hasOpenWork) {
                                AgentTaskList(state.tasks, working = state.isBusy, approvalPending = state.pendingPermissions.isNotEmpty(), chatKey = chat.key)
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
                                    commands = state.commands,
                                    attachments = attachments,
                                    dropHover = dropHover,
                                    focusRequester = composerFocus,
                                    busy = state.isBusy,
                                    onSend = { text, images ->
                                        val asking = pending?.takeIf { it.questions != null }
                                        when {
                                            // Open questions hold the turn: a message now is the reply to them, not the next prompt.
                                            asking != null && images.isEmpty() && !text.startsWith("/") -> viewModel.answerQuestions(asking, PermissionDecision.Answer(emptyMap(), reply = text))
                                            images.isEmpty() -> viewModel.send(text)
                                            else -> viewModel.send(text, images.map { it.input }, images.map { it.file })
                                        }
                                    },
                                    onStop = { viewModel.interrupt() },
                                )
                                ComposerFooter(
                                    state = state,
                                    onMode = { viewModel.setPermissionMode(it) },
                                    onModel = { viewModel.setModel(it) },
                                    modes = chat.backend.capabilities.modes,
                                )
                            }
                        }
                    }
                    if (showPanel) {
                        // Dragged like the app's other side panes, and remembered with them.
                        PaneSplitter(onDrag = { delta -> paneWidths.setChangesPanel((panelWidth.value - delta).coerceIn(PullRequestsPanelWidthRange)) })
                        PullRequestsPanel(key = key, busy = state.isBusy, nav = nav, width = panelWidth, items = state.items, cwd = state.cwd)
                    }
                }
            }
        }
    }
}

/** How a hashtag link reaches the chat from the Markdown renderer. */
private const val HASHTAG_LINK = "nostr:hashtag?id="

/**
 * Where a link in the chat goes. The Markdown renderer makes every `#word` a Nostr hashtag link,
 * which the system can't open: in an agent's reply, `#4888` is the repository's pull request or
 * issue, opened on GitHub when the workstream has one GitHub repository; any other hashtag opens
 * its feed in the app, where there is [nav] to go to it.
 */
@Composable
internal fun rememberChatLinks(nav: INav?): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val workspace = LocalWorkspace.current
    return remember(uriHandler, nav, workspace) {
        { url: String ->
            if (!url.startsWith(HASHTAG_LINK)) {
                uriHandler.openUri(url)
            } else {
                val tag = url.removePrefix(HASHTAG_LINK)
                val gitHub =
                    workspace
                        ?.repos
                        ?.mapNotNull { GitHubPullRequests.gitHubRepo(it.url) }
                        ?.distinct()
                        ?.singleOrNull()
                when {
                    // GitHub sends /issues/N on to the pull request when N is one.
                    gitHub != null && tag.isNotEmpty() && tag.all { it.isDigit() } -> uriHandler.openUri("https://github.com/$gitHub/issues/$tag")
                    nav != null -> nav.nav(Route.Hashtag(tag))
                }
            }
        }
    }
}

/** The workspace the chat works in, for the parts of the screen that name where it works. */
internal val LocalWorkspace = staticCompositionLocalOf<AgentWorkspace?> { null }

/**
 * `amethyst · pr/feat/x` when the folder is a checkout; `webapp + corelib · 2 repositories` in a
 * workspace; else the folder's name.
 */
@Composable
private fun subtitle(
    state: ClaudeChatState,
    workspace: AgentWorkspace?,
): String {
    if (workspace != null) {
        val names = workspace.repos.joinToString(" + ") { it.name }
        return pluralStringRes(Res.plurals.claude_code_workspace_subtitle, workspace.repos.size, names, workspace.repos.size)
    }
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
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    ShorterTopAppBar(
        title = {
            Column {
                if (onTitleClick != null) {
                    // A click on the name renames the session; a pencil on hover says it can.
                    val hover = remember { MutableInteractionSource() }
                    val hovered by hover.collectIsHoveredAsState()
                    HoverTooltip(stringRes(Res.string.rename)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).hoverable(hover).clickable(onClick = onTitleClick),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                MaterialSymbols.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.grayText,
                                modifier = Modifier.padding(start = 4.dp).size(16.dp).alpha(if (hovered) 1f else 0f),
                            )
                        }
                    }
                } else {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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

/** What the header's counts cover: "Changes · 3 commits ahead of origin/main · 2 files not committed". */
@Composable
private fun changesTooltip(git: GitWorkInfo): String = listOfNotNull(stringRes(Res.string.claude_code_changes), changesDetail(git)).joinToString(" · ")

/** What the counts compare with: "3 commits ahead of main · 2 files not committed". */
@Composable
private fun changesDetail(git: GitWorkInfo): String? =
    listOfNotNull(
        git.base?.takeIf { git.ahead > 0 }?.let { pluralStringRes(Res.plurals.claude_code_ahead_of, git.ahead, git.ahead, it) },
        git.uncommitted.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.claude_code_uncommitted_files, it, it) },
    ).joinToString(" · ").ifEmpty { null }

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

/** Chats that already opened at their newest message: they reopen where they were left. */
private val landedChats: MutableSet<String> = ConcurrentHashMap.newKeySet()

@Composable
private fun Messages(
    chatKey: String,
    state: ClaudeChatState,
    accountViewModel: AccountViewModel,
    readOnly: Boolean,
    /** What a terminal that has the session open says it is doing (`idle`, `busy`, `waiting`). */
    terminalStatus: String?,
    nav: INav,
    modifier: Modifier,
    onEditFrom: ((ChatItem.UserText) -> Unit)?,
    onStarter: (String) -> Unit,
    /** Sends the last message again; offered on a failure that ends the chat. */
    onRetry: (() -> Unit)?,
) {
    // Kept per chat while the app runs: leaving the chat and coming back finds the same place.
    val listState = rememberForeverLazyListState("ClaudeCodeChat-$chatKey")
    val rows = remember(state.items) { state.items.toChatRows() }
    val busy = state.isBusy
    val showIntro = state.historyLoaded && state.items.isEmpty() && !busy && state.streamingText.isEmpty()
    val showResume = state.status == ChatStatus.NOT_STARTED && state.historyLoaded && state.items.isNotEmpty() && !readOnly
    // The agent answered and stopped: it waits for the next message. Not after a note that says
    // how the turn ended (stopped, failed), and not while a terminal still works on it.
    // Only where something waits: a process here between turns, or a terminal that says it's idle.
    // Not a saved session nothing runs for yet, nor one whose folder is gone, nor after an error.
    val waiting =
        if (terminalStatus != null) {
            terminalStatus == TERMINAL_IDLE
        } else {
            !readOnly && state.error == null && (state.status == ChatStatus.READY || state.status == ChatStatus.ENDED)
        }
    val showDone =
        state.historyLoaded &&
            waiting &&
            state.streamingText.isEmpty() &&
            state.items.lastOrNull().let { it is ChatItem.AssistantText || it is ChatItem.ToolCall }
    // Every item the list below emits, in the same order, so the last index is the list's own.
    val itemCount =
        (if (!state.historyLoaded || showIntro) 1 else 0) +
            rows.size +
            (if (state.streamingText.isNotEmpty()) 1 else 0) +
            (if (busy) 1 else 0) +
            (if (showDone) 1 else 0) +
            (if (showResume) 1 else 0)
    // Opening a chat lands on its newest message: the history arrives after the first frame,
    // when the only row in view is the loading note at the top. Only the first time: coming
    // back keeps where the user left it.
    val landed = remember(chatKey) { booleanArrayOf(chatKey in landedChats) }
    // Follows the conversation while its end is in view; someone scrolled up to read is left there.
    // Keyed on the last row and on the turn running too: when a turn ends, its live status leaves
    // as the reply or the error arrives, and the count alone stays the same.
    LaunchedEffect(itemCount, rows.lastOrNull()?.key, busy, state.streamingText.length / 200) {
        if (!state.historyLoaded || itemCount == 0) return@LaunchedEffect
        val last = itemCount - 1
        if (!landed[0]) {
            landed[0] = true
            landedChats.add(chatKey)
            listState.scrollToItem(last, TO_ITS_END)
            return@LaunchedEffect
        }
        val lastVisible =
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        if (lastVisible == null || lastVisible >= last - FOLLOW_SLACK) listState.animateScrollToItem(last, TO_ITS_END)
    }
    val onLink = rememberChatLinks(nav)
    val markdownStyle = rememberChatMarkdownStyle()
    val textStyle = ChatTextStyle
    // A sent image open full size: the message's images, and the one clicked.
    var viewing by remember { mutableStateOf<Pair<ImmutableList<BaseMediaContent>, BaseMediaContent>?>(null) }
    val onOpenImage = remember { { images: List<SentImage>, index: Int -> viewing = sentImagesToView(images, index) } }
    // Outside the SelectionContainer below: a dialog is another layout hierarchy, and its text
    // must not join the chat's selection.
    viewing?.let { (all, shown) ->
        ZoomableImageDialog(imageUrl = shown, allImages = all, onDismiss = { viewing = null }, accountViewModel = accountViewModel)
    }
    // One selection for the whole chat: any part of any message, or a run across several, copies.
    SelectionContainer(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Gutter, vertical = Gutter),
            verticalArrangement = Arrangement.spacedBy(PartGap),
        ) {
            if (!state.historyLoaded) {
                item { CenterNote(stringRes(Res.string.claude_code_loading_history)) }
            }
            if (showIntro) {
                item(key = "intro") { EmptyChatIntro(state, onStarter) }
            }
            // A call the user stopped never gets a result: it only spins while the turn runs, and
            // not while it waits for the user.
            val turnRunning = state.status == ChatStatus.WORKING
            // Waiting on the user is still the turn: a call that runs meanwhile has not stopped.
            val turnLive = turnRunning || state.status == ChatStatus.NEEDS_APPROVAL
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ChatRow.ToolGroup -> ToolGroupRow(row, state.cwd, turnRunning)
                    is ChatRow.Published ->
                        PublishedRow(
                            row,
                            state.cwd,
                            turnLive,
                            waitingForApproval = state.pendingPermissions.any { it.toolUseId == row.call.toolUseId },
                        )
                    is ChatRow.Single ->
                        when (val item = row.item) {
                            // A new turn: more room above it than between the parts of a reply.
                            is ChatItem.UserText ->
                                Box(Modifier.padding(top = GroupGap - PartGap)) {
                                    UserBubble(item.text, item.images, textStyle, onLink, onEditFrom?.let { edit -> { edit(item) } }, onOpenImage)
                                }
                            is ChatItem.AssistantText -> ChatMarkdown(item.text, onLink, Modifier.fillMaxWidth(), style = markdownStyle, textStyle = textStyle, codeBlocks = true)
                            is ChatItem.ToolCall ->
                                when (ToolKind.of(item.name)) {
                                    ToolKind.EDIT -> FileChangeRow(item, state.cwd, turnRunning)
                                    ToolKind.QUESTION -> QuestionCallRow(item, waiting = busy)
                                    else -> ToolCallRow(item, state.cwd, turnRunning)
                                }
                            is ChatItem.Notice -> NoticeRow(item, onRetry = onRetry?.takeIf { !busy && item === state.items.lastOrNull() })
                        }
                }
            }
            if (state.streamingText.isNotEmpty()) {
                // Plain until the message is complete: markdown would re-parse on every delta.
                item(key = "streaming") { Text(state.streamingText, style = textStyle) }
            }
            if (busy) {
                item(key = "status") { LiveStatus(state) }
            }
            if (showDone) {
                item(key = "done") { TurnDone(state, inTerminal = terminalStatus != null) }
            }
            if (showResume) {
                // A fork looks like its source until it starts: say what the first message does.
                item(key = "resume") { CenterNote(stringRes(if (state.forking) Res.string.claude_code_fork_hint else Res.string.claude_code_resume_hint)) }
            }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = CardPadding, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                stringRes(if (state.isAsking) Res.string.claude_code_question_waiting else Res.string.claude_code_waiting_approval)
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

/** The end of a chat whose agent answered and now waits for the user's next message. */
@Composable
private fun TurnDone(
    state: ClaudeChatState,
    inTerminal: Boolean,
) {
    val agent = LocalAgentName.current
    val took = state.lastTurnMillis?.takeIf { !inTerminal && state.turnEndedAt != null }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        // Centering on the line box sits the check below the letters, whose capitals ride high in
        // it: lift it to their middle.
        Icon(
            MaterialSymbols.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            filled = true,
            modifier = Modifier.offset(y = (-1.5).dp).size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                inTerminal -> stringRes(Res.string.claude_code_turn_done_terminal, agent)
                took != null -> stringRes(Res.string.claude_code_turn_done_after, agent, elapsed(took / 1000))
                else -> stringRes(Res.string.claude_code_turn_done, agent)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
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
    if (state.kind == ChatKind.SETTINGS) {
        SettingsAssistantIntro(onStarter)
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PartGap),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.Assistant, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(24.dp))
        }
        val workspace = LocalWorkspace.current
        // The header already names the repository and branch: the intro asks, and says where.
        Text(stringRes(Res.string.claude_code_intro_question, LocalAgentName.current), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            listOf(ToolViews.relative(state.cwd, File(System.getProperty("user.home"))).let { if (it != state.cwd) "~/$it" else it }, modeLabel(state.permissionMode)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.grayText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(4.dp))
        val starters =
            buildList {
                // A workspace of several repositories asks about all of them.
                val several = (workspace?.repos?.size ?: 0) > 1
                add(stringRes(if (several) Res.string.claude_code_starter_explain_repos else Res.string.claude_code_starter_explain))
                add(stringRes(if (several) Res.string.claude_code_starter_tests_repos else Res.string.claude_code_starter_tests))
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
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = CardPadding, vertical = 12.dp))
            }
        }
    }
}

/** The settings assistant before its first message: what it can do, and a few ways to start. */
@Composable
private fun SettingsAssistantIntro(onStarter: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PartGap),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(24.dp))
        }
        Text(stringRes(Res.string.amethyst_assistant), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringRes(Res.string.amethyst_assistant_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.size(4.dp))
        listOf(
            stringRes(Res.string.claude_code_settings_starter_data),
            stringRes(Res.string.claude_code_settings_starter_text),
            stringRes(Res.string.claude_code_settings_starter_notifications),
            stringRes(Res.string.claude_code_settings_starter_privacy),
        ).forEach { text ->
            Surface(
                onClick = { onStarter(text) },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = CardPadding, vertical = 12.dp))
            }
        }
    }
}

/** What the user wrote: a quiet rounded block on the right, in the reply's own type. */
@Composable
private fun UserBubble(
    text: String,
    images: List<SentImage>,
    textStyle: TextStyle,
    onLink: (String) -> Unit,
    onEdit: (() -> Unit)?,
    onOpenImage: (List<SentImage>, Int) -> Unit,
) {
    // One column: the caller may place this in a Box, which would stack the images and the
    // text on top of each other.
    Column(Modifier.fillMaxWidth()) {
        if (images.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(start = 48.dp), contentAlignment = Alignment.CenterEnd) {
                SentImageRow(images, onOpen = { index -> onOpenImage(images, index) })
            }
        }
        if (text.isNotEmpty()) UserTextBubble(text, images.isNotEmpty(), textStyle, onLink, onEdit)
    }
}

@Composable
private fun UserTextBubble(
    text: String,
    belowImages: Boolean,
    textStyle: TextStyle,
    onLink: (String) -> Unit,
    onEdit: (() -> Unit)?,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(top = if (belowImages) 4.dp else 0.dp).hoverable(hover),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ask this again differently, in a copy of the session cut just before it. Faint until
        // the message is hovered or the button has the keyboard's focus, but always there: a
        // touch screen or a keyboard reaches it too.
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            if (onEdit != null) {
                val label = stringRes(Res.string.claude_code_edit_message)
                val buttonInteractions = remember { MutableInteractionSource() }
                val focused by buttonInteractions.collectIsFocusedAsState()
                HoverTooltip(label) {
                    IconButton(onClick = onEdit, interactionSource = buttonInteractions, modifier = Modifier.size(32.dp).alpha(if (hovered || focused) 1f else 0.35f)) {
                        Icon(MaterialSymbols.Edit, contentDescription = label, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(18.dp),
        ) {
            val padding = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            // Typed text stays as typed (markdown would join its lines); pasted code and review
            // prompts carry fences, which read better as code blocks.
            if (text.contains("```")) {
                val style = rememberChatMarkdownStyle(codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest)
                ChatMarkdown(text, onLink, padding, style = style, textStyle = textStyle)
            } else {
                // Selectable through the chat's SelectionContainer.
                Text(text, padding, style = textStyle)
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
                    ToolKind.SETTINGS -> Res.plurals.claude_code_tool_group_settings
                    // Left out of the rows (the task card shows them); counted as other tools if one slips in.
                    ToolKind.PLAN, ToolKind.QUESTION, ToolKind.OTHER -> Res.plurals.claude_code_tool_group_other
                },
                n,
                n,
            )
        }
    val failed = group.failed.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.claude_code_tool_group_failed, it, it) }
    val summary = parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    Column {
        ToolLine(
            symbol = ToolKind.of(group.calls.first().name).symbol(),
            text = summary,
            running = turnRunning && group.running != null,
            error = false,
            expanded = expanded,
            // In red, so a failure folded into the line still shows.
            errorSuffix = failed,
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
private fun NoticeRow(
    notice: ChatItem.Notice,
    /** Offered on the failure that ends the chat: sends the last message again. */
    onRetry: (() -> Unit)?,
) {
    when (notice.kind) {
        NoticeKind.INTERRUPTED -> CenterNote(stringRes(Res.string.claude_code_notice_interrupted))
        NoticeKind.CLI_NOTE -> CenterNote(notice.detail ?: "")
        // An error is not a stop the user chose: it says so in the error's colors, and offers a retry.
        NoticeKind.TURN_FAILED, NoticeKind.PROCESS_ENDED ->
            Banner(
                text =
                    if (notice.kind == NoticeKind.TURN_FAILED) {
                        stringRes(Res.string.claude_code_notice_turn_failed) + (notice.detail?.let { ": $it" } ?: "")
                    } else {
                        stringRes(Res.string.claude_code_notice_process_ended, LocalAgentName.current)
                    },
                action = onRetry?.let { stringRes(Res.string.try_again) },
                modifier = Modifier,
                onAction = { onRetry?.invoke() },
            )
    }
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

/** A new workstream's repositories still cloning: what was typed meanwhile goes out once they are in. */
@Composable
private fun CloningNote(repositories: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.grayText)
        Spacer(Modifier.width(InlineGap))
        Text(pluralStringRes(Res.plurals.claude_code_workstream_cloning, repositories, repositories), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
    }
}

@Composable
private fun InlineNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
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
    /** What the call would do, from the chat's own tool (a setting's before and after). */
    note: String?,
    cwd: String,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    val view = rememberToolView(request.toolName, request.input, cwd, pending = true)
    // A page, deck or doc for claude.ai: asked as what it publishes, not as the tool's JSON.
    val publishing = remember(request.toolName, request.input) { PublishedArtifacts.of(request.toolName, request.input.toString(), null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(PartGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(publishing?.kind?.symbol() ?: ToolKind.of(request.toolName).symbol(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        // A post, an upload or a payment from the assistant: it acts as the user, not on a setting.
                        note != null && request.toolName.startsWith(ACCOUNT_TOOLS_PREFIX) -> stringRes(Res.string.amethyst_assistant_permission_act, LocalAgentName.current)
                        note != null && request.toolName.startsWith(SCREEN_TOOLS_PREFIX) -> stringRes(Res.string.amethyst_assistant_permission_screen, LocalAgentName.current)
                        note != null -> stringRes(Res.string.claude_code_permission_setting)
                        publishing != null ->
                            stringRes(
                                when {
                                    publishing.kind == PublishedArtifact.Kind.DOCUMENT -> Res.string.claude_code_permission_create_doc
                                    publishing.kind == PublishedArtifact.Kind.TYPED -> Res.string.claude_code_permission_create_artifact
                                    publishing.change == PublishedArtifact.Change.UPDATED -> Res.string.claude_code_permission_republish
                                    else -> Res.string.claude_code_permission_publish
                                },
                                LocalAgentName.current,
                            )
                        view is ToolView.FileChange ->
                            // The diff below names the file: the question needn't say it again.
                            stringRes(if (view.newFile) Res.string.claude_code_permission_create_this else Res.string.claude_code_permission_change_this, LocalAgentName.current)
                        view is ToolView.Command -> stringRes(Res.string.claude_code_permission_run, LocalAgentName.current)
                        else -> stringRes(Res.string.claude_code_permission_title, LocalAgentName.current, request.displayName ?: request.toolName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                note != null ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
                    }
                publishing != null -> PublishPreview(publishing, cwd)
                view is ToolView.FileChange ->
                    Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { FileChangeDiff(view) }
                view is ToolView.Command -> {
                    view.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
                    CodeBlock("$ " + view.command, maxHeight = 200)
                }
                else -> CodeBlock(ToolViews.relative(request.summary, File(cwd)), maxHeight = 160)
            }
            request.decisionReason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            }
            // The app's own tools (a setting) are approved one change at a time: "always" would let
            // the assistant change any setting unasked.
            val always = if (note != null) null else alwaysLabel(request)
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
    commands: List<SlashCommand>,
    attachments: ComposerAttachments,
    dropHover: Boolean,
    focusRequester: FocusRequester,
    busy: Boolean,
    onSend: (String, List<ComposerImage>) -> Unit,
    onStop: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canSend = text.text.isNotBlank() || attachments.images.isNotEmpty()
    val send = {
        val value = text.text.toString()
        if (value.isNotBlank() || attachments.images.isNotEmpty()) {
            onSend(value, attachments.take())
            text.clearText()
        }
    }
    val pickTitle = stringRes(Res.string.claude_code_attach_pick)
    // Typing `/` offers the CLI's commands; Esc puts the menu away until the text changes.
    val typed = text.text.toString()
    var dismissedFor by remember { mutableStateOf<String?>(null) }
    val matches = remember(typed, commands) { slashMatches(typed, commands) }
    val showMenu = matches.isNotEmpty() && dismissedFor != typed
    var selected by remember(matches) { mutableStateOf(0) }
    val pick = { command: SlashCommand ->
        text.setTextAndPlaceCursorAtEnd("/" + command.name + " ")
        dismissedFor = null
    }
    if (showMenu) {
        SlashCommandMenu(matches, selected.coerceIn(0, matches.lastIndex), pick, Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp))
    }
    if (attachments.images.isNotEmpty()) {
        ComposerImageRow(attachments, Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp))
    }
    attachments.refused?.let { name ->
        Text(
            stringRes(Res.string.claude_code_attach_failed, name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = Gutter, end = Gutter, top = 8.dp),
        )
    }
    ThinPaddingTextField(
        state = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 8.dp)
                // Files dragged over the chat land here.
                .then(if (dropHover) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, EditFieldBorder) else Modifier)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        // The command menu: arrows move, Tab or Enter takes the command (Enter sends
                        // one already typed in full), Esc closes it.
                        showMenu && event.key == Key.DirectionDown -> {
                            selected = (selected + 1) % matches.size
                            true
                        }
                        showMenu && event.key == Key.DirectionUp -> {
                            selected = (selected - 1 + matches.size) % matches.size
                            true
                        }
                        showMenu && event.key == Key.Escape -> {
                            dismissedFor = typed
                            true
                        }
                        showMenu && (event.key == Key.Tab || (event.key == Key.Enter && !event.isShiftPressed)) &&
                            !(event.key == Key.Enter && typed == "/" + matches[selected.coerceIn(0, matches.lastIndex)].name) -> {
                            pick(matches[selected.coerceIn(0, matches.lastIndex)])
                            true
                        }
                        // Enter sends; Shift+Enter is a new line.
                        event.key == Key.Enter && !event.isShiftPressed -> {
                            send()
                            true
                        }
                        // Pasting a screenshot or image files attaches them; pasting text goes on as usual.
                        event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed) && attachments.clipboardHasImage() -> {
                            scope.launch { attachments.pasteFromClipboard() }
                            true
                        }
                        else -> false
                    }
                },
        shape = EditFieldBorder,
        textStyle = ChatTextStyle,
        maxLines = 10,
        placeholder = { Text(stringRes(Res.string.claude_code_message_hint, LocalAgentName.current), color = MaterialTheme.colorScheme.placeholderText) },
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        leadingIcon = {
            val attach = stringRes(Res.string.claude_code_attach)
            HoverTooltip(attach) {
                IconButton(onClick = { scope.launch { attachments.add(pickFiles(pickTitle, multiple = true, ImageInput.EXTENSIONS)) } }) {
                    Icon(MaterialSymbols.AddPhotoAlternate, contentDescription = attach, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(22.dp))
                }
            }
        },
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
                ThinSendButton(isActive = canSend, modifier = Modifier) { send() }
            }
        },
    )
}

/** Under the composer: the permission mode, and how full the context is. */
@Composable
private fun ComposerFooter(
    state: ClaudeChatState,
    onMode: (PermissionMode) -> Unit,
    onModel: (String?) -> Unit,
    modes: List<PermissionMode>,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The settings assistant asks before every change whatever the mode: no mode to pick.
        if (state.kind != ChatKind.SETTINGS) ModeChip(state.permissionMode, onMode, modes.filter { it in PICKABLE_MODES })
        ModelChip(state, onModel)
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
            Text(
                if (percent == 0 && used > 0) stringRes(Res.string.claude_code_context_used_little) else stringRes(Res.string.claude_code_context_used, percent),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
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
                Spacer(Modifier.width(InlineGap))
                Text(stringRes(Res.string.claude_code_fork))
            }
        }
        if (wide) {
            Row(Modifier.padding(start = CardPadding, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                note(Modifier.weight(1f))
                Spacer(Modifier.width(InlineGap))
                fork()
            }
        } else {
            Column(Modifier.padding(start = CardPadding, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(InlineGap)) {
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
    modes: List<PermissionMode>,
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
            Spacer(Modifier.width(4.dp))
            Text(modeLabel(mode), style = MaterialTheme.typography.labelMedium, color = color)
            Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection {
                modes.forEach { option ->
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
    descriptionMaxLines: Int = Int.MAX_VALUE,
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
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = descriptionMaxLines, overflow = TextOverflow.Ellipsis) }
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
    onRename: () -> Unit,
    onRunApp: () -> Unit,
    onEnd: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(MaterialSymbols.MoreVert, contentDescription = null) }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection {
                MenuRow(stringRes(Res.string.rename)) {
                    open = false
                    onRename()
                }
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

/**
 * The top bar's one button for what the session made: the branch's changes (with their size),
 * the workspace's pull requests, and the pages and docs it put on claude.ai. Nothing shows until
 * there is one of them.
 */
@Composable
private fun SessionMenu(
    git: GitWorkInfo?,
    hasPullRequests: Boolean,
    items: List<ChatItem>,
    cwd: String,
    onChanges: () -> Unit,
    onPullRequests: () -> Unit,
) {
    val published = rememberSessionArtifacts(items, cwd)
    if (git == null && !hasPullRequests && published.isEmpty()) return
    val open = rememberArtifactOpener()
    var expanded by remember { mutableStateOf(false) }
    // A narrow window keeps the title readable: an icon stands for the word.
    val wide = LocalScreenLayout.current.isLargeScreen
    val label = stringRes(Res.string.claude_code_session_panel)
    val tooltip =
        listOfNotNull(
            git?.let { changesTooltip(it) },
            published.size.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.claude_code_published_count, it, it) },
        ).joinToString("\n").ifEmpty { label }
    Box {
        HoverTooltip(tooltip) {
            TextButton(onClick = { expanded = true }) {
                if (wide) Text(label) else Icon(MaterialSymbols.Difference, contentDescription = label, modifier = Modifier.size(18.dp))
                // The counts that say there is something to look at: the branch's size, the pages up.
                if (git != null) {
                    Spacer(Modifier.width(6.dp))
                    DiffStat(git.insertions, git.deletions)
                }
                if (published.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(published.size.toString(), style = MaterialTheme.typography.labelMedium)
                }
                Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        RoundedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (git != null || hasPullRequests) {
                // The rows name themselves ("Changes", "Pull requests"): no label over them.
                Spacer(Modifier.height(4.dp))
                RoundedMenuSection {
                    if (git != null) {
                        SessionMenuRow(MaterialSymbols.Difference, stringRes(Res.string.claude_code_changes), changesDetail(git), trailing = { DiffStat(git.insertions, git.deletions) }) {
                            expanded = false
                            onChanges()
                        }
                    }
                    if (hasPullRequests) {
                        SessionMenuRow(MaterialSymbols.AltRoute, stringRes(Res.string.claude_code_pull_requests), null) {
                            expanded = false
                            onPullRequests()
                        }
                    }
                }
            }
            if (published.isNotEmpty()) {
                SessionMenuLabel(stringRes(Res.string.claude_code_published_section))
                RoundedMenuSection {
                    published.forEach { artifact ->
                        val url = artifact.url ?: return@forEach
                        PublishedArtifactRow(artifact, Modifier.width(320.dp)) {
                            open.link(url)
                            // Nothing opened it: the menu stays, saying so.
                            if (open.failed == null) expanded = false
                        }
                    }
                }
                open.failed?.let {
                    Text(
                        stringRes(Res.string.claude_code_artifact_open_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionMenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.grayText,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** A place the session's menu goes to: its icon, name and what it holds, as the artifact rows under it. */
@Composable
private fun SessionMenuRow(
    symbol: MaterialSymbol,
    title: String,
    description: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Row(verticalAlignment = Alignment.CenterVertically) { it() } }
    }
}

@Composable
private fun ErrorBanner(
    error: ChatError,
    /** Offered where trying again can work: the agent wasn't installed, or didn't start. */
    onRetry: (() -> Unit)? = null,
) {
    val action = onRetry?.let { stringRes(Res.string.try_again) }
    when (error) {
        ChatError.NotInstalled -> Banner(stringRes(Res.string.claude_code_not_installed), action) { onRetry?.invoke() }
        is ChatError.StartFailed -> Banner(stringRes(Res.string.claude_code_error_start_failed, LocalAgentName.current, error.message ?: ""), action) { onRetry?.invoke() }
        ChatError.FolderGone -> Banner(stringRes(Res.string.claude_code_folder_gone))
        ChatError.HeldElsewhere -> Banner(stringRes(Res.string.claude_code_open_elsewhere_readonly))
        is ChatError.CloneFailed -> Banner(stringRes(Res.string.claude_code_workspace_clone_failed, error.message ?: ""))
        is ChatError.Exited ->
            Column {
                Banner(stringRes(Res.string.claude_code_error_exited, LocalAgentName.current, error.code?.toString() ?: "?"))
                if (error.stderr.isNotBlank()) Box(Modifier.padding(horizontal = 16.dp)) { CodeBlock(error.stderr, maxHeight = 160) }
            }
    }
}

internal val PICKABLE_MODES = listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN, PermissionMode.AUTO)

/** A new session's mode: Auto where the agent has it (Claude Code), else asking first. */
internal fun defaultModeOf(modes: List<PermissionMode>): PermissionMode = if (PermissionMode.AUTO in modes) PermissionMode.AUTO else PermissionMode.DEFAULT

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

/**
 * [images] as the app's image viewer takes them, with the one at [index] shown first: only those
 * whose file is still on this computer. Null when that one's file is gone.
 */
internal fun sentImagesToView(
    images: List<SentImage>,
    index: Int,
): Pair<ImmutableList<BaseMediaContent>, BaseMediaContent>? {
    val viewable =
        images.map { image ->
            image.file
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { MediaUrlImage(it.toURI().toString(), description = image.name) }
        }
    val shown = viewable.getOrNull(index) ?: return null
    return viewable.filterNotNull().toImmutableList() to shown
}

/** How the agent names the assistant's account tools: `mcp__amethyst-account__…`. */
private val ACCOUNT_TOOLS_PREFIX = "mcp__${AccountToolServer.NAME}__"
private val SCREEN_TOOLS_PREFIX = "mcp__${ScreenToolServer.NAME}__"
