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
package com.vitorpamplona.quartz.experimental.decentralizedLists.item

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.AddressableDecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListItem
import com.vitorpamplona.quartz.experimental.decentralizedLists.description
import com.vitorpamplona.quartz.experimental.decentralizedLists.forEachSearchableListField
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.acceptedItemKinds
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.allowedTags
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.declaresList
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.disallowedTags
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.itemKinds
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.names
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.recommendedTags
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.requiredTags
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.slugs
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tagRules
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.titles
import com.vitorpamplona.quartz.experimental.decentralizedLists.inheritFrom
import com.vitorpamplona.quartz.experimental.decentralizedLists.inheritFromTargets
import com.vitorpamplona.quartz.experimental.decentralizedLists.isDeliberatelyUnaffiliated
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.json
import com.vitorpamplona.quartz.experimental.decentralizedLists.searchableListContent
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritType
import com.vitorpamplona.quartz.experimental.decentralizedLists.wordWrapper
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Decentralized Lists: an editable list item declaration (kind 39999).
 *
 * Same tags as [ListItemEvent], plus the `d` tag that makes it addressable, so the contributor
 * can revise the item in place. When it declares a list through the nonstandard method, its
 * children point at its `39999:pubkey:d` coordinate.
 */
@Immutable
class AddressableListItemEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    DecentralizedListItem,
    AddressableDecentralizedListEvent,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun listPointer() = addressTag()

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    /** Event items plus any parent lists referenced by id. */
    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId) + tags.mapNotNull(ParentListTag::parseEventId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    /** Addressable items plus any parent lists referenced by coordinate. */
    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseValidAddress) + tags.mapNotNull { ParentListTag.parseAddress(it)?.toValue() }

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun indexableContent() = tags.searchableListContent()

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        tags.forEachSearchableListField(visitor)
    }

    fun parentLists() = tags.parentLists()

    fun parentListPointers() = tags.parentListPointers()

    fun name() = tags.name()

    fun title() = tags.title()

    fun slug() = tags.slug()

    fun description() = tags.description()

    fun comments() = tags.comments()

    fun itemPubKeys() = tags.itemPubKeys()

    fun itemEvents() = tags.itemEvents()

    fun itemStrings() = tags.itemStrings()

    fun itemAddresses() = tags.itemAddresses()

    /**
     * True when this item uses the spec's nonstandard method to declare a list: it carries a
     * `names` tag and sits on a list of lists. Its header fields then read the same way as a
     * 9998's, and children point at it through [listPointer].
     */
    fun declaresList() = tags.declaresList()

    fun names() = tags.names()

    fun titles() = tags.titles()

    fun slugs() = tags.slugs()

    fun tagRules() = tags.tagRules()

    fun requiredTags() = tags.requiredTags()

    fun allowedTags() = tags.allowedTags()

    fun recommendedTags() = tags.recommendedTags()

    fun disallowedTags() = tags.disallowedTags()

    fun itemKinds() = tags.itemKinds()

    fun acceptedItemKinds() = tags.acceptedItemKinds()

    fun inheritFrom() = tags.inheritFrom()

    fun inheritFromTargets(type: InheritType) = tags.inheritFromTargets(type)

    fun isDeliberatelyUnaffiliated() = tags.isDeliberatelyUnaffiliated()

    fun json() = tags.json()

    fun wordWrapper() = tags.wordWrapper()

    fun elementOf() = tags.elementOf()

    fun subsetOf() = tags.subsetOf()

    companion object {
        const val KIND = 39999

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            parent: DecentralizedListEvent,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            content: String = "",
            initializer: TagArrayBuilder<AddressableListItemEvent>.() -> Unit = {},
        ) = build(ParentListTag.classify(parent.listPointer()), dTag, createdAt, content, initializer)

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            parent: ParentList,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            content: String = "",
            initializer: TagArrayBuilder<AddressableListItemEvent>.() -> Unit = {},
        ) = eventTemplate<AddressableListItemEvent>(KIND, content, createdAt) {
            dTag(dTag)
            parentList(parent)
            initializer()
        }
    }
}
