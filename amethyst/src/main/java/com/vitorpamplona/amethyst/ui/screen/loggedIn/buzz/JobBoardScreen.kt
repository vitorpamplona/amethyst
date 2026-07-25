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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.JobState
import com.vitorpamplona.amethyst.commons.model.buzz.JobView
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * The shared **backlog** of one Buzz channel — where a team drives an AI agent together.
 *
 * Every member sees the same board: file a task (kind-43001), upvote to reprioritize (kind-7),
 * and watch the workspace bot work items back through accept → progress → result/error
 * (43002-43006). Jobs are grouped by lifecycle state; the queue is ordered by the group's
 * upvotes. The heavy lifting is the shared
 * [com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator]; this screen renders its
 * [JobView] output and routes the three write actions through [JobBoardViewModel].
 *
 * Merge is deliberately NOT here — a completed job's result is its PR; the merge happens on
 * GitHub, the only human gate left.
 */
@Composable
fun JobBoardScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val me = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: JobBoardViewModel = viewModel(key = "JobBoard-$relayUrl-$channelId")
    viewModel.bind(accountViewModel.account, channelId, relayUrl)

    DisposableEffect(channelId) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }

    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var composing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton("Backlog", nav) },
        floatingActionButton = {
            FloatingActionButton(onClick = { composing = true }, shape = CircleShape) {
                Icon(symbol = MaterialSymbols.Add, contentDescription = "New task")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val running = jobs.filter { it.state == JobState.IN_PROGRESS || it.state == JobState.ACCEPTED }
            val queued =
                jobs
                    .filter { it.state == JobState.REQUESTED }
                    .sortedWith(compareByDescending<JobView> { it.upvotes }.thenBy { it.createdAt })
            val done = jobs.filter { it.state == JobState.COMPLETED }
            val closed = jobs.filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED }

            if (jobs.isEmpty() && !isLoading) {
                EmptyBoard()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    section("In progress", running, me, viewModel)
                    section("Queued", queued, me, viewModel)
                    section("Done", done, me, viewModel)
                    section("Closed", closed, me, viewModel)
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                )
            }
        }
    }

    if (composing) {
        NewTaskDialog(
            onDismiss = { composing = false },
            onFile = { text ->
                viewModel.file(text)
                composing = false
            },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    jobs: List<JobView>,
    me: String,
    viewModel: JobBoardViewModel,
) {
    if (jobs.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = "$title · ${jobs.size}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    items(jobs, key = { it.jobId }) { job ->
        JobCard(job, me, viewModel)
    }
}

@Composable
private fun JobCard(
    job: JobView,
    me: String,
    viewModel: JobBoardViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatePill(job.state)
                UpvoteChip(job.upvotes) { viewModel.upvote(job.jobId) }
            }

            Text(
                text = job.request?.takeIf { it.isNotBlank() } ?: "(no description)",
                style = MaterialTheme.typography.bodyLarge,
            )

            val meta =
                buildString {
                    append("by ${shortKey(job.requester)}")
                    job.agent?.let { append("  ·  agent ${shortKey(it)}") }
                }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (job.state) {
                JobState.COMPLETED ->
                    job.result?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                JobState.FAILED ->
                    job.error?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                JobState.CANCELLED ->
                    job.cancelReason?.let {
                        Text("Cancelled: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else ->
                    job.lastProgress?.takeIf { it.isNotBlank() }?.let {
                        Text("… $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
            }

            // The requester can cancel their own job while it's still open.
            if (!job.isTerminal && job.requester == me) {
                TextButton(onClick = { viewModel.cancel(job.jobId) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun StatePill(state: JobState) {
    val (label, symbol, color) = stateStyle(state)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(symbol = symbol, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UpvoteChip(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(symbol = MaterialSymbols.ThumbUp, contentDescription = "Upvote", modifier = Modifier.size(18.dp))
        Text(text = count.toString(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NewTaskDialog(
    onDismiss: () -> Unit,
    onFile: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Describe the feature or fix") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onFile(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("File") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyBoard() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No tasks yet. Tap + to ask the workspace agent to build or fix something — the whole channel will see it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class StateStyle(
    val label: String,
    val symbol: MaterialSymbol,
    val color: Color,
)

@Composable
private fun stateStyle(state: JobState): StateStyle =
    when (state) {
        JobState.REQUESTED -> StateStyle("Queued", MaterialSymbols.Schedule, MaterialTheme.colorScheme.onSurfaceVariant)
        JobState.ACCEPTED -> StateStyle("Picked up", MaterialSymbols.Bolt, MaterialTheme.colorScheme.primary)
        JobState.IN_PROGRESS -> StateStyle("Working", MaterialSymbols.Bolt, MaterialTheme.colorScheme.primary)
        JobState.COMPLETED -> StateStyle("Done", MaterialSymbols.CheckCircle, MaterialTheme.colorScheme.tertiary)
        JobState.FAILED -> StateStyle("Failed", MaterialSymbols.Error, MaterialTheme.colorScheme.error)
        JobState.CANCELLED -> StateStyle("Cancelled", MaterialSymbols.Cancel, MaterialTheme.colorScheme.onSurfaceVariant)
    }

private fun shortKey(hex: String?): String =
    when {
        hex == null -> "unknown"
        hex.length <= 16 -> hex
        else -> hex.take(8) + "…" + hex.takeLast(4)
    }
