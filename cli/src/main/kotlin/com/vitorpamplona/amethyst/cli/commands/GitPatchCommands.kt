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
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import java.io.File

/**
 * `amy git patch REPO` — publish a NIP-34 kind:1617 patch against a repository.
 *
 * The patch body is the raw `git format-patch` output, read from `--file PATH`
 * or (by default) stdin, so the natural pipeline is:
 *
 *   git format-patch --stdout HEAD~1 | amy git patch nostr:naddr1… --root
 *
 * Thin assembly only — every tag lives in quartz's [GitPatchEvent].
 */
object GitPatchCommands {
    suspend fun patch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val addr =
            GitSupport.resolveAddress(coord)
                ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val root = args.bool("root")
        val rootRevision = args.bool("root-revision")
        val commit = args.flag("commit")
        val parentCommit = args.flag("parent-commit")
        val eucOverride = args.flag("earliest-commit")
        val replyTo = args.flag("in-reply-to")
        val file = args.flag("file")
        // `--relay` is consumed later by deliveryTargets / fetchRepo's queryTargets.
        args.rejectUnknown("relay")

        val body = readPatch(file)
        if (body.isBlank()) return Output.error("bad_args", "empty patch (pass --file PATH or pipe `git format-patch` to stdin)")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val repo =
                GitSupport.fetchRepo(ctx, addr, args)
                    ?: return Output.error("not_found", "no repository announcement found for $coord")
            val euc =
                repo.earliestUniqueCommit()
                    ?: eucOverride
                    ?: return Output.error(
                        "bad_args",
                        "repository announcement has no earliest-unique-commit; pass --earliest-commit <id>",
                    )
            val repoBundle = EventHintBundle(repo)

            val template =
                if (replyTo != null) {
                    val replyId =
                        GitSupport.resolveEventId(replyTo)
                            ?: return Output.error("bad_args", "--in-reply-to expects a note/nevent/64-hex, got '$replyTo'")
                    val prior =
                        GitSupport.fetchEvent(ctx, replyId, args) as? GitPatchEvent
                            ?: return Output.error("not_found", "no patch found to reply to: $replyTo")
                    GitPatchEvent.reply(
                        patch = body,
                        repository = repoBundle,
                        earliestUniqueCommit = euc,
                        replyingTo = EventHintBundle(prior),
                        commit = commit,
                        parentCommit = parentCommit,
                    )
                } else {
                    GitPatchEvent.build(
                        patch = body,
                        repository = repoBundle,
                        earliestUniqueCommit = euc,
                        commit = commit,
                        parentCommit = parentCommit,
                        root = root,
                        rootRevision = rootRevision,
                    )
                }

            val signed = ctx.signer.sign(template)
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "repository" to Address.assemble(addr.kind, addr.pubKeyHex, addr.dTag),
                    "subject" to signed.subject(),
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /** Read the patch body from [file] when given, otherwise from stdin. */
    private fun readPatch(file: String?): String =
        if (file != null) {
            File(file).takeIf { it.isFile }?.readText()
                ?: throw IllegalArgumentException("--file not found: $file")
        } else {
            // Non-interactive: don't block waiting for a human to type a patch.
            require(System.console() == null) { "no patch given: pass --file PATH or pipe `git format-patch` to stdin" }
            System.`in`.readBytes().decodeToString()
        }.trim()
}
