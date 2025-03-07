/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isTagged
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

@Immutable
abstract class GeneralListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, kind, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint) + (cachedPrivateTags()?.mapNotNull(ETag::parseAsHint) ?: emptyList())

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint) + (cachedPrivateTags()?.mapNotNull(ATag::parseAsHint) ?: emptyList())

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint) + (cachedPrivateTags()?.mapNotNull(PTag::parseAsHint) ?: emptyList())

    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)

    @Deprecated("NIP-51 has deprecated Title. Use name instead", ReplaceWith("name()"))
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun nameOrTitle() = name() ?: title()
    fun description() = tags.firstOrNull { it.size > 1 && it[0] == "description" }?.get(1)

    fun nameOrTitle() = name()?.ifBlank { null } ?: title()?.ifBlank { null }

    fun filterTagList(
        key: String,
        privateTags: Array<Array<String>>?,
    ): ImmutableSet<String> {
        val result = HashSet<String>(tags.size + (privateTags?.size ?: 0))

        privateTags?.let { it.filter { it.size > 1 && it[0] == key }.mapTo(result) { it[1] } }

        tags.filter { it.size > 1 && it[0] == key }.mapTo(result) { it[1] }

        return result.toImmutableSet()
    }

    fun isTagged(
        key: String,
        tag: String,
        isPrivate: Boolean,
        signer: NostrSigner,
        onReady: (Boolean) -> Unit,
    ) = if (isPrivate) {
        privateTagsOrEmpty(signer = signer) {
            onReady(
                it.any { it.size > 1 && it[0] == key && it[1] == tag },
            )
        }
    } else {
        onReady(tags.isTagged(key, tag))
    }

    fun privateTagsOrEmpty(
        signer: NostrSigner,
        onReady: (Array<Array<String>>) -> Unit,
    ) {
        privateTags(signer, onReady)
    }

    fun privateTaggedUsers(
        signer: NostrSigner,
        onReady: (List<String>) -> Unit,
    ) = privateTags(signer) { onReady(filterUsers(it)) }

    fun privateHashtags(
        signer: NostrSigner,
        onReady: (List<String>) -> Unit,
    ) = privateTags(signer) { onReady(filterHashtags(it)) }

    fun privateGeohashes(
        signer: NostrSigner,
        onReady: (List<String>) -> Unit,
    ) = privateTags(signer) { onReady(filterGeohashes(it)) }

    fun privateTaggedEvents(
        signer: NostrSigner,
        onReady: (List<String>) -> Unit,
    ) = privateTags(signer) { onReady(filterEvents(it)) }

    fun privateATags(
        signer: NostrSigner,
        onReady: (List<ATag>) -> Unit,
    ) = privateTags(signer) { onReady(filterATags(it)) }

    fun privateAddress(
        signer: NostrSigner,
        onReady: (List<Address>) -> Unit,
    ) = privateTags(signer) { onReady(filterAddresses(it)) }

    fun filterUsers(tags: Array<Array<String>>): List<String> = tags.mapNotNull(PTag::parseKey)

    fun filterHashtags(tags: Array<Array<String>>): List<String> = tags.mapNotNull(HashtagTag::parse)

    fun filterGeohashes(tags: Array<Array<String>>): List<String> = tags.geohashes()

    fun filterEvents(tags: Array<Array<String>>): List<String> = tags.mapNotNull(ETag::parseId)

    fun filterATags(tags: Array<Array<String>>): List<ATag> = tags.mapNotNull(ATag::parse)

    fun filterAddresses(tags: Array<Array<String>>): List<Address> = tags.mapNotNull(ATag::parseAddress)

    companion object {
        fun createPrivateTags(
            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,
            signer: NostrSigner,
            onReady: (String) -> Unit,
        ) {
            val privTags = mutableListOf<Array<String>>()
            privEvents?.forEach { privTags.add(arrayOf("e", it)) }
            privUsers?.forEach { privTags.add(arrayOf("p", it)) }
            privAddresses?.forEach { privTags.add(arrayOf("a", it.toTag())) }

            return encryptTags(privTags.toTypedArray(), signer, onReady)
        }

        fun encryptTags(
            privateTags: Array<Array<String>>? = null,
            signer: NostrSigner,
            onReady: (String) -> Unit,
        ) {
            val msg = EventMapper.mapper.writeValueAsString(privateTags)

            signer.nip04Encrypt(
                msg,
                signer.pubKey,
                onReady,
            )
        }
    }
}
