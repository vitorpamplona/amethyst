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
package com.vitorpamplona.amethyst.commons.rendering

import com.vitorpamplona.amethyst.commons.rendering.renderers.CommentRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.ContactListRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.DeletionRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.DmRelayListRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.LongFormRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.MetadataRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.NIP65RelayListRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.ReactionRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.RepostRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.TextNoteRenderer
import com.vitorpamplona.amethyst.commons.rendering.renderers.ZapReceiptRenderer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/** Turns one event into its [RenderedEvent]. Implementations are pure: no I/O, no clock, no cache. */
fun interface EventRenderer {
    fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent
}

/**
 * Kind → [EventRenderer]. Any kind without a specialised renderer goes through
 * [DefaultRenderer], so nothing is ever un-renderable: its content is still parsed
 * as rich text and its `e`/`p`/`q`/`t` tags still surface as refs.
 *
 * Front ends call [render]; they never pick a renderer themselves.
 */
object EventRendererRegistry {
    private val renderers: MutableMap<Int, EventRenderer> =
        mutableMapOf(
            MetadataEvent.KIND to MetadataRenderer,
            TextNoteEvent.KIND to TextNoteRenderer,
            ContactListEvent.KIND to ContactListRenderer,
            DeletionRequestEvent.KIND to DeletionRenderer,
            RepostEvent.KIND to RepostRenderer,
            ReactionEvent.KIND to ReactionRenderer,
            GenericRepostEvent.KIND to RepostRenderer,
            CommentEvent.KIND to CommentRenderer,
            ZapReceiptEvent.KIND to ZapReceiptRenderer,
            AdvertisedRelayListEvent.KIND to NIP65RelayListRenderer,
            DmRelayListEvent.KIND to DmRelayListRenderer,
            LongFormContentEvent.KIND to LongFormRenderer,
        )

    fun register(
        kind: Int,
        renderer: EventRenderer,
    ) {
        renderers[kind] = renderer
    }

    fun rendererFor(kind: Int): EventRenderer = renderers[kind] ?: DefaultRenderer

    fun render(
        event: Event,
        ctx: RenderContext = RenderContext.EMPTY,
    ): RenderedEvent = rendererFor(event.kind).render(event, ctx)
}

/** The fallback: rich-text content + generic tag refs, no kind-specific details. */
object DefaultRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent = RenderSupport.build(event, ctx)
}
