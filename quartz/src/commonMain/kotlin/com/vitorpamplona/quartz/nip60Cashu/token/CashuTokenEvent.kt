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
package com.vitorpamplona.quartz.nip60Cashu.token

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-60 Cashu Token Event (kind:7375).
 *
 * Records unspent cashu proofs. The content is NIP-44 encrypted JSON:
 * {
 *   "mint": "https://mint.example.com",
 *   "unit": "sat",
 *   "proofs": [{"id": "...", "amount": 1, "secret": "...", "C": "..."}],
 *   "del": ["token-event-id-1", "token-event-id-2"]
 * }
 *
 * When proofs are spent, this event should be NIP-09 deleted and unspent proofs
 * rolled over into a new token event.
 */
@Immutable
class CashuTokenEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Decrypts the content and parses it into a [TokenContent].
     */
    suspend fun tokenContent(signer: NostrSigner): TokenContent? {
        val json = signer.nip44Decrypt(content, pubKey)
        return TokenContentParser.fromJson(json)
    }

    companion object {
        const val KIND = 7375
        const val ALT_DESCRIPTION = "Cashu token"

        suspend fun build(
            tokenContent: TokenContent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CashuTokenEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            initializer()
        }.let { template ->
            val encryptedContent = signer.nip44Encrypt(TokenContentParser.toJson(tokenContent), signer.pubKey)

            EventTemplate<CashuTokenEvent>(
                template.createdAt,
                template.kind,
                template.tags,
                encryptedContent,
            )
        }
    }
}
