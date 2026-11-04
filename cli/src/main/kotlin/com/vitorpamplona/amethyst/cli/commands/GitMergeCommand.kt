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
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeCheck
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeEngine
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeInputs
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.repository

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
            val repoTag = GitSupport.repositoryOf(item) ?: return@use Output.error("not_found", "$ref names no repository")
            val dTag = repoTag.dTag
            val repoAddress = Address(repoTag.kind, repoTag.pubKeyHex, dTag)

            // Every announcement under the identifier: the membership decides whose count.
            val lead = GitSupport.fetchRepo(ctx, repoAddress, args)
            val relays = GitSupport.readTargets(ctx, lead, args)
            val announcementFilter = Filter(kinds = listOf(GitRepositoryEvent.KIND), tags = mapOf("d" to listOf(dTag)))
            ctx.drain(relays.associateWith { listOf(announcementFilter) })
            val announcements =
                ctx.store
                    .query<GitRepositoryEvent>(announcementFilter)
                    .groupBy { it.pubKey }
                    .mapValues { (_, events) -> events.maxBy { it.createdAt } }
            val membership =
                GitRepositoryMembership.resolveFromLead(repoTag.pubKeyHex, dTag, announcements)
                    ?: return@use Output.error("not_found", "no announcement for ${repoTag.toTag()}")
            val me = ctx.identity.pubKeyHex
            if (!membership.isMaintainer(me)) return@use Output.error("not_maintainer", "only a confirmed maintainer can merge")

            val repoRelays = relays + membership.confirmedAnnouncements.flatMap { it.relaysNorm() }
            val stateFilter = Filter(kinds = listOf(GitRepositoryStateEvent.KIND), authors = membership.confirmedMaintainers, tags = mapOf("d" to listOf(dTag)))
            val updateFilter = Filter(kinds = listOf(GitPullRequestUpdateEvent.KIND), tags = mapOf("E" to listOf(item.id)))
            val seriesFilter = Filter(kinds = listOf(GitPatchEvent.KIND), authors = listOf(item.pubKey), tags = mapOf("e" to listOf(item.id)))
            ctx.drain(repoRelays.associateWith { listOf(stateFilter, updateFilter, seriesFilter) })

            val state = GitSignedState.latest(ctx.store.query<GitRepositoryStateEvent>(stateFilter))
            val update =
                if (item is GitPullRequestEvent) {
                    ctx.store
                        .query<GitPullRequestUpdateEvent>(updateFilter)
                        .filter { it.parentPullRequestId() == item.id && (it.pubKey == item.pubKey || membership.isMaintainer(it.pubKey)) }
                        .maxWithOrNull(compareBy<GitPullRequestUpdateEvent> { it.createdAt }.thenByDescending { it.id })
                } else {
                    null
                }
            val patches =
                if (item is GitPatchEvent) {
                    GitPatchSeries.chain(item, ctx.store.query<GitPatchEvent>(seriesFilter).filter { it.id != item.id && it.seriesRootId() == item.id && !it.isCoverLetter() })
                } else {
                    emptyList()
                }

            val inputs =
                GitMergeInputs(
                    item = item,
                    patches = patches,
                    pullRequestTip = update?.currentCommit() ?: (item as? GitPullRequestEvent)?.currentCommit(),
                    pullRequestClones = ((update?.cloneUrls() ?: emptyList()) + ((item as? GitPullRequestEvent)?.cloneUrls() ?: emptyList())).distinct(),
                    repository = repoAddress,
                    announcements = membership.confirmedAnnouncements,
                    signedState = state,
                    title = (item as? GitPullRequestEvent)?.subject() ?: (item as? GitPatchEvent)?.title() ?: "",
                    nostrUri = NEvent.createNostrUri(item.id, item.pubKey, item.kind, repoRelays.firstOrNull()),
                    authorName = nameOf(ctx, item.pubKey),
                    authorNpub = NPub.create(item.pubKey),
                    description = ((item as? GitPullRequestEvent)?.content ?: (item as? GitPatchEvent)?.description())?.takeIf { it.isNotBlank() },
                    descriptionIsCoverNote = false,
                )

            val engine = GitMergeEngine { ctx.okhttp }
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
                )
            if (!confirm) {
                Output.emit(plan + mapOf("merged" to false, "hint" to "pass --yes to sign the state, push and mark it merged"))
                return@use 0
            }

            val push = engine.push(ready, ctx.signer, ctx.client)
            if (!push.result.isSuccess) return@use Output.error("push_failed", push.result.describeFailures())
            ctx.publish(push.state, repoRelays + ready.targets.map { it.relay } + ctx.outboxRelays())

            val coordinates = membership.confirmedCoordinates()
            val status =
                ctx.signer.sign(
                    GitStatusAppliedEvent.build(
                        content = "",
                        target = EventHintBundle<Event>(item),
                        appliedPatches = patches.map { EventHintBundle(it) },
                        mergeCommit = ready.plan.mergeCommit,
                        appliedAsCommits = ready.plan.appliedCommits,
                        notify =
                            coordinates
                                .map { it.pubKeyHex }
                                .filter { it != me && it != item.pubKey }
                                .distinct()
                                .map { PTag(it) },
                    ) { coordinates.forEach { repository(ATag(it)) } },
                )
            val ack = ctx.publish(status, repoRelays)
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

    private suspend fun metadataOf(
        ctx: Context,
        pubKey: String,
    ): MetadataEvent? = ctx.store.query<MetadataEvent>(Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubKey), limit = 1)).firstOrNull()

    private suspend fun nameOf(
        ctx: Context,
        pubKey: String,
    ): String? = metadataOf(ctx, pubKey)?.contactMetaData()?.let { it.displayName ?: it.name }?.takeIf { it.isNotBlank() }
}
