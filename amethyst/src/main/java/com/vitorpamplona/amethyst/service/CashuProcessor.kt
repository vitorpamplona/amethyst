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
package com.vitorpamplona.amethyst.service

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

@Immutable
data class CashuToken(
    val token: String,
    val mint: String,
    val totalAmount: Long,
    val proofs: List<Proof>,
)

@Serializable
@Immutable
class Proof(
    val amount: Int,
    val id: String,
    val secret: String,
    val C: String,
)

object CachedCashuProcessor {
    val cashuCache = LruCache<String, GenericLoadable<ImmutableList<CashuToken>>>(20)

    fun cached(token: String): GenericLoadable<ImmutableList<CashuToken>> = cashuCache[token] ?: GenericLoadable.Loading()

    fun parse(token: String): GenericLoadable<ImmutableList<CashuToken>> {
        if (cashuCache[token] !is GenericLoadable.Loaded) {
            checkNotInMainThread()
            val newCachuData = CashuProcessor().parse(token)

            cashuCache.put(token, newCachuData)
        }

        return cashuCache[token]
    }
}

class CashuProcessor {
    @Serializable
    class V3Token(
        val unit: String?,
        val memo: String?,
        val token: List<V3T>?,
    )

    @Serializable
    class V3T(
        val mint: String,
        val proofs: List<Proof>,
    )

    fun parse(cashuToken: String): GenericLoadable<ImmutableList<CashuToken>> {
        checkNotInMainThread()

        if (cashuToken.startsWith("cashuA")) {
            return parseCashuA(cashuToken)
        }

        if (cashuToken.startsWith("cashuB")) {
            return parseCashuB(cashuToken)
        }

        return GenericLoadable.Error("Could not parse this cashu token")
    }

    fun parseCashuA(cashuToken: String): GenericLoadable<ImmutableList<CashuToken>> {
        checkNotInMainThread()

        try {
            val base64token = cashuToken.replace("cashuA", "")
            val cashu = jacksonObjectMapper().readValue<V3Token>(String(Base64.getDecoder().decode(base64token)))

            if (cashu.token == null) {
                return GenericLoadable.Error("No token found")
            }

            val converted =
                cashu.token.map { token ->
                    val proofs = token.proofs
                    val mint = token.mint

                    var totalAmount = 0L
                    for (proof in proofs) {
                        totalAmount += proof.amount
                    }

                    CashuToken(cashuToken, mint, totalAmount, proofs)
                }

            return GenericLoadable.Loaded(converted.toImmutableList())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return GenericLoadable.Error("Could not parse this cashu token")
        }
    }

    @Serializable
    class V4Token(
        // mint
        val m: String,
        // unit
        val u: String,
        // memo
        val d: String? = null,
        val t: Array<V4T>?,
    )

    @Serializable
    class V4T(
        // identifier
        @ByteString
        val i: ByteArray,
        val p: Array<V4Proof>,
    )

    @Serializable
    class V4Proof(
        // amount
        val a: Int,
        // secret
        val s: String,
        // signature
        @ByteString
        val c: ByteArray,
        // no idea what this is
        val d: V4DleqProof? = null,
        // witness
        val w: String? = null,
    )

    @Serializable
    class V4DleqProof(
        @ByteString
        val e: ByteArray,
        @ByteString
        val s: ByteArray,
        @ByteString
        val r: ByteArray,
    )

    @OptIn(ExperimentalSerializationApi::class)
    fun parseCashuB(cashuToken: String): GenericLoadable<ImmutableList<CashuToken>> {
        checkNotInMainThread()

        try {
            val base64token = cashuToken.replace("cashuB", "")

            val parser = Cbor { ignoreUnknownKeys = true }

            val v4Token = parser.decodeFromByteArray<V4Token>(Base64.getUrlDecoder().decode(base64token))

            val v4proofs = v4Token.t ?: return GenericLoadable.Error("No token found")

            val converted =
                v4proofs.map { id ->
                    val proofs =
                        id.p.map {
                            Proof(
                                it.a,
                                id.i.toHexKey(),
                                it.s,
                                it.c.toHexKey(),
                            )
                        }
                    val mint = v4Token.m

                    var totalAmount = 0L
                    for (proof in proofs) {
                        totalAmount += proof.amount
                    }

                    CashuToken(cashuToken, mint, totalAmount, proofs)
                }

            return GenericLoadable.Loaded(converted.toImmutableList())
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
            return GenericLoadable.Error("Could not parse this cashu token")
        }
    }

