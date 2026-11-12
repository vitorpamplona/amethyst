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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ChatError
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.ChatStatus
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChatState
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeChatViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.NoticeKind
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow
import com.vitorpamplona.amethyst.commons.resources.claude_code_allow_always
import com.vitorpamplona.amethyst.commons.resources.claude_code_app_running
import com.vitorpamplona.amethyst.commons.resources.claude_code_cost
import com.vitorpamplona.amethyst.commons.resources.claude_code_deny
import com.vitorpamplona.amethyst.commons.resources.claude_code_end_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_exited
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_start_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_loading_history
import com.vitorpamplona.amethyst.commons.resources.claude_code_message_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_accept_edits
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_auto
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_default
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_plan
import com.vitorpamplona.amethyst.commons.resources.claude_code_new_session
import com.vitorpamplona.amethyst.commons.resources.claude_code_not_installed
import com.vitorpamplona.amethyst.commons.resources.claude_code_notice_interrupted
import com.vitorpamplona.amethyst.commons.resources.claude_code_notice_process_ended
import com.vitorpamplona.amethyst.commons.resources.claude_code_notice_turn_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_open_elsewhere_warning
import com.vitorpamplona.amethyst.commons.resources.claude_code_permission_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_resume_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_run_app
import com.vitorpamplona.amethyst.commons.resources.claude_code_run_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_send
import com.vitorpamplona.amethyst.commons.resources.claude_code_session_missing
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_ended
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_needs_approval
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_ready
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_starting
import com.vitorpamplona.amethyst.commons.resources.claude_code_status_working
import com.vitorpamplona.amethyst.commons.resources.claude_code_stop
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_input
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_output
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_running
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.protocol.PermissionRequest

/** One Claude Code session: its history, the live turn, and the approvals it waits on. */
@Composable
fun ClaudeCodeChatScreen(
    key: String,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodeChat-$key") { ClaudeCodeChatViewModel(ClaudeCodeService.shared, key) }
    val chat = viewModel.chat
    if (chat == null) {
        Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.claude_code_new_session), nav) }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringRes(Res.string.claude_code_session_missing))
            }
        }
        return
    }
    val state by chat.state.collectAsStateWithLifecycle()
    val runningApp by viewModel.runningApp.collectAsStateWithLifecycle()
    val runError by viewModel.runError.collectAsStateWithLifecycle()
    val rows by ClaudeCodeService.shared.rows.collectAsStateWithLifecycle()
    val openElsewhere = rows.any { it.sessionId != null && it.sessionId == state.sessionId && it.openElsewhere != null }
    val canRunApp = remember(state.cwd) { viewModel.canRunApp() }

    Scaffold(
        topBar = {
            TopBarWithBackButton(state.title?.takeIf { it.isNotBlank() } ?: firstPrompt(state) ?: stringRes(Res.string.claude_code_new_session), nav) {
                if (canRunApp) {
                    IconButton(enabled = !runningApp, onClick = { viewModel.runApp() }) {
                        Icon(MaterialSymbols.PlayArrow, contentDescription = stringRes(Res.string.claude_code_run_app))
                    }
                }
                ChatMenu(onEnd = { viewModel.endSession() }, enabled = state.status != ChatStatus.NOT_STARTED && state.status != ChatStatus.ENDED)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Header(state) { viewModel.setPermissionMode(it) }
            if (openElsewhere) Banner(stringRes(Res.string.claude_code_open_elsewhere_warning))
            state.error?.let { ErrorBanner(it) }
            if (runningApp) Note(stringRes(Res.string.claude_code_app_running))
            runError?.let { Banner(stringRes(Res.string.claude_code_run_failed, it)) }

            Messages(state, Modifier.weight(1f))

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
        }
    }
}

private fun firstPrompt(state: ClaudeChatState): String? =
    (state.items.firstOrNull { it is ChatItem.UserText } as? ChatItem.UserText)
        ?.text
        ?.lineSequence()
        ?.first()

