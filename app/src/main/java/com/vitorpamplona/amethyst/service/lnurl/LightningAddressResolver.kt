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
package com.vitorpamplona.amethyst.service.lnurl

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.HttpClientManager
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.encoders.Lud06
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class LightningAddressResolver() {
    val client = HttpClientManager.getHttpClient()

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

    private fun fetchLightningAddressJson(
        lnaddress: String,
        onSuccess: (String) -> Unit,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
        checkNotInMainThread()

        val url = assembleUrl(lnaddress)

        if (url == null) {
            onError(
                context.getString(R.string.error_unable_to_fetch_invoice),
                context.getString(
                    R.string.could_not_assemble_lnurl_from_lightning_address_check_the_user_s_setup,
                    lnaddress,
                ),
            )
            return
        }

        try {
            val request: Request =
                Request.Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .url(url)
                    .build()

            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    onSuccess(it.body.string())
                } else {
                    onError(
                        context.getString(R.string.error_unable_to_fetch_invoice),
                        context.getString(
                            R.string
                                .the_receiver_s_lightning_service_at_is_not_available_it_was_calculated_from_the_lightning_address_error_check_if_the_server_is_up_and_if_the_lightning_address_is_correct,
                            url,
                            lnaddress,
                            it.code.toString(),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            onError(
                context.getString(R.string.error_unable_to_fetch_invoice),
                context.getString(
                    R.string
                        .could_not_resolve_check_if_you_are_connected_if_the_server_is_up_and_if_the_lightning_address_is_correct_exception,
                    url,
                    lnaddress,
                    e.suppressedExceptions.getOrNull(0)?.message ?: e.cause?.message ?: e.message,
                ),
            )
        }
    }

    fun fetchLightningInvoice(
        lnCallback: String,
        milliSats: Long,
        message: String,
        nostrRequest: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
        checkNotInMainThread()

        val encodedMessage = URLEncoder.encode(message, "utf-8")

        val urlBinder = if (lnCallback.contains("?")) "&" else "?"
        var url = "$lnCallback${urlBinder}amount=$milliSats&comment=$encodedMessage"

        if (nostrRequest != null) {
            val encodedNostrRequest = URLEncoder.encode(nostrRequest, "utf-8")
            url += "&nostr=$encodedNostrRequest"
        }

        val request: Request =
            Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url)
                .build()

        client.newCall(request).execute().use {
            if (it.isSuccessful) {
                onSuccess(it.body.string())
            } else {
                onError(
                    context.getString(R.string.error_unable_to_fetch_invoice),
                    context.getString(R.string.could_not_fetch_invoice_from, lnCallback),
                )
            }
        }
    }

    fun lnAddressInvoice(
        lnaddress: String,
        milliSats: Long,
        message: String,
        nostrRequest: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
    ) {
        val mapper = jacksonObjectMapper()

        fetchLightningAddressJson(
            lnaddress,
            onSuccess = { lnAddressJson ->
                onProgress(0.4f)

                val lnurlp =
                    try {
                        mapper.readTree(lnAddressJson)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        onError(
                            context.getString(R.string.error_unable_to_fetch_invoice),
                            context.getString(
                                R.string.error_parsing_json_from_lightning_address_check_the_user_s_lightning_setup_with_user,
                                lnaddress,
                            ),
                        )
                        null
                    }

                val callback = lnurlp?.get("callback")?.asText()

                if (callback == null) {
                    onError(
                        context.getString(R.string.error_unable_to_fetch_invoice),
                        context.getString(
                            R.string.callback_url_not_found_in_the_user_s_lightning_address_server_configuration_with_user,
                            lnaddress,
                        ),
                    )
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

                            val lnInvoice =
                                try {
                                    mapper.readTree(it)
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    onError(
                                        context.getString(R.string.error_unable_to_fetch_invoice),
                                        context.getString(
                                            R.string
                                                .error_parsing_json_from_lightning_address_s_invoice_fetch_check_the_user_s_lightning_setup_with_user,
                                            lnaddress,
                                        ),
                                    )
                                    null
                                }

                            lnInvoice
                                ?.get("pr")
                                ?.asText()
                                ?.ifBlank { null }
                                ?.let { pr ->
                                    // Forces LN Invoice amount to be the requested amount.
                                    val expectedAmountInSats =
                                        BigDecimal(milliSats).divide(BigDecimal(1000), RoundingMode.HALF_UP).toLong()
                                    val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)
                                    if (invoiceAmount.toLong() == expectedAmountInSats) {
                                        onProgress(0.7f)
                                        onSuccess(pr)
                                    } else {
                                        onProgress(0.0f)
                                        onError(
                                            context.getString(R.string.error_unable_to_fetch_invoice),
                                            context.getString(
                                                R.string.incorrect_invoice_amount_sats_from_it_should_have_been,
                                                invoiceAmount.toLong().toString(),
                                                lnaddress,
                                                expectedAmountInSats.toString(),
                                            ),
                                        )
                                    }
                                }
                                ?: lnInvoice
                                    ?.get("reason")
                                    ?.asText()
                                    ?.ifBlank { null }
                                    ?.let { reason ->
                                        onProgress(0.0f)
                                        onError(
                                            context.getString(R.string.error_unable_to_fetch_invoice),
                                            context.getString(
                                                R.string
                                                    .unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error_with_user,
                                                lnaddress,
                                                reason,
                                            ),
                                        )
                                    }
                                ?: run {
                                    onProgress(0.0f)
                                    onError(
                                        context.getString(R.string.error_unable_to_fetch_invoice),
                                        context.getString(
                                            R.string
                                                .unable_to_create_a_lightning_invoice_before_sending_the_zap_element_pr_not_found_in_the_resulting_json_with_user,
                                            lnaddress,
                                        ),
                                    )
                                }
                        },
                        onError = onError,
                        context,
                    )
                }
            },
            onError = onError,
            context,
        )
    }
}
