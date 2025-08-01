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
package com.vitorpamplona.amethyst.service.lnurl

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.lightning.Lud06
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class LightningAddressResolver {
    fun assembleUrl(lnAddress: String): String? {
        val parts = lnAddress.split("@")

        if (parts.size == 2) {
            return "https://${parts[1]}/.well-known/lnurlp/${parts[0]}"
        }

        if (lnAddress.lowercase().startsWith("lnurl")) {
            return Lud06().toLnUrlp(lnAddress)
        }

        return null
    }

    class LightningAddressError(
        val title: String,
        val msg: String,
    ) : Exception(msg)

    private suspend fun fetchLightningAddressJson(
        lnAddress: String,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
    ): String {
        val url = assembleUrl(lnAddress)

        if (url == null) {
            throw LightningAddressError(
                stringRes(context, R.string.error_unable_to_fetch_invoice),
                stringRes(context, R.string.could_not_assemble_lnurl_from_lightning_address_check_the_user_s_setup, lnAddress),
            )
        }

        val client = okHttpClient(url)

        return try {
            val request: Request =
                Request
                    .Builder()
                    .url(url)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    if (response.isSuccessful) {
                        response.body.string()
                    } else {
                        throw LightningAddressError(
                            stringRes(context, R.string.error_unable_to_fetch_invoice),
                            stringRes(
                                context,
                                R.string
                                    .the_receiver_s_lightning_service_at_is_not_available_it_was_calculated_from_the_lightning_address_error_check_if_the_server_is_up_and_if_the_lightning_address_is_correct,
                                url,
                                lnAddress,
                                errorMessage(response, context),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw LightningAddressError(
                stringRes(context, R.string.error_unable_to_fetch_invoice),
                stringRes(
                    context,
                    R.string
                        .could_not_resolve_check_if_you_are_connected_if_the_server_is_up_and_if_the_lightning_address_is_correct_exception,
                    url,
                    lnAddress,
                    e.suppressedExceptions.getOrNull(0)?.message ?: e.cause?.message ?: e.message,
                ),
            )
        }
    }

    suspend fun fetchLightningInvoice(
        lnCallback: String,
        milliSats: Long,
        message: String,
        nostrRequest: LnZapRequestEvent? = null,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
    ): String {
        val encodedMessage = URLEncoder.encode(message, "utf-8")

        val urlBinder = if (lnCallback.contains("?")) "&" else "?"
        var url = "$lnCallback${urlBinder}amount=$milliSats&comment=$encodedMessage"

        if (nostrRequest != null) {
            val encodedNostrRequest = URLEncoder.encode(nostrRequest.toJson(), "utf-8")
            url += "&nostr=$encodedNostrRequest"
        }

        val client = okHttpClient(url)

        val request: Request =
            Request
                .Builder()
                .url(url)
                .build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    response.body.string()
                } else {
                    throw LightningAddressError(
                        stringRes(context, R.string.error_unable_to_fetch_invoice),
                        stringRes(context, R.string.could_not_fetch_invoice_from_details, lnCallback, errorMessage(response, context)),
                    )
                }
            }
        }
    }

    fun errorMessage(
        response: Response,
        context: Context,
    ): String {
        val body = response.body.string()

        val errorMessage =
            runCatching {
                jacksonObjectMapper().readTree(body)
            }.getOrNull()?.let { tree ->
                val errorNode = tree.get("error")
                val messageNode = tree.get("message")
                val statusNode = tree.get("status")

                if (tree.get("error").isBoolean && messageNode != null) {
                    if (errorNode.asBoolean()) {
                        return messageNode.asText()
                    }
                }

                val status = statusNode?.asText()
                val message = messageNode?.asText()

                if (status == "error" && message != null) {
                    message
                } else {
                    tree.get("error")?.get("message")?.asText()
                }
            }

        if (errorMessage == null) {
            Log.d("LightningAddressResolver", "Error parsing LNResponse: $body")
        }

        return errorMessage
            ?: HttpStatusMessages.resourceIdFor(response.code)?.let { stringRes(context, it) }
            ?: response.message.ifBlank { null }
            ?: response.code.toString()
    }

    suspend fun lnAddressInvoice(
        lnAddress: String,
        milliSats: Long,
        message: String,
        nostrRequest: LnZapRequestEvent? = null,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percent: Float) -> Unit,
        context: Context,
    ): String {
        val mapper = jacksonObjectMapper()

        val lnAddressJson =
            fetchLightningAddressJson(
                lnAddress,
                okHttpClient,
                context,
            )

        onProgress(0.4f)

        val lnurlp =
            try {
                mapper.readTree(lnAddressJson)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw LightningAddressError(
                    stringRes(context, R.string.error_unable_to_fetch_invoice),
                    stringRes(
                        context,
                        R.string.error_parsing_json_from_lightning_address_check_the_user_s_lightning_setup_with_user,
                        lnAddress,
                    ),
                )
            }

        val callbackUrl = lnurlp?.get("callback")?.asText()?.ifBlank { null }

        if (callbackUrl == null) {
            throw LightningAddressError(
                stringRes(context, R.string.error_unable_to_fetch_invoice),
                stringRes(
                    context,
                    R.string.callback_url_not_found_in_the_user_s_lightning_address_server_configuration_with_user,
                    lnAddress,
                ),
            )
        }

        val allowsNostr = lnurlp.get("allowsNostr")?.asBoolean() ?: false

        val invoice =
            fetchLightningInvoice(
                lnCallback = callbackUrl,
                milliSats = milliSats,
                message = message,
                nostrRequest = if (allowsNostr) nostrRequest else null,
                okHttpClient = okHttpClient,
                context = context,
            )

        onProgress(0.6f)

        val lnInvoice =
            try {
                mapper.readTree(invoice)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw LightningAddressError(
                    stringRes(context, R.string.error_unable_to_fetch_invoice),
                    stringRes(
                        context,
                        R.string
                            .error_parsing_json_from_lightning_address_s_invoice_fetch_check_the_user_s_lightning_setup_with_user,
                        lnAddress,
                    ),
                )
            }

        val pr = lnInvoice?.get("pr")?.asText()?.ifBlank { null }

        if (pr == null) {
            onProgress(0.0f)
            val reason = lnInvoice?.get("reason")?.asText()?.ifBlank { null }

            if (reason != null) {
                throw LightningAddressError(
                    stringRes(context, R.string.error_unable_to_fetch_invoice),
                    stringRes(
                        context,
                        R.string
                            .unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error_with_user,
                        lnAddress,
                        reason,
                    ),
                )
            } else {
                throw LightningAddressError(
                    stringRes(context, R.string.error_unable_to_fetch_invoice),
                    stringRes(
                        context,
                        R.string
                            .unable_to_create_a_lightning_invoice_before_sending_the_zap_element_pr_not_found_in_the_resulting_json_with_user,
                        lnAddress,
                    ),
                )
            }
        }

        // Forces LN Invoice amount to be the requested amount.
        val expectedAmountInSats =
            BigDecimal(milliSats).divide(BigDecimal(1000), RoundingMode.HALF_UP).toLong()

        val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)

        if (invoiceAmount.toLong() != expectedAmountInSats) {
            onProgress(0.0f)
            throw LightningAddressError(
                stringRes(context, R.string.error_unable_to_fetch_invoice),
                stringRes(
                    context,
                    R.string.incorrect_invoice_amount_sats_from_it_should_have_been,
                    invoiceAmount.toLong().toString(),
                    lnAddress,
                    expectedAmountInSats.toString(),
                ),
            )
        }

        onProgress(0.7f)

        return pr
    }
}
