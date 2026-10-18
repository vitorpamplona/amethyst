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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiRollup
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_cancelled
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_failure
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_neutral
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_skipped
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_startup_failure
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_success
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_timed_out
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_unknown
import com.vitorpamplona.amethyst.commons.resources.git_ci_full_log
import com.vitorpamplona.amethyst.commons.resources.git_ci_in_progress
import com.vitorpamplona.amethyst.commons.resources.git_ci_queued
import com.vitorpamplona.amethyst.commons.resources.git_commit
import com.vitorpamplona.amethyst.commons.resources.git_repo_branches
import com.vitorpamplona.amethyst.commons.resources.git_repo_tags
import com.vitorpamplona.amethyst.commons.resources.kc_dev_app_id
import com.vitorpamplona.amethyst.commons.resources.kc_dev_asset_bundle_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_asset_bundle_untitled
import com.vitorpamplona.amethyst.commons.resources.kc_dev_certificate
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_accepts_secrets
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission_maintainer
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission_open
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission_operator
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_availability
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_billing
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_billing_free
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_billing_out_of_band
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_coordinator_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_execution
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_execution_automatic
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_execution_request
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_exit_code
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_job
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_jobs
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_manual_trigger_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_offline
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_online_until
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_policy_unknown
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_readiness_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_ref
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_repo_status
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_repo_status_active
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_repositories
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_repository
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_id
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_runners
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_running_jobs
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_runs_on
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_secrets
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_service_request_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_service_stop_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_manual
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_pull_request
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_push
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_schedule
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_workflow
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_workflow_run
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_workflows
import com.vitorpamplona.amethyst.commons.resources.kc_dev_expired
import com.vitorpamplona.amethyst.commons.resources.kc_dev_expires
import com.vitorpamplona.amethyst.commons.resources.kc_dev_feed_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_dev_feed_untitled
import com.vitorpamplona.amethyst.commons.resources.kc_dev_files
import com.vitorpamplona.amethyst.commons.resources.kc_dev_files_count
import com.vitorpamplona.amethyst.commons.resources.kc_dev_grasp_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_hash
import com.vitorpamplona.amethyst.commons.resources.kc_dev_identity_proof_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_dev_identity_proof_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ipv4
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ipv6
import com.vitorpamplona.amethyst.commons.resources.kc_dev_more
import com.vitorpamplona.amethyst.commons.resources.kc_dev_napplet_snapshot
import com.vitorpamplona.amethyst.commons.resources.kc_dev_napplet_snapshot_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_nns_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_dev_permissions
import com.vitorpamplona.amethyst.commons.resources.kc_dev_platform
import com.vitorpamplona.amethyst.commons.resources.kc_dev_reason
import com.vitorpamplona.amethyst.commons.resources.kc_dev_repo_state_discontinued
import com.vitorpamplona.amethyst.commons.resources.kc_dev_repo_state_head
import com.vitorpamplona.amethyst.commons.resources.kc_dev_repo_state_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_revoked
import com.vitorpamplona.amethyst.commons.resources.kc_dev_servers
import com.vitorpamplona.amethyst.commons.resources.kc_dev_site_snapshot
import com.vitorpamplona.amethyst.commons.resources.kc_dev_site_snapshot_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_source
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_payload
import com.vitorpamplona.amethyst.commons.resources.version
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.zapstore.identityProof.IdentityProofEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiAdmissionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiBillingPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiExecutionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.state.tags.RefTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip78AppData.AppDataEvent
import org.jetbrains.compose.resources.StringResource

/*
 * Cards for the developer-tooling kinds: NIP-34 CI (runs, jobs, coordinators, service requests),
 * repository state and grasp server lists, app-store proofs and asset bundles, nSite and nApplet
 * snapshots, feed definitions, NIP-78 app data and NNS records. Most of them carry their meaning
 * in tags and have empty or machine content, so the text fallback showed nothing useful.
 */

private const val MAX_ROWS = 6
private const val LOG_TAIL_LINES = 6

