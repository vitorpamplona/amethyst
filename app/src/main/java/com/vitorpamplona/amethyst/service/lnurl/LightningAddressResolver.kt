package com.vitorpamplona.amethyst.service.lnurl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.Bech32
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
            return try {
                String(Bech32.decodeBytes(lnaddress, false).second)
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    fun fetchLightningAddressJson(lnaddress: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            fetchLightningAddressJsonSuspend(lnaddress, onSuccess, onError)
        }
    }

    private suspend fun fetchLightningAddressJsonSuspend(lnaddress: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        checkNotInMainThread()

        val url = assembleUrl(lnaddress)

        if (url == null) {
            onError("Could not assemble LNUrl from Lightning Address \"${lnaddress}\". Check the user's setup")
            return
        }

        withContext(Dispatchers.IO) {
            val request: Request = Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (it.isSuccessful) {
                            onSuccess(it.body.string())
                        } else {
                            onError("Could not resolve $lnaddress. Error: ${it.code}. Check if the server up and if the lightning address $lnaddress is correct")
                        }
                    }
                }

                override fun onFailure(call: Call, e: java.io.IOException) {
                    onError("Could not resolve $url. Check if the server up and if the lightning address $lnaddress is correct")
                    e.printStackTrace()
                }
            })
        }
    }

    fun fetchLightningInvoice(lnCallback: String, milliSats: Long, message: String, nostrRequest: String? = null, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            fetchLightningInvoiceSuspend(lnCallback, milliSats, message, nostrRequest, onSuccess, onError)
        }
    }

    private suspend fun fetchLightningInvoiceSuspend(lnCallback: String, milliSats: Long, message: String, nostrRequest: String? = null, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        withContext(Dispatchers.IO) {
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
    }

    fun lnAddressToLnUrl(lnaddress: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        fetchLightningAddressJson(
            lnaddress,
            onSuccess = {
                onSuccess(Bech32.encodeBytes("lnurl", it.toByteArray(), Bech32.Encoding.Bech32))
            },
            onError = onError
        )
    }

    fun lnAddressInvoice(lnaddress: String, milliSats: Long, message: String, nostrRequest: String? = null, onSuccess: (String) -> Unit, onError: (String) -> Unit, onProgress: (percent: Float) -> Unit) {
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

                            lnInvoice?.get("pr")?.asText()?.let { pr ->
                                // Forces LN Invoice amount to be the requested amount.
                                val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)
                                if (invoiceAmount.multiply(BigDecimal(1000)).toLong() == BigDecimal(milliSats).toLong()) {
                                    onProgress(0.7f)
                                    onSuccess(pr)
                                } else {
                                    onError("Incorrect invoice amount (${invoiceAmount.toLong()} sats) from server")
                                }
                            } ?: onError("Invoice Not Created (element pr not found in the resulting JSON)")
                        },
                        onError = onError
                    )
                }
            },
            onError = onError
        )
    }
}
