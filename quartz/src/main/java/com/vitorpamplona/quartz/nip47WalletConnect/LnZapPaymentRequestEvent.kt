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
package com.vitorpamplona.quartz.nip47WalletConnect

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class LnZapPaymentRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun walletServicePubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) walletServicePubKey() ?: pubKey else pubKey

    fun canDecrypt(signer: NostrSigner) = pubKey == signer.pubKey || walletServicePubKey() == signer.pubKey

    suspend fun decryptRequest(signer: NostrSigner): Request {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()
        val jsonText = signer.decrypt(content, talkingWith(signer.pubKey))
        return JsonMapper.mapper.readValue(jsonText, Request::class.java)
    }

    companion object {
        const val KIND = 23194
        const val ALT = "Zap payment request"

        suspend fun create(
            lnInvoice: String,
            walletServicePubkey: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): LnZapPaymentRequestEvent {
            val serializedRequest = JsonMapper.mapper.writeValueAsString(PayInvoiceMethod.create(lnInvoice))

            val tags = arrayOf(arrayOf("p", walletServicePubkey), AltTag.assemble(ALT))

            val encrypted =
                signer.nip04Encrypt(
                    serializedRequest,
                    walletServicePubkey,
                )

            return signer.sign(createdAt, KIND, tags, encrypted)
        }
    }
}
