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
package com.vitorpamplona.quartz.nip51Lists.relaySets

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relaySet
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.RelayTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class RelaySetEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun relays(): List<NormalizedRelayUrl> = tags.mapNotNull(RelayTag.Companion::parse)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    companion object {
        const val KIND = 30002
        const val ALT = "Relay list"
        val ALT_TAG = arrayOf(AltTag.assemble(ALT))

        suspend fun updateRelayList(
            earlierVersion: RelaySetEvent,
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): RelaySetEvent {
            val newRelayList = relays.map { RelayTag.assemble(it) }
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

            val publicTags = earlierVersion.tags.remove(RelayTag::match)
            val newPrivateTags = privateTags.remove(RelayTag::match).plus(newRelayList)

            return signer.signNip51List(createdAt, KIND, publicTags, newPrivateTags)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): RelaySetEvent {
            val privateTagArray = relays.map { RelayTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, ALT_TAG, privateTagArray)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): RelaySetEvent {
            val privateTagArray = relays.map { RelayTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, ALT_TAG, privateTagArray)
        }

        suspend fun build(
            publicRelays: List<NormalizedRelayUrl> = emptyList(),
            privateRelays: List<NormalizedRelayUrl> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelaySetEvent>.() -> Unit = {},
        ) = eventTemplate<RelaySetEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateRelays.map { RelayTag.assemble(it) }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            relaySet(publicRelays)

            initializer()
        }
    }
}
