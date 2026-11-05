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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitBranchesState
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalResult
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalUiState
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposals
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitPalette
import com.vitorpamplona.amethyst.commons.nip34Git.ui.normalizeLabel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_edit_message
import com.vitorpamplona.amethyst.commons.resources.git_edit_propose
import com.vitorpamplona.amethyst.commons.resources.git_edit_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_cancel
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_subject
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_base
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_branch
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_create
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_description
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_labels
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_load_branches
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_source
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_source_hint
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_title
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_working
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_action
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_submit
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_title
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import kotlin.reflect.KClass

private class GitProposalViewModelFactory(
    private val accountViewModel: AccountViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T =
        GitProposalViewModel(
            proposals = GitProposals(accountViewModel.account, accountViewModel.httpClientBuilder::okHttpClientForPreview),
            workScope = accountViewModel.account.scope,
            reportSignerErrors = { accountViewModel.reportSignerErrors(it) },
        ) as T
}

@Composable
fun rememberGitProposals(
    key: String,
    accountViewModel: AccountViewModel,
): GitProposalViewModel = viewModel(key = key + "GitProposals", factory = GitProposalViewModelFactory(accountViewModel))

/** Once a proposal is published, opens it in place of the screen that made it. */
@Composable
private fun OpenWhenPublished(
    viewModel: GitProposalViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    leaveFirst: () -> Unit = { nav.popBack() },
    onFailed: @Composable (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is GitProposalUiState.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is GitProposalUiState.Done ->
            when (val result = s.result) {
                is GitProposalResult.Published ->
                    LaunchedEffect(result.event.id) {
                        viewModel.reset()
                        leaveFirst()
                        routeFor(LocalCache.getOrCreateNote(result.event.id), accountViewModel.account)?.let { nav.nav(it) }
                    }
                is GitProposalResult.Failed -> onFailed(result.reason)
            }
        else -> {}
    }
}

@Composable
fun GitNewPullRequestScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitNewPullRequestForm(it, accountViewModel, nav) }
    }
}

/**
 * "New pull request": a branch (of this repository, or of a fork at any clone URL) proposed into
 * one of this repository's branches. Its commits are pushed to the repository's GRASP servers and
 * the PR published once they hold them, as ngit opens one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitNewPullRequestForm(
    repoNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberGitProposals(repoNote.idHex, accountViewModel)
    val target = remember(repoNote) { viewModel.target(repoNote) }
    val branchesState by viewModel.branches.collectAsStateWithLifecycle()
    val working = viewModel.state.collectAsStateWithLifecycle().value is GitProposalUiState.Working

    var source by rememberSaveable {
        mutableStateOf(
            target
                ?.targets
                ?.grasp
                ?.firstOrNull()
                ?.cloneUrl ?: "",
        )
    }
    var sourceBranch by rememberSaveable { mutableStateOf<String?>(null) }
    var targetBranch by rememberSaveable { mutableStateOf(target?.defaultBranch) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var labels by rememberSaveable { mutableStateOf("") }

    val loaded = (branchesState as? GitBranchesState.Loaded)?.branches.orEmpty()
    val tip = loaded.firstOrNull { it.first == sourceBranch }?.second
    val targetBranches =
        remember(target) {
            target
                ?.signedState
                ?.event
                ?.branches()
                ?.map { it.shortName }
                .orEmpty()
        }
    val canCreate = target != null && tip != null && title.isNotBlank() && !working

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { Text(stringRes(Res.string.git_pr_new_title), fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                    }
                },
                actions = {
                    Button(
                        enabled = canCreate,
                        onClick = {
                            val branch = sourceBranch
                            if (target != null && tip != null && branch != null) {
                                viewModel.openPullRequest(
                                    target,
                                    source,
                                    tip,
                                    branch,
                                    targetBranch,
                                    title.trim(),
                                    description.trim(),
                                    labels.split(',').map(::normalizeLabel).filter { it.isNotEmpty() },
                                )
                            }
                        },
                        shape = GitShape,
                        colors = ButtonDefaults.buttonColors(containerColor = gitPalette().open, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 10.dp).height(34.dp),
                    ) {
                        Text(stringRes(Res.string.git_pr_new_create), style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalDisappearingScaffoldPadding.current)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OpenWhenPublished(viewModel, accountViewModel, nav) { reason -> Text(reason, color = MaterialTheme.colorScheme.error) }
            if (working) Text(stringRes(Res.string.git_pr_new_working), color = MaterialTheme.colorScheme.grayText)

            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text(stringRes(Res.string.git_pr_new_source)) },
                supportingText = { Text(stringRes(Res.string.git_pr_new_source_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { viewModel.loadBranches(source) }, enabled = source.isNotBlank()) {
                Text(stringRes(Res.string.git_pr_new_load_branches))
            }
            when (val b = branchesState) {
                is GitBranchesState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is GitBranchesState.Failed -> Text(b.reason, color = MaterialTheme.colorScheme.error)
                else -> {}
            }
            if (loaded.isNotEmpty()) {
                BranchPicker(stringRes(Res.string.git_pr_new_branch), loaded.map { it.first }, sourceBranch) {
                    sourceBranch = it
                    if (title.isBlank()) title = it.replace('-', ' ').replace('_', ' ')
                }
            }
            if (targetBranches.isNotEmpty()) {
                BranchPicker(stringRes(Res.string.git_pr_new_base), targetBranches, targetBranch) { targetBranch = it }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringRes(Res.string.git_new_issue_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringRes(Res.string.git_pr_new_description)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = labels,
                onValueChange = { labels = it },
                label = { Text(stringRes(Res.string.git_pr_new_labels)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchPicker(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option, fontFamily = FontFamily.Monospace) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

/**
 * For a pull request's author: point it at a newer tip (a kind 1619), its commits pushed to the
 * repository's GRASP servers first. The source defaults to where the PR's commits are served.
 */
