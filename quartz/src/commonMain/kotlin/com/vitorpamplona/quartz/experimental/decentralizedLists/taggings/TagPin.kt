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
package com.vitorpamplona.quartz.experimental.decentralizedLists.taggings

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemAddress
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.CurationMethod
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class TagPinningInfo(
    val tagEventId: HexKey? = null,
    val curationMethod: CurationMethod? = null,
)

/** The `content` mirror of a pin: `{"tagPinning": {...}}`. */
@Immutable
@Serializable
data class TagPinningContent(
    val tagPinning: TagPinningInfo,
)

/**
 * Tags & Taggings: a viewer opting a tag into their personal curated set — a kind 39999 item.
 *
 * ```
 * ["d", "tag-pin-<tagSlug>-<tagAuthor[0:8]>-<viewer[0:8]>"]
 * ["e", <tagEventId>]                   the version pinned
 * ["a", "39999:<tagAuthor>:<slug>"]     survives the tag author's later edits
 * ["z", <tag-pinning concept>]
 * ["curation-method", "<json>"]
 * ```
 *
 * Unpinning is a plain NIP-09 deletion of the pin; a live pin event means pinned.
 */
@Immutable
data class TagPin(
    val tag: Address?,
    val tagEventId: HexKey?,
    val curationMethod: CurationMethod?,
) {
    companion object {
        fun dTag(
            tagSlug: String,
            tagAuthor: HexKey,
            viewer: HexKey,
        ) = "tag-pin-$tagSlug-${tagAuthor.take(8)}-${viewer.take(8)}"

        /**
         * @param tagPinningConcept the deployment's `tag-pinning` concept address.
         * @param viewer the pubkey that will sign, needed for the deterministic `d`.
         */
        fun build(
            tagPinningConcept: String,
            tagElement: AddressableListItemEvent,
            viewer: HexKey,
            curationMethod: CurationMethod,
            createdAt: Long = TimeUtils.now(),
        ) = AddressableListItemEvent.build(
            parent = ParentListTag.classify(tagPinningConcept),
            dTag = dTag(tagElement.dTag(), tagElement.pubKey, viewer),
            createdAt = createdAt,
            content = JsonMapper.toJson(TagPinningContent(TagPinningInfo(tagElement.id, curationMethod))),
        ) {
            itemEvent(tagElement.id)
            itemAddress(tagElement.address())
            curationMethod(curationMethod)
        }

        /** Null unless [event] joins [tagPinningConcept]. */
        fun parse(
            event: AddressableListItemEvent,
            tagPinningConcept: String,
        ): TagPin? {
            if (tagPinningConcept !in event.parentListPointers()) return null
            return TagPin(
                tag = event.tags.firstNotNullOfOrNull(ATag::parseAddress),
                tagEventId = event.tags.firstNotNullOfOrNull(ETag::parseId),
                curationMethod = event.tags.curationMethod(),
            )
        }
    }
}
