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
package com.vitorpamplona.quartz.nipF4Podcasts.authored

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-F4 author counter-claim (`kind:10064`). A user authors this to assert which
 * podcast pubkeys they actually create — used by clients to confirm that the
 * authorship claim made by a [com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent]
 * is reciprocated by the alleged author, and to discover all podcasts authored
 * by a given user.
 */
@Immutable
class AuthoredPodcastsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    fun authored() = tags.mapNotNull(UserTag::parse)

    fun authoredKeys() = tags.mapNotNull(UserTag::parseKey)

    fun authors(podcastPubKey: HexKey) = tags.any { UserTag.isTagged(it, podcastPubKey) }

    override fun linkedPubKeys() = tags.mapNotNull(UserTag::parseKey)

    override fun pubKeyHints() = tags.mapNotNull(UserTag::parseAsHint)

    companion object {
        const val KIND = 10064
        const val ALT_DESCRIPTION = "List of authored podcasts"

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey) = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun build(
            podcasts: List<UserTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AuthoredPodcastsEvent>.() -> Unit = {},
        ) = eventTemplate<AuthoredPodcastsEvent>(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            podcasts.forEach { podcast(it) }
            initializer()
        }
    }
}
