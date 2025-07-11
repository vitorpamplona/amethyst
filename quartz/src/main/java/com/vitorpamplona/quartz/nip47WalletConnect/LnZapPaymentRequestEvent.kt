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

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class LnZapPaymentRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    // Once one of an app user decrypts the payment, all users else can see it.
    @Transient private var lnInvoice: String? = null

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (lnInvoice?.bytesUsedInMemory() ?: 0) // rough calculation

    fun walletServicePubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) walletServicePubKey() ?: pubKey else pubKey

    fun lnInvoice(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        lnInvoice?.let {
            onReady(it)
            return
        }

        try {
            signer.decrypt(content, talkingWith(signer.pubKey)) { jsonText ->
                val payInvoiceMethod = JsonMapper.mapper.readValue(jsonText, Request::class.java)

                lnInvoice = (payInvoiceMethod as? PayInvoiceMethod)?.params?.invoice

                lnInvoice?.let { onReady(it) }
            }
        } catch (e: Exception) {
            Log.w("BookmarkList", "Error decrypting the message ${e.message}")
        }
    }

    companion object {
        const val KIND = 23194
        const val ALT = "Zap payment request"

        fun create(
            lnInvoice: String,
            walletServicePubkey: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (LnZapPaymentRequestEvent) -> Unit,
        ) {
            val serializedRequest = JsonMapper.mapper.writeValueAsString(PayInvoiceMethod.create(lnInvoice))

            val tags = arrayOf(arrayOf("p", walletServicePubkey), AltTag.assemble(ALT))

            signer.nip04Encrypt(
                serializedRequest,
                walletServicePubkey,
            ) { content ->
                signer.sign(createdAt, KIND, tags, content, onReady)
            }
        }
    }
}
