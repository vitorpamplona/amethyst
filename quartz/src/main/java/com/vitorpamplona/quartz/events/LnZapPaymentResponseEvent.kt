package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class LnZapPaymentResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    // Once one of an app user decrypts the payment, all users else can see it.
    @Transient
    private var response: Response? = null

    fun requestAuthor() = tags.firstOrNull() { it.size > 1 && it[0] == "p" }?.get(1)
    fun requestId() = tags.firstOrNull() { it.size > 1 && it[0] == "e" }?.get(1)

    private fun decrypt(privKey: ByteArray, pubKey: ByteArray): String? {
        return try {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privKey, pubKey)

            val retVal = CryptoUtils.decryptNIP04(content, sharedSecret)

            if (retVal.startsWith(PrivateDmEvent.nip18Advertisement)) {
                retVal.substring(16)
            } else {
                retVal
            }
        } catch (e: Exception) {
            Log.w("PrivateDM", "Error decrypting the message ${e.message}")
            null
        }
    }

    fun response(privKey: ByteArray, pubKey: ByteArray): Response? {
        if (response != null) response

        return try {
            if (content.isNotEmpty()) {
                val decrypted = decrypt(privKey, pubKey)
                response = mapper.readValue(decrypted, Response::class.java)
                response
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("LnZapPaymentResponseEvent", "Can't parse content as a payment response: $content", e)
            null
        }
    }

    companion object {
        const val kind = 23195
    }
}

// RESPONSE OBJECTS
abstract class Response(
    @JsonProperty("result_type")
    val resultType: String
)

// PayInvoice Call

class PayInvoiceSuccessResponse(val result: PayInvoiceResultParams? = null) :
    Response("pay_invoice") {
    class PayInvoiceResultParams(val preimage: String)
}

class PayInvoiceErrorResponse(val error: PayInvoiceErrorParams? = null) :
    Response("pay_invoice") {
    class PayInvoiceErrorParams(val code: ErrorType?, val message: String?)

    enum class ErrorType {
        @JsonProperty(value = "RATE_LIMITED")
        RATE_LIMITED, // The client is sending commands too fast. It should retry in a few seconds.
        @JsonProperty(value = "NOT_IMPLEMENTED")
        NOT_IMPLEMENTED, // The command is not known or is intentionally not implemented.
        @JsonProperty(value = "INSUFFICIENT_BALANCE")
        INSUFFICIENT_BALANCE, // The wallet does not have enough funds to cover a fee reserve or the payment amount.
        @JsonProperty(value = "QUOTA_EXCEEDED")
        QUOTA_EXCEEDED, // The wallet has exceeded its spending quota.
        @JsonProperty(value = "RESTRICTED")
        RESTRICTED, // This public key is not allowed to do this operation.
        @JsonProperty(value = "UNAUTHORIZED")
        UNAUTHORIZED, // This public key has no wallet connected.
        @JsonProperty(value = "INTERNAL")
        INTERNAL, // An internal error.
        @JsonProperty(value = "OTHER")
        OTHER // Other error.
    }
}


class ResponseDeserializer : StdDeserializer<Response>(Response::class.java) {
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): Response? {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val resultType = jsonObject.get("result_type")?.asText()

        if (resultType == "pay_invoice") {
            val result = jsonObject.get("result")
            val error = jsonObject.get("error")
            if (result != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
            }
            if (error != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceErrorResponse::class.java)
            }
        } else {
            // tries to guess
            if (jsonObject.get("result")?.get("preimage") != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
            }
            if (jsonObject.get("error")?.get("code") != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceErrorResponse::class.java)
            }
        }
        return null
    }
}