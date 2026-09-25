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
package com.vitorpamplona.quartz.experimental.decentralizedLists.header

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.AddressableDecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.description
import com.vitorpamplona.quartz.experimental.decentralizedLists.forEachSearchableListField
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.acceptedItemKinds
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.conceptGraph
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.itemKinds
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.ConceptGraphTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.inheritFrom
import com.vitorpamplona.quartz.experimental.decentralizedLists.inheritFromTargets
import com.vitorpamplona.quartz.experimental.decentralizedLists.isDeliberatelyUnaffiliated
import com.vitorpamplona.quartz.experimental.decentralizedLists.json
import com.vitorpamplona.quartz.experimental.decentralizedLists.searchableListContent
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritType
import com.vitorpamplona.quartz.experimental.decentralizedLists.wordWrapper
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Decentralized Lists: an editable list declaration, a.k.a. list header (kind 39998).
 *
 * Same tags as [ListHeaderEvent], but addressable: the author can revise it in place, and
 * items point at it with a `z` tag holding its `39998:<pubkey>:<d>` coordinate, since its
 * event id changes with every edit.
 */
@Immutable
class AddressableListHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressableDecentralizedListEvent,
    SearchableEvent {
    override fun listPointer() = addressTag()

    override fun indexableContent() = tags.searchableListContent()

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        tags.forEachSearchableListField(visitor)
    }

    fun names() = tags.names()

    fun titles() = tags.titles()

    fun slugs() = tags.slugs()

    fun description() = tags.description()

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

    /**
     * The Concept Graph core node of this concept: the `concept-graph` tag if present, else
     * computed from this header's pubkey and `d`, as the resolution contract requires.
     */
    fun conceptGraph() = tags.conceptGraph() ?: ConceptGraphTag.compute(pubKey, dTag())

    companion object {
        const val KIND = 39998

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            singularName: String,
            pluralName: String,
            description: String? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AddressableListHeaderEvent>.() -> Unit = {},
        ) = eventTemplate<AddressableListHeaderEvent>(KIND, "", createdAt) {
            dTag(dTag)
            names(singularName, pluralName)
            description?.let { this.description(it) }
            initializer()
        }
    }
}
