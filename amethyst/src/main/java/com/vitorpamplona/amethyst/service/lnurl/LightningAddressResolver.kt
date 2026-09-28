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
package com.vitorpamplona.amethyst.service.lnurl

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.callback_url_not_found_in_the_user_s_lightning_address_server_configuration_with_user
import com.vitorpamplona.amethyst.commons.resources.could_not_assemble_lnurl_from_lightning_address_check_the_user_s_setup
import com.vitorpamplona.amethyst.commons.resources.could_not_fetch_invoice_from_details
import com.vitorpamplona.amethyst.commons.resources.could_not_resolve_check_if_you_are_connected_if_the_server_is_up_and_if_the_lightning_address_is_correct_exception
import com.vitorpamplona.amethyst.commons.resources.error_parsing_json_from_lightning_address_check_the_user_s_lightning_setup_with_user
import com.vitorpamplona.amethyst.commons.resources.error_parsing_json_from_lightning_address_s_invoice_fetch_check_the_user_s_lightning_setup_with_user
import com.vitorpamplona.amethyst.commons.resources.error_unable_to_fetch_invoice
import com.vitorpamplona.amethyst.commons.resources.incorrect_invoice_amount_sats_from_it_should_have_been
import com.vitorpamplona.amethyst.commons.resources.the_receiver_s_lightning_service_at_is_not_available_it_was_calculated_from_the_lightning_address_error_check_if_the_server_is_up_and_if_the_lightning_address_is_correct
import com.vitorpamplona.amethyst.commons.resources.unable_to_create_a_lightning_invoice_before_sending_the_zap_element_pr_not_found_in_the_resulting_json_with_user
import com.vitorpamplona.amethyst.commons.resources.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error_with_user
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpResponse
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.lightning.Lud06
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.UrlEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException

