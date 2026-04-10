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
package com.vitorpamplona.amethyst.ios.nwc

import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosHttpClient
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.create
import platform.Foundation.setValue

/**
 * Orchestrates the full NIP-57 + NIP-47 zap flow:
 *
 * 1. Create a kind-9734 zap request event
 * 2. Fetch the LNURL callback from the note author's lud16
 * 3. Request a BOLT-11 invoice from the LNURL server
 * 4. Pay the invoice via NWC (NIP-47)
 * 5. Broadcast the zap request to relays
 */
class ZapController(
    private val signer: NostrSigner,
    private val relayManager: IosRelayConnectionManager,
    private val localCache: IosLocalCache,
) {
    /**
     * Executes a full zap: zap request → LNURL → invoice → NWC pay.
     *
     * @param noteId The note event id to zap
     * @param amountSats Amount in sats
     * @param message Optional zap message
     * @return Result with success message or failure
     */
    suspend fun zap(
        noteId: String,
        amountSats: Long,
        message: String = "",
    ): Result<String> =
        try {
            val nwcClient =
                NwcSettings.createClient()
                    ?: return Result.failure(IllegalStateException("NWC not configured. Set your wallet connection in Settings."))

            // 1. Get the note and author info
            val note =
                localCache.getNoteIfExists(noteId)
                    ?: return Result.failure(IllegalStateException("Note not found"))
            val event =
                note.event
                    ?: return Result.failure(IllegalStateException("Note event not loaded"))
            val authorHex = event.pubKey
            val user =
                localCache.getUserIfExists(authorHex)
                    ?: return Result.failure(IllegalStateException("Author profile not loaded"))

            // 2. Get the author's lightning address
            val lnAddress =
                user.lnAddress()
                    ?: return Result.failure(IllegalStateException("Author has no lightning address"))

            // 3. Fetch LNURL pay endpoint
            val lnurlPayUrl = lnAddressToUrl(lnAddress)
            val lnurlJson = httpGet(lnurlPayUrl)
            val lnurlData = Json.parseToJsonElement(lnurlJson).jsonObject
            val callback =
                lnurlData["callback"]?.jsonPrimitive?.content
                    ?: return Result.failure(IllegalStateException("No callback in LNURL response"))
            val allowsNostr = lnurlData["allowsNostr"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

            if (!allowsNostr) {
                return Result.failure(IllegalStateException("LNURL server does not support Nostr zaps"))
            }

            // 4. Create kind 9734 zap request
            val relays = relayManager.connectedRelays.value
            val zapRequest =
                LnZapRequestEvent.create(
                    zappedEvent = event,
                    relays = relays,
                    signer = signer,
                    pollOption = null,
                    message = message,
                    zapType = LnZapEvent.ZapType.PUBLIC,
                    toUserPubHex = authorHex,
                )

            // 5. Request invoice from LNURL callback with zap request
            val amountMillisats = amountSats * 1000
            val zapRequestJson = zapRequest.toJson()
            val encodedNostr = percentEncode(zapRequestJson)
            val separator = if (callback.contains("?")) "&" else "?"
            val invoiceUrl = "${callback}${separator}amount=$amountMillisats&nostr=$encodedNostr"
            val invoiceJson = httpGet(invoiceUrl)
            val invoiceData = Json.parseToJsonElement(invoiceJson).jsonObject
            val bolt11 =
                invoiceData["pr"]?.jsonPrimitive?.content
                    ?: return Result.failure(IllegalStateException("No invoice returned from LNURL server"))

            // 6. Pay invoice via NWC
            val paymentRequest = nwcClient.payInvoice(bolt11)
            relayManager.sendToRelay(nwcClient.relayUrl, paymentRequest)

            // 7. Broadcast zap request to relays
            relayManager.broadcastToAll(zapRequest)

            Result.success("Zap of $amountSats sats sent!")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }

    companion object {
        /**
         * Converts a lightning address (user@domain) to an LNURL pay endpoint URL.
         */
        fun lnAddressToUrl(lnAddress: String): String {
            val parts = lnAddress.split("@")
            if (parts.size != 2) throw IllegalArgumentException("Invalid lightning address: $lnAddress")
            val (user, domain) = parts
            return "https://$domain/.well-known/lnurlp/$user"
        }

        /**
         * Simple percent-encoding for URL query parameters.
         */
        fun percentEncode(value: String): String =
            buildString {
                for (c in value) {
                    when {
                        c.isLetterOrDigit() || c in "-._~" -> {
                            append(c)
                        }

                        else -> {
                            val bytes = c.toString().encodeToByteArray()
                            for (b in bytes) {
                                append('%')
                                append(
                                    (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'),
                                )
                            }
                        }
                    }
                }
            }

        /**
         * Async HTTP GET using NSURLSession via IosHttpClient.
         */
        suspend fun httpGet(url: String): String = IosHttpClient.get(url)
    }
}
