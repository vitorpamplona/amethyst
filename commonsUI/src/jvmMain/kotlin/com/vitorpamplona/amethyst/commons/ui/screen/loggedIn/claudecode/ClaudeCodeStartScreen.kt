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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.CheckoutState
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeStartViewModel
import com.vitorpamplona.amethyst.commons.claudecode.NostrRepoRef
import com.vitorpamplona.amethyst.commons.claudecode.NostrWorkTarget
import com.vitorpamplona.amethyst.commons.claudecode.NostrWorkspaces
import com.vitorpamplona.amethyst.commons.claudecode.StartError
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitPullRequestUpdates
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitSignedState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_branch
import com.vitorpamplona.amethyst.commons.resources.claude_code_change_folder
import com.vitorpamplona.amethyst.commons.resources.claude_code_checkout
import com.vitorpamplona.amethyst.commons.resources.claude_code_checkout_looking
import com.vitorpamplona.amethyst.commons.resources.claude_code_checkout_missing
import com.vitorpamplona.amethyst.commons.resources.claude_code_clone_here
import com.vitorpamplona.amethyst.commons.resources.claude_code_cloning
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_not_a_repository
import com.vitorpamplona.amethyst.commons.resources.claude_code_error_start_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_first_message
import com.vitorpamplona.amethyst.commons.resources.claude_code_folder
import com.vitorpamplona.amethyst.commons.resources.claude_code_permissions
import com.vitorpamplona.amethyst.commons.resources.claude_code_prompt_issue
import com.vitorpamplona.amethyst.commons.resources.claude_code_prompt_proposal
import com.vitorpamplona.amethyst.commons.resources.claude_code_start
import com.vitorpamplona.amethyst.commons.resources.claude_code_start_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_start_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_use_folder
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import java.io.File

/**
 * Starts a Claude Code session on a NIP-34 repository: in a worktree of the user's checkout, on a
 * `pr/feat/...` branch made from the repository's current main, or from a proposal's latest
 * version to continue it. What it works on (an issue, a proposal) becomes its first message.
 */
