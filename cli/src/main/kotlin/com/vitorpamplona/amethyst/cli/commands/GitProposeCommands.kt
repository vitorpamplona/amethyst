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
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeEngine
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeTargets
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalHost
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalResult
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalTarget
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposals
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip34Git.git.GitPerson
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * The commit-pushing half of `amy git pr`, `amy git pr-update` and `amy git edit`: the same
 * [GitProposals] the app runs. Commits go to `refs/nostr/<event-id>` on the repository's GRASP
 * servers, and the event is published once they hold them, naming only those that do.
 */
object GitProposeCommands {
    private const val RELAY_IDLE_MS = 1_500L

    /** amy's [GitProposalHost]: its signer, its profile's name, and its publish. */
    private class AmyHost(
        private val ctx: Context,
        private val name: String?,
        private val nip05: String?,
    ) : GitProposalHost {
        override val signer: NostrSigner get() = ctx.signer

        override fun author(): GitPerson = GitMergeEngine.committer(name ?: "Anonymous", nip05 ?: "${NPub.create(ctx.identity.pubKeyHex)}@nostr")

        override suspend fun publish(
            event: Event,
            repositoryRelays: Set<NormalizedRelayUrl>,
            private: Boolean,
        ) {
            ctx.publish(event, if (private) repositoryRelays else repositoryRelays + ctx.outboxRelays())
        }
    }

