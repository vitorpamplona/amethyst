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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRunnerSelector
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
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
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowTag

fun <T : Event> TagArrayBuilder<T>.ciRepositories(repositories: List<ATag>) = addAll(repositories.map { RepositoryTag.assemble(it) })

fun <T : Event> TagArrayBuilder<T>.ciCommits(objectIds: List<String>) = addAll(objectIds.map(CommitIdTag::assemble))

fun <T : Event> TagArrayBuilder<T>.ciWorkflow(workflow: CiWorkflowFile) = addUnique(WorkflowTag.assemble(workflow))

fun <T : Event> TagArrayBuilder<T>.ciTrigger(trigger: CiTrigger) = addUnique(TriggerTag.assemble(trigger))

// `add`, not `addUnique`: a Workflow Result carries its run id under the same `r` name.
fun <T : Event> TagArrayBuilder<T>.ciGitRef(ref: String) = add(GitRefTag.assemble(ref))

/**
 * Writes `E`/`K`/`P` for the PR and `e`/`k`/`p` for the event that supplied the commit. The
 * lowercase `p` is skipped when [withSourceAuthor] is false (a Manual Trigger, whose only `p` is
 * the coordinator).
 */
fun <T : Event> TagArrayBuilder<T>.ciPullRequest(
    context: CiPullRequestContext,
    withSourceAuthor: Boolean = true,
): TagArrayBuilder<T> {
    add(RootEventTag.assemble(context.pullRequestId, null, null))
    context.pullRequestKind?.let { add(RootKindTag.assemble(it)) }
    context.pullRequestAuthor?.let { add(RootAuthorTag.assemble(it, null)) }
    context.sourceEventId?.let { add(ReplyEventTag.assemble(it, null, null)) }
    context.sourceKind?.let { add(ReplyKindTag.assemble(it)) }
    if (withSourceAuthor) context.sourceAuthor?.let { add(PTag.assemble(it, null)) }
    return this
}

/** The common tags of [context]; the trigger (`o`) is written separately. */
fun <T : Event> TagArrayBuilder<T>.ciRunContext(
    context: CiRunContext,
    withSourceAuthor: Boolean = true,
): TagArrayBuilder<T> {
    ciRepositories(context.repositories)
    ciCommits(context.commits)
    ciWorkflow(context.workflow)
    context.gitRef?.let { ciGitRef(it) }
    context.pullRequest?.let { ciPullRequest(it, withSourceAuthor) }
    return this
}

fun <T : Event> TagArrayBuilder<T>.ciProvenance(quote: CiProvenanceQuote) = add(ProvenanceQuoteTag.assemble(quote))

fun <T : Event> TagArrayBuilder<T>.ciJobResults(quotes: List<CiJobResultQuote>) = addAll(quotes.map { JobResultQuoteTag.assemble(it) })

fun <T : Event> TagArrayBuilder<T>.ciConclusion(conclusion: CiConclusion) = addUnique(ConclusionTag.assemble(conclusion))

fun <T : Event> TagArrayBuilder<T>.ciQueuedAt(timestamp: Long) = addUnique(QueuedAtTag.assemble(timestamp))

fun <T : Event> TagArrayBuilder<T>.ciStartedAt(timestamp: Long) = addUnique(StartedAtTag.assemble(timestamp))

fun <T : Event> TagArrayBuilder<T>.ciRunnerFamilies(families: List<String>) = addAll(families.map { RunnerFamilyTag.assemble(it) })

fun <T : Event> TagArrayBuilder<T>.ciRunnerSelectors(selectors: List<CiRunnerSelector>) = addAll(selectors.map { RunnerSelectorTag.assemble(it) })
