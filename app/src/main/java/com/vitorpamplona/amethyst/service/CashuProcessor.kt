package com.vitorpamplona.amethyst.service

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

@Immutable
data class CashuToken(
    val token: String,
    val mint: String,
    val totalAmount: Long,
    val fees: Int,
    val redeemInvoiceAmount: Long,
    val proofs: JsonArray
)

class CashuProcessor {
    fun parse(cashuToken: String): GenericLoadable<CashuToken> {
        checkNotInMainThread()

        try {
            val base64token = cashuToken.replace("cashuA", "")
            val cashu = JsonParser.parseString(String(Base64.getDecoder().decode(base64token)))
            val token = cashu.asJsonObject.get("token").asJsonArray[0].asJsonObject
            val proofs = token["proofs"].asJsonArray
            val mint = token["mint"].asString

            var totalAmount = 0L
            for (proof in proofs) {
                totalAmount += proof.asJsonObject["amount"].asLong
            }
            val fees = Math.max(((totalAmount * 0.02).toInt()), 2)
            val redeemInvoiceAmount = totalAmount - fees

            return GenericLoadable.Loaded(CashuToken(cashuToken, mint, totalAmount, fees, redeemInvoiceAmount, proofs))
        } catch (e: Exception) {
            return GenericLoadable.Error<CashuToken>("Could not parse this cashu token")
        }
    }

    fun melt(token: CashuToken, lud16: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        checkNotInMainThread()

        runCatching {
            LightningAddressResolver().lnAddressInvoice(
                lnaddress = lud16,
                milliSats = token.redeemInvoiceAmount * 1000, // Make invoice and leave room for fees
                message = "Redeem Cashu",
                onSuccess = { invoice ->
                    meltInvoice(token, invoice, onSuccess, onError)
                },
                onProgress = {
                },
                onError = onError
            )
        }
    }

    private fun meltInvoice(token: CashuToken, invoice: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        try {
            val client = HttpClient.getHttpClient()
            val url = token.mint + "/melt" // Melt cashu tokens at Mint

            val jsonObject = JsonObject()
            jsonObject.add("proofs", token.proofs)
            jsonObject.addProperty("pr", invoice)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use {
                val body = it.body.string()
                val tree = jacksonObjectMapper().readTree(body)

                val successful = tree?.get("paid")?.asText() == "true"

                if (successful) {
                    onSuccess("Redeemed ${token.totalAmount} Sats" + " (Fees: ${token.fees} Sats)")
                } else {
                    onError(tree?.get("detail")?.asText()?.split('.')?.getOrNull(0) ?: "Cashu: Tokens already spent.")
                }
            }
        } catch (e: Exception) {
            onError("Token melt failure: " + e.message)
        }
    }
}
