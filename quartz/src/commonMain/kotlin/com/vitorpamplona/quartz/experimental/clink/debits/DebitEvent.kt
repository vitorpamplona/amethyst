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
package com.vitorpamplona.quartz.experimental.clink.debits

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.clink.tags.ClinkVersionTag
import com.vitorpamplona.quartz.experimental.clink.tags.clinkVersion
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * CLINK Debits event (kind 21002). The same kind carries both the request (requestor →
 * service) and the response (service → requestor); a response is distinguished by an `e`
 * tag referencing the request. Content is NIP-44 encrypted between the two parties.
 *
 * See https://github.com/shocknet/clink/blob/master/specs/clink-debits.md
 */
@Immutable
class DebitEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun recipientPubKey() = tags.firstNotNullOfOrNull(PTag::parseKey)

    fun requestId() = tags.firstNotNullOfOrNull(ETag::parseId)

    fun isResponse() = requestId() != null

    fun version() = tags.firstNotNullOfOrNull(ClinkVersionTag::parse)

    private fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) recipientPubKey() ?: pubKey else pubKey

    fun canDecrypt(signer: NostrSigner) = pubKey == signer.pubKey || recipientPubKey() == signer.pubKey

    suspend fun decryptContent(signer: NostrSigner): String {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()
        return signer.nip44Decrypt(content, talkingWith(signer.pubKey))
    }

    suspend fun decryptRequest(signer: NostrSigner): DebitRequest = OptimizedJsonMapper.fromJsonTo<DebitRequest>(decryptContent(signer))

    suspend fun decryptResponse(signer: NostrSigner): DebitResponse = OptimizedJsonMapper.fromJsonTo<DebitResponse>(decryptContent(signer))

    companion object {
        const val KIND = 21002
        const val ALT = "CLINK debit"

        /** Builds a request event (requestor side) addressed to the debit service. */
        suspend fun createRequest(
            request: DebitRequest,
            servicePubKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DebitEvent {
            val encrypted = signer.nip44Encrypt(OptimizedJsonMapper.toJson(request), servicePubKey)
            return signer.sign(
                eventTemplate(KIND, encrypted, createdAt) {
                    pTag(servicePubKey, null)
                    clinkVersion()
                    alt(ALT)
                },
            )
        }

        /** Builds a response event (service side) referencing the original [requestEvent]. */
        suspend fun createResponse(
            response: DebitResponse,
            requestEvent: DebitEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DebitEvent {
            val requestorPubKey = requestEvent.pubKey
            val encrypted = signer.nip44Encrypt(OptimizedJsonMapper.toJson(response), requestorPubKey)
            return signer.sign(
                eventTemplate(KIND, encrypted, createdAt) {
                    pTag(requestorPubKey, null)
                    add(ETag.assemble(requestEvent.id, null, null))
                    clinkVersion()
                    alt(ALT)
                },
            )
        }
    }
}
