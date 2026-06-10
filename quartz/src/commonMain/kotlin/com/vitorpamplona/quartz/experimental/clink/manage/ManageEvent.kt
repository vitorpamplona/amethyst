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
package com.vitorpamplona.quartz.experimental.clink.manage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.clink.Clink
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
 * CLINK Manage event (kind 21003). The same kind carries both the request (app →
 * wallet server) and the response (server → app); a response is distinguished by an `e`
 * tag referencing the request. Content is NIP-44 encrypted between the two parties.
 *
 * See https://github.com/shocknet/clink/blob/master/specs/clink-manage.md
 */
@Immutable
class ManageEvent(
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

    fun version() = tags.firstOrNull { it.size > 1 && it[0] == Clink.VERSION_TAG_NAME }?.get(1)

    private fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) recipientPubKey() ?: pubKey else pubKey

    fun canDecrypt(signer: NostrSigner) = pubKey == signer.pubKey || recipientPubKey() == signer.pubKey

    suspend fun decryptContent(signer: NostrSigner): String {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()
        return signer.nip44Decrypt(content, talkingWith(signer.pubKey))
    }

    suspend fun decryptRequest(signer: NostrSigner): ManageRequest = OptimizedJsonMapper.fromJsonTo<ManageRequest>(decryptContent(signer))

    suspend fun decryptResponse(signer: NostrSigner): ManageResponse = OptimizedJsonMapper.fromJsonTo<ManageResponse>(decryptContent(signer))

    companion object {
        const val KIND = 21003
        const val ALT = "CLINK manage"

        /** Builds a request event (app side) addressed to the wallet server. */
        suspend fun createRequest(
            request: ManageRequest,
            serverPubKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ManageEvent {
            val encrypted = signer.nip44Encrypt(OptimizedJsonMapper.toJson(request), serverPubKey)
            return signer.sign(
                eventTemplate(KIND, encrypted, createdAt) {
                    pTag(serverPubKey, null)
                    add(Clink.versionTag())
                    alt(ALT)
                },
            )
        }

        /** Builds a response event (server side) referencing the original [requestEvent]. */
        suspend fun createResponse(
            response: ManageResponse,
            requestEvent: ManageEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ManageEvent {
            val appPubKey = requestEvent.pubKey
            val encrypted = signer.nip44Encrypt(OptimizedJsonMapper.toJson(response), appPubKey)
            return signer.sign(
                eventTemplate(KIND, encrypted, createdAt) {
                    pTag(appPubKey, null)
                    add(ETag.assemble(requestEvent.id, null, null))
                    add(Clink.versionTag())
                    alt(ALT)
                },
            )
        }
    }
}
