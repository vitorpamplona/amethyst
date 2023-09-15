package com.vitorpamplona.amethyst.service.lnurl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.encoders.Lud06
import com.vitorpamplona.quartz.encoders.toLnUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.math.BigDecimal
import java.net.URLEncoder

class LightningAddressResolver() {
    val client = HttpClient.getHttpClient()

    fun assembleUrl(lnaddress: String): String? {
        val parts = lnaddress.split("@")

        if (parts.size == 2) {
            return "https://${parts[1]}/.well-known/lnurlp/${parts[0]}"
        }

        if (lnaddress.lowercase().startsWith("lnurl")) {
            return Lud06().toLnUrlp(lnaddress)
        }

        return null
    }

    private suspend fun fetchLightningAddressJson(lnaddress: String, onSuccess: suspend (String) -> Unit, onError: (String) -> Unit) = withContext(Dispatchers.IO) {
        checkNotInMainThread()

        val url = assembleUrl(lnaddress)

        if (url == null) {
            onError("Could not assemble LNUrl from Lightning Address \"${lnaddress}\". Check the user's setup")
            return@withContext
        }

        try {
            val request: Request = Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url)
                .build()

            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    onSuccess(it.body.string())
                } else {
                    onError("The receiver's lightning service at $url is not available. It was calculated from the lightning address \"${lnaddress}\". Error: ${it.code}. Check if the server is up and if the lightning address is correct")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Could not resolve $url. Check if the server is up and if the lightning address $lnaddress is correct")
        }
    }

    suspend fun fetchLightningInvoice(lnCallback: String, milliSats: Long, message: String, nostrRequest: String? = null, onSuccess: (String) -> Unit, onError: (String) -> Unit) = withContext(Dispatchers.IO) {
        val encodedMessage = URLEncoder.encode(message, "utf-8")

        val urlBinder = if (lnCallback.contains("?")) "&" else "?"
        var url = "$lnCallback${urlBinder}amount=$milliSats&comment=$encodedMessage"

        if (nostrRequest != null) {
            val encodedNostrRequest = URLEncoder.encode(nostrRequest, "utf-8")
            url += "&nostr=$encodedNostrRequest"
        }

        val request: Request = Request.Builder()
            .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        onSuccess(response.body.string())
                    } else {
                        onError("Could not fetch invoice from $lnCallback")
                    }
                }
            }

            override fun onFailure(call: Call, e: java.io.IOException) {
                onError("Could not fetch an invoice from $lnCallback. Message ${e.message}")
                e.printStackTrace()
            }
        })
    }

    suspend fun lnAddressToLnUrl(lnaddress: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        fetchLightningAddressJson(
            lnaddress,
            onSuccess = {
                onSuccess(it.toByteArray().toLnUrl())
            },
            onError = onError
        )
    }

    suspend fun lnAddressInvoice(
        lnaddress: String,
        milliSats: Long,
        message: String,
        nostrRequest: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onProgress: (percent: Float) -> Unit
    ) {
        val mapper = jacksonObjectMapper()

        fetchLightningAddressJson(
            lnaddress,
            onSuccess = { lnAddressJson ->
                onProgress(0.4f)

                val lnurlp = try {
                    mapper.readTree(lnAddressJson)
                } catch (t: Throwable) {
                    onError("Error Parsing JSON from Lightning Address. Check the user's lightning setup")
                    null
                }

                val callback = lnurlp?.get("callback")?.asText()

                if (callback == null) {
                    onError("Callback URL not found in the User's lightning address server configuration")
                }

                val allowsNostr = lnurlp?.get("allowsNostr")?.asBoolean() ?: false

                callback?.let { cb ->
                    fetchLightningInvoice(
                        cb,
                        milliSats,
                        message,
                        if (allowsNostr) nostrRequest else null,
                        onSuccess = {
                            onProgress(0.6f)

                            val lnInvoice = try {
                                mapper.readTree(it)
                            } catch (t: Throwable) {
                                onError("Error Parsing JSON from Lightning Address's invoice fetch. Check the user's lightning setup")
                                null
                            }

                            lnInvoice?.get("pr")?.asText()?.ifBlank { null }?.let { pr ->
                                // Forces LN Invoice amount to be the requested amount.
                                val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)
                                if (invoiceAmount.multiply(BigDecimal(1000)).toLong() == BigDecimal(milliSats).toLong()) {
                                    onProgress(0.7f)
                                    onSuccess(pr)
                                } else {
                                    onProgress(0.0f)
                                    onError("Incorrect invoice amount (${invoiceAmount.toLong()} sats) from server")
                                }
                            } ?: lnInvoice?.get("reason")?.asText()?.ifBlank { null }?.let { reason ->
                                onProgress(0.0f)
                                onError("Unable to create a lightning invoice before sending the zap. The receiver's lightning wallet sent the following error: $reason")
                            } ?: run {
                                onProgress(0.0f)
                                onError("nable to create a lightning invoice before sending the zap. Element pr not found in the resulting JSON.")
                            }
                        },
                        onError = onError
                    )
                }
            },
            onError = onError
        )
    }
}
