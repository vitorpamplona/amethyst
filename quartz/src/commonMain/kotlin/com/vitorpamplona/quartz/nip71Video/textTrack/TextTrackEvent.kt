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
package com.vitorpamplona.quartz.nip71Video.textTrack

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An addressable timed-text track for a NIP-71 video — captions, subtitles, chapters or
 * metadata — referenced from the video's `text-track` tag.
 *
 * NIP-71 only says a `text-track` points at "a WebVTT file"; publishers that want the track to be
 * replaceable (retranslated, corrected) wrap it in this event instead, and reference it by
 * coordinate. divine.video does both at once: the same track appears on the video as a raw
 * Blossom URL *and* as `39307:<pubkey>:subtitles:<video-d>`.
 *
 * The cue text lives in [content] as WebVTT, which makes the event self-contained; [url] is the
 * same document hosted as a file. Prefer the URL when there is one — the player streams it and
 * only parses the cues it needs — and fall back to the inline copy when the host is gone.
 */
@Immutable
class TextTrackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    // The cue text is the only searchable thing here: the `d` tag is a slug and the rest is
    // plumbing. Indexing it makes a video findable by what is said in it.
    override fun indexableContent() = content

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    /** The video this track belongs to. */
    fun video() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    fun url() = tags.firstNotNullOfOrNull(UrlTag::parse)

    fun mimeType() = tags.firstNotNullOfOrNull(MimeTypeTag::parse)

    /** ISO-639-1 code, from the NIP-32 style single-letter `l` tag publishers use here. */
    fun language() = tags.firstNotNullOfOrNull(LanguageTag::parse)

    companion object {
        const val KIND = 39307
        const val DEFAULT_MIME_TYPE = "text/vtt"

        /** `subtitles:<video d tag>` — the convention divine.video publishes under. */
        fun identifierFor(
            type: String,
            videoDTag: String,
        ) = "$type:$videoDTag"

        fun build(
            webVtt: String,
            dTag: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TextTrackEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, webVtt, createdAt) {
            dTag(dTag)
            initializer()
        }
    }
}
