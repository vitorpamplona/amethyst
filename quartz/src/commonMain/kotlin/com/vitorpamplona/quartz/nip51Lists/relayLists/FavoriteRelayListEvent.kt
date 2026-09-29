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
package com.vitorpamplona.quartz.nip51Lists.relayLists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.diff.DiffableEvent
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.RelayTag
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relayFeeds
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relaySetPointers
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relays
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.splitRelayListUpdate
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class FavoriteRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    DiffableEvent<RelayListDiff>,
    LinkProvider {
    override fun diffFrom(older: Event): RelayListDiff? {
        if (older !is FavoriteRelayListEvent || older.pubKey != pubKey || older.dTag() != dTag()) return null
        return RelayListDiff(ListDiff.of(older.publicRelays(), publicRelays(), { it }), privateItemsChangeFrom(older))
    }

    fun publicRelays() = tags.relays()

    suspend fun decryptPrivateRelays(signer: NostrSigner) = privateTags(signer)?.relays()

    suspend fun decryptRelays(signer: NostrSigner): List<NormalizedRelayUrl> = publicRelays() + (decryptPrivateRelays(signer) ?: emptyList())

    /** NIP-51: besides relays, kind 10012 can point (`a`) to kind:30002 relay sets. */
    fun publicRelaySets(): List<AddressBookmark> = tags.relaySetPointers()

    suspend fun decryptPrivateRelaySets(signer: NostrSigner) = privateTags(signer)?.relaySetPointers()

    suspend fun decryptRelaySets(signer: NostrSigner): List<AddressBookmark> = publicRelaySets() + (decryptPrivateRelaySets(signer) ?: emptyList())

    /** NIP-51: the relay sets (kind 30002 `a` tags) among the favorite relays. The relay URLs themselves are not link targets. */
    override fun links(): List<Link<*>> = links { publicRelaySets().forEach { address(Relation.FAVORITE, it.address, "a") } }

    companion object {
        const val KIND = 10012

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, "")

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, "", null)

        fun createAddressTag(pubKey: HexKey): String = Address.Companion.assemble(KIND, pubKey, "")

        suspend fun updateRelayList(
            earlierVersion: FavoriteRelayListEvent,
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteRelayListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

            // Keeps public relays public and private relays private: rewriting them all as
            // private tags blanks the list out for clients that only read the plain tags.
            val split = splitRelayListUpdate(earlierVersion.tags.relays(), privateTags.relays(), relays)

            val publicTags = earlierVersion.tags.remove(RelayTag::match).plus(split.publicRelays.map { RelayTag.assemble(it) })
            val newPrivateTags = privateTags.remove(RelayTag::match).plus(split.privateRelays.map { RelayTag.assemble(it) })

            return signer.signNip51List(createdAt, KIND, publicTags, newPrivateTags)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteRelayListEvent {
            val privateTagArray = relays.map { RelayTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, emptyArray(), privateTagArray)
        }

        fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteRelayListEvent {
            val privateTagArray = relays.map { RelayTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, emptyArray(), privateTagArray)
        }

        suspend fun build(
            publicRelays: List<NormalizedRelayUrl> = emptyList(),
            privateRelays: List<NormalizedRelayUrl> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<FavoriteRelayListEvent>.() -> Unit = {},
        ) = eventTemplate<FavoriteRelayListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateRelays.map { RelayTag.assemble(it) }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            relayFeeds(publicRelays)

            initializer()
        }
    }
}

@Deprecated(
    "Renamed to FavoriteRelayListEvent. NIP-51 names kind 10012 the favorite relays list.",
    ReplaceWith("FavoriteRelayListEvent", "com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent"),
)
typealias RelayFeedsListEvent = FavoriteRelayListEvent
