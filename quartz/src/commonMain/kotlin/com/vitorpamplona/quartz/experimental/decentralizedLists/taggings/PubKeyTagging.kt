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
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemPubKey
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.Polarity
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NostrUserTagInfo(
    val taggedPubkey: HexKey,
    val tagEventId: HexKey? = null,
)

/** The `content` mirror of a pubkey tagging: `{"nostrUserTag": {...}}`. */
@Immutable
@Serializable
data class NostrUserTagContent(
    val nostrUserTag: NostrUserTagInfo,
)

/**
 * Tags & Taggings: "Avi is a Podcaster" — a kind 39999 item asserting that a pubkey belongs to
 * a tag, or disputing it.
 *
 * ```
 * ["d", "profile-tag-<tagSlug>-<target[0:8]>-<asserter[0:8]>"]
 * ["p", <target>]
 * ["a", "39999:<tagAuthor>:<slug>"]     the tag applied: stable identity, scan this
 * ["e", <tagEventId>]                    the tag's version at apply time: provenance only
 * ["z", <nostr-user-tag concept>]
 * ["polarity", "1" | "-1"]
 * ```
 *
 * The deterministic `d` gives each asserter one live stance per (target, tag): republishing
 * replaces it, including a flip between apply and dispute.
 */
@Immutable
data class PubKeyTagging(
    val target: HexKey,
    val tag: Address?,
    val tagEventId: HexKey?,
    val polarity: Polarity,
) {
    companion object {
        fun dTag(
            tagSlug: String,
            target: HexKey,
            asserter: HexKey,
        ) = "profile-tag-$tagSlug-${target.take(8)}-${asserter.take(8)}"

        /**
         * @param userTagConcepts the `nostr-user-tag` concept of each authority namespace to join.
         * @param asserter the pubkey that will sign, needed for the deterministic `d`.
         */
        fun build(
            userTagConcepts: Collection<String>,
            tagElement: Address,
            tagEventId: HexKey?,
            target: HexKey,
            asserter: HexKey,
            apply: Boolean = true,
            createdAt: Long = TimeUtils.now(),
        ) = AddressableListItemEvent.build(
            parent = userTagConcepts.firstNamespace(),
            dTag = dTag(tagElement.dTag, target, asserter),
            createdAt = createdAt,
            content = JsonMapper.toJson(NostrUserTagContent(NostrUserTagInfo(target, tagEventId))),
        ) {
            conceptNamespaces(userTagConcepts)
            itemPubKey(target)
            itemAddress(tagElement)
            tagEventId?.let { itemEvent(it) }
            polarity(apply)
        }

        fun build(
            userTagConcept: String,
            tagElement: AddressableListItemEvent,
            target: HexKey,
            asserter: HexKey,
            apply: Boolean = true,
            createdAt: Long = TimeUtils.now(),
        ) = build(listOf(userTagConcept), tagElement.address(), tagElement.id, target, asserter, apply, createdAt)

        fun build(
            userTagConcept: String,
            tagElement: Address,
            tagEventId: HexKey?,
            target: HexKey,
            asserter: HexKey,
            apply: Boolean = true,
            createdAt: Long = TimeUtils.now(),
        ) = build(listOf(userTagConcept), tagElement, tagEventId, target, asserter, apply, createdAt)

        /** Null unless [event] joins one of the [honoredNamespaces] and names a target pubkey. */
        fun parse(
            event: AddressableListItemEvent,
            honoredNamespaces: Set<String>,
        ): PubKeyTagging? {
            if (event.parentListPointers().none { it in honoredNamespaces }) return null
            val target = event.tags.fastFirstNotNullOfOrNull(PTag::parseKey) ?: return null
            return PubKeyTagging(
                target = target,
                tag = event.tags.fastFirstNotNullOfOrNull(ATag::parseAddress),
                tagEventId = event.tags.fastFirstNotNullOfOrNull(ETag::parseId),
                polarity = event.tags.polarity(),
            )
        }

        fun parse(
            event: AddressableListItemEvent,
            userTagConcept: String,
        ) = parse(event, setOf(userTagConcept))
    }
}
