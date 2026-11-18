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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeProposeViewModel
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.GateState
import com.vitorpamplona.amethyst.commons.claudecode.GitWorkInfo
import com.vitorpamplona.amethyst.commons.claudecode.Ngit
import com.vitorpamplona.amethyst.commons.claudecode.ProposalDraft
import com.vitorpamplona.amethyst.commons.claudecode.PushRemote
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalResult
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalUiState
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.claude_code_ask_to_commit
import com.vitorpamplona.amethyst.commons.resources.claude_code_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_check_read_diff
import com.vitorpamplona.amethyst.commons.resources.claude_code_check_tested
import com.vitorpamplona.amethyst.commons.resources.claude_code_close
import com.vitorpamplona.amethyst.commons.resources.claude_code_commit_prompt
import com.vitorpamplona.amethyst.commons.resources.claude_code_create_pr
import com.vitorpamplona.amethyst.commons.resources.claude_code_draft_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_drafting
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_cancelled
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_full_log
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_none
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_passed
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_run
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_running
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_desc_no_grasp
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_description
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_description_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_propose_update_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_new_version
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_ngit
import com.vitorpamplona.amethyst.commons.resources.claude_code_publish_no_repo
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_clone_url
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_no_remotes
import com.vitorpamplona.amethyst.commons.resources.claude_code_push_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_redraft
import com.vitorpamplona.amethyst.commons.resources.claude_code_show_changes
import com.vitorpamplona.amethyst.commons.resources.claude_code_step_check
import com.vitorpamplona.amethyst.commons.resources.claude_code_step_confirm
import com.vitorpamplona.amethyst.commons.resources.claude_code_step_describe
import com.vitorpamplona.amethyst.commons.resources.claude_code_uncommitted_warning
import com.vitorpamplona.amethyst.commons.resources.claude_code_update_pr
import com.vitorpamplona.amethyst.commons.resources.claude_code_write_it_myself
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.ui.components.FormTextField
import com.vitorpamplona.amethyst.commons.ui.components.LabeledField
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.rememberGitProposalTarget
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.rememberGitProposals
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
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
    /** A repository's folder in the chat's workspace, or null for the chat's own folder. */
    folder: String? = null,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodePropose-$key-$folder") { ClaudeCodeProposeViewModel(ClaudeCodeService.shared, key, folder) }
    val info by viewModel.info.collectAsStateWithLifecycle()
    val isUpdate = info?.proposalId != null

    Scaffold(
        topBar = {
            ChatTopBar(
                title = stringRes(if (isUpdate) Res.string.claude_code_update_pr else Res.string.claude_code_create_pr),
                subtitle = info?.let { listOfNotNull(it.repo?.identifier, it.branch).joinToString(" · ") },
                nav = nav,
            ) {
                TextButton(onClick = { nav.nav(Route.ClaudeCodeChanges(key, folder)) }) { Text(stringRes(Res.string.claude_code_changes)) }
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
                ProposeForm(key, folder, current, repoNote, viewModel, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun ProposeForm(
    key: String,
    folder: String?,
    info: GitWorkInfo,
    repoNote: AddressableNote,
    viewModel: ClaudeCodeProposeViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val proposals = rememberGitProposals("ClaudeCode-$key-$folder", accountViewModel)
    val target = rememberGitProposalTarget(repoNote, proposals, accountViewModel)
    val publishState by proposals.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val drafting by viewModel.drafting.collectAsStateWithLifecycle()
    val draftFailed by viewModel.draftFailed.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isUpdate = info.proposalId != null

    var readDiff by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    val ngitAccount by viewModel.ngitAccount.collectAsStateWithLifecycle()
    var ngitError by remember { mutableStateOf<String?>(null) }

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
    val gateOk = gate is GateState.Passed || gate is GateState.None
    val title = draft?.title.orEmpty()
    val pushReady = !noGrasp || (remote != null && cloneUrl.isNotBlank())
    val commitPrompt = stringRes(Res.string.claude_code_commit_prompt)

    // The steps scroll; publishing stays at the bottom, with what still blocks it.
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Gutter),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
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
                // Left out of the proposal unless committed: say so, with the two ways forward.
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    // The buttons at the bottom bring their own room: less padding there and on the right.
                    Column(Modifier.padding(start = CardPadding, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(
                            pluralStringRes(Res.plurals.claude_code_uncommitted_warning, info.uncommitted, info.uncommitted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row {
                            TextButton(onClick = { nav.nav(Route.ClaudeCodeChanges(key, folder)) }) { Text(stringRes(Res.string.claude_code_show_changes), color = MaterialTheme.colorScheme.onErrorContainer) }
                            TextButton(onClick = {
                                viewModel.chat?.send(commitPrompt)
                                nav.popBack()
                            }) { Text(stringRes(Res.string.claude_code_ask_to_commit), color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
            }

            var step = 0
            if (!isUpdate) {
                ProposeStep(
                    ++step,
                    stringRes(Res.string.claude_code_step_describe),
                    done = title.isNotBlank(),
                    trailing = {
                        DraftMenu(
                            agent = viewModel.agentName,
                            drafting = drafting,
                            onRedraft = viewModel::draftWithAgent,
                            onStop = viewModel::stopDrafting,
                        )
                    },
                ) {
                    val value = draft ?: ProposalDraft("", "")
                    if (draftFailed) {
                        Text(stringRes(Res.string.claude_code_draft_failed, viewModel.agentName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    LabeledField(Res.string.claude_code_propose_title) {
                        FormTextField(value = value.title, onValueChange = { viewModel.setDraft(value.copy(title = it)) })
                    }
                    LabeledField(Res.string.claude_code_propose_description) {
                        FormTextField(
                            value = value.description,
                            onValueChange = { viewModel.setDraft(value.copy(description = it)) },
                            placeholder = stringRes(Res.string.claude_code_propose_description_hint),
                            singleLine = false,
                            minLines = 6,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 360.dp),
                        )
                    }
                }
            }

            if (noGrasp) {
                ProposeStep(++step, stringRes(Res.string.claude_code_push_title), done = pushReady) {
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
            }
            val logFile by viewModel.gateLogFile.collectAsStateWithLifecycle()
            ProposeStep(++step, stringRes(Res.string.claude_code_step_check), done = gateOk) {
                GateSection(
                    gate = gate,
                    logFile = logFile,
                    fullLog = viewModel::fullGateLog,
                    onRun = { viewModel.runGate() },
                    onCancel = { viewModel.cancelGate() },
                )
            }

            ProposeStep(++step, stringRes(Res.string.claude_code_step_confirm), done = readDiff && tested) {
                // In a card like the checks above, each whole row a box to tick.
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        CheckRow(stringRes(Res.string.claude_code_check_read_diff), readDiff, { readDiff = it })
                        CheckRow(stringRes(Res.string.claude_code_check_tested), tested, { tested = it })
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(InlineGap)) {
            (done?.result as? GitProposalResult.Failed)?.let {
                Text(stringRes(Res.string.claude_code_publish_failed, it.reason), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (publishState is GitProposalUiState.Working || preparing) LinearProgressIndicator(Modifier.fillMaxWidth())

            pushError?.let { Text(stringRes(Res.string.claude_code_push_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

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

            // The same branch through the user's own ngit, signed by their ngit account.
            ngitAccount?.let { account ->
                val canSend = readDiff && tested && gateOk && target != null && (isUpdate || title.isNotBlank()) && !preparing && publishState !is GitProposalUiState.Working
                OutlinedButton(
                    enabled = canSend,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val t = target ?: return@OutlinedButton
                        preparing = true
                        ngitError = null
                        scope.launch {
                            val branch = t.defaultBranch
                            val head = branch?.let { t.signedState?.commitFor(it) }
                            val tip = viewModel.localTip(head, t.targets.grasp.map { it.cloneUrl } + t.targets.others, branch)
                            val result = viewModel.sendWithNgit(repoNote.toNAddr(), tip?.mergeBase, title.trim(), draft?.description.orEmpty().trim())
                            preparing = false
                            when (result) {
                                is Ngit.SendResult.Failed -> ngitError = result.reason
                                is Ngit.SendResult.Published -> {
                                    val id = info.proposalId ?: result.eventId
                                    result.eventId?.let { viewModel.published(it) }
                                    nav.popBack()
                                    if (id != null) nav.nav(Route.Note(id))
                                }
                            }
                        }
                    },
                ) {
                    Text(stringRes(Res.string.claude_code_publish_ngit, ngitAccountName(account, accountViewModel)))
                }
                ngitError?.let { Text(stringRes(Res.string.claude_code_publish_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** One numbered step of publishing; its number turns into a check once it is done. */
@Composable
private fun ProposeStep(
    number: Int,
    title: String,
    done: Boolean,
    /** At the end of the step's title row: its status and menu. */
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // A step starts a section: more room above it than between its parts.
    Column(Modifier.padding(top = SectionGap - GroupGap), verticalArrangement = Arrangement.spacedBy(PartGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                } else {
                    Text(number.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(InlineGap))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            trailing()
        }
        // Under the title, past the 24 dp mark and its gap.
        Column(Modifier.padding(start = 24.dp + InlineGap), verticalArrangement = Arrangement.spacedBy(PartGap)) { content() }
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
    // A darker card than the others: its field is filled with their tone.
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringRes(Res.string.claude_code_push_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            if (remotes.isEmpty()) {
                Text(stringRes(Res.string.claude_code_push_no_remotes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    remotes.forEach { r ->
                        FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(r.name) })
                    }
                }
                LabeledField(Res.string.claude_code_push_clone_url) {
                    FormTextField(value = cloneUrl, onValueChange = onCloneUrl, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                }
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
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * Who ngit signs as, by the name Amethyst knows for that key (its profile, loaded or cached), so
 * the user sees whether it is the account they are logged in with; ngit's own name, else the npub.
 */
@Composable
private fun ngitAccountName(
    account: Ngit.Account,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(account.npub) { decodePublicKeyAsHexOrNull(account.npub)?.let { LocalCache.checkGetOrCreateUser(it) } }
    val fallback = account.name.ifBlank { account.npub.take(16) + "…" }
    if (user == null) return fallback
    val info by observeUserInfo(user, accountViewModel)
    return info?.info?.bestName()?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * The describe step's corner: a note while the agent writes the title and description, and the
 * menu to ask it again or to stop it and write them by hand.
 */
@Composable
private fun RowScope.DraftMenu(
    agent: String,
    drafting: Boolean,
    onRedraft: () -> Unit,
    onStop: () -> Unit,
) {
    if (drafting) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(InlineGap))
        Text(stringRes(Res.string.claude_code_drafting, agent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.more_options)) }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection {
                if (drafting) {
                    MenuRow(stringRes(Res.string.claude_code_write_it_myself)) {
                        open = false
                        onStop()
                    }
                } else {
                    MenuRow(stringRes(Res.string.claude_code_redraft, agent)) {
                        open = false
                        onRedraft()
                    }
                }
            }
        }
    }
}
