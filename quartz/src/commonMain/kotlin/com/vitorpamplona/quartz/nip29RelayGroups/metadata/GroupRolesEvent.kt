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
package com.vitorpamplona.quartz.nip29RelayGroups.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.RoleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GroupRolesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    fun groupId() = dTag()

    fun roles() = tags.mapNotNull(RoleTag::parse)

    // Each role's name, then its human-written description, one per line.
    override fun indexableContent() = roles().flatMap { listOfNotNull(it.name, it.description) }.joinToString("\n")

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        // Inline over the tags rather than roles(): this runs per event per search keystroke,
        // and needs neither the list nor a RoleTag per role.
        for (tag in tags) {
            val name = RoleTag.parseName(tag) ?: continue
            if (!visitor.visit(name)) return
            val description = RoleTag.parseDescription(tag) ?: continue
            if (!visitor.visit(description)) return
        }
    }

    companion object {
        const val KIND = 39003

        fun build(
            groupId: String,
            roles: List<RoleTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupRolesEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(groupId)
            addAll(RoleTag.assemble(roles))
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to GroupRolesEvent. NIP-29 names kind 39003 the group roles. NIP-29 group events carry the Group prefix to keep them apart from NIP-43 relay membership events.",
    ReplaceWith("GroupRolesEvent", "com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent"),
)
typealias SupportedRolesEvent = GroupRolesEvent
