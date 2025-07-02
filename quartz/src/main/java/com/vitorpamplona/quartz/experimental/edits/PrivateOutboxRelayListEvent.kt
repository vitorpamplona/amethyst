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
package com.vitorpamplona.quartz.experimental.edits

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.experimental.edits.tags.RelayTag
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class PrivateOutboxRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient private var privateTagsCache: Array<Array<String>>? = null

    override fun isContentEncoded() = true

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (privateTagsCache?.sumOf { pointerSizeInBytes + it.sumOf { pointerSizeInBytes + it.bytesUsedInMemory() } } ?: 0)

    fun relays(): List<NormalizedRelayUrl>? =
        tags
            .mapNotNull(RelayTag::parse)
            .plus(
                privateTagsCache?.mapNotNull(RelayTag::parse) ?: emptyList(),
            ).ifEmpty { null }

    fun cachedPrivateTags(): Array<Array<String>>? = privateTagsCache

    fun privateTags(
        signer: NostrSigner,
        onReady: (Array<Array<String>>) -> Unit,
    ) {
        if (content.isEmpty()) {
            onReady(emptyArray())
            return
        }

        privateTagsCache?.let {
            onReady(it)
            return
        }

        try {
            signer.nip44Decrypt(content, pubKey) {
                try {
                    privateTagsCache = EventMapper.mapper.readValue<TagArray>(it)
                    privateTagsCache?.let { onReady(it) }
                } catch (e: Throwable) {
                    Log.w("PrivateOutboxRelayListEvent", "Error parsing the JSON: ${e.message}. Json `$it` from event `${toNostrUri()}`")
                }
            }
        } catch (e: Throwable) {
            Log.w("PrivateOutboxRelayListEvent", "Error decrypting content: ${e.message}. Event: `${toNostrUri()}`")
        }
    }

    companion object {
        const val KIND = 10013
        val TAGS = arrayOf(AltTag.assemble("Relay list to store private content from this author"))

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun encryptTags(
            privateTags: Array<Array<String>>? = null,
            signer: NostrSigner,
            onReady: (String) -> Unit,
        ) {
            val msg = EventMapper.mapper.writeValueAsString(privateTags)

            signer.nip44Encrypt(
                msg,
                signer.pubKey,
                onReady,
            )
        }

        fun createTagArray(relays: List<NormalizedRelayUrl>): Array<Array<String>> =
            relays
                .map {
                    RelayTag.assemble(it)
                }.toTypedArray()

        fun updateRelayList(
            earlierVersion: PrivateOutboxRelayListEvent,
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PrivateOutboxRelayListEvent) -> Unit,
        ) {
            val tags =
                earlierVersion.privateTagsCache
                    ?.filter(RelayTag::notMatch)
                    ?.plus(
                        relays.map {
                            RelayTag.assemble(it)
                        },
                    )?.toTypedArray() ?: emptyArray()

            encryptTags(tags, signer) {
                signer.sign<PrivateOutboxRelayListEvent>(createdAt, KIND, TAGS, it) {
                    it.privateTagsCache = tags
                    onReady(it)
                }
            }
        }

        fun createFromScratch(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PrivateOutboxRelayListEvent) -> Unit,
        ) {
            create(relays, signer, createdAt, onReady)
        }

        fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PrivateOutboxRelayListEvent) -> Unit,
        ) {
            val privateTagArray = createTagArray(relays)
            encryptTags(privateTagArray, signer) { privateTags ->
                signer.sign<PrivateOutboxRelayListEvent>(createdAt, KIND, TAGS, privateTags) {
                    it.privateTagsCache = privateTagArray
                    onReady(it)
                }
            }
        }
    }
}
