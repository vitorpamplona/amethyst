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
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * `amy git comment TARGET [BODY]` — reply to a NIP-34 issue, patch, pull
 * request, or repository with a NIP-22 kind:1111 comment (the modern
 * replacement for the deprecated kind:1622 git reply). TARGET is a
 * note/nevent/64-hex event id, or an naddr / `kind:pubkey:id` repo coordinate.
 * BODY comes from the argument or stdin.
 */
object GitCommentCommand {
    suspend fun comment(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val targetRef = args.positional(0, "target-event-or-repo")
        val body = (args.positionalOrNull(1) ?: System.`in`.readBytes().decodeToString()).trim()
        if (body.isBlank()) return Output.error("bad_args", "empty comment (pass BODY as an argument or on stdin)")
        args.rejectUnknown("relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val target =
                resolveTarget(ctx, targetRef, args)
                    ?: return Output.error("not_found", "no event or repository found for $targetRef")
            val template = CommentEvent.replyBuilder(body, EventHintBundle<Event>(target))
            val signed = ctx.signer.sign(template)

            // Deliver to the repository's advertised relays when we can find them.
            val repo =
                (target as? GitRepositoryEvent)
                    ?: GitSupport.repositoryOf(target)?.let { GitSupport.fetchRepo(ctx, Address(it.kind, it.pubKeyHex, it.dTag), args) }
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "in_reply_to" to target.id,
                    "target_kind" to target.kind,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /** Resolve TARGET as an event id first, then fall back to a repo coordinate. */
    private suspend fun resolveTarget(
        ctx: Context,
        ref: String,
        args: Args,
    ): Event? {
        GitSupport.resolveEventId(ref)?.let { id ->
            GitSupport.fetchEvent(ctx, id, args)?.let { return it }
        }
        GitSupport.resolveAddress(ref)?.let { addr ->
            if (addr.kind == GitRepositoryEvent.KIND) return GitSupport.fetchRepo(ctx, addr, args)
        }
        return null
    }
}
