/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.audio.track

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.audio.track.tags.CoverTag
import com.vitorpamplona.quartz.experimental.audio.track.tags.MediaTag
import com.vitorpamplona.quartz.experimental.audio.track.tags.ParticipantTag
import com.vitorpamplona.quartz.experimental.audio.track.tags.PriceTag
import com.vitorpamplona.quartz.experimental.audio.track.tags.TypeTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

@Immutable
class AudioTrackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun participants() = tags.mapNotNull(ParticipantTag::parse)

    fun type() = tags.firstNotNullOfOrNull(TypeTag::parse)

    fun price() = tags.firstNotNullOfOrNull(PriceTag::parse)

    fun cover() = tags.firstNotNullOfOrNull(CoverTag::parse)

    fun media() = tags.firstNotNullOfOrNull(MediaTag::parse)

    companion object {
        const val KIND = 31337
        const val ALT_DESCRIPTION = "Audio track"

        fun build(
            type: String,
            media: String,
            price: String? = null,
            cover: String? = null,
            subject: String? = null,
            dTag: String = UUID.randomUUID().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AudioTrackEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            alt(ALT_DESCRIPTION)

            type(type)
            media(media)
            price?.let { price(it) }
            cover?.let { cover(it) }
            subject?.let { subject(it) }

            initializer()
        }
    }
}
