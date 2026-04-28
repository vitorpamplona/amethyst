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
package com.vitorpamplona.quartz.nip34Git.state

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.state.tags.HeadTag
import com.vitorpamplona.quartz.nip34Git.state.tags.RefTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-34 kind 30618 — Repository State Announcement.
 *
 * Publishes the current branch tips, tag refs, and HEAD for a repository
 * identified by the same `d` tag used in the corresponding kind-30617
 * announcement. Absence of any `refs` tags signals that the author has
 * stopped tracking state (distinct from a NIP-09 deletion).
 */
@Immutable
class GitRepositoryStateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun refs(): List<RefTag> = tags.mapNotNull(RefTag::parse)

    fun branches(): List<RefTag> = refs().filter { it.kind == RefTag.Kind.BRANCH }

    fun tagRefs(): List<RefTag> = refs().filter { it.kind == RefTag.Kind.TAG }

    fun head(): String? = tags.firstNotNullOfOrNull(HeadTag::parse)

    /** `true` if the author is no longer tracking state for this repository. */
    fun isDiscontinued(): Boolean = refs().isEmpty()

    companion object {
        const val KIND = 30618
        const val ALT_DESCRIPTION = "Git Repository State"

        fun build(
            dTag: String,
            refs: List<RefTag>,
            head: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitRepositoryStateEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTag)
            refs.forEach { ref(it) }
            head?.let { head(it) }
            initializer()
        }
    }
}
