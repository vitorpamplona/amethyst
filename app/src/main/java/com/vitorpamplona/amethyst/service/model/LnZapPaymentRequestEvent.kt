package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.lang.reflect.Type
import java.util.Date

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
            val sharedSecret = Utils.getSharedSecret(privKey, pubkey)

            val jsonText = Utils.decrypt(content, sharedSecret)

            val payInvoiceMethod = gson.fromJson(jsonText, Request::class.java)

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
            createdAt: Long = Date().time / 1000
        ): LnZapPaymentRequestEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)
            val serializedRequest = gson.toJson(PayInvoiceMethod.create(lnInvoice))

            val content = Utils.encrypt(
                serializedRequest,
                privateKey,
                walletServicePubkey.hexToByteArray()
            )

            val tags = mutableListOf<List<String>>()
            tags.add(listOf("p", walletServicePubkey))

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
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

class RequestDeserializer :
    JsonDeserializer<Request?> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Request? {
        val jsonObject = json.asJsonObject
        val method = jsonObject.get("method")?.asString

        if (method == "pay_invoice") {
            return context.deserialize<PayInvoiceMethod>(jsonObject, PayInvoiceMethod::class.java)
        }
        return null
    }

    companion object {
    }
}