/** LNURL-pay (LUD-06/16) for zaps and payments, over whatever [transport] the platform supplies. */
class LightningAddressResolver(
    private val transport: LnurlHttpTransport,
) {
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

    private suspend fun fetchLightningAddressJson(lnAddress: String): String {
        val url =
            assembleUrl(lnAddress) ?: throw LightningAddressError(
                loadStringRes(Res.string.error_unable_to_fetch_invoice),
                loadStringRes(Res.string.could_not_assemble_lnurl_from_lightning_address_check_the_user_s_setup, lnAddress),
            )

        return try {
            val response = transport.get(url)
            if (response.isSuccessful) {
                response.body
            } else {
                throw LightningAddressError(
                    loadStringRes(Res.string.error_unable_to_fetch_invoice),
                    loadStringRes(
                        Res.string.the_receiver_s_lightning_service_at_is_not_available_it_was_calculated_from_the_lightning_address_error_check_if_the_server_is_up_and_if_the_lightning_address_is_correct,
                        url,
                        lnAddress,
                        errorMessage(response),
                    ),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw LightningAddressError(
                loadStringRes(Res.string.error_unable_to_fetch_invoice),
                loadStringRes(
                    Res.string.could_not_resolve_check_if_you_are_connected_if_the_server_is_up_and_if_the_lightning_address_is_correct_exception,
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
        nostrRequest: ZapRequestEvent? = null,
    ): String {
        val encodedMessage = UrlEncoder.encode(message)

        val urlBinder = if (lnCallback.contains("?")) "&" else "?"
        var url = "$lnCallback${urlBinder}amount=$milliSats&comment=$encodedMessage"

        if (nostrRequest != null) {
            val encodedNostrRequest = UrlEncoder.encode(nostrRequest.toJson())
            url += "&nostr=$encodedNostrRequest"
        }

        val response = transport.get(url)
        return if (response.isSuccessful) {
            response.body
        } else {
            throw LightningAddressError(
                loadStringRes(Res.string.error_unable_to_fetch_invoice),
                loadStringRes(Res.string.could_not_fetch_invoice_from_details, lnCallback, errorMessage(response)),
            )
        }
    }

    suspend fun errorMessage(response: LnurlHttpResponse): String {
        val body = response.body

        val errorMessage =
            (parseJson(body) as? JsonObject)?.let { tree ->
                val errorNode = tree["error"]
                val messageNode = tree["message"]
                val statusNode = tree["status"]

                if (errorNode != null &&
                    errorNode.isBoolean() &&
                    messageNode != null &&
                    errorNode.asBoolean()
                ) {
                    messageNode.asText()?.let { return it }
                }

                val status = statusNode?.asText()
                val message = messageNode?.asText()

                if (status == "error" && message != null) {
                    message
                } else {
                    (errorNode as? JsonObject)?.get("message")?.asText()
                }
            }

        if (errorMessage == null) {
            Log.d("LightningAddressResolver") { "Error parsing LNResponse: $body" }
        }

        return errorMessage
            ?: HttpStatusMessages.resourceIdFor(response.status)?.let { loadStringRes(it) }
            ?: response.reason.ifBlank { null }
            ?: response.status.toString()
    }

    /**
     * @param onZapRequestSent receives the zap request that was ACTUALLY sent to the
     *   callback, or null when it was not. A provider that does not advertise
     *   `allowsNostr` never sees [nostrRequest], and its invoice therefore commits to
     *   nothing about it — so a caller must not go on to claim the two are bound. See
     *   the drop below.
     */
    suspend fun lnAddressInvoice(
        lnAddress: String,
        milliSats: Long,
        message: String,
        nostrRequest: ZapRequestEvent? = null,
        onProgress: (percent: Float) -> Unit,
        onZapRequestSent: (ZapRequestEvent?) -> Unit = {},
    ): String {
        val lnurlpUrl = assembleUrl(lnAddress)

        val lnAddressJson =
            fetchLightningAddressJson(lnAddress)

        onProgress(0.4f)

        val lnurlp =
            try {
                Json.parseToJsonElement(lnAddressJson) as? JsonObject
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw LightningAddressError(
                    loadStringRes(Res.string.error_unable_to_fetch_invoice),
                    loadStringRes(
                        Res.string.error_parsing_json_from_lightning_address_check_the_user_s_lightning_setup_with_user,
                        lnAddress,
                    ),
                )
            }

        val callbackUrl = lnurlp?.get("callback")?.asText()?.ifBlank { null }

        if (callbackUrl == null) {
            throw LightningAddressError(
                loadStringRes(Res.string.error_unable_to_fetch_invoice),
                loadStringRes(
                    Res.string.callback_url_not_found_in_the_user_s_lightning_address_server_configuration_with_user,
                    lnAddress,
                ),
            )
        }

        val allowsNostr = lnurlp["allowsNostr"]?.asBoolean() ?: false
        val nostrPubkey = lnurlp["nostrPubkey"]?.asText()?.ifBlank { null }

        // Prime the receipt-validation cache so incoming zap receipts can be
        // checked against this provider's nostrPubkey (NIP-57 Appendix F)
        // without re-fetching the lnurlp endpoint.
        if (lnurlpUrl != null) {
            LnurlEndpointCache.put(
                lnurlpUrl,
                LnurlEndpointInfo(nostrPubkey = nostrPubkey, allowsNostr = allowsNostr),
            )
        }

        // NIP-57 binds a zap request to its invoice through `description_hash`, and a
        // provider that ignores `nostr=` mints an invoice that commits to nothing about
        // it. Report what actually went, so a caller cannot attach the event to a
        // payment it was never bound to.
        val sentZapRequest = nostrRequest?.takeIf { allowsNostr }
        onZapRequestSent(sentZapRequest)

        val invoice =
            fetchLightningInvoice(
                lnCallback = callbackUrl,
                milliSats = milliSats,
                message = message,
                nostrRequest = sentZapRequest,
            )

        onProgress(0.6f)

        val lnInvoice =
            try {
                Json.parseToJsonElement(invoice) as? JsonObject
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw LightningAddressError(
                    loadStringRes(Res.string.error_unable_to_fetch_invoice),
                    loadStringRes(
                        Res.string.error_parsing_json_from_lightning_address_s_invoice_fetch_check_the_user_s_lightning_setup_with_user,
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
                    loadStringRes(Res.string.error_unable_to_fetch_invoice),
                    loadStringRes(
                        Res.string.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error_with_user,
                        lnAddress,
                        reason,
                    ),
                )
            } else {
                throw LightningAddressError(
                    loadStringRes(Res.string.error_unable_to_fetch_invoice),
                    loadStringRes(
                        Res.string.unable_to_create_a_lightning_invoice_before_sending_the_zap_element_pr_not_found_in_the_resulting_json_with_user,
                        lnAddress,
                    ),
                )
            }
        }

        // Forces LN Invoice amount to be the requested amount.
        val expectedAmountInSats = roundHalfUpToSats(milliSats)

        val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)

        if (invoiceAmount.toLong() != expectedAmountInSats) {
            onProgress(0.0f)
            throw LightningAddressError(
                loadStringRes(Res.string.error_unable_to_fetch_invoice),
                loadStringRes(
                    Res.string.incorrect_invoice_amount_sats_from_it_should_have_been,
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

/** Millisats to sats, rounding a half sat up, as `BigDecimal.divide(1000, HALF_UP)` did. */
internal fun roundHalfUpToSats(milliSats: Long): Long = if (milliSats >= 0) (milliSats + 500) / 1000 else -((-milliSats + 500) / 1000)

private fun parseJson(body: String): JsonElement? =
    try {
        Json.parseToJsonElement(body)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

// Jackson's JsonNode readers, which LNURL servers' loosely-typed replies were written against:
// asText() gives a scalar's text and "" for a container; asBoolean() reads true, a non-zero
// number or the text "true". JSON null reads as absent rather than as the text "null".

private fun JsonElement.asText(): String? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> ""
    }

private fun JsonElement.isBoolean(): Boolean = this is JsonPrimitive && !isString && booleanOrNull != null

private fun JsonElement.asBoolean(): Boolean =
    when {
        this !is JsonPrimitive || this is JsonNull -> false
        isString -> content.trim() == "true"
        else -> booleanOrNull ?: longOrNull?.let { it != 0L } ?: false
    }
