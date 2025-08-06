/**
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
package com.vitorpamplona.quartz.nip34Git.repository

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.repository.tags.CloneTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.DescriptionTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.NameTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.WebTag
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

@Immutable
class GitRepositoryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun web() = tags.firstNotNullOfOrNull(WebTag::parse)

    fun clone() = tags.firstNotNullOfOrNull(CloneTag::parse)

    companion object {
        const val KIND = 30617
        const val ALT_DESCRIPTION = "Git Repository"

        fun build(
            name: String,
            description: String? = null,
            webUrl: String? = null,
            cloneUrl: String? = null,
            dTag: String = UUID.randomUUID().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitRepositoryEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTag)
            name(name)
            description?.let { description(it) }
            webUrl?.let { webUrl(it) }
            cloneUrl?.let { cloneUrl(it) }
            initializer()
        }
    }
}
