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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.core.tagArray
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.hashtags
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ActiveHoursEndTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ActiveHoursStartTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.FirstCreatedAtTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.FollowerCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.HopsTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.PetNameTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.PostCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReactionsCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReplyCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReportsCountReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReportsCountSentTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.SummaryTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAmountReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAmountSentTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAvgAmountDayReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAvgAmountDaySentTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapCountReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapCountSentTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class UserAssertionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    LinkProvider {
    // Only the public petname and summary tags are indexed; the rest of the
    // card lives in NIP-44 encrypted content and is intentionally never
    // indexed. (petName()/summary() read the public tag array, so a petname
    // kept in the encrypted content stays out of the index.)
    override fun indexableContent() = (listOfNotNull(petName(), summary()) + topics()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(petName())) return
        if (!visitor.visit(summary())) return
        topics().forEach { if (!visitor.visit(it)) return }
    }

    fun aboutUser() = tags.dTag()

    fun rank() = tags.rank()

    fun followerCount() = tags.followerCount()

    fun hops() = tags.hops()

    fun firstCreatedAt() = tags.firstCreatedAt()

    fun postCount() = tags.postCount()

    fun replyCount() = tags.replyCount()

    fun reactionsCount() = tags.reactionsCount()

    fun zapAmountReceived() = tags.zapAmountReceived()

    fun zapAmountSent() = tags.zapAmountSent()

    fun zapCountReceived() = tags.zapCountReceived()

    fun zapCountSent() = tags.zapCountSent()

    fun zapAvgAmountDayReceived() = tags.zapAvgAmountDayReceived()

    fun zapAvgAmountDaySent() = tags.zapAvgAmountDaySent()

    fun reportsCountReceived() = tags.reportsCountReceived()

    fun reportsCountSent() = tags.reportsCountSent()

    fun topics() = tags.topics()

    fun activeHoursStart() = tags.activeHoursStart()

    fun activeHoursEnd() = tags.activeHoursEnd()

    fun petName() = tags.petName()

    fun summary() = tags.summary()

    /**
     * NIP-85: the `d` is the SUBJECT, the user this card is about (not the card's own identity,
     * so it is linked although it is the `d`). The public scores ride on it, keyed by their NIP-85
     * tag names (`rank`, `followers`, `hops`, …); `t` are the user's topics. A `p` equal to the
     * `d` is only its relay hint. The encrypted contact-card fields are invisible.
     */
    override fun links(): List<Link> =
        links {
            val scores = LinkedHashMap<String, Any>()
            tags.fastForEach { tag ->
                if (tag.size < 2) return@fastForEach
                val value: Number? =
                    when (tag[0]) {
                        RankTag.TAG_NAME -> RankTag.parse(tag)
                        FollowerCountTag.TAG_NAME -> FollowerCountTag.parse(tag)
                        HopsTag.TAG_NAME -> HopsTag.parse(tag)
                        FirstCreatedAtTag.TAG_NAME -> FirstCreatedAtTag.parse(tag)
                        PostCountTag.TAG_NAME -> PostCountTag.parse(tag)
                        ReplyCountTag.TAG_NAME -> ReplyCountTag.parse(tag)
                        ReactionsCountTag.TAG_NAME -> ReactionsCountTag.parse(tag)
                        ZapAmountReceivedTag.TAG_NAME -> ZapAmountReceivedTag.parse(tag)
                        ZapAmountSentTag.TAG_NAME -> ZapAmountSentTag.parse(tag)
                        ZapCountReceivedTag.TAG_NAME -> ZapCountReceivedTag.parse(tag)
                        ZapCountSentTag.TAG_NAME -> ZapCountSentTag.parse(tag)
                        ZapAvgAmountDayReceivedTag.TAG_NAME -> ZapAvgAmountDayReceivedTag.parse(tag)
                        ZapAvgAmountDaySentTag.TAG_NAME -> ZapAvgAmountDaySentTag.parse(tag)
                        ReportsCountReceivedTag.TAG_NAME -> ReportsCountReceivedTag.parse(tag)
                        ReportsCountSentTag.TAG_NAME -> ReportsCountSentTag.parse(tag)
                        ActiveHoursStartTag.TAG_NAME -> ActiveHoursStartTag.parse(tag)
                        ActiveHoursEndTag.TAG_NAME -> ActiveHoursEndTag.parse(tag)
                        else -> null
                    }
                // The first of each wins, as the accessors (rank(), followerCount()…) read them.
                if (value != null && tag[0] !in scores) scores[tag[0]] = value
            }
            user(Relation.SUBJECT, aboutUser(), "d", scores.ifEmpty { null })
            hashtags(tags)
        }

    companion object {
        const val KIND = 30382

        fun createAddress(
            owner: HexKey,
            target: HexKey,
        ): Address = Address(KIND, owner, target)

        fun createAddressTag(
            owner: HexKey,
            target: HexKey,
        ): ATag = ATag(KIND, owner, target, null)

        suspend fun create(
            targetUser: HexKey,
            petName: String? = null,
            summary: String? = null,
            emojis: List<EmojiUrlTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            publicInitializer: TagArrayBuilder<UserAssertionEvent>.() -> Unit = {},
            privateInitializer: TagArrayBuilder<UserAssertionEvent>.() -> Unit = {},
        ): UserAssertionEvent = signer.sign(build(targetUser, petName, summary, emojis, signer, createdAt, publicInitializer, privateInitializer))

        /**
         * Unsigned template for a new card about [targetUser]. The petname, summary
         * and the NIP-30 emoji mappings their shortcodes use always go in the NIP-44
         * encrypted content ([signer] only encrypts here; the caller signs).
         */
        suspend fun build(
            targetUser: HexKey,
            petName: String? = null,
            summary: String? = null,
            emojis: List<EmojiUrlTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            publicInitializer: TagArrayBuilder<UserAssertionEvent>.() -> Unit = {},
            privateInitializer: TagArrayBuilder<UserAssertionEvent>.() -> Unit = {},
        ): EventTemplate<UserAssertionEvent> {
            val privateTags =
                tagArray {
                    petName?.let { petName(it) }
                    summary?.let { summary(it) }
                    emojis(emojis)
                    privateInitializer()
                }

            return eventTemplate(
                kind = KIND,
                description = PrivateTagsInContent.encryptNip44(privateTags, signer),
                createdAt = createdAt,
            ) {
                dTag(targetUser)
                publicInitializer()
            }
        }

        /**
         * Unsigned template that replaces the petname, summary and their NIP-30
         * custom emoji mappings on an existing card, keeping every other public and
         * private tag intact. All of them always live in the NIP-44 encrypted
         * content — any stray public petname/summary copy is stripped. A `null`
         * value removes the field; the private `emoji` tag set is replaced
         * wholesale since it only exists to render the petname/summary shortcodes.
         * [signer] only decrypts/encrypts here; the caller signs the template.
         */
        suspend fun updatePetNameAndSummary(
            earlierVersion: UserAssertionEvent,
            petName: String? = null,
            summary: String? = null,
            emojis: List<EmojiUrlTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<UserAssertionEvent> {
            val privateTags =
                earlierVersion.privateTags(signer)
                    ?: throw SignerExceptions.UnauthorizedDecryptionException()

            var newPrivateTags =
                privateTags
                    .remove(arrayOf(PetNameTag.TAG_NAME))
                    .remove(arrayOf(SummaryTag.TAG_NAME))
                    .remove(arrayOf(EmojiUrlTag.TAG_NAME))

            petName?.let { newPrivateTags = newPrivateTags.plus(PetNameTag.assemble(it)) }
            summary?.let { newPrivateTags = newPrivateTags.plus(SummaryTag.assemble(it)) }
            if (emojis.isNotEmpty()) {
                newPrivateTags = newPrivateTags.plus(emojis.map { it.toTagArray() })
            }

            val newPublicTags =
                earlierVersion.tags
                    .remove(arrayOf(PetNameTag.TAG_NAME))
                    .remove(arrayOf(SummaryTag.TAG_NAME))

            return EventTemplate(
                createdAt = createdAt,
                kind = KIND,
                tags = newPublicTags,
                content = PrivateTagsInContent.encryptNip44(newPrivateTags, signer),
            )
        }
    }
}

@Deprecated(
    "Renamed to UserAssertionEvent. NIP-85 names kind 30382 the user trusted assertion.",
    ReplaceWith("UserAssertionEvent", "com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent"),
)
typealias ContactCardEvent = UserAssertionEvent