@Composable
private fun Header(
    state: ClaudeChatState,
    onMode: (PermissionMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            listOfNotNull(state.cwd, state.model).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        state.costUsd?.let {
            Text(stringRes(Res.string.claude_code_cost, "%.2f".format(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(state.status)
    }
    Box(Modifier.padding(horizontal = 12.dp)) {
        PermissionModePicker(state.permissionMode, onPick = onMode)
    }
}

@Composable
private fun Messages(
    state: ClaudeChatState,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val count = state.items.size + (if (state.streamingText.isNotEmpty()) 1 else 0)
    LaunchedEffect(count, state.streamingText.length / 200) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.historyLoaded) {
            item { Note(stringRes(Res.string.claude_code_loading_history)) }
        }
        items(state.items, key = { it.id }) { item ->
            when (item) {
                is ChatItem.UserText -> UserBubble(item.text)
                is ChatItem.AssistantText -> AssistantText(item.text)
                is ChatItem.ToolCall -> ToolCallCard(item)
                is ChatItem.Notice -> NoticeRow(item)
            }
        }
        if (state.streamingText.isNotEmpty()) {
            item(key = "streaming") { AssistantText(state.streamingText) }
        } else if (state.status == ChatStatus.WORKING || state.status == ChatStatus.STARTING) {
            item(key = "working") { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        }
        if (state.status == ChatStatus.NOT_STARTED && state.historyLoaded && state.items.isNotEmpty()) {
            item(key = "resume") { Note(stringRes(Res.string.claude_code_resume_hint)) }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            SelectionContainer {
                Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AssistantText(text: String) {
    SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToolCallCard(call: ChatItem.ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    val border = if (call.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    !call.isDone -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    call.isError -> Icon(MaterialSymbols.Error, contentDescription = null, tint = border, modifier = Modifier.size(16.dp))
                    else -> Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(call.name, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    call.summary ?: if (call.isDone) "" else stringRes(Res.string.claude_code_tool_running),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            if (expanded) {
                Label(stringRes(Res.string.claude_code_tool_input))
                Code(call.input)
                call.result?.let {
                    Label(stringRes(Res.string.claude_code_tool_output))
                    Code(it.take(RESULT_SHOWN), if (call.isError) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Code(
    text: String,
    color: Color = Color.Unspecified,
) {
    SelectionContainer {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .padding(6.dp),
        )
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
    Note(text)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun PermissionCard(
    request: PermissionRequest,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringRes(Res.string.claude_code_permission_title, request.displayName ?: request.toolName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Code(request.summary)
            request.decisionReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (request.defaultToNo) {
                    Button(onClick = onDeny) { Text(stringRes(Res.string.claude_code_deny)) }
                    OutlinedButton(onClick = onAllow) { Text(stringRes(Res.string.claude_code_allow)) }
                } else {
                    Button(onClick = onAllow) { Text(stringRes(Res.string.claude_code_allow)) }
                    OutlinedButton(onClick = onDeny) { Text(stringRes(Res.string.claude_code_deny)) }
                }
                if (!request.suppressAlwaysAllow && request.suggestions.isNotEmpty()) {
                    FilledTonalButton(onClick = onAllowAlways) { Text(stringRes(Res.string.claude_code_allow_always)) }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringRes(Res.string.claude_code_message_hint)) },
            maxLines = 8,
            modifier =
                Modifier.weight(1f).onPreviewKeyEvent { event ->
                    // Enter sends; Shift+Enter is a new line.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                        send()
                        true
                    } else {
                        false
                    }
                },
        )
        Spacer(Modifier.width(8.dp))
        if (busy) {
            OutlinedButton(onClick = onStop) {
                Icon(MaterialSymbols.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringRes(Res.string.claude_code_stop))
            }
            Spacer(Modifier.width(8.dp))
        }
        Button(enabled = text.isNotBlank(), onClick = send) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringRes(Res.string.claude_code_send))
        }
    }
}

@Composable
private fun ChatMenu(
    enabled: Boolean,
    onEnd: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(MaterialSymbols.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.claude_code_end_session)) },
                enabled = enabled,
                onClick = {
                    open = false
                    onEnd()
                },
            )
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
                if (error.stderr.isNotBlank()) Box(Modifier.padding(horizontal = 16.dp)) { Code(error.stderr) }
            }
    }
}

/** The four modes a user would pick (bypass is left to the CLI's own flags). */
@Composable
internal fun PermissionModePicker(
    mode: PermissionMode,
    onPick: (PermissionMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PICKABLE_MODES.forEach { option ->
            FilterChip(
                selected = option == mode,
                onClick = { onPick(option) },
                label = { Text(modeLabel(option)) },
            )
        }
    }
}

private val PICKABLE_MODES = listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN, PermissionMode.AUTO)

@Composable
private fun modeLabel(mode: PermissionMode): String =
    when (mode) {
        PermissionMode.ACCEPT_EDITS -> stringRes(Res.string.claude_code_mode_accept_edits)
        PermissionMode.PLAN -> stringRes(Res.string.claude_code_mode_plan)
        PermissionMode.AUTO -> stringRes(Res.string.claude_code_mode_auto)
        else -> stringRes(Res.string.claude_code_mode_default)
    }

@Composable
internal fun StatusPill(status: ChatStatus) {
    val (label, color) =
        when (status) {
            ChatStatus.NOT_STARTED -> return
            ChatStatus.STARTING -> stringRes(Res.string.claude_code_status_starting) to MaterialTheme.colorScheme.surfaceVariant
            ChatStatus.WORKING -> stringRes(Res.string.claude_code_status_working) to MaterialTheme.colorScheme.primaryContainer
            ChatStatus.NEEDS_APPROVAL -> stringRes(Res.string.claude_code_status_needs_approval) to MaterialTheme.colorScheme.errorContainer
            ChatStatus.READY -> stringRes(Res.string.claude_code_status_ready) to MaterialTheme.colorScheme.secondaryContainer
            ChatStatus.ENDED -> stringRes(Res.string.claude_code_status_ended) to MaterialTheme.colorScheme.surfaceVariant
        }
    Pill(label, color)
}

@Composable
internal fun Pill(
    text: String,
    color: Color,
) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

private const val RESULT_SHOWN = 20_000
