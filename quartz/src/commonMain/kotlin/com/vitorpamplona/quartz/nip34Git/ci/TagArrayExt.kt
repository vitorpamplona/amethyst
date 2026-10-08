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
package com.vitorpamplona.quartz.nip34Git.ci

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRunnerSelector
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowFile
import com.vitorpamplona.quartz.nip34Git.ci.tags.CommitIdTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ConclusionTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.GitRefTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.JobResultQuoteTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ProvenanceQuoteTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.QueuedAtTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RunnerFamilyTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RunnerSelectorTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.StartedAtTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.TriggerTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowRunQuoteTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowTag
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag

/**
 * The one value [transform] yields across the tags, or null when none or **more than one** tag
 * yields a value. For the tags the spec says appear exactly once (`w`, `o`, `status`, a 9843's `a`
 * and `p`, …): a second one makes the event ambiguous, and ngit rejects rather than picks.
 */
inline fun <R : Any> TagArray.ciSingleOrNull(transform: (Array<String>) -> R?): R? {
    var found: R? = null
    for (index in indices) {
        val value = transform(this[index]) ?: continue
        if (found != null) return null
        found = value
    }
    return found
}

/** Every `a` that is a kind 30617 repository coordinate with a non-empty id. */
fun TagArray.ciRepositories(): List<ATag> = mapNotNull(RepositoryTag::parse)

fun TagArray.ciRepositoryAddressIds(): List<String> = mapNotNull(RepositoryTag::parseAddressId)

fun TagArray.ciRepositoryHints(): List<AddressHint> = mapNotNull(RepositoryTag::parseAsHint)

/** Runner families (`W`), case-folded and deduplicated. */
fun TagArray.ciRunnerFamilies(): List<String> = mapNotNull(RunnerFamilyTag::parse).distinct()

/**
 * Runner selectors (`R`), case-folded and deduplicated, keeping only those whose family appears in
 * a `W` tag: the spec says a selector's family "MUST appear in a `W` tag".
 */
fun TagArray.ciRunnerSelectors(): List<CiRunnerSelector> {
    val families = ciRunnerFamilies().toSet()
    return mapNotNull(RunnerSelectorTag::parse).filter { it.family in families }.distinct()
}

/** The git object ids (`c`): the run's commit first, then any annotated tag object id. */
fun TagArray.ciCommits(): List<String> = mapNotNull(CommitIdTag::parse)

fun TagArray.ciWorkflow(): CiWorkflowFile? = ciSingleOrNull(WorkflowTag::parse)

fun TagArray.ciTrigger() = ciSingleOrNull(TriggerTag::parse)

/** The push context: the single `r` starting with `refs/`. */
fun TagArray.ciGitRef(): String? = ciSingleOrNull(GitRefTag::parse)

/**
 * The pull-request context, or null when the event carries no `E` root. [withSourceAuthor] reads
 * the first lowercase `p` as the parent's author; pass false for a Manual Trigger, whose `p` is
 * the coordinator.
 */
fun TagArray.ciPullRequest(withSourceAuthor: Boolean = true): CiPullRequestContext? {
    val root = ciSingleOrNull(RootEventTag::parseKey) ?: return null
    return CiPullRequestContext(
        pullRequestId = root,
        pullRequestAuthor = fastFirstNotNullOfOrNull { RootAuthorTag.parseKey(it) },
        pullRequestKind = fastFirstNotNullOfOrNull(RootKindTag::parse)?.toIntOrNull(),
        sourceEventId = fastFirstNotNullOfOrNull(ReplyEventTag::parseKey),
        sourceKind = fastFirstNotNullOfOrNull(ReplyKindTag::parse)?.toIntOrNull(),
        sourceAuthor = if (withSourceAuthor) fastFirstNotNullOfOrNull(PTag::parseKey) else null,
    )
}

/** The run's frozen request-provenance quote; null when absent or repeated (ngit rejects a second). */
fun TagArray.ciProvenance(): CiProvenanceQuote? = ciSingleOrNull(ProvenanceQuoteTag::parse)

fun TagArray.ciJobResultQuotes(): List<CiJobResultQuote> = mapNotNull(JobResultQuoteTag::parse)

fun TagArray.ciConclusion() = ciSingleOrNull(ConclusionTag::parse)

fun TagArray.ciQueuedAt() = ciSingleOrNull(QueuedAtTag::parse)

fun TagArray.ciStartedAt() = ciSingleOrNull(StartedAtTag::parse)

/**
 * The single NIP-40 `expiration`, but only when it is later than [createdAt] and at most
 * [maxSeconds] after it — the lifetime bound each CI kind sets (30 minutes for 19843/39842,
 * 24 hours for 19844/39844). A marker outside the bound would let one event claim liveness
 * indefinitely, so it reads as having no valid expiration rather than being clamped.
 */
fun TagArray.ciBoundedExpiration(
    createdAt: Long,
    maxSeconds: Long,
): Long? = ciSingleOrNull(ExpirationTag::parse)?.takeIf { it > createdAt && it <= createdAt + maxSeconds }

// ---- Graph edges shared by the run-related kinds (9840, 9841, 9842, 39842) ----

/** Hints from the PR context (`E`, `e`) and from quotes (`q`) that carry a relay. */
fun TagArray.ciRunEventHints(): List<EventIdHint> {
    // Runs for every relay copy of every CI event: one list, filled in the old concatenation's order.
    val result = mapNotNullTo(ArrayList(), RootEventTag::parseAsHint)
    mapNotNullTo(result, ReplyEventTag::parseAsHint)
    mapNotNullTo(result, QTag::parseEventAsHint)
    return result
}

/** `ROOT` (`E`, the PR), `PARENT` (`e`, the PR or PR Update that supplied the commit), and the quoted events (`q`). */
fun TagArray.ciRunLinkedEventIds(): List<HexKey> {
    val result = mapNotNullTo(ArrayList(), RootEventTag::parseKey)
    mapNotNullTo(result, ReplyEventTag::parseKey)
    mapNotNullTo(result, QTag::parseEventId)
    return result
}

fun TagArray.ciRunPubKeyHints(): List<PubKeyHint> {
    val result = mapNotNullTo(ArrayList(), RootAuthorTag::parseAsHint)
    mapNotNullTo(result, PTag::parseAsHint)
    return result
}

/**
 * `ROOT_AUTHOR` (`P`), every lowercase `p` (the parent author on results, the coordinator on a
 * Manual Trigger), and the pubkey slot of the CI quotes: a provenance quote's requester and a Job
 * Result quote's compute provider.
 */
fun TagArray.ciRunLinkedPubKeys(): List<HexKey> {
    val result = mapNotNullTo(ArrayList()) { RootAuthorTag.parseKey(it) }
    mapNotNullTo(result, PTag::parseKey)
    mapNotNullTo(result, ProvenanceQuoteTag::parseRequester)
    mapNotNullTo(result, JobResultQuoteTag::parsePublisher)
    return result
}

fun TagArray.ciRunAddressHints(): List<AddressHint> {
    val result = mapNotNullTo(ArrayList(), RepositoryTag::parseAsHint)
    mapNotNullTo(result, WorkflowRunQuoteTag::parseAsHint)
    return result
}

/** `REPOSITORY` (`a`, validated 30617 coordinates) and a quoted Workflow Progress run (`q` 39842 address). */
fun TagArray.ciRunLinkedAddressIds(): List<String> {
    val result = mapNotNullTo(ArrayList(), RepositoryTag::parseAddressId)
    mapNotNullTo(result, WorkflowRunQuoteTag::parseAddressId)
    return result
}
