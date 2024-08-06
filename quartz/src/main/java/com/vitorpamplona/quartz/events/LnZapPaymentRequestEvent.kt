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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
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
            signer.nip04Decrypt(content, talkingWith(signer.pubKey)) { jsonText ->
                val payInvoiceMethod = mapper.readValue(jsonText, Request::class.java)

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
            val serializedRequest = mapper.writeValueAsString(PayInvoiceMethod.create(lnInvoice))

            val tags = arrayOf(arrayOf("p", walletServicePubkey), arrayOf("alt", ALT))

            signer.nip04Encrypt(
                serializedRequest,
                walletServicePubkey,
            ) { content ->
                signer.sign(createdAt, KIND, tags, content, onReady)
            }
        }
    }
}

// REQUEST OBJECTS

abstract class Request(
    var method: String? = null,
)

// PayInvoice Call
class PayInvoiceParams(
    var invoice: String? = null,
)

class PayInvoiceMethod(
    var params: PayInvoiceParams? = null,
) : Request("pay_invoice") {
    companion object {
        fun create(bolt11: String): PayInvoiceMethod = PayInvoiceMethod(PayInvoiceParams(bolt11))
    }
}

class RequestDeserializer : StdDeserializer<Request>(Request::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Request? {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val method = jsonObject.get("method")?.asText()

        if (method == "pay_invoice") {
            return jp.codec.treeToValue(jsonObject, PayInvoiceMethod::class.java)
        }
        return null
    }
}
