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
package com.vitorpamplona.quartz.nip34Git.repository

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.repository.tags.CloneTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.DescriptionTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.EucTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.MaintainersTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.NameTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.RelaysTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.WebTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    /** First web URL, for backwards compatibility. Prefer [webs]. */
    fun web() = tags.firstNotNullOfOrNull(WebTag::parse)

    fun webs(): List<String> = tags.mapNotNull(WebTag::parse)

    /** First clone URL, for backwards compatibility. Prefer [clones]. */
    fun clone() = tags.firstNotNullOfOrNull(CloneTag::parse)

    fun clones(): List<String> = tags.mapNotNull(CloneTag::parse)

    /**
     * Relays the repository author monitors for patches and issues. NIP-34
     * encodes this as a single multi-value tag, so the first matching
     * `relays` tag wins.
     */
    fun relays(): List<String> = tags.firstNotNullOfOrNull(RelaysTag::parse) ?: emptyList()

    /**
     * Accepted maintainer pubkeys. The event author is an implicit maintainer
     * and is not required to appear in this list.
     */
    fun maintainers(): List<HexKey> = tags.firstNotNullOfOrNull(MaintainersTag::parse) ?: emptyList()

    fun hashtags(): List<String> = tags.hashtags()

    /** Earliest-unique-commit ID (NIP-34 `["r", <commit>, "euc"]`). */
    fun earliestUniqueCommit(): String? = tags.firstNotNullOfOrNull(EucTag::parse)

    /**
     * `true` if the event carries `["t", "personal-fork"]`, signaling the
     * author does not actively seek patches/feedback for this repository.
     */
    fun isPersonalFork(): Boolean = tags.any { HashtagTag.isTagged(it, PERSONAL_FORK) }

    companion object {
        const val KIND = 30617
        const val ALT_DESCRIPTION = "Git Repository"
        const val PERSONAL_FORK = "personal-fork"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            name: String,
            description: String? = null,
            webUrl: String? = null,
            cloneUrl: String? = null,
            dTag: String = Uuid.random().toString(),
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

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            name: String,
            description: String?,
            webUrls: List<String>,
            cloneUrls: List<String>,
            relays: List<String>,
            maintainers: List<HexKey>,
            hashtags: List<String>,
            earliestUniqueCommit: String?,
            personalFork: Boolean = false,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitRepositoryEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTag)
            name(name)
            description?.let { description(it) }
            webUrls.forEach { webUrl(it) }
            cloneUrls.forEach { cloneUrl(it) }
            if (relays.isNotEmpty()) relays(relays)
            if (maintainers.isNotEmpty()) maintainers(maintainers)
            if (hashtags.isNotEmpty()) hashtags(hashtags)
            earliestUniqueCommit?.let { euc(it) }
            if (personalFork) hashtag(PERSONAL_FORK)
            initializer()
        }
    }
}
