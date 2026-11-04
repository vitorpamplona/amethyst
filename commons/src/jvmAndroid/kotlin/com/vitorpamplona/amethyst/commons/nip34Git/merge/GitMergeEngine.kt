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

import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.git.GitObjectParser
import com.vitorpamplona.quartz.nip34Git.git.GitObjectType
import com.vitorpamplona.quartz.nip34Git.git.GitPerson
import com.vitorpamplona.quartz.nip34Git.git.GitPushClient
import com.vitorpamplona.quartz.nip34Git.git.GitSmartHttpTransport
import com.vitorpamplona.quartz.nip34Git.merge.GitMergeMessage
import com.vitorpamplona.quartz.nip34Git.merge.GitMergePlan
import com.vitorpamplona.quartz.nip34Git.merge.GitMergePlanner
import com.vitorpamplona.quartz.nip34Git.merge.GitObjectSource
import com.vitorpamplona.quartz.nip34Git.merge.GraspPushResult
import com.vitorpamplona.quartz.nip34Git.merge.GraspPusher
import com.vitorpamplona.quartz.nip34Git.merge.GraspTarget
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import okhttp3.OkHttpClient
import java.util.TimeZone

/**
 * Everything a merge reads about an item and its repository, gathered by the caller (the app from
 * its cache, `amy` from relays) so the merge itself is the same for both.
 *
 * @param item the pull request (1618) or the root patch (1617) of a series.
 * @param patches for a patch series, its patches in order, [item] first.
 * @param pullRequestTip the PR's current tip: its latest authorised update's, else its own.
 * @param pullRequestClones where that tip is served: the update's and the PR's clone URLs.
 * @param announcements the repository's confirmed announcements: their clone URLs are pushed to.
 * @param signedState the maintainers' latest signed state (kind 30618), the branch tips a merge starts from.
 * @param description the current cover note ([descriptionIsCoverNote]), else the item's own text.
 */
class GitMergeInputs(
    val item: Event,
    val patches: List<GitPatchEvent>,
    val pullRequestTip: String?,
    val pullRequestClones: List<String>,
    val repository: Address,
    val announcements: List<GitRepositoryEvent>,
    val signedState: GitSignedState?,
    val title: String,
    val nostrUri: String,
    val authorName: String?,
    val authorNpub: String,
    val description: String?,
    val descriptionIsCoverNote: Boolean,
)

/** Why a merge can't be made in the app; the maintainer merges with git instead. */
enum class GitMergeBlocker {
    /** The item's repository announcement has not loaded yet. */
    REPOSITORY_NOT_LOADED,

    /** Some clone URL is not a GRASP server, which only a local `git push` can reach. */
    NOT_ON_GRASP,

    /** The maintainers never signed a state (kind 30618), so there is no branch tip to start from. */
    NO_SIGNED_STATE,

    /** The signed state has no tip for the branch the item targets. */
    NO_BRANCH_TIP,

    /** The pull request names no tip commit. */
    NO_PULL_REQUEST_TIP,
}

/** What merging an item here would do, before anything is signed. */
sealed interface GitMergeCheck {
    class Blocked(
        val blocker: GitMergeBlocker,
    ) : GitMergeCheck

    class Conflicts(
        val paths: List<String>,
    ) : GitMergeCheck

    object AlreadyMerged : GitMergeCheck

    class Failed(
        val message: String,
    ) : GitMergeCheck

    class Ready(
        val merge: GitPreparedMerge,
    ) : GitMergeCheck
}

/**
 * A merge ready to publish: [branch] moves from [oldTip] to [plan]'s new tip on [targets].
 * [newCommits] are the commits the branch gains (the merge commit aside), whose messages may
 * resolve issues; [isDefaultBranch] says whether they count for that (only the default branch's do).
 */
class GitPreparedMerge(
    val inputs: GitMergeInputs,
    val branch: String,
    val isDefaultBranch: Boolean,
    val oldTip: String,
    val plan: GitMergePlan.Ready,
    val targets: List<GraspTarget>,
    val newCommits: List<GitCommit>,
)

/** The signed state a merge published, and how each GRASP server took the push. */
class GitMergePush(
    val state: GitRepositoryStateEvent,
    val result: GraspPushResult,
)

