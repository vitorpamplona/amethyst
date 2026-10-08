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
package com.vitorpamplona.quartz.nip34Git.ci.jobResult

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip34Git.ci.CiRunContext
import com.vitorpamplona.quartz.nip34Git.ci.ciCommits
import com.vitorpamplona.quartz.nip34Git.ci.ciConclusion
import com.vitorpamplona.quartz.nip34Git.ci.ciGitRef
import com.vitorpamplona.quartz.nip34Git.ci.ciProvenance
import com.vitorpamplona.quartz.nip34Git.ci.ciPullRequest
import com.vitorpamplona.quartz.nip34Git.ci.ciQueuedAt
import com.vitorpamplona.quartz.nip34Git.ci.ciRepositories
import com.vitorpamplona.quartz.nip34Git.ci.ciRunAddressHints
import com.vitorpamplona.quartz.nip34Git.ci.ciRunContext
import com.vitorpamplona.quartz.nip34Git.ci.ciRunEventHints
import com.vitorpamplona.quartz.nip34Git.ci.ciRunLinkedAddressIds
import com.vitorpamplona.quartz.nip34Git.ci.ciRunLinkedEventIds
import com.vitorpamplona.quartz.nip34Git.ci.ciRunLinkedPubKeys
import com.vitorpamplona.quartz.nip34Git.ci.ciRunPubKeyHints
import com.vitorpamplona.quartz.nip34Git.ci.ciStartedAt
import com.vitorpamplona.quartz.nip34Git.ci.ciTrigger
import com.vitorpamplona.quartz.nip34Git.ci.ciWorkflow
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiArtifact
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOmittedOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceKind
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 9841 — Nostr CI Job Result (draft "CI Extension to NIP-34").
 *
 * The outcome of one job in a workflow run, signed by the compute provider that executed it (which
 * may or may not be the coordinator). It carries the common run context, the job id, its
 * conclusion, the full log's Blossom URL, artifacts, outputs and timings, and quotes the run's
 * Workflow Progress **address** (`q` 39842) to say which run it belongs to. `content` is a small
 * tail of the job's log, usually prefixed `[log-tail omitted=<bytes>]`.
 *
 * Not searchable, deliberately: the log tail is machine output (docker, compiler and test-runner
 * lines), not natural language, and it is the bulk of every event — indexing it would fill the
 * full-text index with build noise that drowns real matches. The job `name` is a workflow
 * identifier ("CI and deployments/android-lint"), not prose. Not a NIP-22 root scope either: CI
 * discussion belongs on the PR.
 */
@Immutable
class CiJobResultEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun eventHints() = tags.ciRunEventHints()

    /**
     * `ROOT`: the PR (`E`); `PARENT`: the PR or PR Update that supplied the commit (`e`);
     * `MANUAL_TRIGGER`: the 9840 request this run replayed (`q` with that marker), when quoted.
     */
    override fun linkedEventIds() = tags.ciRunLinkedEventIds()

    override fun pubKeyHints() = tags.ciRunPubKeyHints()

    /** `ROOT_AUTHOR` (`P`), `PARENT_AUTHOR` (`p`), and `REQUESTER`: the quoted Manual Trigger's author. */
    override fun linkedPubKeys() = tags.ciRunLinkedPubKeys()

    override fun addressHints() = tags.ciRunAddressHints()

    /** `REPOSITORY`: the repository announcements (`a`); `WORKFLOW_RUN`: the run's Workflow Progress address (`q`). */
    override fun linkedAddressIds() = tags.ciRunLinkedAddressIds()

    fun logTail() = content

    /** The byte count from a `[log-tail omitted=<bytes>]` prefix: how much log precedes the tail. */
    fun omittedLogBytes() = content.omittedLogBytes()

    fun repositories() = tags.ciRepositories()

    fun commits() = tags.ciCommits()

    fun commit() = commits().firstOrNull()

    fun workflow() = tags.ciWorkflow()

    fun trigger() = tags.ciTrigger()

    fun gitRef() = tags.ciGitRef()

    fun pullRequest() = tags.ciPullRequest()

    /** The run this job belongs to: the single quoted 39842 address (null when absent or ambiguous). */
    fun workflowRun() = tags.workflowRun()

    fun jobId() = tags.jobId()

    fun name() = tags.jobName()

    fun conclusion() = tags.ciConclusion()

    fun logsUrl() = tags.logsUrl()

    fun artifacts() = tags.artifacts()

    fun outputs() = tags.outputs()

    fun omittedOutputs() = tags.omittedOutputs()

    fun queuedAt() = tags.ciQueuedAt()

    fun startedAt() = tags.ciStartedAt()

    fun exitCode() = tags.exitCode()

    fun runsOn() = tags.runsOn()

    /**
     * The Manual Trigger this run replayed, if quoted. A Job Result MUST NOT quote a Service
     * Request — the coordinator made that decision, the provider asserts only execution — so a
     * `service-request` quote here is not returned.
     */
    fun manualTrigger() = tags.ciProvenance()?.takeIf { it.kind == CiProvenanceKind.MANUAL_TRIGGER }

    companion object {
        const val KIND = 9841

        fun build(
            logTail: String,
            context: CiRunContext,
            trigger: CiTrigger,
            workflowRun: Address,
            workflowRunRelay: NormalizedRelayUrl?,
            jobId: String,
            conclusion: CiConclusion,
            logsUrl: String? = null,
            name: String? = null,
            artifacts: List<CiArtifact> = emptyList(),
            outputs: List<CiJobOutput> = emptyList(),
            omittedOutputs: List<CiOmittedOutput> = emptyList(),
            queuedAt: Long? = null,
            startedAt: Long? = null,
            exitCode: Int? = null,
            runsOn: List<String> = emptyList(),
            manualTrigger: CiProvenanceQuote? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CiJobResultEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, logTail, createdAt) {
            ciRunContext(context)
            ciTrigger(trigger)
            workflowRun(workflowRun, workflowRunRelay)
            jobId(jobId)
            name?.let { jobName(it) }
            ciConclusion(conclusion)
            logsUrl?.let { logsUrl(it) }
            artifacts(artifacts)
            outputs(outputs)
            omittedOutputs(omittedOutputs)
            queuedAt?.let { ciQueuedAt(it) }
            startedAt?.let { ciStartedAt(it) }
            exitCode?.let { exitCode(it) }
            if (runsOn.isNotEmpty()) runsOn(runsOn)
            manualTrigger?.takeIf { it.kind == CiProvenanceKind.MANUAL_TRIGGER }?.let { ciProvenance(it) }
            initializer()
        }
    }
}
