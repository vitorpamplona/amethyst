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
package com.vitorpamplona.quartz.nip51Lists.starterPack

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.each
import com.vitorpamplona.quartz.nip01Core.links.hashtags
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class StarterPackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(description())
    }

    override fun pubKeyHints() = tags.mapNotNull(UserTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(UserTag::parseKey)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun follows() = tags.follows()

    fun followIds() = tags.followIds()

    fun followIdSet() = tags.followIdSet()

    fun hashtags(): List<String> = tags.mapNotNull(HashtagTag::parse)

    /** NIP-51: the people in the pack are its `MEMBER`s; `t` are its topics. */
    override fun links(): List<Link<*>> =
        links {
            each(tags, UserTag::parse) { user(Relation.MEMBER, it, UserTag.TAG_NAME) }
            hashtags(tags)
        }

    companion object {
        const val KIND = 39089

        fun createAddress(
            pubKey: HexKey,
            dTag: String,
        ) = Address(KIND, pubKey, dTag)

        fun listFor(
            pubKey: HexKey,
            dTag: String,
        ): String = Address.assemble(KIND, pubKey, dTag)

        @OptIn(ExperimentalUuidApi::class)
        suspend fun create(
            name: String,
            person: UserTag,
            signer: NostrSigner,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
        ): StarterPackEvent =
            create(
                name = name,
                people = listOf(person),
                signer = signer,
                dTag = dTag,
                createdAt = createdAt,
            )

        suspend fun addUsers(
            earlierVersion: StarterPackEvent,
            people: List<UserTag>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): StarterPackEvent =
            resign(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(people.map { it.toTagArray() }),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun add(
            earlierVersion: StarterPackEvent,
            person: UserTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = addUsers(earlierVersion, listOf(person), signer, createdAt)

        suspend fun remove(
            earlierVersion: StarterPackEvent,
            person: UserTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): StarterPackEvent =
            resign(
                content = earlierVersion.content,
                tags = earlierVersion.tags.remove(person.toTagIdOnly()),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun resign(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): StarterPackEvent {
            val newTags = tags

            return signer.sign(createdAt, KIND, newTags, content)
        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun create(
            name: String,
            people: List<UserTag> = emptyList(),
            signer: NostrSigner,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
        ): StarterPackEvent {
            val template = build(name, people, dTag, createdAt)
            return signer.sign(template)
        }

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            name: String,
            people: List<UserTag> = emptyList(),
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StarterPackEvent>.() -> Unit = {},
        ) = eventTemplate(
            kind = KIND,
            description = "",
            createdAt = createdAt,
        ) {
            dTag(dTag)
            title(name)
            people(people)

            initializer()
        }
    }
}
