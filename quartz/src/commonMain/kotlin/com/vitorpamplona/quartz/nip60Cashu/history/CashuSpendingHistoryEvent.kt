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
package com.vitorpamplona.quartz.nip60Cashu.history

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.userTags
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip61Nutzaps.redemption.redeemedNutzaps
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-60 Cashu Spending History Event (kind:7376).
 *
 * Records transaction history when the wallet balance changes.
 * Content is NIP-44 encrypted and contains a tag array with:
 * - ["direction", "in"|"out"]
 * - ["amount", "<amount>"]
 * - ["unit", "sat"|"usd"|"eur"]
 * - ["e", "<event-id>", "<relay>", "created"|"destroyed"|"redeemed"]
 *
 * The "e" tags with "redeemed" marker SHOULD be left unencrypted in the tags array.
 * All other "e" tags should be encrypted in the content.
 *
 * NIP-61 nutzap redemptions are this same kind: a history entry whose public tags
 * carry the redeemed nutzap (`["e", <nutzap-id>, <relay>, "redeemed"]`) and the
 * sender's `p` tag. See `buildNutzapRedemption` in `nip61Nutzaps.redemption`.
 */
@Immutable
class CashuSpendingHistoryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    LinkProvider {
    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /**
     * NIP-61: the nutzaps this entry redeemed, from the public `e` tags marked `redeemed`.
     */
    fun redeemedNutzaps() = tags.redeemedNutzaps()

    /**
     * Decrypts the content to get the private spending history tags.
     */
    suspend fun cachedPrivateTags(signer: NostrSigner): TagArray = PrivateTagsInContent.decrypt(content, signer)

    /**
     * Gets the transaction direction from the decrypted content.
     */
    suspend fun direction(signer: NostrSigner): SpendingDirection? {
        val privateTags = cachedPrivateTags(signer)
        val directionTag = privateTags.firstOrNull { it.size >= 2 && it[0] == "direction" } ?: return null
        return SpendingDirection.fromCode(directionTag[1])
    }

    /**
     * Gets the transaction amount from the decrypted content.
     */
    suspend fun amount(signer: NostrSigner): Long? {
        val privateTags = cachedPrivateTags(signer)
        return privateTags
            .firstOrNull { it.size >= 2 && it[0] == "amount" }
            ?.get(1)
            ?.toLongOrNull()
    }

    /**
     * Gets the unit from the decrypted content. Default: "sat".
     */
    suspend fun unit(signer: NostrSigner): String {
        val privateTags = cachedPrivateTags(signer)
        return privateTags
            .firstOrNull { it.size >= 2 && it[0] == "unit" }
            ?.get(1)
            ?: "sat"
    }

    /**
     * Gets token references from both encrypted content and public tags.
     */
    suspend fun tokenReferences(signer: NostrSigner): List<TokenReference> {
        val privateTags = cachedPrivateTags(signer)
        val privateRefs = privateTags.mapNotNull(TokenReference::parseFromTag)
        val publicRefs = tags.mapNotNull(TokenReference::parseFromTag)
        return privateRefs + publicRefs
    }

    /**
     * Gets unencrypted "redeemed" token references from public tags.
     */
    fun redeemedReferences(): List<TokenReference> =
        tags
            .mapNotNull(TokenReference::parseFromTag)
            .filter { it.marker == TokenReference.MARKER_REDEEMED }

    /**
     * NIP-60 marks each public `e` with what the spend did to it. Only `redeemed` (a NIP-61 nutzap)
     * is meant to stay public; `created` and `destroyed` token events are normally encrypted and so
     * invisible here. The `p` is the redeemed nutzap's sender.
     */
    override fun links(): List<Link<*>> =
        links {
            tags.fastForEach {
                val reference = TokenReference.parseFromTag(it) ?: return@fastForEach
                val relation =
                    when (reference.marker) {
                        TokenReference.MARKER_REDEEMED -> Relation.REDEEMED
                        TokenReference.MARKER_CREATED -> Relation.CREATED
                        else -> Relation.DESTROYED
                    }
                event(relation, reference.eventId, "e")
            }
            userTags(Relation.REDEEMED_AUTHOR, tags)
        }

    companion object {
        const val KIND = 7376

        suspend fun build(
            direction: SpendingDirection,
            amount: Long,
            unit: String = "sat",
            tokenReferences: List<TokenReference>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CashuSpendingHistoryEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            // Unencrypted "e" tags for redeemed markers
            tokenReferences
                .filter { it.marker == TokenReference.MARKER_REDEEMED }
                .forEach { ref ->
                    add(TokenReference.assemble(ref.eventId, ref.relay, ref.marker))
                }
            initializer()
        }.let { template ->
            val privateTags =
                buildList {
                    add(arrayOf("direction", direction.code))
                    add(arrayOf("amount", amount.toString()))
                    add(arrayOf("unit", unit))
                    // Encrypt non-redeemed token references
                    tokenReferences
                        .filter { it.marker != TokenReference.MARKER_REDEEMED }
                        .forEach { ref ->
                            add(TokenReference.assemble(ref.eventId, ref.relay, ref.marker))
                        }
                }.toTypedArray()

            val encryptedContent = PrivateTagsInContent.encryptNip44(privateTags, signer)

            EventTemplate<CashuSpendingHistoryEvent>(
                template.createdAt,
                template.kind,
                template.tags,
                encryptedContent,
            )
        }
    }
}