    /** The repository at [address] as a proposal target: its confirmed announcements, signed state and GRASP servers. */
    private suspend fun targetOf(
        ctx: Context,
        address: Address,
        args: Args,
    ): Pair<GitProposalTarget, GitProposals>? {
        val repo = GitSupport.loadRepository(ctx, address, args, RELAY_IDLE_MS) ?: return null
        val lead = repo.lead ?: return null
        val target =
            GitProposalTarget(
                address,
                lead,
                repo.relays.firstOrNull(),
                repo.membership.confirmedAnnouncements,
                GitSignedState.latest(repo.states),
                GitMergeTargets.of(repo.membership.confirmedAnnouncements),
            )
        val me =
            ctx.store
                .query<MetadataEvent>(Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(ctx.identity.pubKeyHex), limit = 1))
                .firstOrNull()
                ?.contactMetaData()
        val host = AmyHost(ctx, me?.let { it.displayName ?: it.name }?.takeIf { it.isNotBlank() }, me?.nip05?.takeIf { it.isNotBlank() })
        val proposals = GitProposals(host, { ctx.okhttp }, EventCache(), repo.authority, repo.httpAuth(ctx))
        return target to proposals
    }

    /** Runs [work], reporting a transport failure (an unreachable or refusing server) as `push_failed`. */
    private suspend fun proposing(work: suspend () -> GitProposalResult): GitProposalResult =
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GitProposalResult.Failed(e.message ?: e::class.simpleName ?: "failed")
        }

    private fun emit(
        result: GitProposalResult,
        extra: Map<String, Any?>,
    ): Int =
        when (result) {
            is GitProposalResult.Failed -> Output.error("push_failed", result.reason)
            is GitProposalResult.Published -> {
                Output.emit(mapOf("event_id" to result.event.id, "kind" to result.event.kind, "servers" to result.servers) + extra)
                0
            }
        }

    /** `amy git pr REPO --source URL --branch NAME [--into BRANCH] [--subject S] [--label L,..] [DESCRIPTION]`. */
    suspend fun open(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val address = GitSupport.resolveAddress(args.positional(0, "repo-naddr-or-coordinates")) ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val source = args.flag("source") ?: return Output.error("bad_args", "--source <clone url of the branch>")
        val branch = args.flag("branch") ?: return Output.error("bad_args", "--branch <name> to propose")
        val into = args.flag("into")
        val subject = args.flag("subject") ?: branch
        val labels = GitSupport.csv(args, "label")
        val description = args.positionalOrNull(1) ?: ""
        args.rejectUnknown("relay")
        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (target, proposals) = targetOf(ctx, address, args) ?: return@use Output.error("not_found", "no announcement for ${address.toValue()}")
            val branches = runCatching { proposals.branches(source) }.getOrElse { return@use Output.error("push_failed", "cannot read $source: ${it.message}") }
            val tip = branches.firstOrNull { it.first == branch }?.second ?: return@use Output.error("not_found", "$source has no branch $branch")
            emit(proposing { proposals.openPullRequest(target, listOf(source), tip, subject, description, labels, branchName = branch, targetBranch = into) }, mapOf("tip" to tip))
        }
    }

    /** `amy git pr-update PR --source URL [--branch NAME]`: the PR moved to that branch's tip. */
    suspend fun update(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positional(0, "pull-request-id")
        val id = GitSupport.resolveEventId(ref) ?: return Output.error("bad_args", "expected a note/nevent/64-hex pull-request id")
        val source = args.flag("source") ?: return Output.error("bad_args", "--source <clone url of the branch>")
        val branchFlag = args.flag("branch")
        args.rejectUnknown("relay")
        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val pr = GitSupport.fetchEvent(ctx, id, args) as? GitPullRequestEvent ?: return@use Output.error("not_found", "no pull request (kind 1618) for $ref")
            val address = pr.repositoryAddress() ?: return@use Output.error("bad_args", "$ref names no repository")
            val (target, proposals) = targetOf(ctx, address, args) ?: return@use Output.error("not_found", "no announcement for ${address.toValue()}")
            val branch = branchFlag ?: pr.branchName() ?: return@use Output.error("bad_args", "--branch: the PR names none")
            val branches = runCatching { proposals.branches(source) }.getOrElse { return@use Output.error("push_failed", "cannot read $source: ${it.message}") }
            val tip = branches.firstOrNull { it.first == branch }?.second ?: return@use Output.error("not_found", "$source has no branch $branch")
            emit(proposing { proposals.updatePullRequest(EventHintBundle(pr), target, listOf(source), tip) }, mapOf("tip" to tip))
        }
    }

    /**
     * `amy git edit REPO --path P (--file LOCAL | --text T) --message M [--branch B]`: a commit
     * that sets one file on the branch's tip (the default branch when none), opened as a PR.
     */
    suspend fun edit(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val address = GitSupport.resolveAddress(args.positional(0, "repo-naddr-or-coordinates")) ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val path = args.flag("path")?.split('/')?.filter { it.isNotEmpty() } ?: return Output.error("bad_args", "--path <file in the repository>")
        if (!GitProposals.isSafePath(path)) return Output.error("bad_args", "--path must name a file in the repository, not .git or ..")
        val file = args.flag("file")?.let { File(it) }
        if (file != null && !file.isFile) return Output.error("bad_args", "--file $file is not a readable file")
        val bytes =
            file?.readBytes()
                ?: args.flag("text")?.encodeToByteArray()
                ?: return Output.error("bad_args", "--file <local file> or --text <content>")
        val message = args.flag("message") ?: return Output.error("bad_args", "--message <commit message>")
        val branchFlag = args.flag("branch")
        args.rejectUnknown("relay")
        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (target, proposals) = targetOf(ctx, address, args) ?: return@use Output.error("not_found", "no announcement for ${address.toValue()}")
            val branch = branchFlag ?: target.defaultBranch ?: return@use Output.error("bad_args", "--branch: the repository signs no default branch")
            val parent = target.signedState?.commitFor(branch) ?: return@use Output.error("not_found", "the maintainers sign no tip for $branch")
            val clones = target.targets.grasp.map { it.cloneUrl } + target.targets.others
            emit(proposing { proposals.proposeEdit(target, clones, branch, parent, mapOf(path to bytes), message, "") }, mapOf("parent" to parent))
        }
    }
}
