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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * `amy git pr REPO` (kind:1618) and `amy git pr-update PR` (kind:1619) —
 * branch-based NIP-34 contributions that reference a pushed tip by clone URL +
 * commit id instead of inlining a patch. The actual git branch push (to a clone
 * or GRASP server) is out of scope — amy only publishes the collaboration event.
 */
object GitPrCommands {
    suspend fun pr(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val addr =
            GitSupport.resolveAddress(coord)
                ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val currentCommit = args.flag("commit") ?: return Output.error("bad_args", "git pr requires --commit <tip-commit-id>")
        val cloneUrls = GitSupport.csv(args, "clone")
        if (cloneUrls.isEmpty()) return Output.error("bad_args", "git pr requires --clone <url>[,<url>] where the branch tip can be fetched")
        val subject = args.flag("subject")
        val branchName = args.flag("branch-name")
        val mergeBase = args.flag("merge-base")
        val labels = GitSupport.csv(args, "label")
        val eucOverride = args.flag("earliest-commit")
        val description = args.positionalOrNull(1) ?: ""
        args.rejectUnknown("relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val repo =
                GitSupport.fetchRepo(ctx, addr, args)
                    ?: return Output.error("not_found", "no repository announcement found for $coord")
            val euc =
                repo.earliestUniqueCommit()
                    ?: eucOverride
                    ?: return Output.error("bad_args", "repository announcement has no earliest-unique-commit; pass --earliest-commit <id>")

            val template =
                GitPullRequestEvent.build(
                    description = description,
                    repository = EventHintBundle(repo),
                    earliestUniqueCommit = euc,
                    currentCommit = currentCommit,
                    cloneUrls = cloneUrls,
                    subject = subject,
                    labels = labels,
                    branchName = branchName,
                    mergeBase = mergeBase,
                )
            val signed = ctx.signer.sign(template)
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "repository" to Address.assemble(addr.kind, addr.pubKeyHex, addr.dTag),
                    "subject" to subject,
                    "current_commit" to currentCommit,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    suspend fun prUpdate(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val prRef = args.positional(0, "pull-request-id")
        val prId =
            GitSupport.resolveEventId(prRef)
                ?: return Output.error("bad_args", "expected a note/nevent/64-hex pull-request id")
        val currentCommit = args.flag("commit") ?: return Output.error("bad_args", "git pr-update requires --commit <new-tip-commit-id>")
        val cloneUrls = GitSupport.csv(args, "clone")
        if (cloneUrls.isEmpty()) return Output.error("bad_args", "git pr-update requires --clone <url>[,<url>]")
        val mergeBase = args.flag("merge-base")
        val eucOverride = args.flag("earliest-commit")
        args.rejectUnknown("relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val parent =
                GitSupport.fetchEvent(ctx, prId, args) as? GitPullRequestEvent
                    ?: return Output.error("not_found", "no pull request (kind 1618) found for $prRef")
            val repoAddr =
                parent.repositoryAddress()
                    ?: return Output.error("bad_args", "pull request $prRef carries no repository address")
            val repo =
                GitSupport.fetchRepo(ctx, repoAddr, args)
                    ?: return Output.error("not_found", "no repository announcement found for ${GitSupport.repoCoordinate(repoAddr)}")
            val euc =
                repo.earliestUniqueCommit()
                    ?: parent.earliestUniqueCommit()
                    ?: eucOverride
                    ?: return Output.error("bad_args", "no earliest-unique-commit available; pass --earliest-commit <id>")

            val template =
                GitPullRequestUpdateEvent.build(
                    parentPullRequest = EventHintBundle(parent),
                    repository = EventHintBundle<GitRepositoryEvent>(repo),
                    earliestUniqueCommit = euc,
                    currentCommit = currentCommit,
                    cloneUrls = cloneUrls,
                    mergeBase = mergeBase,
                )
            val signed = ctx.signer.sign(template)
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "pull_request" to parent.id,
                    "current_commit" to currentCommit,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
