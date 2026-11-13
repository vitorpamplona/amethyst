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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeProposeViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.GateState
import com.vitorpamplona.amethyst.commons.claudecode.GitWorkInfo
import com.vitorpamplona.amethyst.commons.claudecode.ProposalDraft
import com.vitorpamplona.amethyst.commons.claudecode.PushRemote
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalResult
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalUiState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_check_read_diff
import com.vitorpamplona.amethyst.commons.resources.claude_code_check_tested
import com.vitorpamplona.amethyst.commons.resources.claude_code_close
import com.vitorpamplona.amethyst.commons.resources.claude_code_draft_with_claude
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_cancelled
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_full_log
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_none
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_passed
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_run
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_running
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_desc_no_grasp
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_description
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_update_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_new_version
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_no_repo
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_clone_url
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_no_remotes
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_uncommitted_warning
import com.vitorpamplona.amethyst.commons.resources.claude_code_update_proposal
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.rememberGitProposalTarget
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.rememberGitProposals
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.coroutines.launch
import java.io.File

/**
 * Publishes a session's branch as a NIP-34 proposal, signed by the user: the step an agent must
 * never take itself (CONTRIBUTING-WITH-AI.md). The user confirms they read the diff and tried the
 * change, the repository's build gate runs, and the commits go to the GRASP servers with the event,
 * as ngit sends them. A branch already published becomes a new version of its proposal.
 */