    suspend fun melt(
        token: CashuToken,
        lud16: String,
        okHttpClient: (String) -> OkHttpClient,
        onSuccess: (String, String) -> Unit,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
        checkNotInMainThread()

        runCatching {
            LightningAddressResolver()
                .lnAddressInvoice(
                    lnaddress = lud16,
                    // Make invoice and leave room for fees
                    milliSats = token.totalAmount * 1000,
                    message = "Calculate Fees for Cashu",
                    okHttpClient = okHttpClient,
                    onSuccess = { baseInvoice ->
                        feeCalculator(
                            token.mint,
                            baseInvoice,
                            okHttpClient = okHttpClient,
                            onSuccess = { fees ->
                                LightningAddressResolver()
                                    .lnAddressInvoice(
                                        lnaddress = lud16,
                                        // Make invoice and leave room for fees
                                        milliSats = (token.totalAmount - fees) * 1000,
                                        message = "Redeem Cashu",
                                        okHttpClient = okHttpClient,
                                        onSuccess = { invoice ->
                                            meltInvoice(token, invoice, fees, okHttpClient, onSuccess, onError, context)
                                        },
                                        onProgress = {},
                                        onError = onError,
                                        context = context,
                                    )
                            },
                            onError = onError,
                            context,
                        )
                    },
                    onProgress = {},
                    onError = onError,
                    context = context,
                )
        }
    }

    fun feeCalculator(
        mintAddress: String,
        invoice: String,
        okHttpClient: (String) -> OkHttpClient,
        onSuccess: (Int) -> Unit,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
        checkNotInMainThread()

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

            client.newCall(request).execute().use {
                val body = it.body.string()
                val tree = jacksonObjectMapper().readTree(body)

                val feeCost = tree?.get("fee")?.asInt()

                if (feeCost != null) {
                    onSuccess(
                        feeCost,
                    )
                } else {
                    val msg =
                        tree
                            ?.get("detail")
                            ?.asText()
                            ?.split('.')
                            ?.getOrNull(0)
                            ?.ifBlank { null }
                    onError(
                        stringRes(context, R.string.cashu_failed_redemption),
                        if (msg != null) {
                            stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, msg)
                        } else {
                            stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg)
                        },
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(
                stringRes(context, R.string.cashu_successful_redemption),
                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, e.message),
            )
        }
    }

    private fun meltInvoice(
        token: CashuToken,
        invoice: String,
        fees: Int,
        okHttpClient: (String) -> OkHttpClient,
        onSuccess: (String, String) -> Unit,
        onError: (String, String) -> Unit,
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

            client.newCall(request).execute().use {
                val body = it.body.string()
                val tree = jacksonObjectMapper().readTree(body)

                val successful = tree?.get("paid")?.asText() == "true"

                if (successful) {
                    onSuccess(
                        stringRes(context, R.string.cashu_successful_redemption),
                        stringRes(
                            context,
                            R.string.cashu_successful_redemption_explainer,
                            token.totalAmount.toString(),
                            fees.toString(),
                        ),
                    )
                } else {
                    val msg =
                        tree
                            ?.get("detail")
                            ?.asText()
                            ?.split('.')
                            ?.getOrNull(0)
                            ?.ifBlank { null }
                    onError(
                        stringRes(context, R.string.cashu_failed_redemption),
                        if (msg != null) {
                            stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, msg)
                        } else {
                            stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg)
                        },
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(
                stringRes(context, R.string.cashu_successful_redemption),
                stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, e.message),
            )
        }
    }
}
