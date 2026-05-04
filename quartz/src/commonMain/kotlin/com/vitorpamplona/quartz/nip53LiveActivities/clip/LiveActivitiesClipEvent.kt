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
package com.vitorpamplona.quartz.nip53LiveActivities.clip

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.ReferenceTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-53 live stream clip (zap.stream convention, kind 1313).
 *
 * A clip is a standalone highlight produced from an ongoing or past live stream.
 * The event carries:
 *   - `a`     -> the source stream address (kind 30311)
 *   - `p`     -> the stream host's pubkey
 *   - `r`     -> direct playable video URL (MP4/HLS)
 *   - `title` -> clip title
 *   - `alt`   -> NIP-31 fallback text
 *
 * `content` is an optional free-text caption.
 */
@Immutable
class LiveActivitiesClipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider {
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

    fun activity(): ATag? =
        tags
            .asSequence()
            .mapNotNull(ATag::parse)
            .firstOrNull { it.kind == LiveActivitiesEvent.KIND }

    fun activityAddress(): Address? = activity()?.let { Address(it.kind, it.pubKeyHex, it.dTag) }

    fun host(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

    fun videoUrl(): String? = tags.firstNotNullOfOrNull(ReferenceTag::parse)

    fun title(): String? = tags.firstNotNullOfOrNull(TitleTag::parse)

    companion object {
        const val KIND = 1313
        const val ALT = "Live activity clip"

        /**
         * Builds an event template for a clip. Typically published by the clip-authoring backend
         * on behalf of a viewer, but can also be published directly by a client.
         */
        fun build(
            activity: EventHintBundle<LiveActivitiesEvent>,
            videoUrl: String,
            title: String,
            host: HexKey = activity.event.pubKey,
            caption: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveActivitiesClipEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, caption, createdAt) {
            aTag(ATag(activity.event.kind, activity.event.pubKey, activity.event.dTag(), activity.relay))
            add(PTag.assemble(host, null))
            add(ReferenceTag.assemble(videoUrl))
            add(TitleTag.assemble(title))
            add(AltTag.assemble(ALT))
            initializer()
        }
    }
}
