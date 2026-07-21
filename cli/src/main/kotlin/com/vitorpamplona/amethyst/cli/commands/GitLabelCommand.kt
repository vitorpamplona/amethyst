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
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag

/**
 * `amy git label TARGET LABEL[,LABEL]` — attach NIP-32 kind:1985 labels to a
 * patch, pull request, or issue (the `ngit pr label` / `issue label` surface).
 * Labels default to the `ugc` namespace; override with `--namespace`.
 */
object GitLabelCommand {
    suspend fun label(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val targetRef = args.positional(0, "target-event-id")
        val namespace = args.flag("namespace") ?: LabelTag.DEFAULT_NAMESPACE
        val labels =
            args
                .positional(1, "label[,label]")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LabelTag(it, namespace) }
        if (labels.isEmpty()) return Output.error("bad_args", "git label requires at least one label")
        val content = args.flag("content") ?: ""
        val id =
            GitSupport.resolveEventId(targetRef)
                ?: return Output.error("bad_args", "expected a note/nevent/64-hex target id")
        args.rejectUnknown("relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val target =
                GitSupport.fetchEvent(ctx, id, args)
                    ?: return Output.error("not_found", "no event found for $targetRef")
            val template =
                LabelEvent.buildEventLabel(
                    labeledEventId = target.id,
                    labeledEventAuthor = target.pubKey,
                    labels = labels,
                    content = content,
                )
            val signed = ctx.signer.sign(template)
            val repoATag = GitSupport.repositoryOf(target)
            val repo = repoATag?.let { GitSupport.fetchRepo(ctx, Address(it.kind, it.pubKeyHex, it.dTag), args) }
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "target" to target.id,
                    "namespace" to namespace,
                    "labels" to labels.map { it.label },
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
