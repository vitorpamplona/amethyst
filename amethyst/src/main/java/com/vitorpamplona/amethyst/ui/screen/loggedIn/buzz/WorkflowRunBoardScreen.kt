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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRun
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * The shared **workflow run board** of one Buzz channel — where a team drives an AI agent under a
 * human-approval gate.
 *
 * Every member sees the same runs: trigger a workflow (kind-46020), watch the runner work it through
 * triggered → step → the **approval gate** (46010), and — if the run named you as approver — grant or
 * deny it right here (46030/46031). A granted run ships and lands as a shipped result (its PR);
 * merge stays on GitHub. Runs awaiting a human are the visual hero, sorted first, because they're the
 * ones blocking. Correlation/state is the shared
 * [com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator]; this screen renders it and
 * routes trigger/approve/deny through [WorkflowRunBoardViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunBoardScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val me = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: WorkflowRunBoardViewModel = viewModel(key = "WorkflowRunBoard-$relayUrl-$channelId")
    viewModel.bind(accountViewModel.account, channelId, relayUrl)

    DisposableEffect(channelId, relayUrl) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }

    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) }

    var composing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton("Workflow runs", nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { composing = true },
                icon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null) },
                text = { Text("New run") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            relay?.let { RelayStatusBar(it, accountViewModel) }

            Box(modifier = Modifier.fillMaxSize()) {
                val groups = remember(runs) { RunGroups.from(runs) }

                if (runs.isEmpty() && !isLoading) {
                    EmptyRunBoard()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    ) {
                        section("Needs your approval", groups.awaiting, RunStyle.GATE, me, accountViewModel, nav, viewModel)
                        section("Working now", groups.active, RunStyle.ACTIVE, me, accountViewModel, nav, viewModel)
                        section("Shipped", groups.done, RunStyle.SHIPPED, me, accountViewModel, nav, viewModel)
                        section("Closed", groups.closed, RunStyle.CLOSED, me, accountViewModel, nav, viewModel)
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                    )
                }
            }
        }
    }

    if (composing) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        NewRunSheet(
            sheetState = sheetState,
            onDismiss = { composing = false },
            onTrigger = { workflowId, task ->
                viewModel.trigger(workflowId, task)
                composing = false
            },
        )
    }
}

private enum class RunStyle { GATE, ACTIVE, SHIPPED, CLOSED }

/** The runs bucketed by lifecycle; awaiting-a-human first (those block the room). */
private class RunGroups(
    val awaiting: List<WorkflowRun>,
    val active: List<WorkflowRun>,
    val done: List<WorkflowRun>,
    val closed: List<WorkflowRun>,
) {
    companion object {
        fun from(runs: List<WorkflowRun>) =
            RunGroups(
                awaiting = runs.filter { it.state == WorkflowRunState.AWAITING_APPROVAL },
                active =
                    runs.filter {
                        it.state == WorkflowRunState.TRIGGERED ||
                            it.state == WorkflowRunState.RUNNING ||
                            it.state == WorkflowRunState.APPROVED
                    },
                done = runs.filter { it.state == WorkflowRunState.COMPLETED },
                closed =
                    runs.filter {
                        it.state == WorkflowRunState.FAILED ||
                            it.state == WorkflowRunState.CANCELLED ||
                            it.state == WorkflowRunState.DENIED
                    },
            )
    }
}

private fun LazyListScope.section(
    title: String,
    runs: List<WorkflowRun>,
    style: RunStyle,
    me: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    viewModel: WorkflowRunBoardViewModel,
) {
    if (runs.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = "$title · ${runs.size}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
    items(runs, key = { it.runId }) { run ->
        RunCard(run, style, me, accountViewModel, nav, viewModel)
    }
}

@Composable
private fun LazyItemScope.RunCard(
    run: WorkflowRun,
    style: RunStyle,
    me: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    viewModel: WorkflowRunBoardViewModel,
) {
    val (container, accent) = styleColors(style)
    Surface(
        color = container,
        shape = RoundedCornerShape(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .animateItem()
                .alpha(if (style == RunStyle.CLOSED) 0.7f else 1f),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(accent))

            Column(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatePill(run.state, accent)
                    TimeAgo(time = run.createdAt, style = TimeAgoStyle.Short)
                }

                Text(
                    text = run.task?.takeIf { it.isNotBlank() } ?: run.workflowId?.let { "Workflow: $it" } ?: "(no description)",
                    style = if (style == RunStyle.GATE || style == RunStyle.ACTIVE) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (style == RunStyle.GATE || style == RunStyle.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Person("by", run.requester, accountViewModel, nav)
                    if (run.state == WorkflowRunState.AWAITING_APPROVAL && run.pendingApprover != null && run.pendingApprover != me) {
                        Person("gate", run.pendingApprover, accountViewModel, nav)
                    }
                }

                when (style) {
                    RunStyle.ACTIVE ->
                        run.lastStep?.takeIf { it.isNotBlank() }?.let { WorkingLine(it, accent) }
                    RunStyle.SHIPPED ->
                        run.result?.takeIf { it.isNotBlank() }?.let { ResultLine(it) }
                    RunStyle.CLOSED -> ClosedLine(run)
                    RunStyle.GATE -> Unit
                }

                if (run.state == WorkflowRunState.AWAITING_APPROVAL && run.pendingApprover == me) {
                    ApprovalActions(
                        onApprove = { viewModel.approve(run.runId) },
                        onDeny = { viewModel.deny(run.runId) },
                    )
                }
            }
        }
    }
}

