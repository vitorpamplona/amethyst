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
package com.vitorpamplona.quartz.nip04Dm.messages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
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
    ChatroomKeyable,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun isContentEncoded() = true

    fun isIncluded(signer: NostrSigner) = isIncluded(signer.pubKey)

    override fun isIncluded(user: HexKey) = pubKey == user || recipientPubKey() == user

    suspend fun decryptContent(signer: NostrSigner): String {
        if (!isIncluded(signer.pubKey)) throw SignerExceptions.UnauthorizedDecryptionException()

        val retVal = signer.decrypt(content, talkingWith(signer.pubKey))
        return if (retVal.startsWith(NIP_18_ADVERTISEMENT)) {
            retVal.substring(16)
        } else {
            retVal
        }
    }

    /**
     * This may or may not be the actual recipient's pub key. The event is intended to look like a
     * nip-04 EncryptedDmEvent but may omit the recipient, too. This value can be queried and used for
     * initial messages.
     */
    fun recipientPubKey() = tags.firstNotNullOfOrNull(PTag::parseKey)

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

    override fun chatroomKey(toRemove: HexKey): ChatroomKey = ChatroomKey(persistentSetOf(talkingWith(toRemove)))

    fun replyTo() = tags.firstNotNullOfOrNull(MarkedETag::parseId)

    fun with(pubkeyHex: HexKey): Boolean = pubkeyHex == pubKey || tags.any(PTag::isTagged, pubkeyHex)

    companion object {
        const val KIND = 4
        const val ALT = "Private Message"
        const val NIP_18_ADVERTISEMENT = "[//]: # (nip18)\n"

        fun prepareMessageToEncrypt(
            msg: String,
            iMetas: List<IMetaTag>? = null,
            advertiseNip18: Boolean = false,
        ): String {
            var message = msg
            iMetas?.forEach {
                message = message.replace(it.url, Nip54InlineMetadata().createUrl(it.url, it.properties))
            }

            return if (advertiseNip18) {
                NIP_18_ADVERTISEMENT + message
            } else {
                message
            }
        }

        suspend fun build(
            toUser: PTag,
            message: String,
            imetas: List<IMetaTag>? = null,
            replyingTo: EventHintBundle<PrivateDmEvent>? = null,
            createdAt: Long = TimeUtils.now(),
            signer: NostrSigner,
            initializer: TagArrayBuilder<PrivateDmEvent>.() -> Unit = {},
        ) = eventTemplate(
            kind = KIND,
            description =
                signer.nip04Encrypt(
                    prepareMessageToEncrypt(message, imetas),
                    toUser.pubKey,
                ),
            createdAt = createdAt,
        ) {
            alt(ALT)
            pTag(toUser)
            replyingTo?.let { reply(it) }

            initializer()
        }

        suspend fun create(
            to: PTag,
            message: String,
            imetas: List<IMetaTag>? = null,
            replyingTo: EventHintBundle<PrivateDmEvent>?,
            createdAt: Long = TimeUtils.now(),
            signer: NostrSigner,
        ) = signer.sign(build(to, message, imetas, replyingTo, createdAt, signer))
    }
}
