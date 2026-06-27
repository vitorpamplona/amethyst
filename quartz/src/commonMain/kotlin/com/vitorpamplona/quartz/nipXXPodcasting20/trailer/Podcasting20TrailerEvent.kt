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
package com.vitorpamplona.quartz.nipXXPodcasting20.trailer

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.PubDateTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.TitleTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.LengthTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.SeasonTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.TypeTag
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.tags.UrlTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Podcasting-2.0 draft podcast trailer (`kind:30055`), following the Podcast 2.0
 * `<podcast:trailer>` element. Addressable like the episode (`kind:30054`) and
 * signed by the human creator's keypair, keyed by its `d` tag.
 */
@Immutable
class Podcasting20TrailerEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun url() = tags.firstNotNullOfOrNull(UrlTag::parse)

    /** RFC2822 publication date string, kept verbatim for RSS generation. */
    fun pubDate() = tags.firstNotNullOfOrNull(PubDateTag::parse)

    fun lengthInBytes() = tags.firstNotNullOfOrNull(LengthTag::parse)

    fun mimeType() = tags.firstNotNullOfOrNull(TypeTag::parse)

    fun season() = tags.firstNotNullOfOrNull(SeasonTag::parse)

    fun alt() = tags.firstNotNullOfOrNull(AltTag::parse)

    companion object {
        const val KIND = 30055

        fun build(
            dTag: String,
            title: String,
            url: String,
            pubdate: String,
            alt: String = "Podcast trailer: $title",
            lengthInBytes: Long? = null,
            mimeType: String? = null,
            season: Int? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Podcasting20TrailerEvent>.() -> Unit = {},
        ) = eventTemplate<Podcasting20TrailerEvent>(KIND, title, createdAt) {
            dTag(dTag)
            title(title)
            url(url)
            pubdate(pubdate)
            alt(alt)

            lengthInBytes?.let { length(it) }
            mimeType?.let { type(it) }
            season?.let { season(it) }

            initializer()
        }
    }
}
