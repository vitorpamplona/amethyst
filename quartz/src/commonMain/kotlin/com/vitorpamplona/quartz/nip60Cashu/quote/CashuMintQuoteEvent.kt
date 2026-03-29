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
     * Decrypts the content to get the quote ID.
     */
    suspend fun quoteId(signer: NostrSigner): String = signer.nip44Decrypt(content, pubKey)

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
            expirationTimestamp: Long = TimeUtils.now() + TWO_WEEKS_SECONDS,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CashuMintQuoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            expiration(expirationTimestamp)
            add(arrayOf("mint", mintUrl))
            initializer()
        }.let { template ->
            val encryptedContent = signer.nip44Encrypt(quoteId, signer.pubKey)

            EventTemplate<CashuMintQuoteEvent>(
                template.createdAt,
                template.kind,
                template.tags,
                encryptedContent,
            )
        }
    }
}
