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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

/**
 * `amy git open|applied|close|draft TARGET` — publish a NIP-34 status event
 * (kinds 1630 / 1631 / 1632 / 1633) against a patch, pull request, or issue.
 *
 *   open      1630 — mark open / reopen / ready-for-review
 *   applied   1631 — mark applied / merged (patches, PRs) or resolved (issues)
 *   close     1632 — close without applying
 *   draft     1633 — move back to draft
 *
 * The newest status from the root author or a repo maintainer is authoritative
 * (NIP-34). amy publishes the event; it does not enforce maintainership.
 */
object GitStatusCommands {
    suspend fun open(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = simpleStatus(dataDir, rest, GitStatusOpenEvent.KIND)

    suspend fun close(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = simpleStatus(dataDir, rest, GitStatusClosedEvent.KIND)

    suspend fun draft(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = simpleStatus(dataDir, rest, GitStatusDraftEvent.KIND)

    /** open / close / draft share one shape: `TARGET [MESSAGE]`. */
    private suspend fun simpleStatus(
        dataDir: DataDir,
        rest: Array<String>,
        kind: Int,
    ): Int {
        val args = Args(rest)
        val (targetRef, msg) = args.targetAndMessage() ?: return Output.error("bad_args", "expected a note/nevent/64-hex target id")
        args.rejectUnknown("relay")

        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val target = ctx.resolveTarget(targetRef, args) ?: return@use Output.error("not_found", "no event found for $targetRef")
            val repoATag = GitSupport.repositoryOf(target)
            val template =
                when (kind) {
                    GitStatusOpenEvent.KIND -> GitStatusOpenEvent.build(msg, EventHintBundle(target)) { repoTags(repoATag, target) }
                    GitStatusClosedEvent.KIND -> GitStatusClosedEvent.build(msg, EventHintBundle(target)) { repoTags(repoATag, target) }
                    GitStatusDraftEvent.KIND -> GitStatusDraftEvent.build(msg, EventHintBundle(target)) { repoTags(repoATag, target) }
                    else -> error("unreachable status kind $kind")
                }
            ctx.emitStatus(template, target, repoATag, args, kind)
        }
    }

    /** `applied` (1631) additionally carries merge-commit / applied-as-commits / applied-patch tags. */
    suspend fun applied(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val (targetRef, msg) = args.targetAndMessage() ?: return Output.error("bad_args", "expected a note/nevent/64-hex target id")
        val mergeCommit = args.flag("merge-commit")
        val appliedAsCommits = GitSupport.csv(args, "commit")
        val patchRefs = GitSupport.csv(args, "patch")
        args.rejectUnknown("relay")

        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val target = ctx.resolveTarget(targetRef, args) ?: return@use Output.error("not_found", "no event found for $targetRef")
            val repoATag = GitSupport.repositoryOf(target)
            val appliedPatches =
                patchRefs.mapNotNull { ref ->
                    GitSupport.resolveEventId(ref)?.let { id -> GitSupport.fetchEvent(ctx, id, args) as? GitPatchEvent }?.let { EventHintBundle(it) }
                }
            val template =
                GitStatusAppliedEvent.build(
                    content = msg,
                    target = EventHintBundle(target),
                    appliedPatches = appliedPatches,
                    mergeCommit = mergeCommit,
                    appliedAsCommits = appliedAsCommits,
                ) { repoTags(repoATag, target) }
            ctx.emitStatus(template, target, repoATag, args, GitStatusAppliedEvent.KIND)
        }
    }

    // ------------------------------------------------------------------

    /** `TARGET [MESSAGE]` — the shape every status verb shares. */
    private fun Args.targetAndMessage(): Pair<String, String>? {
        val ref = positionalOrNull(0) ?: return null
        return ref to (positionalOrNull(1) ?: "")
    }

    private suspend fun Context.resolveTarget(
        ref: String,
        args: Args,
    ): Event? {
        val id = GitSupport.resolveEventId(ref) ?: return null
        return GitSupport.fetchEvent(this, id, args)
    }

    /**
     * Attach the repository `a` tag and, when it differs from the target author
     * (already p-tagged by the shared status builder), the repo owner `p` tag.
     */
    private fun <T : Event> TagArrayBuilder<T>.repoTags(
        repoATag: ATag?,
        target: Event,
    ) {
        repoATag ?: return
        add(repoATag.toATagArray())
        if (repoATag.pubKeyHex != target.pubKey) pTag(repoATag.pubKeyHex)
    }

    private suspend fun Context.emitStatus(
        template: EventTemplate<out Event>,
        target: Event,
        repoATag: ATag?,
        args: Args,
        kind: Int,
    ): Int {
        val signed = signer.sign(template)
        val repo = repoATag?.let { GitSupport.fetchRepo(this, Address(it.kind, it.pubKeyHex, it.dTag), args) }
        val targets = GitSupport.deliveryTargets(this, repo, args)
        val ack = publish(signed, targets)
        RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
        Output.emit(
            mapOf(
                "event_id" to signed.id,
                "kind" to signed.kind,
                "status" to GitSupport.statusLabel(kind),
                "target" to target.id,
                "repository" to repoATag?.toTag(),
            ) + RawEventSupport.ackFields(ack),
        )
        return 0
    }
}
