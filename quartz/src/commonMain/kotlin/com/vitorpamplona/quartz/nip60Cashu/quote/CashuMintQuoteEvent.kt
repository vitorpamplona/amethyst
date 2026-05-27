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
package com.vitorpamplona.quartz.nip60Cashu.quote

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * NIP-60 Cashu Mint Quote Event (kind:7374).
 *
 * Optional event to keep the state of a mint quote ID, used to check when the
 * quote has been paid. Should be created with an expiration tag (NIP-40) of ~2 weeks.
 *
 * The content is NIP-44 encrypted and contains the quote-id string.
 * Public tags include:
 * - ["expiration", "<timestamp>"]
 * - ["mint", "<mint-url>"]
 */
@Immutable
class CashuMintQuoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Decrypt the full quote payload — quote id plus, when present, the
     * NUT-20 signing privkey that binds this quote to a specific wallet
     * keypair. The content schema has two historic shapes:
     *   - pre-NUT-20: plain string == the quote id
     *   - post-NUT-20: JSON `{"quote_id": "...", "p2pk_priv": "..."}`
     * The decode tries the JSON shape first; on parse failure it treats
     * the entire decrypted payload as the quote id (legacy events).
     */
    suspend fun decrypt(signer: NostrSigner): Decrypted {
        val plaintext = signer.nip44Decrypt(content, pubKey)
        return runCatching { jsonCodec.decodeFromString<Decrypted>(plaintext) }
            .getOrElse { Decrypted(quoteId = plaintext, p2pkPriv = null) }
    }

    /**
     * Decrypts the content to get the quote ID. Kept for backwards
     * compatibility with callers that don't care about the NUT-20 key.
     */
    suspend fun quoteId(signer: NostrSigner): String = decrypt(signer).quoteId

    /**
     * The NUT-20 signing private key for this quote, when one was generated
     * at quote-creation time. Null for pre-NUT-20 events or quotes the
     * wallet chose not to bind to a key.
     */
    suspend fun signingPrivkey(signer: NostrSigner): String? = decrypt(signer).p2pkPriv

    /** Decrypted kind:7374 content. See [decrypt]. */
    @Serializable
    data class Decrypted(
        @SerialName("quote_id") val quoteId: String,
        @SerialName("p2pk_priv") val p2pkPriv: String? = null,
    )

    /**
     * Gets the mint URL from the public tags.
     */
    fun mint(): String? =
        tags
            .firstOrNull { it.size >= 2 && it[0] == "mint" }
            ?.get(1)

    companion object {
        const val KIND = 7374
        const val ALT_DESCRIPTION = "Cashu mint quote"
        const val TWO_WEEKS_SECONDS = 14 * 24 * 60 * 60L

        suspend fun build(
            quoteId: String,
            mintUrl: String,
            signer: NostrSigner,
            /**
             * NUT-20: the ephemeral signing private key (32-byte hex) the
             * wallet committed to when opening this mint quote. Carried
             * inside the encrypted content so the resume-on-next-launch
             * path can pick it up and sign the matching mint request.
             * Null skips NUT-20 entirely — older wallets / mints stay
             * compatible (plain-string content shape).
             */
            signingPrivkey: String? = null,
            expirationTimestamp: Long = TimeUtils.now() + TWO_WEEKS_SECONDS,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CashuMintQuoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            expiration(expirationTimestamp)
            add(arrayOf("mint", mintUrl))
            initializer()
        }.let { template ->
            val payload =
                if (signingPrivkey != null) {
                    jsonCodec.encodeToString(
                        Decrypted.serializer(),
                        Decrypted(quoteId = quoteId, p2pkPriv = signingPrivkey),
                    )
                } else {
                    // Legacy plain-string shape for events that don't carry
                    // a signing key — keeps wire compatibility with any
                    // pre-NUT-20 reader that expects a bare quote id.
                    quoteId
                }
            val encryptedContent = signer.nip44Encrypt(payload, signer.pubKey)

            EventTemplate<CashuMintQuoteEvent>(
                template.createdAt,
                template.kind,
                template.tags,
                encryptedContent,
            )
        }

        private val jsonCodec = Json { ignoreUnknownKeys = true }
    }
}
