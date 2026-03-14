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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

class NwcTransactionMetadata(
    val comment: String?,
    val payerData: PayerData?,
    val recipientData: RecipientData?,
    val nostr: NostrZapData?,
) {
    class PayerData(
        val name: String?,
        val email: String?,
        val pubkey: String?,
    )

    class RecipientData(
        val identifier: String?,
    )

    class NostrZapData(
        val pubkeyHex: String?,
        val recipientPubkeyHex: String?,
    )

    fun senderPubkeyHex(): String? = nostr?.pubkeyHex ?: payerData?.pubkey?.let { decodePublicKeyAsHexOrNull(it) }

    fun senderDisplayName(): String? = payerData?.name ?: payerData?.email

    fun recipientIdentifier(): String? = recipientData?.identifier

    fun recipientPubkeyHex(): String? = nostr?.recipientPubkeyHex

    companion object {
        fun parse(metadata: Any?): NwcTransactionMetadata? {
            val map = metadata as? Map<*, *> ?: return null

            val comment = map["comment"] as? String

            val payerData = (map["payer_data"] as? Map<*, *>)?.let { pd ->
                PayerData(
                    name = pd["name"] as? String,
                    email = pd["email"] as? String,
                    pubkey = pd["pubkey"] as? String,
                )
            }

            val recipientData = (map["recipient_data"] as? Map<*, *>)?.let { rd ->
                RecipientData(
                    identifier = rd["identifier"] as? String,
                )
            }

            val nostr = (map["nostr"] as? Map<*, *>)?.let { n ->
                val rawPubkey = n["pubkey"] as? String
                val pubkeyHex = rawPubkey?.let { decodePublicKeyAsHexOrNull(it) }

                val tags = n["tags"] as? List<*>
                val recipientHex = tags?.firstNotNullOfOrNull { tag ->
                    val tagList = tag as? List<*>
                    if (tagList != null && tagList.size >= 2 && tagList[0] == "p") {
                        tagList[1] as? String
                    } else {
                        null
                    }
                }

                NostrZapData(
                    pubkeyHex = pubkeyHex,
                    recipientPubkeyHex = recipientHex,
                )
            }

            if (comment == null && payerData == null && recipientData == null && nostr == null) {
                return null
            }

            return NwcTransactionMetadata(
                comment = comment,
                payerData = payerData,
                recipientData = recipientData,
                nostr = nostr,
            )
        }
    }
}
