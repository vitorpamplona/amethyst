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

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * The shared rich-text post-processor: every text-bearing kind runs its text through
 * here, so hashtag / mention / link / media detection is the exact parse the
 * Android and Desktop note viewers use ([RichTextParser]) rather than a second one.
 */
object BodySpans {
    class Parsed(
        val spans: List<BodySpan>,
        val media: List<MediaRef>,
    )

    fun parse(
        text: String,
        tags: TagArray,
    ): Parsed {
        if (text.isEmpty()) return Parsed(emptyList(), emptyList())

        val state = RichTextParser().parseText(text, ImmutableListOfLists(tags), callbackUri = null)

        val spans = mutableListOf<BodySpan>()
        state.paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) spans.appendText("\n")
            paragraph.words.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) spans.appendText(" ")
                spans.add(toSpan(word, state))
            }
        }

        return Parsed(spans.mergeText(), media(state))
    }

    private fun toSpan(
        word: Segment,
        state: RichTextViewerState,
    ): BodySpan {
        val text = word.segmentText
        return when (word) {
            is ImageSegment -> BodySpan.Image(text, text)
            is VideoSegment -> BodySpan.Video(text, text)
            is LinkSegment -> BodySpan.Link(text, text)
            is SchemelessUrlSegment -> BodySpan.Link(text, "https://$text")
            is HashTagSegment -> BodySpan.Hashtag(text, word.hashtag)
            is EmojiSegment -> BodySpan.CustomEmoji(text, state.customEmoji[text])
            is InvoiceSegment -> BodySpan.Payment(text, "lightning_invoice")
            is WithdrawSegment -> BodySpan.Payment(text, "lnurl_withdraw")
            is CashuSegment -> BodySpan.Payment(text, "cashu_token")
            is ClinkOfferSegment -> BodySpan.Payment(text, "clink_offer")
            is HashIndexUserSegment -> BodySpan.UserMention(text, word.hex)
            is HashIndexEventSegment -> BodySpan.EventMention(text, EventRef(eventId = word.hex))
            is BechSegment -> bechSpan(text)
            else -> BodySpan.Text(text)
        }
    }

    private fun bechSpan(text: String): BodySpan =
        when (val entity = Nip19Parser.uriToRoute(text)?.entity) {
            is NPub -> BodySpan.UserMention(text, entity.hex)
            is NProfile -> BodySpan.UserMention(text, entity.hex)
            is NNote -> BodySpan.EventMention(text, EventRef(eventId = entity.hex))
            is NEvent -> BodySpan.EventMention(text, EventRef(eventId = entity.hex, relay = entity.relay.firstOrNull()?.url, author = entity.author, kind = entity.kind))
            is NAddress -> BodySpan.EventMention(text, EventRef(eventId = null, address = entity.aTag(), relay = entity.relay.firstOrNull()?.url, author = entity.author, kind = entity.kind))
            else -> BodySpan.Text(text)
        }

    private fun media(state: RichTextViewerState): List<MediaRef> =
        state.mediaList.mapNotNull { media ->
            val type =
                when (media) {
                    is MediaUrlImage -> "image"
                    is MediaUrlVideo -> "video"
                    else -> return@mapNotNull null
                }
            MediaRef(
                url = media.url,
                type = type,
                mimeType = media.mimeType,
                alt = media.description,
                dimensions = media.dim?.let { "${it.width}x${it.height}" },
                blurhash = media.blurhash,
            )
        }

    private fun MutableList<BodySpan>.appendText(text: String) = add(BodySpan.Text(text))

    /** Collapses runs of adjacent [BodySpan.Text] into one, so a plain sentence is one span, not one per word. */
    private fun List<BodySpan>.mergeText(): List<BodySpan> {
        val out = ArrayList<BodySpan>(size)
        val buffer = StringBuilder()
        for (span in this) {
            if (span is BodySpan.Text) {
                buffer.append(span.text)
            } else {
                if (buffer.isNotEmpty()) {
                    out.add(BodySpan.Text(buffer.toString()))
                    buffer.clear()
                }
                out.add(span)
            }
        }
        if (buffer.isNotEmpty()) out.add(BodySpan.Text(buffer.toString()))
        return out
    }
}
