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
package com.vitorpamplona.quartz.nip34Git.ci.workflowProgress

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip34Git.ci.CiRunContext
import com.vitorpamplona.quartz.nip34Git.ci.ciBoundedExpiration
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
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceKind
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 39842 — Nostr CI Workflow Progress (draft "CI Extension to NIP-34").
 *
 * An addressable, NIP-40-expiring marker that a run is queued, executing or recently concluded.
 * It is the addressable twin of the Workflow Result (9842): every 9842 tag, with `d` (the run id,
 * unique per attempt) in place of the run-id `r`, plus `status`, an optional `queue` estimate,
 * `in-progress` job ids, `conclusion` only once concluded, and an `expiration` at most 30 minutes
 * after `created_at`. Its growing set of Job Result `q` tags reveals finished jobs before the
 * Workflow Result exists. Content is empty.
 *
 * Gitworkshop treats it as the live source of truth, but clients MUST NOT require one to accept a
 * Workflow Result. Not searchable (no human text) and not a NIP-22 root scope.
 */
@Immutable
class CiWorkflowProgressEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun eventHints() = tags.ciRunEventHints()

    /**
     * `ROOT`: the PR (`E`); `PARENT`: the PR or PR Update that supplied the commit (`e`);
     * `JOB_RESULT`: each finished job's 9841 (`q`); `SERVICE_REQUEST` / `MANUAL_TRIGGER`: the
     * frozen provenance quote (`q`).
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

    /** The workflow run id, which the Workflow Result repeats as its non-ref `r`. */
    fun runId() = dTag()

    fun repositories() = tags.ciRepositories()

    fun commits() = tags.ciCommits()

    fun commit() = commits().firstOrNull()

    fun workflow() = tags.ciWorkflow()

    fun trigger() = tags.ciTrigger()

    fun gitRef() = tags.ciGitRef()

    fun pullRequest() = tags.ciPullRequest()

    fun status() = tags.workflowStatus()

    /**
     * The run's conclusion, read only when [status] is `concluded`: the spec has the tag present
     * exactly then, and a conclusion on a still-running marker is not a result.
     */
    fun conclusion() = if (status() == CiWorkflowStatus.CONCLUDED) tags.ciConclusion() else null

    fun queuedAt() = tags.ciQueuedAt()

    fun startedAt() = tags.ciStartedAt()

    /** Capacity rounds before the run starts: an estimate, never an exact count of jobs ahead. */
    fun queue() = tags.queue()

    /** Jobs currently executing. Jobs neither here nor in [jobResults] are pending. */
    fun inProgressJobs() = tags.inProgressJobs()

    /** The Job Results finished so far. */
    fun jobResults() = tags.ciJobResultQuotes()

    /**
     * The frozen provenance quote. A *queued* marker must omit the `service-request` quote —
     * final authorization is only selected at runner handoff — so one there is not returned.
     */
    fun provenance() =
        tags.ciProvenance()?.takeUnless {
            it.kind == CiProvenanceKind.SERVICE_REQUEST && status() == CiWorkflowStatus.QUEUED
        }

    fun expiration() = tags.expiration()

    /** The expiration, when it is later than `created_at` and within the spec's 30-minute bound. */
    fun validExpiration() = tags.ciBoundedExpiration(createdAt, MAX_EXPIRATION_SECONDS)

    /** Whether the marker is within a valid, unexpired lifetime at [now]. */
    fun isLive(now: Long = TimeUtils.now()) = validExpiration()?.let { it > now } ?: false

    /** Whether a live marker still counts as pending: queued or in progress. */
    fun isPending(now: Long = TimeUtils.now()) = isLive(now) && (status() == CiWorkflowStatus.QUEUED || status() == CiWorkflowStatus.IN_PROGRESS)

    companion object {
        const val KIND = 39842
        const val MAX_EXPIRATION_SECONDS = 30 * 60L

        fun build(
            runId: String,
            context: CiRunContext,
            trigger: CiTrigger,
            status: CiWorkflowStatus,
            conclusion: CiConclusion? = null,
            jobResults: List<CiJobResultQuote> = emptyList(),
            inProgressJobs: List<String> = emptyList(),
            queue: Long? = null,
            provenance: CiProvenanceQuote? = null,
            queuedAt: Long? = null,
            startedAt: Long? = null,
            createdAt: Long = TimeUtils.now(),
            expiresAt: Long = createdAt + MAX_EXPIRATION_SECONDS,
            initializer: TagArrayBuilder<CiWorkflowProgressEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(runId)
            ciRunContext(context)
            ciTrigger(trigger)
            workflowStatus(status)
            if (status == CiWorkflowStatus.CONCLUDED) conclusion?.let { ciConclusion(it) }
            queue?.let { queue(it) }
            if (inProgressJobs.isNotEmpty()) inProgressJobs(inProgressJobs)
            queuedAt?.let { ciQueuedAt(it) }
            startedAt?.let { ciStartedAt(it) }
            provenance?.let { ciProvenance(it) }
            ciJobResults(jobResults)
            expiration(expiresAt)
            initializer()
        }
    }
}
