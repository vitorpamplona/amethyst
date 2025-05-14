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
package com.vitorpamplona.quartz.nip51Lists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag.Companion.parse
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class FollowListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun pubKeys() = tags.mapNotNull(PTag::parseKey)

    fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun nameOrTitle() = name() ?: title()

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    companion object {
        const val KIND = 39089
        const val ALT = "List of people to follow"

        fun createListWithUser(
            name: String,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FollowListEvent) -> Unit,
        ) {
            create(
                content = "",
                tags = arrayOf(arrayOf("d", name), arrayOf("p", pubKeyHex)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun addUsers(
            earlierVersion: FollowListEvent,
            listPubKeyHex: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FollowListEvent) -> Unit,
        ) {
            create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags.plus(
                        listPubKeyHex.map { arrayOf("p", it) },
                    ),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun addUser(
            earlierVersion: FollowListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FollowListEvent) -> Unit,
        ) = addUsers(earlierVersion, listOf(pubKeyHex), signer, createdAt, onReady)

        fun removeUser(
            earlierVersion: FollowListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FollowListEvent) -> Unit,
        ) {
            create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags
                        .filter { it.size > 1 && !(it[0] == "p" && it[1] == pubKeyHex) }
                        .toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FollowListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }
    }
}
