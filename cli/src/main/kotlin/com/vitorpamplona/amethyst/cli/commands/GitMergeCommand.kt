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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.gitRepo.dal.isSeriesRoot
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeCheck
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeEngine
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeInputs
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitMergeStatuses
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitClosingReferences
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/**
 * `amy git merge TARGET [--yes]` — merge a pull request or a patch series on the repository's
 * GRASP servers, as gitworkshop and the app do: plan the merge from the servers' objects, then
 * (with `--yes`) sign the new state, push, and publish the applied status. Without `--yes` it only
 * reports what the merge would do.
 */
object GitMergeCommand {
    suspend fun merge(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positional(0, "pr-or-patch-id")
        val confirm = args.bool("yes")
        args.rejectUnknown("relay")

        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val id = GitSupport.resolveEventId(ref) ?: return@use Output.error("bad_args", "expected a note/nevent/64-hex id")
            val item = GitSupport.fetchEvent(ctx, id, args) ?: return@use Output.error("not_found", "no event found for $ref")
            if (item !is GitPullRequestEvent && item !is GitPatchEvent) return@use Output.error("bad_args", "$ref is not a pull request or patch")
            // A series merges from its first patch (a revision, from the original it revises).
            if (item is GitPatchEvent && !item.isSeriesRoot()) return@use Output.error("bad_args", "$ref is not the first patch of a series: pass the series' root")
            val repoTag = GitSupport.repositoryOf(item) ?: return@use Output.error("not_found", "$ref names no repository")
            val dTag = repoTag.dTag
            val repoAddress = Address(repoTag.kind, repoTag.pubKeyHex, dTag)

            // What threads onto the item: revisions, cover notes and renames (and a series' patches).
            val threadFilter = Filter(kinds = listOf(GitPatchEvent.KIND, GitPullRequestEvent.KIND, GitCoverNoteEvent.KIND, LabelEvent.KIND), tags = mapOf("e" to listOf(item.id)))
            val repo =
                GitSupport.loadRepository(ctx, repoAddress, args, RELAY_IDLE_MS, listOf(threadFilter))
                    ?: return@use Output.error("not_found", "no announcement for ${repoTag.toTag()}")
            val membership = repo.membership
            val me = ctx.identity.pubKeyHex
            if (!membership.isMaintainer(me)) return@use Output.error("not_maintainer", "only a confirmed maintainer can merge")
            val repoRelays = repo.relays
            val authority = repo.authority
            val threaded = ctx.store.query<Event>(threadFilter)

            // The series and PR updates of whatever gets merged: the item, or its latest revision.
            val revision = if (item is GitPatchEvent) GitPatchSeries.latestRevision(item, threaded, authority) else null
            val source = revision ?: item
            val sourceFilters =
                listOf(
                    Filter(kinds = listOf(GitPatchEvent.KIND), authors = listOf(source.pubKey), tags = mapOf("e" to listOf(source.id))),
                    Filter(kinds = listOf(GitPullRequestUpdateEvent.KIND), tags = mapOf("E" to listOf(source.id))),
                )
            if (revision != null || item is GitPullRequestEvent) ctx.drain(repoRelays.associateWith { sourceFilters }, RELAY_IDLE_MS)
            val sourceEvents = if (source.id == item.id) threaded + ctx.store.query<Event>(sourceFilters[1]) else ctx.store.query<Event>(sourceFilters)
            val series = (source as? GitPatchEvent)?.let { seriesOf(ctx, it, sourceEvents.filterIsInstance<GitPatchEvent>(), repoRelays) } ?: emptyList()

            val inputs =
                GitMergeInputs.build(
                    item = item,
                    threaded = threaded,
                    seriesOf = { root -> if (root.id == source.id) series else listOf(root) },
                    updatesOf = { pr -> sourceEvents.filterIsInstance<GitPullRequestUpdateEvent>().filter { it.parentPullRequestId() == pr.id } },
                    repository = repoAddress,
                    announcements = membership.confirmedAnnouncements,
                    states = repo.states,
                    authority = authority,
                    authorName = nameOf(ctx, item.pubKey),
                    relayHint = repoRelays.firstOrNull(),
                )
            val patches = inputs.patches

            val engine = GitMergeEngine({ ctx.okhttp }, repo.httpAuth(ctx))
            val email = metadataOf(ctx, me)?.contactMetaData()?.nip05?.takeIf { it.isNotBlank() } ?: "${NPub.create(me)}@nostr"
            val check = engine.check(inputs, GitMergeEngine.committer(nameOf(ctx, me) ?: "Anonymous", email))
            val ready =
                when (check) {
                    is GitMergeCheck.Ready -> check.merge
                    is GitMergeCheck.AlreadyMerged -> return@use Output.error("already_merged", "its commits are already in the branch: mark it with `amy git merged`")
                    is GitMergeCheck.Conflicts -> return@use Output.error("conflicts", "conflicts in: ${check.paths.joinToString(", ")}")
                    is GitMergeCheck.Blocked -> return@use Output.error("blocked", check.blocker.name.lowercase())
                    is GitMergeCheck.Failed -> return@use Output.error("failed", check.message)
                }

            val plan =
                mapOf(
                    "branch" to ready.branch,
                    "old_tip" to ready.oldTip,
                    "new_tip" to ready.plan.newTip,
                    "strategy" to
                        ready.plan.strategy.name
                            .lowercase(),
                    "servers" to ready.targets.map { it.cloneUrl },
                    "not_grasp" to ready.otherClones,
                    "merged_from" to inputs.source.id,
                )
            if (!confirm) {
                Output.emit(plan + mapOf("merged" to false, "hint" to "pass --yes to sign the state, push and mark it merged"))
                return@use 0
            }

            val push = engine.push(ready, ctx.signer, ctx.client)
            if (!push.result.isSuccess) return@use Output.error("push_failed", push.result.describeFailures())
            // A private repository's state stays on its own relays (GRASP-08).
            val stateRelays = repoRelays + ready.targets.map { it.relay }
            ctx.publish(push.state, if (repo.isPrivate) stateRelays else stateRelays + ctx.outboxRelays())

            val coordinates = membership.confirmedCoordinates()
            val euc = membership.confirmedAnnouncements.firstNotNullOfOrNull { it.earliestUniqueCommit() }
            val itemHint = EventHintBundle<Event>(item, repoRelays.firstOrNull())
            val status =
                ctx.signer.sign(
                    GitMergeStatuses.merged(
                        target = itemHint,
                        coordinates = coordinates,
                        earliestUniqueCommit = euc,
                        signer = me,
                        mergeCommit = ready.plan.mergeCommit,
                        appliedPatches = patches.map { EventHintBundle(it) },
                        appliedAsCommits = ready.plan.appliedCommits,
                    ),
                )
            val ack = ctx.publish(status, repoRelays)

            // Issues its commits close, on the default branch only, each once and only while open.
            val resolved =
                if (!ready.isDefaultBranch) {
                    emptyList()
                } else {
                    val commits = ready.newCommits.map { GitMergeStatuses.MergedCommit(it.oid, it.message) }
                    val references = commits.flatMap { GitClosingReferences.parse(it.message) }
                    // Issues a full id names, by id; every issue only when a #prefix has to match.
                    val issueFilters =
                        listOfNotNull(
                            references
                                .filter { !it.isPrefix }
                                .map { it.id }
                                .takeIf { it.isNotEmpty() }
                                ?.let { Filter(ids = it, kinds = listOf(GitIssueEvent.KIND)) },
                            if (references.any { it.isPrefix }) Filter(kinds = listOf(GitIssueEvent.KIND), tags = mapOf("a" to coordinates.map { it.toValue() })) else null,
                        )
                    if (issueFilters.isNotEmpty()) ctx.drain(repoRelays.associateWith { issueFilters }, RELAY_IDLE_MS)
                    val issues = if (issueFilters.isEmpty()) emptyList() else ctx.store.query<GitIssueEvent>(issueFilters)
                    // Statuses only for the issues the commits name, not the whole tracker.
                    val named = GitMergeStatuses.resolutions(commits, issues, coordinates.toSet()) { true }.map { it.issue }
                    val statusFilter = Filter(kinds = listOf(GitStatusEvent.KIND_OPEN, GitStatusEvent.KIND_APPLIED, GitStatusEvent.KIND_CLOSED, GitStatusEvent.KIND_DRAFT), tags = mapOf("e" to named.map { it.id }))
                    if (named.isNotEmpty()) ctx.drain(repoRelays.associateWith { listOf(statusFilter) }, RELAY_IDLE_MS)
                    val latest = if (named.isEmpty()) emptyMap() else GitStatuses.latestByItem(ctx.store.query<GitStatusEvent>(statusFilter), named.associateBy { it.id }, authority)
                    GitMergeStatuses.resolutions(commits, named, coordinates.toSet()) { !GitStatuses.isClosedOrResolved(latest[it.id]) }.map { resolution ->
                        val signed =
                            ctx.signer.sign(
                                GitMergeStatuses.resolved(resolution, EventHintBundle<Event>(resolution.issue), ready.plan.mergeCommit, itemHint, coordinates, euc, me),
                            )
                        ctx.publish(signed, repoRelays)
                        resolution.issue.id
                    }
                }

            Output.emit(
                plan +
                    mapOf(
                        "merged" to true,
                        "merge_commit" to ready.plan.mergeCommit,
                        "state_event_id" to push.state.id,
                        "status_event_id" to status.id,
                        "pushed" to
                            push.result.servers
                                .filter { it.pushed }
                                .map { it.target.cloneUrl },
                        "not_pushed" to
                            push.result.servers
                                .filter { !it.pushed }
                                .associate { it.target.cloneUrl to it.error },
                    ) + RawEventSupport.ackFields(ack),
            )
            0
        }
    }

    /** Small queries: a relay quiet this long has nothing more to send. */
    private const val RELAY_IDLE_MS = 3_000L

    /**
     * [root]'s series in order. ngit tags every patch with the root, but a client that only marks
     * `reply` threads each patch under the one before, so the chain is followed relay by relay
     * until it stops: a series is never merged with its tail cut off.
     */
    private suspend fun seriesOf(
        ctx: Context,
        root: GitPatchEvent,
        known: List<GitPatchEvent>,
        relays: Set<NormalizedRelayUrl>,
    ): List<GitPatchEvent> {
        val found = LinkedHashMap<String, GitPatchEvent>()

        fun belongs(patch: GitPatchEvent) =
            patch.id != root.id && patch.pubKey == root.pubKey && !patch.isCoverLetter() && !patch.isRootRevision() &&
                (patch.seriesRootId() == null || patch.seriesRootId() == root.id)
        known.filter(::belongs).forEach { found[it.id] = it }
        var chain = GitPatchSeries.chain(root, found.values)
        // ngit tags every patch with the root, so a series whose patches all carry it is complete.
        if (found.isNotEmpty() && found.values.all { it.seriesRootId() == root.id } && chain.size == found.size + 1) return chain
        repeat(MAX_SERIES_ROUNDS) {
            val last = chain.last()
            val next = Filter(kinds = listOf(GitPatchEvent.KIND), authors = listOf(root.pubKey), tags = mapOf("e" to listOf(last.id)))
            ctx.drain(relays.associateWith { listOf(next) }, RELAY_IDLE_MS)
            val before = found.size
            ctx.store
                .query<GitPatchEvent>(next)
                .filter(::belongs)
                .forEach { found[it.id] = it }
            val longer = GitPatchSeries.chain(root, found.values)
            if (found.size == before || longer.size == chain.size) return longer
            chain = longer
        }
        return chain
    }

    private const val MAX_SERIES_ROUNDS = 64

    private suspend fun metadataOf(
        ctx: Context,
        pubKey: String,
    ): MetadataEvent? = ctx.store.query<MetadataEvent>(Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubKey), limit = 1)).firstOrNull()

    private suspend fun nameOf(
        ctx: Context,
        pubKey: String,
    ): String? = metadataOf(ctx, pubKey)?.contactMetaData()?.let { it.displayName ?: it.name }?.takeIf { it.isNotBlank() }
}
