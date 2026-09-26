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
import com.vitorpamplona.quartz.experimental.decentralizedLists.description
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.names
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemAddress
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.parentList
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.Polarity
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.sha256.sha256

/** What an event tagging is about: an addressable event (`a`) or a plain one (`e`). */
@Immutable
sealed interface TaggingTarget {
    /**
     * What stands for this target in the assertion `d`: `<id8>` for a plain event,
     * `<author8>-<d16>-<hash8>` for an addressable one. Two distinct targets must never produce
     * the same one — kind 39999 is addressable, so taggings sharing a `d` do not sit side by
     * side: the later one replaces the earlier.
     */
    val prefix: String

    @Immutable
    data class ByAddress(
        val address: Address,
    ) : TaggingTarget {
        /**
         * `<author8>-<d16>-<hash8>`. Only `hash8` carries uniqueness — it is taken over the WHOLE
         * coordinate, because no single segment identifies the target: the author repeats across
         * everything they write, and the kind across everything of a type. The other two are
         * decoration the draft asks for so a `d` stays readable.
         *
         * An empty `dTag` yields an empty `d16`, i.e. `<author8>--<hash8>`, which the draft spells
         * out. A plain event uses its own id (below): that already covers the whole event.
         */
        override val prefix get() = "${address.pubKeyHex.take(AUTHOR_CHARS)}-${address.dTag.take(D_TAG_CHARS)}-${hash8()}"

        // Lowercase hex of the UTF-8 coordinate exactly as the `a` tag carries it: no trimming,
        // no normalization, no reordering. Hex.encode already emits lower case.
        private fun hash8() = sha256(address.toValue().encodeToByteArray()).toHexKey().take(HASH_CHARS)
    }

    @Immutable
    data class ByEventId(
        val eventId: HexKey,
    ) : TaggingTarget {
        override val prefix get() = eventId.take(EVENT_ID_CHARS)
    }

    companion object {
        const val EVENT_ID_CHARS = 8
        const val AUTHOR_CHARS = 8
        const val HASH_CHARS = 8

        /**
         * UTF-16 code units, which is what the draft pins ("as JavaScript `String.prototype.slice`
         * counts them") and what [String.take] counts.
         */
        const val D_TAG_CHARS = 16
    }
}

/**
 * Event Taggings: the per-tag *tagging header* — "taggings of events as an Awesome Tag".
 *
 * A kind 39999 that is at once a list header (`names`, `description`) and an item of the
 * deployment's `tagging-with-specific-tag` list, naming its tag with a direct `a`. Assertions
 * point at it with a `z`, because their own `a`/`e` slot is taken by the target.
 */
object TaggingHeader {
    fun dTag(tagSlug: String) = "tagging:$tagSlug-tagging"

    /**
     * @param taggingWithSpecificTagConcept the deployment's `tagging-with-specific-tag` concept.
     */
    fun build(
        taggingWithSpecificTagConcepts: Collection<String>,
        tagElement: Address,
        singularName: String,
        pluralName: String,
        description: String? = null,
        createdAt: Long = TimeUtils.now(),
    ) = AddressableListItemEvent.build(
        parent = taggingWithSpecificTagConcepts.firstNamespace(),
        dTag = dTag(tagElement.dTag),
        createdAt = createdAt,
    ) {
        conceptNamespaces(taggingWithSpecificTagConcepts)
        names(singularName, pluralName)
        description?.let { this.description(it) }
        itemAddress(tagElement)
    }

    fun build(
        taggingWithSpecificTagConcept: String,
        tagElement: Address,
        singularName: String,
        pluralName: String,
        description: String? = null,
        createdAt: Long = TimeUtils.now(),
    ) = build(listOf(taggingWithSpecificTagConcept), tagElement, singularName, pluralName, description, createdAt)
}

