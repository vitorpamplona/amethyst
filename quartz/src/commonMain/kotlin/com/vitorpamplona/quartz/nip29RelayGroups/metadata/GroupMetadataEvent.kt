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
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.hasTagWithContent
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GroupMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun groupId() = dTag()

    fun name() = tags.firstTagValue("name")

    fun about() = tags.firstTagValue("about")

    fun picture() = tags.firstTagValue("picture")

    fun isPrivate() = tags.hasTagWithContent("private") || !tags.hasTagWithContent("public")

    fun isRestricted() = tags.hasTagWithContent("closed") || !tags.hasTagWithContent("open")

    fun isHidden() = tags.hasTagWithContent("private")

    fun isClosed() = tags.hasTagWithContent("closed")

    fun statusTags(): Set<GroupStatus> {
        val statuses = mutableSetOf<GroupStatus>()
        GroupStatus.entries.forEach { status ->
            if (tags.hasTagWithContent(status.code)) {
                statuses.add(status)
            }
        }
        return statuses
    }

    enum class GroupStatus(
        val code: String,
    ) {
        PRIVATE("private"),
        PUBLIC("public"),
        OPEN("open"),
        CLOSED("closed"),
    }

    companion object {
        const val KIND = 39000
        const val ALT_DESCRIPTION = "Group metadata"

        fun build(
            groupId: String,
            name: String? = null,
            about: String? = null,
            picture: String? = null,
            status: Set<GroupStatus> = emptySet(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupMetadataEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(groupId)
            name?.let { add(arrayOf("name", it)) }
            about?.let { add(arrayOf("about", it)) }
            picture?.let { add(arrayOf("picture", it)) }
            status.forEach { add(arrayOf(it.code)) }
            initializer()
        }
    }
}
