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
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip31Alts.alt
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
    private fun recipientPubKey() = tags.firstNotNullOfOrNull(PTag::parseKey)

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
    fun replyTo() = tags.firstNotNullOfOrNull(MarkedETag::parseId)

    fun with(pubkeyHex: HexKey): Boolean = pubkeyHex == pubKey || tags.any(PTag::isTagged, pubkeyHex)

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

        fun prepareMessageToEncrypt(
            msg: String,
            imetas: List<IMetaTag>? = null,
            advertiseNip18: Boolean = true,
        ): String {
            var message = msg
            imetas?.forEach {
                message = message.replace(it.url, Nip54InlineMetadata().createUrl(it.url, it.properties))
            }

            return if (advertiseNip18) {
                NIP_18_ADVERTISEMENT + message
            } else {
                message
            }
        }

        fun build(
            to: PTag,
            encryptedMessage: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PrivateDmEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, encryptedMessage, createdAt) {
            alt(ALT)
            pTag(to)

            initializer()
        }
    }
}