@Composable
fun ClaudeCodeStartScreen(
    route: Route.ClaudeCodeStart,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "ClaudeCodeStart") { ClaudeCodeStartViewModel(ClaudeCodeService.shared) }
    val address = remember(route) { Address(route.kind, route.pubKeyHex, route.dTag) }
    val repoNote = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
    val repoEvent by observeNoteEvent<GitRepositoryEvent>(repoNote, accountViewModel)
    LoadGitRepositoryAnnouncements(remember(address) { listOf(address) })
    val signedState = observeGitSignedState(address, accountViewModel)

    val itemId = route.proposalId ?: route.issueId
    itemId?.let { ThreadFilterAssemblerSubscription(it, accountViewModel) }
    val itemNote = remember(itemId) { itemId?.let { LocalCache.getOrCreateNote(it) } }
    val issue = itemNote?.takeIf { route.issueId != null }?.let { observeNoteEvent<GitIssueEvent>(it, accountViewModel).value }
    val proposal = itemNote?.takeIf { route.proposalId != null }?.let { observeNoteEvent<GitPullRequestEvent>(it, accountViewModel).value }
    val latestUpdate = itemNote?.takeIf { route.proposalId != null }?.let { observeGitPullRequestUpdates(it).lastOrNull() }

    val repo = remember(address) { NostrRepoRef(address.pubKeyHex, address.dTag, address.kind) }
    val repoName = repoEvent?.name() ?: address.dTag
    val cloneUrls = repoEvent?.clones().orEmpty()
    val defaultBranch = signedState?.headBranch
    val base = "nostr/${defaultBranch ?: "main"}"

    LaunchedEffect(repo, cloneUrls) { viewModel.find(repo, cloneUrls) }

    val checkout by viewModel.checkout.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val starting by viewModel.starting.collectAsStateWithLifecycle()

    val itemTitle = proposal?.subject() ?: issue?.subject()
    var branch by remember(itemTitle, proposal) {
        mutableStateOf(
            proposal?.branchName()?.let { if (it.startsWith("pr/")) it else "pr/$it" } ?: NostrWorkspaces.branchFor(itemTitle),
        )
    }
    var mode by remember { mutableStateOf(PermissionMode.DEFAULT) }

    val issuePrompt =
        issue?.let {
            stringRes(Res.string.claude_code_prompt_issue, repoName, it.subject() ?: "", it.content.trim(), branch, base)
        }
    val proposalPrompt =
        proposal?.let {
            stringRes(Res.string.claude_code_prompt_proposal, it.subject() ?: "", repoName, it.content.trim(), base)
        }
    // An issue or proposal with no description leaves an empty paragraph in the template.
    var prompt by remember(issuePrompt, proposalPrompt) { mutableStateOf((issuePrompt ?: proposalPrompt ?: "").replace(Regex("\n{3,}"), "\n\n")) }

    Scaffold(
        topBar = { ChatTopBar(stringRes(Res.string.claude_code_start_title), repoName, nav) {} },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(stringRes(Res.string.claude_code_start_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText)

                itemTitle?.let {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (proposal != null) MaterialSymbols.AltRoute else MaterialSymbols.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(it, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                CheckoutSection(
                    state = checkout,
                    repoName = repoName,
                    onUse = { viewModel.useFolder(repo, it) },
                    onClone = { viewModel.clone(repo, cloneUrls, it) },
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringRes(Res.string.claude_code_branch)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringRes(Res.string.claude_code_permissions), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PICKABLE_MODES.forEach { option ->
                            FilterChip(selected = option == mode, onClick = { mode = option }, label = { Text(modeLabel(option)) })
                        }
                    }
                    Text(modeDescription(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringRes(Res.string.claude_code_first_message)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )

                error?.let {
                    Text(
                        when (it) {
                            StartError.NotARepository -> stringRes(Res.string.claude_code_error_not_a_repository)
                            is StartError.Failed -> stringRes(Res.string.claude_code_error_start_failed, it.message ?: "")
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    enabled = checkout is CheckoutState.Found && branch.isNotBlank() && !starting && (route.proposalId == null || proposal != null),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val proposalTip = latestUpdate?.currentCommit() ?: proposal?.currentCommit()
                        val proposalClones = (latestUpdate?.cloneUrls().orEmpty() + proposal?.cloneUrls().orEmpty()).filter { it.isNotBlank() }.distinct()
                        val target =
                            NostrWorkTarget(
                                repo = repo,
                                cloneUrls = cloneUrls,
                                defaultBranch = defaultBranch,
                                issueId = route.issueId,
                                proposalId = route.proposalId,
                                proposalTip = proposalTip,
                                proposalCloneUrls = proposalClones,
                                proposalBranch = proposal?.branchName(),
                            )
                        viewModel.start(target, branch, mode, prompt) { key ->
                            nav.popBack()
                            nav.nav(Route.ClaudeCodeChat(key))
                        }
                    },
                ) {
                    if (starting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringRes(Res.string.claude_code_start))
                }
            }
        }
    }
}

@Composable
private fun CheckoutSection(
    state: CheckoutState,
    repoName: String,
    onUse: (String) -> Unit,
    onClone: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var path by remember(repoName) { mutableStateOf(File(System.getProperty("user.home"), repoName).absolutePath) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(Res.string.claude_code_checkout), style = MaterialTheme.typography.labelLarge)
        when (state) {
            CheckoutState.Looking -> Note(stringRes(Res.string.claude_code_checkout_looking))
            CheckoutState.Cloning -> Note(stringRes(Res.string.claude_code_cloning))
            is CheckoutState.Found ->
                if (!editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbols.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.dir.absolutePath, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            path = state.dir.absolutePath
                            editing = true
                        }) { Text(stringRes(Res.string.claude_code_change_folder)) }
                    }
                }
            CheckoutState.Missing -> Note(stringRes(Res.string.claude_code_checkout_missing, repoName))
        }
        if (state is CheckoutState.Missing || editing) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text(stringRes(Res.string.claude_code_folder)) },
                singleLine = true,
                leadingIcon = { Icon(MaterialSymbols.Folder, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    editing = false
                    onUse(path)
                }) { Text(stringRes(Res.string.claude_code_use_folder)) }
                OutlinedButton(onClick = {
                    editing = false
                    onClone(path)
                }) { Text(stringRes(Res.string.claude_code_clone_here)) }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
}