@Composable
fun GitPullRequestUpdateButton(
    pullRequest: Note,
    repository: AddressableNote?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = pullRequest.event as? GitPullRequestEvent ?: return
    if (event.pubKey != accountViewModel.account.signer.pubKey || repository == null) return
    val viewModel = rememberGitProposals(pullRequest.idHex, accountViewModel)
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringRes(Res.string.git_pr_update_action)) }
    if (!open) return

    val target = remember(repository) { viewModel.target(repository) }
    val branchesState by viewModel.branches.collectAsStateWithLifecycle()
    var source by rememberSaveable {
        mutableStateOf(
            event.cloneUrls().firstOrNull() ?: target
                ?.targets
                ?.grasp
                ?.firstOrNull()
                ?.cloneUrl ?: "",
        )
    }
    var branch by rememberSaveable { mutableStateOf(event.branchName()) }
    val loaded = (branchesState as? GitBranchesState.Loaded)?.branches.orEmpty()
    val tip = loaded.firstOrNull { it.first == branch }?.second
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if ((state as? GitProposalUiState.Done)?.result is GitProposalResult.Published) {
            viewModel.reset()
            open = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (state !is GitProposalUiState.Working) open = false },
        title = { Text(stringRes(Res.string.git_pr_update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text(stringRes(Res.string.git_pr_new_source)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { viewModel.loadBranches(source) }) { Text(stringRes(Res.string.git_pr_new_load_branches)) }
                if (loaded.isNotEmpty()) BranchPicker(stringRes(Res.string.git_pr_new_branch), loaded.map { it.first }, branch) { branch = it }
                (branchesState as? GitBranchesState.Failed)?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
                when (val s = state) {
                    is GitProposalUiState.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is GitProposalUiState.Done -> (s.result as? GitProposalResult.Failed)?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                enabled = target != null && tip != null && tip != event.currentCommit() && state !is GitProposalUiState.Working,
                onClick = { if (target != null && tip != null) viewModel.updatePullRequest(pullRequest, target, source, tip) },
            ) { Text(stringRes(Res.string.git_pr_update_submit)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringRes(Res.string.git_merge_cancel)) } },
    )
}

/**
 * Editing a file in the app: its text, then a commit message, proposed as a pull request into the
 * branch being browsed ([GitProposals.proposeEdit]). Opens the pull request once it is published.
 */
@Composable
fun GitFileEditor(
    repository: AddressableNote,
    sourceClones: List<String>,
    branch: String?,
    parent: String,
    path: List<String>,
    initial: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    onCancel: () -> Unit,
) {
    val viewModel = rememberGitProposals(repository.idHex + path.joinToString("/"), accountViewModel)
    val target = remember(repository) { viewModel.target(repository) }
    var text by rememberSaveable { mutableStateOf(initial) }
    var message by rememberSaveable { mutableStateOf("Update ${path.lastOrNull() ?: ""}") }
    val working = viewModel.state.collectAsStateWithLifecycle().value is GitProposalUiState.Working

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(Res.string.git_edit_title, path.joinToString("/")), style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
        OpenWhenPublished(viewModel, accountViewModel, nav, leaveFirst = onCancel) { reason -> Text(reason, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(stringRes(Res.string.git_edit_message)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !working) { Text(stringRes(Res.string.git_merge_cancel)) }
            Button(
                enabled = target != null && message.isNotBlank() && text != initial && !working,
                onClick = { if (target != null) viewModel.proposeEdit(target, sourceClones, branch, parent, path, text, message.trim()) },
            ) { Text(stringRes(Res.string.git_edit_propose)) }
        }
    }
}
