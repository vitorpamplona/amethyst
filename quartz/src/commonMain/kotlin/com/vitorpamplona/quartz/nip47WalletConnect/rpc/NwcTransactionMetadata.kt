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
package com.vitorpamplona.quartz.nip47WalletConnect.rpc

import com.vitorpamplona.quartz.nip01Core.core.Event
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
        val content: String?,
    )

    fun senderPubkeyHex(): String? = nostr?.pubkeyHex ?: payerData?.pubkey?.let { decodePublicKeyAsHexOrNull(it) }

    fun senderDisplayName(): String? = payerData?.name?.ifBlank { null } ?: payerData?.email?.ifBlank { null }

    fun recipientIdentifier(): String? = recipientData?.identifier?.ifBlank { null }

    fun recipientPubkeyHex(): String? = nostr?.recipientPubkeyHex

    /**
     * The message to show for this transaction.
     *
     * A wallet that stores only `nostr` still carries the message: a zap request's
     * `content` IS the public zap comment. Private-zap messages are encrypted into
     * the `anon` tag rather than content, so nothing encrypted can surface here.
     */
    fun displayComment(): String? = comment?.ifBlank { null } ?: nostr?.content?.ifBlank { null }

    companion object {
        fun parse(metadata: Any?): NwcTransactionMetadata? {
            val map = metadata as? Map<*, *> ?: return null

            val comment = map["comment"] as? String

            val payerData =
                (map["payer_data"] as? Map<*, *>)?.let { pd ->
                    PayerData(
                        name = pd["name"] as? String,
                        email = pd["email"] as? String,
                        pubkey = pd["pubkey"] as? String,
                    )
                }

            val recipientData =
                (map["recipient_data"] as? Map<*, *>)?.let { rd ->
                    RecipientData(
                        identifier = rd["identifier"] as? String,
                    )
                }

            val nostr =
                (map["nostr"] as? Map<*, *>)?.let { n ->
                    val rawPubkey = n["pubkey"] as? String
                    val pubkeyHex = rawPubkey?.let { decodePublicKeyAsHexOrNull(it) }

                    val tags = n["tags"] as? List<*>
                    val recipientHex =
                        tags?.firstNotNullOfOrNull { tag ->
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
                        content = n["content"] as? String,
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

        /**
         * NWC-06: "The metadata MUST be no more than 4096 characters, otherwise MUST
         * be dropped." A wallet is required to discard an over-long object wholesale,
         * so breaching this loses the recipient entirely rather than degrading.
         */
        const val MAX_METADATA_CHARS = 4096

        // Slack for the keys and punctuation around the values once serialized —
        // `{"recipient_data":{"identifier":""},"comment":"","nostr":}` is ~56 chars.
        // Deliberately generous: overshooting drops `nostr` on a payment that would
        // just have fit, undershooting sends an object the wallet must throw away.
        private const val KEY_OVERHEAD = 96

        /**
         * Assembles NWC-06 `metadata` for an outgoing payment, or null when there is
         * nothing worth saying.
         *
         * `nostr` is built from the event's TYPED fields rather than by re-parsing its
         * JSON. A wallet that verifies the request recomputes the event id from these
         * values, so `kind` and `created_at` must stay integers — and the obvious
         * shortcut breaks exactly that: [com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.toAnyValue]
         * resolves untyped numbers with `toDoubleOrNull()` BEFORE `toLongOrNull()`, so
         * a JSON round-trip would emit `"kind": 9734.0` on the kotlinx (native) path
         * while the JVM/Jackson path stayed correct — invisible on the platform we
         * test on, broken on the one we do not.
         *
         * When the whole object would breach [MAX_METADATA_CHARS], `nostr` is dropped
         * and the much smaller `recipient_data`/`comment` pair survives, so the row
         * still names the payee instead of arriving blank.
         */
        fun build(
            zapRequest: Event?,
            recipientIdentifier: String?,
            comment: String?,
        ): Map<String, Any?>? {
            val lean = mutableMapOf<String, Any?>()

            recipientIdentifier?.ifBlank { null }?.let {
                lean["recipient_data"] = mapOf("identifier" to it)
            }
            comment?.ifBlank { null }?.let { lean["comment"] = it }

            if (zapRequest != null) {
                // toJson() is the exact serialized length of the `nostr` sub-object.
                val chars =
                    recipientIdentifier.orEmpty().length + comment.orEmpty().length +
                        zapRequest.toJson().length + KEY_OVERHEAD
                if (chars <= MAX_METADATA_CHARS) {
                    lean["nostr"] = zapRequestFields(zapRequest)
                }
            }

            return lean.ifEmpty { null }
        }

        private fun zapRequestFields(event: Event): Map<String, Any?> =
            mapOf(
                "id" to event.id,
                "pubkey" to event.pubKey,
                "created_at" to event.createdAt,
                "kind" to event.kind,
                "tags" to event.tags.map { it.toList() },
                "content" to event.content,
                "sig" to event.sig,
            )
    }
}
