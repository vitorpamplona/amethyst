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
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.description
import com.vitorpamplona.quartz.experimental.decentralizedLists.forEachSearchableListField
import com.vitorpamplona.quartz.experimental.decentralizedLists.searchableListContent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Decentralized Lists: an immutable list declaration, a.k.a. list header (kind 9998).
 *
 * The author declares *what* the list is — its `names`, and which tags its items must, may, or
 * must not carry — but not its members: anyone contributes items with kind 9999/39999 events
 * whose `z` tag holds this event's id. Use [AddressableListHeaderEvent] (39998) for an editable
 * header.
 */
@Immutable
class ListHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    DecentralizedListEvent,
    SearchableEvent {
    override fun listPointer() = id

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

    companion object {
        const val KIND = 9998

        fun build(
            singularName: String,
            pluralName: String,
            description: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ListHeaderEvent>.() -> Unit = {},
        ) = eventTemplate<ListHeaderEvent>(KIND, "", createdAt) {
            names(singularName, pluralName)
            description?.let { this.description(it) }
            initializer()
        }
    }
}
