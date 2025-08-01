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
package com.vitorpamplona.quartz.nip51Lists.peopleList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlin.collections.map
import kotlin.collections.plus

@Immutable
class PeopleListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(UserTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(UserTag::parseKey)

    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)

    fun nameOrTitle() = name() ?: title()

    @Deprecated("NIP-51 has deprecated Title. Use name instead", ReplaceWith("name()"))
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun users() = tags.users()

    fun countMutes() = tags.count(MuteTag::isTagged)

    fun publicPeople(): List<MuteTag> = tags.mapNotNull(MuteTag::parse)

    suspend fun privatePeople(signer: NostrSigner): List<MuteTag>? = privateTags(signer)?.mapNotNull(MuteTag::parse)

    companion object {
        const val KIND = 30000
        const val BLOCK_LIST_D_TAG = "mute"
        const val ALT = "List of people"

        fun createBlockAddress(pubKey: HexKey) = Address(KIND, pubKey, BLOCK_LIST_D_TAG)

        fun blockListFor(pubKeyHex: HexKey): String = "30000:$pubKeyHex:$BLOCK_LIST_D_TAG"

        suspend fun create(
            name: String,
            person: UserTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            dTag: String = UUID.randomUUID().toString(),
            createdAt: Long = TimeUtils.now(),
        ): PeopleListEvent =
            if (isPrivate) {
                create(
                    name = name,
                    publicPeople = emptyList(),
                    privatePeople = listOf(person),
                    signer = signer,
                    dTag = dTag,
                    createdAt = createdAt,
                )
            } else {
                create(
                    name = name,
                    publicPeople = listOf(person),
                    privatePeople = emptyList(),
                    signer = signer,
                    dTag = dTag,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: PeopleListEvent,
            person: UserTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PeopleListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    publicTags = earlierVersion.tags,
                    privateTags = privateTags.plus(person.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(person.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: PeopleListEvent,
            person: MuteTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PeopleListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

            return resign(
                privateTags = privateTags.remove(person.toTagIdOnly()),
                publicTags = earlierVersion.tags.remove(person.toTagIdOnly()),
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun resign(
            publicTags: TagArray,
            privateTags: TagArray,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = resign(
            content = PrivateTagsInContent.encryptNip04(privateTags, signer),
            tags = publicTags,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun resign(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PeopleListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            name: String,
            publicPeople: List<UserTag> = emptyList(),
            privatePeople: List<UserTag> = emptyList(),
            signer: NostrSigner,
            dTag: String = UUID.randomUUID().toString(),
            createdAt: Long = TimeUtils.now(),
        ): PeopleListEvent {
            val template = build(name, publicPeople, privatePeople, signer, dTag, createdAt)
            return signer.sign(template)
        }

        suspend fun build(
            name: String,
            publicPeople: List<UserTag> = emptyList(),
            privatePeople: List<UserTag> = emptyList(),
            signer: NostrSigner,
            dTag: String = UUID.randomUUID().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PeopleListEvent>.() -> Unit = {},
        ) = eventTemplate<PeopleListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip04(privatePeople.map { it.toTagArray() }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            dTag(dTag)
            alt(ALT)
            name(name)
            peoples(publicPeople)

            initializer()
        }
    }
}