// ---------------------------------------------------------------------------------------------
// NIP-34 CI
// ---------------------------------------------------------------------------------------------

/** Kind 39842: a workflow run's live progress (queued, running, concluded). */
@Composable
fun RenderCiWorkflowProgress(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiWorkflowProgressEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                gitRef = event.gitRef(),
                trigger = event.trigger(),
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val status = remember(event) { event.status() }
    val conclusion = remember(event) { event.conclusion() }
    val jobs = remember(event) { event.jobResults().map { it.jobId } }
    val running = remember(event) { event.inProgressJobs() }

    val state =
        when (status) {
            CiWorkflowStatus.QUEUED, CiWorkflowStatus.IN_PROGRESS -> CiState.PENDING
            else -> CiRollup.stateOf(conclusion)
        }
    val statusLabel =
        when (status) {
            CiWorkflowStatus.QUEUED -> stringRes(Res.string.git_ci_queued)
            CiWorkflowStatus.IN_PROGRESS -> stringRes(Res.string.git_ci_in_progress)
            else -> stringRes(conclusionLabel(conclusion))
        }

    KindCard(
        icon = ciSymbol(state),
        title = info.workflow ?: stringRes(Res.string.kc_dev_ci_workflow_run),
        subtitle = statusLabel,
        iconTint = ciTint(state),
    ) {
        CiRunFields(info)
        KindCardField(stringRes(Res.string.kc_dev_ci_run_id), event.runId(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_dev_ci_jobs), jobs.joinToString(", "))
        KindCardField(stringRes(Res.string.kc_dev_ci_running_jobs), running.joinToString(", "))
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9842: the final result of a workflow run. */
@Composable
fun RenderCiWorkflowResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiWorkflowResultEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                gitRef = event.gitRef(),
                trigger = event.trigger(),
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val conclusion = remember(event) { event.conclusion() }
    val jobs = remember(event) { event.jobResults().map { it.jobId } }
    val state = CiRollup.stateOf(conclusion)

    KindCard(
        icon = ciSymbol(state),
        title = info.workflow ?: stringRes(Res.string.kc_dev_ci_workflow_run),
        subtitle = stringRes(conclusionLabel(conclusion)),
        iconTint = ciTint(state),
    ) {
        CiRunFields(info)
        KindCardField(stringRes(Res.string.kc_dev_ci_run_id), event.runId(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_dev_ci_jobs), jobs.joinToString(", "))
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9841: one job of a workflow run, with its log tail, full log and artifacts. */
@Composable
fun RenderCiJobResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiJobResultEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                gitRef = event.gitRef(),
                trigger = event.trigger(),
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val conclusion = remember(event) { event.conclusion() }
    val tail =
        remember(event) {
            event
                .logTail()
                .lines()
                .dropWhile { it.startsWith("[log-tail omitted=") }
                .takeLast(LOG_TAIL_LINES)
                .joinToString("\n")
                .trim()
        }
    val artifacts = remember(event) { event.artifacts() }
    val state = CiRollup.stateOf(conclusion)

    KindCard(
        icon = ciSymbol(state),
        title = event.name() ?: event.jobId() ?: stringRes(Res.string.kc_dev_ci_job),
        subtitle = stringRes(conclusionLabel(conclusion)),
        iconTint = ciTint(state),
    ) {
        KindCardField(stringRes(Res.string.kc_dev_ci_workflow), info.workflow)
        CiRunFields(info.copy(workflow = null))
        KindCardField(stringRes(Res.string.kc_dev_ci_runs_on), event.runsOn().joinToString(", "))
        KindCardField(stringRes(Res.string.kc_dev_ci_exit_code), event.exitCode()?.toString(), monospace = true)
        KindCardCode(tail)
        event.logsUrl()?.let { url ->
            ClickableUrlOrBlossom(
                urlText = stringRes(Res.string.git_ci_full_log),
                url = url,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        artifacts.take(MAX_ROWS).forEach { artifact ->
            ClickableUrlOrBlossom(
                urlText = artifact.name ?: artifact.fileName ?: artifact.url,
                url = artifact.url,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9840: a maintainer asks a coordinator to replay a workflow at a commit. */
@Composable
fun RenderCiManualTrigger(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiManualTriggerEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = null,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                gitRef = event.gitRef(),
                trigger = null,
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val coordinator = remember(event) { listOfNotNull(event.coordinator()) }

    KindCard(
        icon = MaterialSymbols.PlayArrow,
        title = stringRes(Res.string.kc_dev_ci_manual_trigger_title),
        subtitle = event.workflow()?.path,
    ) {
        CiRunFields(info)
        KindCardUsers(coordinator, accountViewModel, nav)
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9843: a maintainer asks a coordinator to start serving a repository. */
@Composable
fun RenderCiServiceRequest(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiServiceRequestEvent ?: return
    val repository = remember(event) { event.repository() }
    val coordinator = remember(event) { listOfNotNull(event.coordinator()) }
    CiServiceCard(
        icon = MaterialSymbols.PlayCircle,
        title = stringRes(Res.string.kc_dev_ci_service_request_title),
        repository = repository,
        coordinator = coordinator,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** Kind 9844: a maintainer asks a coordinator to stop serving a repository. */
@Composable
fun RenderCiServiceStop(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiServiceStopEvent ?: return
    val repository = remember(event) { event.repository() }
    val coordinator = remember(event) { listOfNotNull(event.coordinator()) }
    CiServiceCard(
        icon = MaterialSymbols.Stop,
        title = stringRes(Res.string.kc_dev_ci_service_stop_title),
        repository = repository,
        coordinator = coordinator,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun CiServiceCard(
    icon: MaterialSymbol,
    title: String,
    repository: ATag?,
    coordinator: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    KindCard(icon = icon, title = title, subtitle = repository?.dTag) {
        KindCardUsers(coordinator, accountViewModel, nav)
    }
}

/** Kind 19843: a CI coordinator advertising its software, runners and admission policies. */
@Composable
fun RenderCiCoordinatorAdvertisement(baseNote: Note) {
    val event = baseNote.event as? CiCoordinatorAdvertisementEvent ?: return
    val software = remember(event) { event.software()?.let { listOfNotNull(it.name, it.version).joinToString(" ") } }
    val runners =
        remember(event) {
            (event.runnerFamilies() + event.runnerSelectors().map { it.toValue() }).distinct()
        }
    val admission = remember(event) { event.admission() }
    val execution = remember(event) { event.execution() }
    val billing = remember(event) { event.billing() }
    val acceptsSecrets = remember(event) { event.acceptsSecretUpdates() }
    val liveUntil = remember(event) { if (event.isLive()) event.validExpiration() else null }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = stringRes(Res.string.kc_dev_ci_coordinator_title),
        subtitle = software,
    ) {
        KindCardField(stringRes(Res.string.kc_dev_ci_admission), admission?.let { stringRes(admissionLabel(it)) })
        KindCardField(stringRes(Res.string.kc_dev_ci_execution), execution?.let { stringRes(executionLabel(it)) })
        KindCardField(stringRes(Res.string.kc_dev_ci_billing), billing?.let { stringRes(billingLabel(it)) })
        LabeledChips(stringRes(Res.string.kc_dev_ci_runners), runners)
        if (acceptsSecrets) {
            KindCardChips(listOf(stringRes(Res.string.kc_dev_ci_accepts_secrets)))
        }
        CiAvailability(liveUntil)
    }
}

/** Kind 19844: a coordinator's list of repositories (and maintainers) it is ready to serve. */
@Composable
fun RenderCiRequestReadinessList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiRequestReadinessListEvent ?: return
    val repositories = remember(event) { event.repositories().map { it.dTag } }
    val maintainers = remember(event) { event.maintainers().map { it.pubKey } }
    val liveUntil = remember(event) { if (event.isLive()) event.validExpiration() else null }

    KindCard(
        icon = MaterialSymbols.Checklist,
        title = stringRes(Res.string.kc_dev_ci_readiness_title),
    ) {
        LabeledChips(stringRes(Res.string.kc_dev_ci_repositories), repositories)
        KindCardUsers(maintainers, accountViewModel, nav)
        CiAvailability(liveUntil)
    }
}

/** Kind 39844: a coordinator's status for one repository: workflows, runners and secret names. */
@Composable
fun RenderCiRepositoryStatus(baseNote: Note) {
    val event = baseNote.event as? CiRepositoryStatusEvent ?: return
    val repository = remember(event) { event.selectedRepository()?.dTag ?: event.repositories().firstOrNull()?.dTag ?: "" }
    val acting = remember(event) { event.isActing() }
    val workflows = remember(event) { event.workflowPaths() }
    val runners =
        remember(event) {
            (event.runnerFamilies() + event.runnerSelectors().map { it.toValue() }).distinct()
        }
    val secrets = remember(event) { event.secrets().map { it.name } }
    val liveUntil = remember(event) { if (event.isLive()) event.validExpiration() else null }

    KindCard(
        icon = MaterialSymbols.Dns,
        title =
            if (acting) {
                stringRes(Res.string.kc_dev_ci_repo_status_active, repository)
            } else {
                stringRes(Res.string.kc_dev_ci_repo_status, repository)
            },
        iconTint = if (acting) ciTint(CiState.SUCCESS) else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        LabeledChips(stringRes(Res.string.kc_dev_ci_workflows), workflows)
        LabeledChips(stringRes(Res.string.kc_dev_ci_runners), runners)
        LabeledChips(stringRes(Res.string.kc_dev_ci_secrets), secrets)
        CiAvailability(liveUntil)
    }
}

/** The run context shared by the CI run kinds. */
private data class CiRunInfo(
    val workflow: String?,
    val repository: String?,
    val commit: String?,
    val gitRef: String?,
    val trigger: CiTrigger?,
    val pullRequest: HexKey?,
)

@Composable
private fun CiRunFields(info: CiRunInfo) {
    KindCardField(stringRes(Res.string.kc_dev_ci_workflow), info.workflow)
    KindCardField(stringRes(Res.string.kc_dev_ci_repository), info.repository)
    KindCardField(stringRes(Res.string.git_commit), info.commit?.take(7), monospace = true)
    KindCardField(stringRes(Res.string.kc_dev_ci_ref), info.gitRef, monospace = true, maxLines = 1)
    KindCardField(stringRes(Res.string.kc_dev_ci_trigger), info.trigger?.let { stringRes(triggerLabel(it)) })
}

@Composable
private fun CiPullRequestQuote(
    pullRequest: HexKey?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pullRequest == null) return
    KindCardQuoteEvent(pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
}

@Composable
private fun CiAvailability(liveUntil: Long?) {
    KindCardField(
        stringRes(Res.string.kc_dev_ci_availability),
        if (liveUntil != null) {
            stringRes(Res.string.kc_dev_ci_online_until, timeAbsoluteNoDot(liveUntil))
        } else {
            stringRes(Res.string.kc_dev_ci_offline)
        },
    )
}

private fun ciSymbol(state: CiState): MaterialSymbol =
    when (state) {
        CiState.SUCCESS -> MaterialSymbols.CheckCircle
        CiState.FAILURE -> MaterialSymbols.Cancel
        CiState.PENDING -> MaterialSymbols.HourglassTop
        CiState.CANCELLED -> MaterialSymbols.Block
        CiState.NEUTRAL -> MaterialSymbols.RemoveCircleOutline
    }

@Composable
private fun ciTint(state: CiState): Color =
    when (state) {
        CiState.SUCCESS -> Color(0xFF1F883D)
        CiState.FAILURE -> Color(0xFFCF222E)
        CiState.PENDING -> Color(0xFF9A6700)
        CiState.CANCELLED, CiState.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun conclusionLabel(conclusion: CiConclusion?): StringResource =
    when (conclusion) {
        CiConclusion.SUCCESS -> Res.string.git_ci_conclusion_success
        CiConclusion.FAILURE -> Res.string.git_ci_conclusion_failure
        CiConclusion.NEUTRAL -> Res.string.git_ci_conclusion_neutral
        CiConclusion.CANCELLED -> Res.string.git_ci_conclusion_cancelled
        CiConclusion.SKIPPED -> Res.string.git_ci_conclusion_skipped
        CiConclusion.TIMED_OUT -> Res.string.git_ci_conclusion_timed_out
        CiConclusion.STARTUP_FAILURE -> Res.string.git_ci_conclusion_startup_failure
        null -> Res.string.git_ci_conclusion_unknown
    }

private fun triggerLabel(trigger: CiTrigger): StringResource =
    when (trigger) {
        CiTrigger.PUSH -> Res.string.kc_dev_ci_trigger_push
        CiTrigger.PULL_REQUEST -> Res.string.kc_dev_ci_trigger_pull_request
        CiTrigger.SCHEDULE -> Res.string.kc_dev_ci_trigger_schedule
        CiTrigger.MANUAL -> Res.string.kc_dev_ci_trigger_manual
    }

private fun admissionLabel(policy: CiAdmissionPolicy): StringResource =
    when (policy) {
        CiAdmissionPolicy.OPERATOR_SELECTED -> Res.string.kc_dev_ci_admission_operator
        CiAdmissionPolicy.MAINTAINER_REQUEST -> Res.string.kc_dev_ci_admission_maintainer
        CiAdmissionPolicy.OPEN -> Res.string.kc_dev_ci_admission_open
        CiAdmissionPolicy.UNKNOWN -> Res.string.kc_dev_ci_policy_unknown
    }

private fun executionLabel(policy: CiExecutionPolicy): StringResource =
    when (policy) {
        CiExecutionPolicy.AUTOMATIC -> Res.string.kc_dev_ci_execution_automatic
        CiExecutionPolicy.REQUEST_REQUIRED -> Res.string.kc_dev_ci_execution_request
        CiExecutionPolicy.UNKNOWN -> Res.string.kc_dev_ci_policy_unknown
    }

private fun billingLabel(policy: CiBillingPolicy): StringResource =
    when (policy) {
        CiBillingPolicy.NOT_REQUIRED -> Res.string.kc_dev_ci_billing_free
        CiBillingPolicy.OUT_OF_BAND -> Res.string.kc_dev_ci_billing_out_of_band
        CiBillingPolicy.UNKNOWN -> Res.string.kc_dev_ci_policy_unknown
    }

// ---------------------------------------------------------------------------------------------
// NIP-34 repository state and grasp servers
// ---------------------------------------------------------------------------------------------

/** Kind 30618: a repository's branch tips, tags and HEAD. */
@Composable
fun RenderGitRepositoryState(baseNote: Note) {
    val event = baseNote.event as? GitRepositoryStateEvent ?: return
    val branches = remember(event) { event.branches() }
    val tags = remember(event) { event.tagRefs() }
    val head = remember(event) { event.head() }
    val discontinued = branches.isEmpty() && tags.isEmpty()

    KindCard(
        icon = MaterialSymbols.Commit,
        title = stringRes(Res.string.kc_dev_repo_state_title, event.dTag()),
        subtitle =
            if (discontinued) {
                stringRes(Res.string.kc_dev_repo_state_discontinued)
            } else {
                head?.let { stringRes(Res.string.kc_dev_repo_state_head, it) }
            },
    ) {
        RefRows(stringRes(Res.string.git_repo_branches), branches)
        RefRows(stringRes(Res.string.git_repo_tags), tags)
    }
}

@Composable
private fun RefRows(
    label: String,
    refs: List<RefTag>,
) {
    if (refs.isEmpty()) return
    SectionLabel(label)
    refs.take(MAX_ROWS).forEach { ref ->
        KindCardField(ref.shortName, ref.commit.take(7), monospace = true, maxLines = 1)
    }
    MoreRows(refs.size - MAX_ROWS)
}

/** Kind 10317: the user's preferred grasp (git-over-nostr hosting) servers, in order. */
@Composable
fun RenderUserGraspList(baseNote: Note) {
    val event = baseNote.event as? UserGraspListEvent ?: return
    val servers = remember(event) { event.grasps().map { it.removePrefix("wss://").removePrefix("ws://").removeSuffix("/") } }

    KindCard(
        icon = MaterialSymbols.Storage,
        title = stringRes(Res.string.kc_dev_grasp_title),
    ) {
        KindCardChips(servers)
    }
}

// ---------------------------------------------------------------------------------------------
// App stores and verifiers
// ---------------------------------------------------------------------------------------------

/** Kind 30509: Zapstore's proof that an APK signing certificate belongs to this Nostr key. */
@Composable
fun RenderIdentityProof(baseNote: Note) {
    val event = baseNote.event as? IdentityProofEvent ?: return
    val certificate = remember(event) { (event.certificateHash() ?: event.dTag()).toShortDisplay() }
    val revoked = remember(event) { event.isRevoked() }
    val reason = remember(event) { event.revocationReason() }
    val expiry = remember(event) { event.expiry() }
    val expired = remember(event) { event.isExpired() }

    KindCard(
        icon = MaterialSymbols.Security,
        title = stringRes(Res.string.kc_dev_identity_proof_title),
        subtitle = stringRes(Res.string.kc_dev_identity_proof_subtitle),
        iconTint = if (revoked || expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        KindCardField(stringRes(Res.string.kc_dev_certificate), certificate, monospace = true, maxLines = 1)
        if (revoked) {
            KindCardChips(listOf(stringRes(Res.string.kc_dev_revoked)))
            KindCardField(stringRes(Res.string.kc_dev_reason), reason)
        } else if (expiry != null) {
            KindCardField(
                stringRes(if (expired) Res.string.kc_dev_expired else Res.string.kc_dev_expires),
                timeAbsoluteNoDot(expiry),
            )
        }
    }
}

/** Kind 9401: WalletScrutiny registers the files of one release by hash, for build verifiers. */
@Composable
fun RenderAssetBundle(baseNote: Note) {
    val event = baseNote.event as? AssetBundleEvent ?: return
    val productId = remember(event) { event.productId() }
    val files = remember(event) { event.files() }

    KindCard(
        icon = MaterialSymbols.FolderZip,
        title =
            if (productId != null) {
                stringRes(Res.string.kc_dev_asset_bundle_title, productId)
            } else {
                stringRes(Res.string.kc_dev_asset_bundle_untitled)
            },
        subtitle = event.note(),
    ) {
        KindCardField(stringRes(Res.string.version), event.version())
        KindCardField(stringRes(Res.string.kc_dev_platform), event.platform())
        if (files.isNotEmpty()) {
            SectionLabel(pluralStringRes(Res.plurals.kc_dev_files_count, files.size, files.size))
            files.take(MAX_ROWS).forEach { file ->
                KindCardCode(listOfNotNull(file.filename, file.hash.toShortDisplay()).joinToString("  "), maxLines = 1)
            }
            MoreRows(files.size - MAX_ROWS)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-5A / NIP-5D snapshots
// ---------------------------------------------------------------------------------------------

/** Kind 5128: one immutable version of an nSite, quoting the site it captures. */
@Composable
fun RenderSiteSnapshot(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? SiteSnapshotEvent ?: return
    val site = remember(event) { event.snapshotOf() }
    val name = remember(event) { event.title() ?: site?.dTag?.ifBlank { null } }
    val paths = remember(event) { event.paths() }

    KindCard(
        icon = MaterialSymbols.Language,
        title =
            if (name != null) {
                stringRes(Res.string.kc_dev_site_snapshot_title, name)
            } else {
                stringRes(Res.string.kc_dev_site_snapshot)
            },
        subtitle = event.description(),
    ) {
        SnapshotFields(paths, event.declaredAggregateHash(), event.source(), event.servers())
        site?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** Kind 5129: one immutable build of an nApplet. */
@Composable
fun RenderNappletSnapshot(baseNote: Note) {
    val event = baseNote.event as? NappletSnapshotEvent ?: return
    val name = remember(event) { event.title() }
    val paths = remember(event) { event.paths() }
    val requires = remember(event) { event.requires() }

    KindCard(
        icon = MaterialSymbols.Extension,
        title =
            if (name != null) {
                stringRes(Res.string.kc_dev_napplet_snapshot_title, name)
            } else {
                stringRes(Res.string.kc_dev_napplet_snapshot)
            },
        subtitle = event.description(),
    ) {
        LabeledChips(stringRes(Res.string.kc_dev_permissions), requires)
        SnapshotFields(paths, event.declaredAggregateHash(), event.source(), event.servers())
    }
}

@Composable
private fun SnapshotFields(
    paths: List<PathTag>,
    aggregateHash: HexKey?,
    source: String?,
    servers: List<String>,
) {
    if (paths.isNotEmpty()) {
        KindCardField(stringRes(Res.string.kc_dev_files), pluralStringRes(Res.plurals.kc_dev_files_count, paths.size, paths.size))
    }
    KindCardField(stringRes(Res.string.kc_dev_hash), aggregateHash?.toShortDisplay(), monospace = true, maxLines = 1)
    KindCardField(stringRes(Res.string.kc_dev_source), source, maxLines = 1)
    LabeledChips(stringRes(Res.string.kc_dev_servers), servers.map { it.removePrefix("https://").removeSuffix("/") })
}

// ---------------------------------------------------------------------------------------------
// Feeds, app data, NNS
// ---------------------------------------------------------------------------------------------

/** Kind 31890: a shared custom feed (title, emoji, its hashtags and authors, then its config). */
@Composable
fun RenderFeedDefinition(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FeedDefinitionEvent ?: return
    val title = remember(event) { listOfNotNull(event.emoji(), event.title()).joinToString(" ").ifBlank { null } }
    val hashtags = remember(event) { event.hashtags().map { "#$it" } }
    val authors = remember(event) { event.taggedUserIds() }
    val config = remember(event) { event.feedConfigJson() }

    KindCard(
        icon = MaterialSymbols.AutoMirrored.Feed,
        title = title ?: stringRes(Res.string.kc_dev_feed_untitled),
        subtitle = stringRes(Res.string.kc_dev_feed_subtitle),
    ) {
        KindCardChips(hashtags)
        KindCardUsers(authors, accountViewModel, nav)
        KindCardCode(config, maxLines = 8)
    }
}

/**
 * Kind 78: NIP-78 app data. It is app-private, so the card names the app (`d`) and never shows the
 * content.
 */
@Composable
fun RenderAppData(baseNote: Note) {
    val event = baseNote.event as? AppDataEvent ?: return
    val appId = remember(event) { event.dTag() }

    KindCard(
        icon = MaterialSymbols.Settings,
        title = kindName(event.kind),
        subtitle = stringRes(Res.string.kind_card_private_payload),
    ) {
        KindCardField(stringRes(Res.string.kc_dev_app_id), appId, monospace = true, maxLines = 1)
    }
}

/** Kind 30053: an NNS name record pointing a name at an IP address. */
@Composable
fun RenderNNS(baseNote: Note) {
    val event = baseNote.event as? NNSEvent ?: return
    val name = remember(event) { event.dTag() }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = name.ifBlank { kindName(event.kind) },
        subtitle = stringRes(Res.string.kc_dev_nns_subtitle),
    ) {
        KindCardField(stringRes(Res.string.kc_dev_ipv4), event.ip4(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_dev_ipv6), event.ip6(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.version), event.version())
    }
}

// ---------------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LabeledChips(
    label: String,
    values: List<String>,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(label)
        KindCardChips(values)
    }
}

@Composable
private fun MoreRows(hidden: Int) {
    if (hidden <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            symbol = MaterialSymbols.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringRes(Res.string.kc_dev_more, hidden),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
