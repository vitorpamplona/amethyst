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
package com.vitorpamplona.quartz.nip51Lists.relayLists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.RelayTag
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.proxyRelays
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relays
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.plus

@Immutable
class ProxyRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun publicRelays() = tags.relays()

    suspend fun decryptPrivateRelays(signer: NostrSigner) = privateTags(signer)?.relays()

    suspend fun decryptRelays(signer: NostrSigner): List<NormalizedRelayUrl> = publicRelays() + (decryptPrivateRelays(signer) ?: emptyList())

    companion object {
        const val KIND = 10087
        const val ALT = "Proxy relays from this author"
        val TAGS = arrayOf(AltTag.assemble(ALT))

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, "")

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, "", null)

        fun createAddressTag(pubKey: HexKey): String = Address.Companion.assemble(KIND, pubKey, "")

        suspend fun updateRelayList(
            earlierVersion: ProxyRelayListEvent,
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ProxyRelayListEvent {
            val newRelayList = relays.map { RelayTag.assemble(it) }
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

            val publicTags = earlierVersion.tags.remove(RelayTag::match)
            val newPrivateTags = privateTags.remove(RelayTag::notMatch).plus(newRelayList)

            return signer.signNip51List(createdAt, KIND, publicTags, newPrivateTags)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ProxyRelayListEvent {
            val privateTagArray = relays.map { RelayTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, TAGS, privateTagArray)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ProxyRelayListEvent {
            val privateTagArray = relays.map { RelayTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, TAGS, privateTagArray)
        }

        suspend fun build(
            publicRelays: List<NormalizedRelayUrl> = emptyList(),
            privateRelays: List<NormalizedRelayUrl> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProxyRelayListEvent>.() -> Unit = {},
        ) = eventTemplate<ProxyRelayListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateRelays.map { RelayTag.assemble(it) }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            proxyRelays(publicRelays)

            initializer()
        }
    }
}
