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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Encrypted token tag for push notification token distribution (kinds 447, 448).
 *
 * Each EncryptedToken is exactly 280 bytes:
 *   ephemeral_pubkey(32) || nonce(12) || ciphertext(236)
 *
 * The token payload inside is padded to 220 bytes:
 *   platform(1) || token_length(2 BE) || device_token(N) || random_padding(220-3-N)
 *
 * Platform values: 0x01 = APNs, 0x02 = FCM.
 */
@Immutable
data class TokenTagData(
    /** Base64-encoded EncryptedToken (280 bytes when decoded) */
    val encryptedToken: String,
    /** Hex-encoded notification server public key */
    val serverPubKey: HexKey,
    /** Relay hint URL for finding the server's kind:10050 event */
    val relayHint: String,
    /** MLS leaf index of the token owner (only present in kind:448 responses) */
    val leafIndex: Int? = null,
)

class TokenTag {
    companion object {
        const val TAG_NAME = "token"

        /** Expected decoded size of an EncryptedToken */
        const val ENCRYPTED_TOKEN_SIZE = 280

        fun parse(tag: Array<String>): TokenTagData? {
            ensure(tag.has(3) && tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].length == 64) { return null }
            ensure(tag[3].isNotEmpty()) { return null }

            val leafIndex = tag.getOrNull(4)?.toIntOrNull()

            return TokenTagData(
                encryptedToken = tag[1],
                serverPubKey = tag[2],
                relayHint = tag[3],
                leafIndex = leafIndex,
            )
        }

        fun assemble(data: TokenTagData): Array<String> =
            if (data.leafIndex != null) {
                arrayOf(TAG_NAME, data.encryptedToken, data.serverPubKey, data.relayHint, data.leafIndex.toString())
            } else {
                arrayOf(TAG_NAME, data.encryptedToken, data.serverPubKey, data.relayHint)
            }

        fun assemble(tokens: List<TokenTagData>) = tokens.map { assemble(it) }
    }
}
