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
package com.vitorpamplona.amethyst.commons.nip34Git.merge

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.amethyst.commons.nip34Git.status.publishGitApplied
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.issue.GitClosingReferences
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import okhttp3.OkHttpClient

/** Where a merge is, for a progress line. */
enum class GitMergeStep { PUBLISHING_STATE_AND_PUSHING, PUBLISHING_STATUS }

/** How a merge ended. */
sealed interface GitMergeOutcome {
    /** The branch moved on [push]'s servers; [resolvedIssues] issues were marked resolved by its commits. */
    class Merged(
        val push: GitMergePush,
        val resolvedIssues: Int,
    ) : GitMergeOutcome

    /** No server took the push: nothing but the signed state went out, and it named a tip no server has. */
    class PushFailed(
        val reason: String,
    ) : GitMergeOutcome
}

/**
 * The app's side of a merge ([GitMergeEngine]): reads the item, its series or PR tip, the
 * repository's announcements and signed state from the cache, and after the push publishes what
 * gitworkshop publishes: the new state to the repository's relays and the user's outbox, the
 * applied status (1631) naming the merge commit, and a resolved status on every open issue a
 * merged commit says it closes.
 */
class GitMerger(
    private val account: Account,
    okHttpClient: (String) -> OkHttpClient,
    private val cache: LocalCache = account.cache,
    private val authority: GitAuthority = LocalCache.gitAuthority,
) {
    private val engine = GitMergeEngine(okHttpClient)

    /** Null when [item] is not a PR or a series' root patch, or its repository hasn't loaded. */
    fun inputsFor(item: Note): GitMergeInputs? {
        val event = item.event ?: return null
        if (event !is GitPullRequestEvent && event !is GitPatchEvent) return null
        val repository = GitAuthority.repositoriesOf(event).firstOrNull() ?: return null
        val membership = authority.membership(repository) ?: return null

        val states =
            membership.confirmedMaintainers.mapNotNull { maintainer ->
                cache.getAddressableNoteIfExists(Address(GitRepositoryStateEvent.KIND, maintainer, repository.dTag))?.event as? GitRepositoryStateEvent
            }

        val update = if (event is GitPullRequestEvent) GitPullRequestRevisions.latest(item, authority) else null
        val coverNote = GitCoverNotes.latest(item, authority)
        val ownText =
            when (event) {
                is GitPullRequestEvent -> event.content
                is GitPatchEvent -> event.description()
                else -> null
            }?.takeIf { it.isNotBlank() }

        return GitMergeInputs(
            item = event,
            patches = if (event is GitPatchEvent) GitPatchSeries.of(item) else emptyList(),
            pullRequestTip = update?.currentCommit() ?: (event as? GitPullRequestEvent)?.currentCommit(),
            pullRequestClones = ((update?.cloneUrls() ?: emptyList()) + ((event as? GitPullRequestEvent)?.cloneUrls() ?: emptyList())).distinct(),
            repository = repository,
            announcements = membership.confirmedAnnouncements,
            signedState = GitSignedState.latest(states),
            title = GitItemTitles.titleOf(item, authority) ?: "",
            nostrUri = NEvent.createNostrUri(event.id, event.pubKey, event.kind, item.relayHintUrl()),
            authorName =
                cache
                    .getUserIfExists(event.pubKey)
                    ?.metadataOrNull()
                    ?.flow
                    ?.value
                    ?.info
                    ?.bestName(),
            authorNpub = NPub.create(event.pubKey),
            description = coverNote?.content?.takeIf { it.isNotBlank() } ?: ownText,
            descriptionIsCoverNote = coverNote != null && coverNote.content.isNotBlank(),
        )
    }

    /** Plans the merge of [item], fetching what it needs from the repository's servers. */
    suspend fun check(item: Note): GitMergeCheck {
        val inputs = inputsFor(item) ?: return GitMergeCheck.Failed("the repository has not loaded")
        val me = account.userProfile()
        val info =
            me
                .metadataOrNull()
                ?.flow
                ?.value
                ?.info
        val name = info?.bestName()?.takeIf { it.isNotBlank() } ?: "Anonymous"
        val email = info?.nip05?.takeIf { it.isNotBlank() } ?: "${me.pubkeyNpub()}@nostr"
        return engine.check(inputs, GitMergeEngine.committer(name, email))
    }

    suspend fun merge(
        item: Note,
        merge: GitPreparedMerge,
        onStep: (GitMergeStep) -> Unit = {},
    ): GitMergeOutcome {
        onStep(GitMergeStep.PUBLISHING_STATE_AND_PUSHING)
        val push = engine.push(merge, account.signer, account.client)
        if (!push.result.isSuccess) return GitMergeOutcome.PushFailed(push.result.describeFailures())

        // The state the servers took, now everywhere the repository is read.
        cache.justConsumeMyOwnEvent(push.state)
        val relays = merge.inputs.announcements.flatMapTo(HashSet()) { it.relaysNorm() } + merge.targets.map { it.relay } + account.outboxRelays.flow.value
        account.client.publish(push.state, relays)

        onStep(GitMergeStep.PUBLISHING_STATUS)
        val patches = if (item.event is GitPatchEvent) GitPatchSeries.of(item).map { cache.getOrCreateNote(it.id) } else emptyList()
        account.publishGitApplied(
            item = item,
            mergeCommit = merge.plan.mergeCommit,
            appliedPatches = patches,
            appliedAsCommits = merge.plan.appliedCommits,
            authority = authority,
        )

        val resolved = if (merge.isDefaultBranch) resolveIssues(item, merge) else 0
        return GitMergeOutcome.Merged(push, resolved)
    }

    /**
     * Marks resolved each open issue of this repository that a merged commit's message closes
     * ([GitClosingReferences]), once, quoting the line that closed it, as gitworkshop does.
     */
    private suspend fun resolveIssues(
        item: Note,
        merge: GitPreparedMerge,
    ): Int {
        val repository = cache.getGitRepositoryIfExists(merge.inputs.repository.dTag) ?: return 0
        val coordinates = authority.coordinatesToTag(merge.inputs.repository).toSet()
        val issues = ArrayList<Note>()
        repository.items.forEach { _, note -> if (note.event is GitIssueEvent) issues.add(note) }

        fun find(
            id: HexKey,
            isPrefix: Boolean,
        ): Note? {
            val matches = issues.filter { if (isPrefix) it.idHex.startsWith(id) else it.idHex == id }
            val issue = matches.singleOrNull() ?: return null
            val event = issue.event as? GitIssueEvent ?: return null
            return issue.takeIf { GitAuthority.repositoriesOf(event).any { it in coordinates } }
        }

        val done = HashSet<HexKey>()
        var count = 0
        val itemEvent = item.event ?: return 0
        merge.newCommits.forEach { commit ->
            GitClosingReferences.parse(commit.message).forEach { reference ->
                val issue = find(reference.id, reference.isPrefix) ?: return@forEach
                if (!done.add(issue.idHex) || GitStatuses.isClosedOrResolved(issue, authority)) return@forEach
                val content =
                    buildString {
                        append(reference.line).append("\n\nresolved by commit ").append(commit.oid)
                        if (merge.plan.mergeCommit != commit.oid) append(", when merged in commit ").append(merge.plan.mergeCommit)
                    }
                account.publishGitApplied(item = issue, mergeCommit = merge.plan.mergeCommit, content = content, authority = authority) {
                    alt("issue resolved from commit message")
                    add(arrayOf("c", commit.oid))
                    add(QEventTag.assemble(itemEvent.id, item.relayHintUrl(), itemEvent.pubKey))
                }
                count++
            }
        }
        return count
    }
}
