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
package com.vitorpamplona.quartz.nip04Dm

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip17Dm.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.ChatroomKeyable
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupSerializer
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.ZapRaiserSerializer
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import kotlinx.collections.immutable.persistentSetOf

@Immutable
class PrivateDmEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    ChatroomKeyable {
    @Transient private var decryptedContent: Map<HexKey, String> = mapOf()

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (decryptedContent.values.sumOf { pointerSizeInBytes + it.bytesUsedInMemory() })

    override fun isContentEncoded() = true

    /**
     * This may or may not be the actual recipient's pub key. The event is intended to look like a
     * nip-04 EncryptedDmEvent but may omit the recipient, too. This value can be queried and used for
     * initial messages.
     */
    private fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun recipientPubKeyBytes() = recipientPubKey()?.runCatching { Hex.decode(this) }?.getOrNull()

    fun verifiedRecipientPubKey(): HexKey? {
        val recipient = recipientPubKey()
        return if (Hex.isHex(recipient)) {
            recipient
        } else {
            null
        }
    }

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) verifiedRecipientPubKey() ?: pubKey else pubKey

    override fun chatroomKey(toRemove: String): ChatroomKey = ChatroomKey(persistentSetOf(talkingWith(toRemove)))

    /**
     * To be fully compatible with nip-04, we read e-tags that are in violation to nip-18.
     *
     * Nip-18 messages should refer to other events by inline references in the content like
     * `[](e/c06f795e1234a9a1aecc731d768d4f3ca73e80031734767067c82d67ce82e506).
     */
    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun with(pubkeyHex: String): Boolean = pubkeyHex == pubKey || tags.any { it.size > 1 && it[0] == "p" && it[1] == pubkeyHex }

    fun cachedContentFor(signer: NostrSigner): String? = decryptedContent[signer.pubKey]

    fun plainContent(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        decryptedContent[signer.pubKey]?.let {
            onReady(it)
            return
        }

        signer.decrypt(content, talkingWith(signer.pubKey)) { retVal ->
            val content =
                if (retVal.startsWith(NIP_18_ADVERTISEMENT)) {
                    retVal.substring(16)
                } else {
                    retVal
                }

            decryptedContent = decryptedContent + Pair(signer.pubKey, content)

            onReady(content)
        }
    }

    companion object {
        const val KIND = 4
        const val ALT = "Private Message"
        const val NIP_18_ADVERTISEMENT = "[//]: # (nip18)\n"

        fun create(
            recipientPubKey: HexKey,
            msg: String,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            publishedRecipientPubKey: HexKey? = null,
            advertiseNip18: Boolean = true,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null,
            imetas: List<IMetaTag>? = null,
            isDraft: Boolean,
            onReady: (PrivateDmEvent) -> Unit,
        ) {
            var message = msg
            imetas?.forEach {
                message = message.replace(it.url, Nip54InlineMetadata().createUrl(it.url, it.properties))
            }

            message =
                if (advertiseNip18) {
                    NIP_18_ADVERTISEMENT + message
                } else {
                    message
                }

            val tags = mutableListOf<Array<String>>()
            publishedRecipientPubKey?.let { tags.add(arrayOf("p", publishedRecipientPubKey)) }
            replyTos?.forEach { tags.add(arrayOf("e", it, "", "reply")) }
            mentions?.forEach { tags.add(arrayOf("p", it)) }
            zapReceiver?.forEach { tags.add(ZapSplitSetupSerializer.toTagArray(it)) }
            zapRaiserAmount?.let { tags.add(ZapRaiserSerializer.toTagArray(it)) }

            if (markAsSensitive) {
                tags.add(ContentWarningSerializer.toTagArray())
            }

            geohash?.let { tags.addAll(geohashMipMap(it)) }
            /* Privacy issue: DO NOT ADD THESE TO THE TAGS.
            imetas?.forEach {
                tags.add(Nip92MediaAttachments.createTag(it))
            }
             */

            tags.add(AltTagSerializer.toTagArray(ALT))

            signer.nip04Encrypt(message, recipientPubKey) { content ->
                if (isDraft) {
                    signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), content, onReady)
                } else {
                    signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
                }
            }
        }
    }
}