@Composable
fun ClaudeCodeProposeScreen(
    key: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodePropose-$key") { ClaudeCodeProposeViewModel(ClaudeCodeService.shared, key) }
    val info by viewModel.info.collectAsStateWithLifecycle()
    val isUpdate = info?.proposalId != null

    Scaffold(
        topBar = {
            ChatTopBar(
                title = stringRes(if (isUpdate) Res.string.claude_code_update_proposal else Res.string.claude_code_propose),
                subtitle = info?.let { listOfNotNull(it.repo?.identifier, it.branch).joinToString(" · ") },
                nav = nav,
            ) {
                TextButton(onClick = { nav.nav(Route.ClaudeCodeChanges(key)) }) { Text(stringRes(Res.string.claude_code_changes)) }
            }
        },
    ) { padding ->
        val current = info
        val repo = current?.repo
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (current == null) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            } else if (repo == null) {
                Text(stringRes(Res.string.claude_code_publish_no_repo), color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(32.dp))
            } else {
                val repoNote = remember(repo) { LocalCache.getOrCreateAddressableNote(Address(repo.kind, repo.pubKeyHex, repo.identifier)) }
                ProposeForm(key, current, repoNote, viewModel, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun ProposeForm(
    key: String,
    info: GitWorkInfo,
    repoNote: AddressableNote,
    viewModel: ClaudeCodeProposeViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val proposals = rememberGitProposals("ClaudeCode-$key", accountViewModel)
    val target = rememberGitProposalTarget(repoNote, proposals, accountViewModel)
    val publishState by proposals.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val drafting by viewModel.drafting.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isUpdate = info.proposalId != null

    var readDiff by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }

    val done = publishState as? GitProposalUiState.Done
    (done?.result as? GitProposalResult.Published)?.let { published ->
        LaunchedEffect(published.event.id) {
            viewModel.published(published.event.id)
            proposals.reset()
            nav.popBack()
            nav.nav(Route.Note(info.proposalId ?: published.event.id))
        }
    }

    // A repository with no GRASP server can't take commits with the event: the branch goes to
    // a host the user picks first, and the proposal points at it.
    val noGrasp = target != null && target.targets.grasp.isEmpty()
    val remotes by viewModel.remotes.collectAsStateWithLifecycle()
    var remote by remember { mutableStateOf<PushRemote?>(null) }
    var cloneUrl by remember { mutableStateOf("") }
    var pushError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(noGrasp) { if (noGrasp) viewModel.loadRemotes() }
    LaunchedEffect(remotes) {
        if (remote == null) {
            remotes.firstOrNull { it.name == "origin" }?.let {
                remote = it
                cloneUrl = it.cloneUrl
            } ?: remotes.firstOrNull()?.let {
                remote = it
                cloneUrl = it.cloneUrl
            }
        }
    }
    Column(
        Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringRes(
                when {
                    isUpdate -> Res.string.claude_code_propose_update_desc
                    noGrasp -> Res.string.claude_code_propose_desc_no_grasp
                    else -> Res.string.claude_code_propose_desc
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
        )

        CommitList(info)
        if (info.uncommitted > 0) {
            Text(
                pluralStringRes(Res.plurals.claude_code_uncommitted_warning, info.uncommitted, info.uncommitted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!isUpdate) {
            val value = draft ?: ProposalDraft("", "")
            OutlinedTextField(
                value = value.title,
                onValueChange = { viewModel.setDraft(value.copy(title = it)) },
                label = { Text(stringRes(Res.string.claude_code_propose_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = value.description,
                onValueChange = { viewModel.setDraft(value.copy(description = it)) },
                label = { Text(stringRes(Res.string.claude_code_propose_description)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !drafting, onClick = { viewModel.draftWithClaude() }) {
                    if (drafting) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringRes(Res.string.claude_code_draft_with_claude))
                }
            }
        }

        if (noGrasp) {
            PushSection(
                remotes = remotes,
                selected = remote,
                cloneUrl = cloneUrl,
                onSelect = {
                    remote = it
                    cloneUrl = it.cloneUrl
                },
                onCloneUrl = { cloneUrl = it },
            )
        }
        val logFile by viewModel.gateLogFile.collectAsStateWithLifecycle()
        GateSection(
            gate = gate,
            logFile = logFile,
            fullLog = viewModel::fullGateLog,
            onRun = { viewModel.runGate() },
            onCancel = { viewModel.cancelGate() },
        )

        Column {
            CheckRow(readDiff, stringRes(Res.string.claude_code_check_read_diff)) { readDiff = it }
            CheckRow(tested, stringRes(Res.string.claude_code_check_tested)) { tested = it }
        }

        (done?.result as? GitProposalResult.Failed)?.let {
            Text(stringRes(Res.string.claude_code_publish_failed, it.reason), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (publishState is GitProposalUiState.Working || preparing) LinearProgressIndicator(Modifier.fillMaxWidth())

        pushError?.let { Text(stringRes(Res.string.claude_code_push_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        val gateOk = gate is GateState.Passed || gate is GateState.None
        val title = draft?.title.orEmpty()
        val pushReady = !noGrasp || (remote != null && cloneUrl.isNotBlank())
        val ready = readDiff && tested && gateOk && pushReady && target != null && (isUpdate || title.isNotBlank()) && publishState !is GitProposalUiState.Working && !preparing
        Button(
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val t = target ?: return@Button
                preparing = true
                pushError = null
                scope.launch {
                    val prNote = info.proposalId?.let { LocalCache.getOrCreateNote(it) }
                    if (noGrasp) {
                        val pushTo = remote
                        val branchName = info.branch
                        val tip = viewModel.localTip(null, emptyList(), null)
                        if (pushTo == null || branchName == null || tip == null) {
                            preparing = false
                            return@launch
                        }
                        val error = viewModel.pushBranch(pushTo.name, branchName)
                        preparing = false
                        if (error != null) {
                            pushError = error
                            return@launch
                        }
                        if (prNote != null) {
                            proposals.updatePullRequest(prNote, t, cloneUrl.trim(), tip.tip)
                        } else {
                            proposals.openPullRequest(t, cloneUrl.trim(), tip.tip, branchName, null, title.trim(), draft?.description.orEmpty().trim(), emptyList())
                        }
                        return@launch
                    }
                    val branch = t.defaultBranch
                    val head = branch?.let { t.signedState?.commitFor(it) }
                    val tip = viewModel.localTip(head, t.targets.grasp.map { it.cloneUrl } + t.targets.others, branch)
                    preparing = false
                    if (tip == null) return@launch
                    proposals.publishFromLocal(
                        target = t,
                        pullRequest = prNote,
                        objects = { viewModel.objects(tip) },
                        tip = tip.tip,
                        mergeBase = tip.mergeBase,
                        sourceBranch = info.branch,
                        title = title.trim(),
                        description = draft?.description.orEmpty().trim(),
                    )
                }
            },
        ) {
            Text(stringRes(if (isUpdate) Res.string.claude_code_publish_new_version else Res.string.claude_code_publish))
        }
    }
}

/** Where the branch goes for a repository with no GRASP server: one of the checkout's remotes. */
@Composable
private fun PushSection(
    remotes: List<PushRemote>,
    selected: PushRemote?,
    cloneUrl: String,
    onSelect: (PushRemote) -> Unit,
    onCloneUrl: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.claude_code_push_title), style = MaterialTheme.typography.titleSmall)
            Text(stringRes(Res.string.claude_code_push_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            if (remotes.isEmpty()) {
                Text(stringRes(Res.string.claude_code_push_no_remotes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    remotes.forEach { r ->
                        FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(r.name) })
                    }
                }
                OutlinedTextField(
                    value = cloneUrl,
                    onValueChange = onCloneUrl,
                    label = { Text(stringRes(Res.string.claude_code_push_clone_url)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GateSection(
    gate: GateState,
    logFile: File?,
    fullLog: () -> String,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    var showLog by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) =
                    when (gate) {
                        is GateState.Passed -> MaterialSymbols.Check to AddedColor
                        is GateState.Failed -> MaterialSymbols.Error to MaterialTheme.colorScheme.error
                        else -> MaterialSymbols.Security to MaterialTheme.colorScheme.grayText
                    }
                if (gate is GateState.Running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringRes(Res.string.claude_code_gate), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringRes(
                            when (gate) {
                                GateState.None -> Res.string.claude_code_gate_none
                                GateState.NotRun -> Res.string.claude_code_gate_desc
                                is GateState.Running -> Res.string.claude_code_gate_running
                                is GateState.Passed -> Res.string.claude_code_gate_passed
                                is GateState.Failed -> Res.string.claude_code_gate_failed
                                is GateState.Cancelled -> Res.string.claude_code_gate_cancelled
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                when (gate) {
                    GateState.None -> {}
                    is GateState.Running -> OutlinedButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
                    else -> OutlinedButton(onClick = onRun) { Text(stringRes(Res.string.claude_code_gate_run)) }
                }
            }
            val tail =
                when (gate) {
                    is GateState.Running -> gate.tail
                    is GateState.Failed -> gate.tail
                    is GateState.Cancelled -> gate.tail
                    else -> ""
                }
            if (tail.isNotBlank()) CodeBlock(tail.lines().takeLast(GATE_LINES_SHOWN).joinToString("\n"), maxHeight = 220)
            if (logFile != null && gate !is GateState.None && gate !is GateState.NotRun) {
                TextButton(onClick = { showLog = true }) { Text(stringRes(Res.string.claude_code_gate_full_log)) }
            }
        }
    }
    if (showLog && logFile != null) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(stringRes(Res.string.claude_code_gate)) },
            text = {
                Column(Modifier.widthIn(min = 560.dp, max = 1000.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer {
                        Text(logFile.absolutePath, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                    }
                    // The newest part: a whole Gradle run is too long to lay out as one text.
                    CodeBlock(remember(gate) { fullLog().takeLast(FULL_LOG_SHOWN) }, maxHeight = 560)
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text(stringRes(Res.string.claude_code_close)) } },
        )
    }
}

private const val GATE_LINES_SHOWN = 12
private const val FULL_LOG_SHOWN = 200_000

@Composable
private fun CheckRow(
    checked: Boolean,
    text: String,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
