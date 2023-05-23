package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.vitorpamplona.amethyst.model.HexKey
import nostr.postr.Utils
import java.lang.reflect.Type

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
            val sharedSecret = Utils.getSharedSecret(privKey, pubKey)

            val retVal = Utils.decrypt(content, sharedSecret)

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
                response = gson.fromJson(decrypted, Response::class.java)
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
    @SerializedName("result_type")
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
        @SerializedName(value = "rate_limited", alternate = ["RATE_LIMITED"])
        RATE_LIMITED, // The client is sending commands too fast. It should retry in a few seconds.
        @SerializedName(value = "not_implemented", alternate = ["NOT_IMPLEMENTED"])
        NOT_IMPLEMENTED, // The command is not known or is intentionally not implemented.
        @SerializedName(value = "insufficient_balance", alternate = ["INSUFFICIENT_BALANCE"])
        INSUFFICIENT_BALANCE, // The wallet does not have enough funds to cover a fee reserve or the payment amount.
        @SerializedName(value = "quota_exceeded", alternate = ["QUOTA_EXCEEDED"])
        QUOTA_EXCEEDED, // The wallet has exceeded its spending quota.
        @SerializedName(value = "restricted", alternate = ["RESTRICTED"])
        RESTRICTED, // This public key is not allowed to do this operation.
        @SerializedName(value = "unauthorized", alternate = ["UNAUTHORIZED"])
        UNAUTHORIZED, // This public key has no wallet connected.
        @SerializedName(value = "internal", alternate = ["INTERNAL"])
        INTERNAL, // An internal error.
        @SerializedName(value = "other", alternate = ["OTHER"])
        OTHER // Other error.
    }
}

class ResponseDeserializer :
    JsonDeserializer<Response?> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Response? {
        val jsonObject = json.asJsonObject
        val resultType = jsonObject.get("result_type")?.asString

        if (resultType == "pay_invoice") {
            val result = jsonObject.get("result")?.asJsonObject
            val error = jsonObject.get("error")?.asJsonObject
            if (result != null) {
                return context.deserialize<PayInvoiceSuccessResponse>(jsonObject, PayInvoiceSuccessResponse::class.java)
            }
            if (error != null) {
                return context.deserialize<PayInvoiceErrorResponse>(jsonObject, PayInvoiceErrorResponse::class.java)
            }
        } else {
            // tries to guess
            if (jsonObject.get("result")?.asJsonObject?.get("preimage") != null) {
                return context.deserialize<PayInvoiceSuccessResponse>(jsonObject, PayInvoiceSuccessResponse::class.java)
            }
            if (jsonObject.get("error")?.asJsonObject?.get("code") != null) {
                return context.deserialize<PayInvoiceErrorResponse>(jsonObject, PayInvoiceErrorResponse::class.java)
            }
        }
        return null
    }

    companion object {
    }
}
