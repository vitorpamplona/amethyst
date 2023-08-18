package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class LnZapPaymentRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    // Once one of an app user decrypts the payment, all users else can see it.
    @Transient
    private var lnInvoice: String? = null

    fun walletServicePubKey() = tags.firstOrNull() { it.size > 1 && it[0] == "p" }?.get(1)

    fun lnInvoice(privKey: ByteArray, pubkey: ByteArray): String? {
        if (lnInvoice != null) {
            return lnInvoice
        }

        return try {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privKey, pubkey)

            val jsonText = CryptoUtils.decryptNIP04(content, sharedSecret)

            val payInvoiceMethod = mapper.readValue(jsonText, Request::class.java)

            lnInvoice = (payInvoiceMethod as? PayInvoiceMethod)?.params?.invoice

            return lnInvoice
        } catch (e: Exception) {
            Log.w("BookmarkList", "Error decrypting the message ${e.message}")
            null
        }
    }

    companion object {
        const val kind = 23194

        fun create(
            lnInvoice: String,
            walletServicePubkey: String,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): LnZapPaymentRequestEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey)
            val serializedRequest = mapper.writeValueAsString(PayInvoiceMethod.create(lnInvoice))

            val content = CryptoUtils.encryptNIP04(
                serializedRequest,
                privateKey,
                walletServicePubkey.hexToByteArray()
            )

            val tags = mutableListOf<List<String>>()
            tags.add(listOf("p", walletServicePubkey))

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return LnZapPaymentRequestEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}

// REQUEST OBJECTS

abstract class Request(var method: String? = null)

// PayInvoice Call
class PayInvoiceParams(var invoice: String? = null)

class PayInvoiceMethod(var params: PayInvoiceParams? = null) : Request("pay_invoice") {

    companion object {
        fun create(bolt11: String): PayInvoiceMethod {
            return PayInvoiceMethod(PayInvoiceParams(bolt11))
        }
    }
}


class RequestDeserializer : StdDeserializer<Request>(Request::class.java) {
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): Request? {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val method = jsonObject.get("method")?.asText()

        if (method == "pay_invoice") {
            return jp.codec.treeToValue(jsonObject, PayInvoiceMethod::class.java)
        }
        return null
    }
}
