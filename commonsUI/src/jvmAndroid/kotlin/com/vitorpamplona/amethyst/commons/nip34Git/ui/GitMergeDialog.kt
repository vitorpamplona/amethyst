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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeBlocker
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeCheck
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeOutcome
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeStep
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeUiState
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMerger
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitPreparedMerge
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_merge_already
import com.vitorpamplona.amethyst.commons.resources.git_merge_already_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_no_branch
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_no_state
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_no_tip
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_not_grasp
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_not_loaded
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_cancel
import com.vitorpamplona.amethyst.commons.resources.git_merge_checking
import com.vitorpamplona.amethyst.commons.resources.git_merge_close
import com.vitorpamplona.amethyst.commons.resources.git_merge_confirm
import com.vitorpamplona.amethyst.commons.resources.git_merge_conflicts
import com.vitorpamplona.amethyst.commons.resources.git_merge_conflicts_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_done
import com.vitorpamplona.amethyst.commons.resources.git_merge_done_issues
import com.vitorpamplona.amethyst.commons.resources.git_merge_done_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_failed_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_other_clones
import com.vitorpamplona.amethyst.commons.resources.git_merge_partial
import com.vitorpamplona.amethyst.commons.resources.git_merge_ref_change
import com.vitorpamplona.amethyst.commons.resources.git_merge_servers
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_push
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_status
import com.vitorpamplona.amethyst.commons.resources.git_merge_strategy_apply
import com.vitorpamplona.amethyst.commons.resources.git_merge_strategy_merge_commit
import com.vitorpamplona.amethyst.commons.resources.git_merge_title
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import com.vitorpamplona.quartz.nip34Git.merge.GitMergeStrategy
import kotlin.reflect.KClass

private class GitMergeViewModelFactory(
    private val accountViewModel: AccountViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T =
        GitMergeViewModel(
            merger = GitMerger(accountViewModel.account, accountViewModel.httpClientBuilder::okHttpClientForPreview),
            workScope = accountViewModel.account.scope,
            reportSignerErrors = { accountViewModel.reportSignerErrors(it) },
        ) as T
}

/**
 * Merging [note] (a pull request or a patch series' root) in the app, as gitworkshop does in the
 * browser. Returns what the Merge button calls; the dialog shows while a merge is checked, made
 * or reported. [onMarkMerged] publishes a plain "merged" status, for an item already in the branch.
 */
@Composable
fun rememberGitMerge(
    note: Note,
    accountViewModel: AccountViewModel,
    onMarkMerged: () -> Unit,
): () -> Unit {
    val viewModel: GitMergeViewModel = viewModel(key = note.idHex + "GitMerge", factory = GitMergeViewModelFactory(accountViewModel))
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state !is GitMergeUiState.Idle) {
        GitMergeDialog(
            state = state,
            onMerge = { viewModel.merge(note) },
            onMarkMerged = {
                viewModel.dismiss()
                onMarkMerged()
            },
            onDismiss = viewModel::dismiss,
        )
    }
    return { viewModel.check(note) }
}

@Composable
fun GitMergeDialog(
    state: GitMergeUiState,
    onMerge: () -> Unit,
    onMarkMerged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state is GitMergeUiState.Checking || state is GitMergeUiState.Merging
    val check = (state as? GitMergeUiState.Checked)?.check
    val ready = (check as? GitMergeCheck.Ready)?.merge ?: (state as? GitMergeUiState.Merging)?.merge

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(titleOf(state, ready)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state) {
                    is GitMergeUiState.Idle -> {}
                    is GitMergeUiState.Checking -> Progress(stringRes(Res.string.git_merge_checking))
                    is GitMergeUiState.Merging -> {
                        MergeSummary(state.merge)
                        Progress(
                            when (state.step) {
                                GitMergeStep.PUBLISHING_STATE_AND_PUSHING -> stringRes(Res.string.git_merge_step_push)
                                GitMergeStep.PUBLISHING_STATUS -> stringRes(Res.string.git_merge_step_status)
                            },
                        )
                    }
                    is GitMergeUiState.Checked -> CheckBody(state.check)
                    is GitMergeUiState.Done -> DoneBody(state.outcome)
                    is GitMergeUiState.Error -> Text(state.message)
                }
            }
        },
        confirmButton = {
            when {
                ready != null ->
                    Button(
                        onClick = onMerge,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = gitStateColor(StatusKind.APPLIED), contentColor = Color.White),
                    ) { Text(stringRes(Res.string.git_merge_confirm)) }
                check is GitMergeCheck.AlreadyMerged ->
                    Button(onClick = onMarkMerged) { Text(stringRes(Res.string.git_status_mark_merged)) }
                !busy -> TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_merge_close)) }
            }
        },
        dismissButton = {
            if (ready != null || check is GitMergeCheck.AlreadyMerged) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringRes(Res.string.git_merge_cancel)) }
            }
        },
    )
}

