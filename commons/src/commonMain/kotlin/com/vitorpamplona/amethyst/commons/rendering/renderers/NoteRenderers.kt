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
package com.vitorpamplona.amethyst.commons.rendering.renderers

import com.vitorpamplona.amethyst.commons.rendering.EventRef
import com.vitorpamplona.amethyst.commons.rendering.EventRenderer
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.RenderSupport
import com.vitorpamplona.amethyst.commons.rendering.RenderedDetails
import com.vitorpamplona.amethyst.commons.rendering.RenderedEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

/** kind:1 — NIP-10 threading: `reply` marker first, then `root`, then the legacy positional form. */
object TextNoteRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val note = event as? TextNoteEvent ?: return RenderSupport.build(event, ctx)
        val root = note.root()
        val reply = note.markedReply() ?: note.unmarkedReply() ?: root
        return RenderSupport.build(
            event,
            ctx,
            title = note.subject(),
            replyTo = RenderSupport.ref(reply),
            // Legacy single-level form: a lone `reply` marker names the root too.
            root = RenderSupport.ref(root ?: reply),
        )
    }
}

/** kind:1111 — NIP-22: lowercase tags name the parent, uppercase name the root scope. */
object CommentRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val comment = event as? CommentEvent ?: return RenderSupport.build(event, ctx)

        val replyKind = comment.tags.firstNotNullOfOrNull(ReplyKindTag::parse)?.toIntOrNull()
        val rootKind = comment.tags.firstNotNullOfOrNull(RootKindTag::parse)?.toIntOrNull()

        val reply =
            comment.tags.lastNotNullOfOrNull(ReplyEventTag::parse)?.ref?.let {
                EventRef(eventId = it.eventId, relay = it.relayHint?.url, author = it.author, kind = replyKind)
            } ?: comment.replyingToAddressId()?.let { EventRef(eventId = null, address = it, kind = replyKind) }

        val root =
            comment.tags.firstNotNullOfOrNull(RootEventTag::parse)?.ref?.let {
                EventRef(eventId = it.eventId, relay = it.relayHint?.url, author = it.author, kind = rootKind)
            } ?: comment.rootAddressIds().firstOrNull()?.let { EventRef(eventId = null, address = it, kind = rootKind) }

        return RenderSupport.build(event, ctx, replyTo = reply, root = root)
    }
}

/** kind:30023 — NIP-23 long-form article. */
object LongFormRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val article = event as? LongFormContentEvent ?: return RenderSupport.build(event, ctx)
        return RenderSupport.build(
            event,
            ctx,
            title = article.title(),
            summary = article.summary(),
            details =
                RenderedDetails.LongForm(
                    identifier = article.dTag(),
                    image = article.image(),
                    publishedAt = article.publishedAt(),
                ),
        )
    }
}
