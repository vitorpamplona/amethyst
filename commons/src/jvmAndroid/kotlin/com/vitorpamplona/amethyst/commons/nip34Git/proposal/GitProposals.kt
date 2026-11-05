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
package com.vitorpamplona.amethyst.commons.nip34Git.proposal

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitPrivateRepositoryAuth
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeEngine
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeTargets
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth
import com.vitorpamplona.quartz.nip34Git.git.GitObject
import com.vitorpamplona.quartz.nip34Git.git.GitPerson
import com.vitorpamplona.quartz.nip34Git.git.GitPushClient
import com.vitorpamplona.quartz.nip34Git.git.GitSmartHttpTransport
import com.vitorpamplona.quartz.nip34Git.merge.GitBranchObjects
import com.vitorpamplona.quartz.nip34Git.merge.GitObjectSource
import com.vitorpamplona.quartz.nip34Git.merge.GraspProposalPusher
import com.vitorpamplona.quartz.nip34Git.merge.commitChanges
import com.vitorpamplona.quartz.nip34Git.merge.mergeBaseOf
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import okhttp3.OkHttpClient

/** A repository a pull request is opened against: its announcement and where its servers are. */
class GitProposalTarget(
    val repository: Address,
    val announcement: GitRepositoryEvent,
    val relayHint: NormalizedRelayUrl?,
    val announcements: List<GitRepositoryEvent>,
    val signedState: GitSignedState?,
    val targets: GitMergeTargets,
) {
    val defaultBranch: String? get() = signedState?.headBranch

    /** The relays the repository is read on: each confirmed announcement's, and its GRASP servers'. */
    val relays: Set<NormalizedRelayUrl> get() = announcements.flatMapTo(HashSet()) { it.relaysNorm() } + targets.grasp.map { it.relay }
}

/** How opening (or updating) a pull request went. */
sealed interface GitProposalResult {
    class Published(
        val event: Event,
        /** The GRASP servers that hold its commits; empty when it names a host that already does. */
        val servers: List<String>,
    ) : GitProposalResult

    class Failed(
        val reason: String,
    ) : GitProposalResult
}

/**
 * Opens and updates pull requests from the app, as ngit does: the event is signed, its commits are
 * pushed to `refs/nostr/<event-id>` on the repository's GRASP servers (anyone may), and only once a
 * server holds them is the event published, naming those servers as its clone URLs. An edit made in
 * the app becomes a commit on the branch's tip and opens such a pull request.
 */