/**
 * Event Taggings: "this note is an Awesome Tag" — a kind 39999 carrying the target in `a`/`e`
 * and two `z`s, one to the deployment's `nostr-event-tag` list and one to the per-tag
 * [TaggingHeader]:
 *
 * ```
 * ["d", "event-tag-<tagSlug>-<target8>-<asserter8>"]   // <target8> = <id8>, or
 *                                                      // <author8>-<d16>-<hash8> for an `a`
 * ["a", <target coordinate>]  or  ["e", <target id>]
 * ["z", <nostr-event-tag concept>]
 * ["z", <tagging header coordinate>]
 * ["polarity", "1" | "-1"]
 * ```
 *
 * An `a`/`e` reference without such a `z` is not a tagging and must not be read as one.
 *
 * The `d` is a replaceability key, never a source of truth: the draft forbids parsing it back into
 * its fields, because `d16` is user-influenced text that may itself contain hyphens. [parse] reads
 * the target from the `a`/`e` tag, which is authoritative.
 */
@Immutable
data class EventTagging(
    val target: TaggingTarget,
    /** The `z`s other than the `nostr-event-tag` one: candidate tagging headers to resolve. */
    val taggingHeaders: List<String>,
    val polarity: Polarity,
) {
    companion object {
        fun dTag(
            tagSlug: String,
            target: TaggingTarget,
            asserter: HexKey,
        ) = "event-tag-$tagSlug-${target.prefix}-${asserter.take(8)}"

        /**
         * @param nostrEventTagConcepts the `nostr-event-tag` concept of each authority namespace
         *   to join: the deployment's own, plus any shared one it federates with.
         * @param taggingHeader the per-tag tagging header's coordinate.
         * @param tagSlug the applied tag's slug, for the deterministic `d`.
         * @param asserter the pubkey that will sign, for the deterministic `d`.
         */
        fun build(
            nostrEventTagConcepts: Collection<String>,
            taggingHeader: Address,
            tagSlug: String,
            target: TaggingTarget,
            asserter: HexKey,
            apply: Boolean = true,
            createdAt: Long = TimeUtils.now(),
        ) = AddressableListItemEvent.build(
            parent = nostrEventTagConcepts.firstNamespace(),
            dTag = dTag(tagSlug, target, asserter),
            createdAt = createdAt,
        ) {
            conceptNamespaces(nostrEventTagConcepts)
            parentList(ParentListTag.classify(taggingHeader.toValue()))
            when (target) {
                is TaggingTarget.ByAddress -> itemAddress(target.address)
                is TaggingTarget.ByEventId -> itemEvent(target.eventId)
            }
            polarity(apply)
        }

        fun build(
            nostrEventTagConcept: String,
            taggingHeader: Address,
            tagSlug: String,
            target: TaggingTarget,
            asserter: HexKey,
            apply: Boolean = true,
            createdAt: Long = TimeUtils.now(),
        ) = build(listOf(nostrEventTagConcept), taggingHeader, tagSlug, target, asserter, apply, createdAt)

        /**
         * Null unless [event] joins one of the [honoredNamespaces]' `nostr-event-tag` concepts,
         * names a tagging header and a target. Every namespace the reader honors must be passed:
         * a federated tagging carries one concept `z` per namespace it joined, and any concept
         * `z` the reader did not list would otherwise be taken for a tagging header.
         */
        fun parse(
            event: AddressableListItemEvent,
            honoredNamespaces: Set<String>,
        ): EventTagging? {
            val pointers = event.parentListPointers()
            if (pointers.none { it in honoredNamespaces }) return null
            val headers = pointers.filter { it !in honoredNamespaces }
            if (headers.isEmpty()) return null

            val target =
                event.tags.fastFirstNotNullOfOrNull(ATag::parseAddress)?.let { TaggingTarget.ByAddress(it) }
                    ?: event.tags.fastFirstNotNullOfOrNull(ETag::parseId)?.let { TaggingTarget.ByEventId(it) }
                    ?: return null

            return EventTagging(target, headers, event.tags.polarity())
        }

        fun parse(
            event: AddressableListItemEvent,
            nostrEventTagConcept: String,
        ) = parse(event, setOf(nostrEventTagConcept))
    }
}
