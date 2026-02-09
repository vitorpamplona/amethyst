/*
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
package com.vitorpamplona.amethyst.service.cashu.melt

import android.content.Context
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.cashu.CashuToken
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.asTextOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import kotlin.coroutines.cancellation.CancellationException

class MeltProcessor {
    suspend fun melt(
        token: CashuToken,
        lud16: String,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
    ): MeltResult {
        val baseInvoice =
            LightningAddressResolver().lnAddressInvoice(
                lnAddress = lud16,
                // Make invoice and leave room for fees
                milliSats = token.totalAmount * 1000,
                message = "Calculate Fees for Cashu",
                okHttpClient = okHttpClient,
                onProgress = {},
                context = context,
            )

        val fees =
            feeCalculator(
                mintAddress = token.mint,
                invoice = baseInvoice,
                okHttpClient = okHttpClient,
                context = context,
            )

        val invoice =
            LightningAddressResolver().lnAddressInvoice(
                lnAddress = lud16,
                // Make invoice and leave room for fees
                milliSats = (token.totalAmount - fees) * 1000,
                message = "Redeem Cashu",
                okHttpClient = okHttpClient,
                onProgress = {},
                context = context,
            )

        meltInvoice(token, invoice, okHttpClient, context)

        return MeltResult(
            token = token,
            invoice = invoice,
            fees = fees,
        )
    }

    suspend fun melt(
        token: CashuToken,
        lud16: String,
        okHttpClient: (String) -> OkHttpClient,
        onSuccess: (String, String) -> Unit,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
    }

    suspend fun feeCalculator(
        mintAddress: String,
        invoice: String,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
    ): Int =
        try {
            val url = "$mintAddress/checkfees" // Melt cashu tokens at Mint
            val client = okHttpClient(url)

            val factory = JsonNodeFactory.instance

            val jsonObject = factory.objectNode()
            jsonObject.put("pr", invoice)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    val body = response.body.string()
                    val tree = jacksonObjectMapper().readTree(body)

                    val feeCost = tree?.get("fee")?.asInt()

                    if (feeCost == null) {
                        val msg =
                            tree
                                ?.get("detail")
                                ?.asTextOrNull()
                                ?.split('.')
                                ?.getOrNull(0)
                                ?.ifBlank { null }

                        throw LightningAddressResolver.LightningAddressError(
                            stringRes(context, R.string.cashu_failed_redemption),
                            if (msg != null) {
                                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, msg)
                            } else {
                                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg)
                            },
                        )
                    }

                    feeCost
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw LightningAddressResolver.LightningAddressError(
                stringRes(context, R.string.cashu_failed_redemption),
                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, e.message),
            )
        }

    private suspend fun meltInvoice(
        token: CashuToken,
        invoice: String,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
    ) {
        try {
            val url = token.mint + "/melt" // Melt cashu tokens at Mint
            val client = okHttpClient(url)

            val factory = JsonNodeFactory.instance

            val jsonObject = factory.objectNode()

            jsonObject.replace(
                "proofs",
                factory.arrayNode(token.proofs.size).apply {
                    token.proofs.forEach {
                        addObject().apply {
                            put("amount", it.amount)
                            put("id", it.id)
                            put("secret", it.secret)
                            put("C", it.C)
                        }
                    }
                },
            )

            jsonObject.put("pr", invoice)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    val body = response.body.string()
                    val tree = jacksonObjectMapper().readTree(body)

                    val successful = tree?.get("paid")?.asText() == "true"

                    if (!successful) {
                        val msg =
                            tree
                                ?.get("detail")
                                ?.asTextOrNull()
                                ?.split('.')
                                ?.getOrNull(0)
                                ?.ifBlank { null }

                        throw LightningAddressResolver.LightningAddressError(
                            stringRes(context, R.string.cashu_failed_redemption),
                            if (msg != null) {
                                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, msg)
                            } else {
                                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg)
                            },
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw LightningAddressResolver.LightningAddressError(
                stringRes(context, R.string.cashu_successful_redemption),
                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, e.message),
            )
        }
    }
}
