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
package com.vitorpamplona.quartz.nip34Git.ci.workflowResult

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip34Git.ci.CiRunContext
import com.vitorpamplona.quartz.nip34Git.ci.ciCommits
import com.vitorpamplona.quartz.nip34Git.ci.ciConclusion
import com.vitorpamplona.quartz.nip34Git.ci.ciGitRef
import com.vitorpamplona.quartz.nip34Git.ci.ciJobResultQuotes
import com.vitorpamplona.quartz.nip34Git.ci.ciJobResults
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
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 9842 — Nostr CI Workflow Result (draft "CI Extension to NIP-34").
 *
 * The coordinator-signed combined outcome of one workflow run **attempt**. It carries the common
 * run context, the run id (the non-`refs/` `r`, equal to the run's Workflow Progress `d`), the
 * combined `conclusion`, timings, one `q` per Job Result (marker = job id, pubkey = the compute
 * provider) and, for a request-gated or replayed run, the frozen provenance quote
 * (`service-request` or `manual-trigger`). Content is always empty.
 *
 * Each 9842 is a distinct attempt: clients must not merge attempts just because their context
 * tags match. Not searchable (no human text) and not a NIP-22 root scope.
 */
@Immutable
class CiWorkflowResultEvent(
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
     * `JOB_RESULT`: each quoted 9841 (`q` with a job-id marker); `SERVICE_REQUEST` /
     * `MANUAL_TRIGGER`: the frozen provenance quote (`q` with that marker).
     */
    override fun linkedEventIds() = tags.ciRunLinkedEventIds()

    override fun pubKeyHints() = tags.ciRunPubKeyHints()

    /**
     * `ROOT_AUTHOR` (`P`), `PARENT_AUTHOR` (`p`), `COMPUTE_PROVIDER`: each quoted Job Result's
     * signer, and `REQUESTER`: the provenance quote's author.
     */
    override fun linkedPubKeys() = tags.ciRunLinkedPubKeys()

    override fun addressHints() = tags.ciRunAddressHints()

    /** `REPOSITORY`: the repository announcements (`a`). */
    override fun linkedAddressIds() = tags.ciRunLinkedAddressIds()

    fun repositories() = tags.ciRepositories()

    fun commits() = tags.ciCommits()

    fun commit() = commits().firstOrNull()

    fun workflow() = tags.ciWorkflow()

    fun trigger() = tags.ciTrigger()

    fun gitRef() = tags.ciGitRef()

    fun pullRequest() = tags.ciPullRequest()

    /** The workflow run id: the single `r` that is not a git ref (null when absent or repeated). */
    fun runId() = tags.workflowRunId()

    fun conclusion() = tags.ciConclusion()

    fun queuedAt() = tags.ciQueuedAt()

    fun startedAt() = tags.ciStartedAt()

    /** The Job Results that make up this attempt, in tag order. */
    fun jobResults() = tags.ciJobResultQuotes()

    /** The frozen `service-request` or `manual-trigger` quote; absent on an automatic run. */
    fun provenance() = tags.ciProvenance()

    companion object {
        const val KIND = 9842

        fun build(
            context: CiRunContext,
            trigger: CiTrigger,
            runId: String,
            conclusion: CiConclusion,
            jobResults: List<CiJobResultQuote>,
            provenance: CiProvenanceQuote? = null,
            queuedAt: Long? = null,
            startedAt: Long? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CiWorkflowResultEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            ciRunContext(context)
            ciTrigger(trigger)
            workflowRunId(runId)
            ciConclusion(conclusion)
            queuedAt?.let { ciQueuedAt(it) }
            startedAt?.let { ciStartedAt(it) }
            provenance?.let { ciProvenance(it) }
            ciJobResults(jobResults)
            initializer()
        }
    }
}