/**
 * Merges a NIP-34 pull request or patch series on the repository's GRASP servers, as gitworkshop
 * does in the browser: plan the merge from the servers' objects ([check]), then sign the new
 * state, publish it to each server's relay and push to the servers whose relay took it ([push]).
 * The applied status and the state's wider broadcast follow a successful push, by the caller.
 */
class GitMergeEngine(
    okHttpClient: (String) -> OkHttpClient,
) {
    private val transport = GitSmartHttpTransport(okHttpClient)
    private val pushClient = GitPushClient(okHttpClient, agent = "amethyst")

    suspend fun check(
        inputs: GitMergeInputs,
        committer: GitPerson,
    ): GitMergeCheck {
        val announcements = inputs.announcements
        if (announcements.isEmpty() || !announcements.all(GraspTarget::coversAllClones)) return GitMergeCheck.Blocked(GitMergeBlocker.NOT_ON_GRASP)
        val targets = announcements.flatMap(GraspTarget::of).distinctBy { it.cloneUrl }

        val state = inputs.signedState ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_SIGNED_STATE)
        val pr = inputs.item as? GitPullRequestEvent
        val branch = pr?.targetBranch() ?: state.headBranch ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_BRANCH_TIP)
        val oldTip = state.commitFor(branch) ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_BRANCH_TIP)

        val clones = targets.map { it.cloneUrl }
        // The repository's servers first; a PR's blob may be only on the PR's own servers.
        val source = GitObjectSource(transport, (clones + inputs.pullRequestClones).distinct())
        val message =
            GitMergeMessage.build(
                rootId = inputs.item.id,
                title = inputs.title,
                nostrUri = inputs.nostrUri,
                authorName = inputs.authorName,
                authorNpub = inputs.authorNpub,
                description = inputs.description,
                descriptionIsCoverNote = inputs.descriptionIsCoverNote,
            )

        val plan =
            if (pr != null) {
                val tip = inputs.pullRequestTip ?: return GitMergeCheck.Blocked(GitMergeBlocker.NO_PULL_REQUEST_TIP)
                // A GRASP server keeps each PR's commits under refs/nostr/<id>, so the repository's
                // own servers come after the PR's.
                val prSource = GitObjectSource(transport, (inputs.pullRequestClones + clones).distinct())
                GitMergePlanner(source).planPullRequest(oldTip, tip, prSource, committer, message)
            } else {
                GitMergePlanner(source).planPatches(oldTip, inputs.patches, committer, message)
            }

        return when (plan) {
            is GitMergePlan.AlreadyMerged -> GitMergeCheck.AlreadyMerged
            is GitMergePlan.Conflicts -> GitMergeCheck.Conflicts(plan.paths)
            is GitMergePlan.Failed -> GitMergeCheck.Failed(plan.reason)
            is GitMergePlan.Ready -> {
                val commits =
                    plan.objects
                        .filter { it.type == GitObjectType.COMMIT && it.oid != plan.mergeCommit }
                        .map { GitObjectParser.parseCommit(it.oid, it.data) }
                GitMergeCheck.Ready(GitPreparedMerge(inputs, branch, branch == state.headBranch, oldTip, plan, targets, commits))
            }
        }
    }

    /** Signs the state that moves the branch and pushes it; nothing else is published. */
    suspend fun push(
        merge: GitPreparedMerge,
        signer: NostrSigner,
        client: INostrClient,
    ): GitMergePush {
        val previous = merge.inputs.signedState?.event
        val template =
            GitRepositoryStateEvent.advance(
                previous = previous,
                dTag = merge.inputs.repository.dTag,
                branch = merge.branch,
                commit = merge.plan.newTip,
                makeHead = merge.isDefaultBranch,
            )
        val state = signer.sign(template)
        val result = GraspPusher(client, pushClient).push(state, merge.targets, merge.branch, merge.plan.newTip, merge.plan.objects, merge.plan.branch)
        return GitMergePush(state, result)
    }

    companion object {
        /** The committer of a merge made now by [name] (`<email>`), in this device's time zone, as gitworkshop writes it. */
        fun committer(
            name: String,
            email: String,
            nowSec: Long = System.currentTimeMillis() / 1000,
        ): GitPerson = GitPerson(name, email, nowSec, TimeZone.getDefault().getOffset(nowSec * 1000) / 60_000)
    }
}