class GitProposals(
    private val account: Account,
    okHttpClient: (String) -> OkHttpClient,
    private val cache: LocalCache = account.cache,
    private val authority: GitAuthority = LocalCache.gitAuthority,
    auth: GitHttpAuth? = GitPrivateRepositoryAuth(account.signer, cache),
) {
    private val transport = GitSmartHttpTransport(okHttpClient, auth)
    private val pusher = GraspProposalPusher(GitPushClient(okHttpClient, "amethyst", auth))

    /** The repository [repositoryNote] (an announcement) proposes into, from what the cache holds. */
    fun targetFor(repositoryNote: AddressableNote): GitProposalTarget? {
        val announcement = repositoryNote.event as? GitRepositoryEvent ?: return null
        val address = repositoryNote.address
        val membership = authority.membership(address)
        val announcements = membership?.confirmedAnnouncements ?: listOf(announcement)
        val maintainers = membership?.confirmedMaintainers ?: listOf(address.pubKeyHex)
        val states =
            maintainers.mapNotNull { cache.getAddressableNoteIfExists(Address(GitRepositoryStateEvent.KIND, it, address.dTag))?.event as? GitRepositoryStateEvent }
        return GitProposalTarget(address, announcement, repositoryNote.relayHintUrl(), announcements, GitSignedState.latest(states), GitMergeTargets.of(announcements))
    }

    /** The branches [cloneUrl] serves, by name, with their tips. */
    suspend fun branches(cloneUrl: String): List<Pair<String, String>> {
        val caps = transport.fetchCapabilities(cloneUrl)
        return transport
            .lsRefs(cloneUrl, caps)
            .filter { it.name.startsWith("refs/heads/") }
            .map { it.name.removePrefix("refs/heads/") to it.oid }
            .sortedBy { it.first }
    }

    /**
     * Opens a pull request of [tip] (served by [sourceClones]) into [targetBranch] (the default
     * branch when null): its merge base is found from the servers, its commits are pushed to the
     * repository's GRASP servers, and the event is published once one holds them.
     */
    suspend fun openPullRequest(
        target: GitProposalTarget,
        sourceClones: List<String>,
        tip: String,
        title: String,
        description: String,
        labels: List<String> = emptyList(),
        branchName: String? = null,
        targetBranch: String? = null,
        newObjects: List<GitObject> = emptyList(),
        knownMergeBase: String? = null,
    ): GitProposalResult {
        val branch = targetBranch ?: target.defaultBranch
        val graspClones = target.targets.grasp.map { it.cloneUrl }
        val targetClones = graspClones + target.targets.others
        val head = branch?.let { target.signedState?.commitFor(it) }

        val sourceObjects = GitObjectSource(transport, (sourceClones + targetClones).distinct())
        val base =
            knownMergeBase ?: head?.let { runCatching { mergeBaseOf(GitObjectSource(transport, targetClones), it, sourceObjects, tip) }.getOrNull() }
        val branchObjects = base?.let { GitBranchObjects(tip, it, sourceObjects) }

        val repositoryHint = EventHintBundle(target.announcement, target.relayHint)
        val euc = target.announcements.firstNotNullOfOrNull { it.earliestUniqueCommit() } ?: ""

        fun template(clones: List<String>): EventTemplate<GitPullRequestEvent> =
            GitPullRequestEvent.build(
                description = description,
                repository = repositoryHint,
                earliestUniqueCommit = euc,
                currentCommit = tip,
                cloneUrls = clones,
                subject = title,
                labels = labels,
                branchName = branchName,
                mergeBase = base,
                targetBranch = targetBranch?.takeIf { it != target.defaultBranch },
            )

        return publishWithCommits(
            target = target,
            graspClones = graspClones,
            fallbackClones = sourceClones,
            tip = tip,
            newObjects = newObjects,
            branchObjects = branchObjects,
            template = ::template,
        )
    }

    /** Moves [pullRequest] (its author's, or a maintainer's) to [tip] served by [sourceClones]: a kind 1619 with its commits pushed first. */
    suspend fun updatePullRequest(
        pullRequest: Note,
        target: GitProposalTarget,
        sourceClones: List<String>,
        tip: String,
    ): GitProposalResult {
        val pr = pullRequest.event as? GitPullRequestEvent ?: return GitProposalResult.Failed("not a pull request")
        val prHint = pullRequest.toEventHint<GitPullRequestEvent>() ?: return GitProposalResult.Failed("not a pull request")
        val graspClones = target.targets.grasp.map { it.cloneUrl }
        val targetClones = graspClones + target.targets.others
        val branch = pr.targetBranch() ?: target.defaultBranch
        val head = branch?.let { target.signedState?.commitFor(it) }
        val sourceObjects = GitObjectSource(transport, (sourceClones + targetClones).distinct())
        val base = head?.let { runCatching { mergeBaseOf(GitObjectSource(transport, targetClones), it, sourceObjects, tip) }.getOrNull() }
        val repositoryHint = EventHintBundle(target.announcement, target.relayHint)
        val euc = target.announcements.firstNotNullOfOrNull { it.earliestUniqueCommit() } ?: ""
        return publishWithCommits(
            target = target,
            graspClones = graspClones,
            fallbackClones = sourceClones,
            tip = tip,
            newObjects = emptyList(),
            branchObjects = base?.let { GitBranchObjects(tip, it, sourceObjects) },
        ) { clones -> GitPullRequestUpdateEvent.build(prHint, repositoryHint, euc, tip, clones, base) }
    }

    /**
     * Proposes [changes] (paths to new bytes, or null to delete) on top of [parent], the tip of
     * [branch] as served by [sourceClones]: a commit by the user, opened as a pull request into it.
     */
    suspend fun proposeEdit(
        target: GitProposalTarget,
        sourceClones: List<String>,
        branch: String?,
        parent: String,
        changes: Map<List<String>, ByteArray?>,
        message: String,
        description: String,
    ): GitProposalResult {
        val commit = GitObjectSource(transport, sourceClones).commitChanges(parent, changes, author(), message)
        val title =
            message
                .lineSequence()
                .firstOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { message }
        val body = message.substringAfter('\n', "").trim()
        return openPullRequest(
            target = target,
            sourceClones = sourceClones,
            tip = commit.commit,
            title = title,
            description = description.ifBlank { body },
            branchName =
                "edit-" + (
                    changes.keys
                        .firstOrNull()
                        ?.lastOrNull()
                        ?.substringBefore('.') ?: "file"
                ),
            targetBranch = branch,
            newObjects = commit.objects,
            knownMergeBase = parent,
        )
    }

    /** The user as git writes them: their name, and NIP-05 address (or npub) as the email. */
    fun author(): GitPerson {
        val me = account.userProfile()
        val info =
            me
                .metadataOrNull()
                ?.flow
                ?.value
                ?.info
        val name = info?.bestName()?.takeIf { it.isNotBlank() } ?: "Anonymous"
        val email = info?.nip05?.takeIf { it.isNotBlank() } ?: "${me.pubkeyNpub()}@nostr"
        return GitMergeEngine.committer(name, email)
    }

    /**
     * Signs the event built by [template] with every GRASP server as a clone URL, pushes its
     * commits to them, and publishes it naming those that took them (signing again when some
     * didn't, as the event must not point at a server without the commits). A repository with no
     * GRASP server gets the event as is, pointing at the [fallbackClones] that serve the commits.
     */
    private suspend fun <T : Event> publishWithCommits(
        target: GitProposalTarget,
        graspClones: List<String>,
        fallbackClones: List<String>,
        tip: String,
        newObjects: List<GitObject>,
        branchObjects: GitBranchObjects?,
        template: (List<String>) -> EventTemplate<T>,
    ): GitProposalResult {
        if (graspClones.isEmpty()) {
            if (newObjects.isNotEmpty()) return GitProposalResult.Failed("the repository has no GRASP server to hold a commit made here")
            return GitProposalResult.Published(publish(target, account.signer.sign(template(fallbackClones))), emptyList())
        }
        var event: Event = account.signer.sign(template(graspClones))
        val results = pusher.push(event.id, graspClones, tip, newObjects, branchObjects)
        val accepted = graspClones.filter { results[it] == null }
        if (accepted.isEmpty()) return GitProposalResult.Failed(results.entries.joinToString("; ") { "${it.key}: ${it.value}" })
        if (accepted.size < graspClones.size) {
            event = account.signer.sign(template(accepted))
            val again = pusher.push(event.id, accepted, tip, newObjects, branchObjects)
            if (accepted.none { again[it] == null }) return GitProposalResult.Failed(again.entries.joinToString("; ") { "${it.key}: ${it.value}" })
        }
        return GitProposalResult.Published(publish(target, event), accepted)
    }

    private fun publish(
        target: GitProposalTarget,
        event: Event,
    ): Event {
        cache.justConsumeMyOwnEvent(event)
        // A private repository's events stay on its own relays (GRASP-08).
        val wider = if (target.announcements.any { it.isPrivate() }) emptySet() else account.computeRelayListToBroadcast(event) + account.outboxRelays.flow.value
        account.client.publish(event, target.relays + wider)
        return event
    }
}