@Composable
private fun titleOf(
    state: GitMergeUiState,
    ready: GitPreparedMerge?,
): String =
    when {
        ready != null -> stringRes(Res.string.git_merge_title, ready.branch)
        state is GitMergeUiState.Checking -> stringRes(Res.string.git_merge_title, "…")
        state is GitMergeUiState.Done && state.outcome is GitMergeOutcome.Merged -> stringRes(Res.string.git_merge_done_title)
        state is GitMergeUiState.Checked ->
            when (state.check) {
                is GitMergeCheck.Conflicts -> stringRes(Res.string.git_merge_conflicts_title)
                is GitMergeCheck.AlreadyMerged -> stringRes(Res.string.git_merge_already_title)
                is GitMergeCheck.Blocked -> stringRes(Res.string.git_merge_blocked_title)
                else -> stringRes(Res.string.git_merge_failed_title)
            }
        else -> stringRes(Res.string.git_merge_failed_title)
    }

@Composable
private fun Progress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, color = MaterialTheme.colorScheme.grayText)
    }
}

@Composable
private fun MergeSummary(merge: GitPreparedMerge) {
    Text(
        when (merge.plan.strategy) {
            GitMergeStrategy.MERGE_COMMIT -> stringRes(Res.string.git_merge_strategy_merge_commit, merge.branch)
            GitMergeStrategy.APPLY_TO_TIP -> stringRes(Res.string.git_merge_strategy_apply, merge.branch)
        },
    )
    Text(
        stringRes(Res.string.git_merge_ref_change, merge.branch, merge.oldTip.take(7), merge.plan.newTip.take(7)),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
    val servers =
        merge.targets
            .mapNotNull { GraspServer.normalize(it.cloneUrl) }
            .distinct()
            .joinToString(", ")
    Text(stringRes(Res.string.git_merge_servers, servers), color = MaterialTheme.colorScheme.grayText, style = MaterialTheme.typography.bodySmall)
    if (merge.otherClones.isNotEmpty()) {
        Text(
            stringRes(Res.string.git_merge_other_clones, merge.otherClones.joinToString(", ") { it.substringAfter("://").removeSuffix(".git") }),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CheckBody(check: GitMergeCheck) {
    when (check) {
        is GitMergeCheck.Ready -> MergeSummary(check.merge)
        is GitMergeCheck.AlreadyMerged -> Text(stringRes(Res.string.git_merge_already))
        is GitMergeCheck.Conflicts -> {
            Text(stringRes(Res.string.git_merge_conflicts))
            check.paths.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
        is GitMergeCheck.Blocked ->
            Text(
                when (check.blocker) {
                    GitMergeBlocker.REPOSITORY_NOT_LOADED -> stringRes(Res.string.git_merge_blocked_not_loaded)
                    GitMergeBlocker.NOT_ON_GRASP -> stringRes(Res.string.git_merge_blocked_not_grasp)
                    GitMergeBlocker.NO_SIGNED_STATE -> stringRes(Res.string.git_merge_blocked_no_state)
                    GitMergeBlocker.NO_BRANCH_TIP -> stringRes(Res.string.git_merge_blocked_no_branch)
                    GitMergeBlocker.NO_PULL_REQUEST_TIP -> stringRes(Res.string.git_merge_blocked_no_tip)
                },
            )
        is GitMergeCheck.Failed -> Text(check.message)
    }
}

@Composable
private fun DoneBody(outcome: GitMergeOutcome) {
    when (outcome) {
        is GitMergeOutcome.Merged -> {
            Text(stringRes(Res.string.git_merge_done, outcome.branch, outcome.newTip.take(7)))
            if (outcome.resolvedIssues > 0) Text(pluralStringRes(Res.plurals.git_merge_done_issues, outcome.resolvedIssues, outcome.resolvedIssues))
            val failures =
                outcome.push.result.servers
                    .filter { !it.pushed }
            if (failures.isNotEmpty()) {
                Text(
                    stringRes(Res.string.git_merge_partial, outcome.push.result.describeFailures()),
                    color = MaterialTheme.colorScheme.grayText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        is GitMergeOutcome.PushFailed -> Text(outcome.reason)
    }
}