/** The grant/deny buttons the named approver sees on a paused run. */
@Composable
private fun ApprovalActions(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onDeny,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(symbol = MaterialSymbols.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Deny")
        }
        Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
            Icon(symbol = MaterialSymbols.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Approve")
        }
    }
}

@Composable
private fun ClosedLine(run: WorkflowRun) {
    val text =
        when (run.state) {
            WorkflowRunState.DENIED -> "Denied — work discarded"
            WorkflowRunState.CANCELLED -> "Cancelled"
            else -> run.error?.let { "Failed: $it" } ?: "Failed"
        }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (run.state == WorkflowRunState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The active-work pulse: the current step breathes while the runner works. */
@Composable
private fun WorkingLine(
    text: String,
    accent: Color,
) {
    val pulse = rememberInfiniteTransition(label = "working")
    val a by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "workingAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.alpha(a)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Medium)
    }
}

/** A shipped result — if it carries a URL, offer a prominent "View PR" instead of raw text. */
@Composable
private fun ResultLine(result: String) {
    val url = remember(result) { Regex("https?://\\S+").find(result)?.value }
    if (url != null) {
        val uriHandler = LocalUriHandler.current
        Button(onClick = { uriHandler.openUri(url) }) {
            Icon(symbol = MaterialSymbols.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("View PR")
        }
    } else {
        Text(result, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Person(
    prefix: String,
    hex: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (hex == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(prefix, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UserPicture(userHex = hex, size = Size20dp, accountViewModel = accountViewModel, nav = nav)
        LoadUser(baseUserHex = hex, accountViewModel = accountViewModel) { user ->
            if (user != null) {
                UsernameDisplay(baseUser = user, fontWeight = FontWeight.Medium, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
private fun StatePill(
    state: WorkflowRunState,
    accent: Color,
) {
    val (label, symbol) = pillContent(state)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(symbol = symbol, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewRunSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onTrigger: (String, String) -> Unit,
) {
    var task by remember { mutableStateOf("") }
    var workflowId by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trigger a workflow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "The runner does the work, then pauses for a human to approve before it opens a PR. The whole channel sees the run.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = workflowId,
                onValueChange = { workflowId = it },
                label = { Text("Workflow id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                label = { Text("What should it do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = { onTrigger(workflowId.trim(), task.trim()) },
                enabled = workflowId.isNotBlank() && task.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Trigger run")
            }
        }
    }
}

@Composable
private fun EmptyRunBoard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(symbol = MaterialSymbols.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("No runs yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Tap “New run” to trigger a workflow. The runner does the work and pauses on an approval gate — a human grants it before anything ships.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun styleColors(style: RunStyle): Pair<Color, Color> =
    when (style) {
        RunStyle.GATE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        RunStyle.ACTIVE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        RunStyle.SHIPPED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        RunStyle.CLOSED -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.outline
    }

private fun pillContent(state: WorkflowRunState): Pair<String, MaterialSymbol> =
    when (state) {
        WorkflowRunState.TRIGGERED -> "Queued" to MaterialSymbols.Schedule
        WorkflowRunState.RUNNING -> "Working" to MaterialSymbols.Bolt
        WorkflowRunState.AWAITING_APPROVAL -> "Needs approval" to MaterialSymbols.Gavel
        WorkflowRunState.APPROVED -> "Approved" to MaterialSymbols.CheckCircle
        WorkflowRunState.COMPLETED -> "Shipped" to MaterialSymbols.CheckCircle
        WorkflowRunState.FAILED -> "Failed" to MaterialSymbols.Error
        WorkflowRunState.CANCELLED -> "Cancelled" to MaterialSymbols.Cancel
        WorkflowRunState.DENIED -> "Denied" to MaterialSymbols.Close
    }
