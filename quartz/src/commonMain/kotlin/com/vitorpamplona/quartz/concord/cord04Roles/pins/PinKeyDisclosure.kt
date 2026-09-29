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
package com.vitorpamplona.quartz.concord.cord04Roles.pins

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import com.vitorpamplona.quartz.utils.equalsConstantTime

/**
 * NIP-44 v2 per-message key disclosure — the primitive a Concord Pin is built on (CORD-04 §7).
 *
 * NIP-44 derives each message's keys as `hkdf-expand(conversation_key, nonce, 76)` =
 * `chacha_key[32] ‖ chacha_nonce[12] ‖ hmac_key[32]`. The expansion is one-way, so disclosing it
 * opens exactly that one message: not the conversation key, not the epoch, not the author's other
 * traffic. Wire form: 76 bytes as 152 lowercase hex characters.
 */
object PinKeyDisclosure {
    const val MESSAGE_KEYS_BYTES = 76

    private val HEX = Regex("^[0-9a-f]{152}$")
    private val chaCha = ChaCha20()

    /** The 76-byte lowercase-hex wire form of [keys]. */
    fun encode(keys: Hkdf.MessageKey): String = (keys.chachaKey + keys.chachaNonce + keys.hmacKey).toHexKey()

    /** Parses a disclosure; null unless it is exactly 152 lowercase hex characters. */
    fun decode(hex: String): Hkdf.MessageKey? {
        if (!HEX.matches(hex)) return null
        val bytes = hex.hexToByteArrayOrNull() ?: return null
        return Hkdf.MessageKey(bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 44), bytes.copyOfRange(44, 76))
    }

    private fun decodePayload(payload: String): Nip44v2.EncryptedInfo? =
        try {
            Nip44v2.EncryptedInfo.decodePayload(payload)
        } catch (_: Exception) {
            null
        }

    /** The disclosure for one NIP-44 [payload], derived with the stream's [conversationKey]; null if the payload is malformed. */
    fun discloseFor(
        payload: String,
        conversationKey: ByteArray,
    ): Hkdf.MessageKey? {
        val decoded = decodePayload(payload) ?: return null
        return Nip44.v2.getMessageKeys(conversationKey, decoded.nonce)
    }

    /**
     * Opens a NIP-44 v2 [payload] with disclosed [keys] (a pin's proof): MAC over nonce‖ciphertext
     * with the disclosed HMAC key, then ChaCha20, then a strict unpad. Null on any failure. Only the
     * standard u16-prefixed form is accepted — a Concord payload never exceeds 65,535 bytes.
     */
    fun decryptWith(
        payload: String,
        keys: Hkdf.MessageKey,
    ): String? {
        val decoded = decodePayload(payload) ?: return null
        val mac =
            try {
                Nip44.v2.hmacAad(keys.hmacKey, decoded.ciphertext, decoded.nonce)
            } catch (_: Exception) {
                return null
            }
        if (!mac.equalsConstantTime(decoded.mac)) return null
        val padded = chaCha.decrypt(decoded.ciphertext, keys.chachaNonce, keys.chachaKey)
        if (padded.size < 2) return null
        val len = (padded[0].toInt() and 0xFF shl 8) or (padded[1].toInt() and 0xFF)
        if (len < 1) return null
        if (padded.size.toLong() != 2 + Nip44.v2.calcPaddedLen(len)) return null
        return try {
            padded.decodeToString(2, 2 + len, throwOnInvalidSequence = true)
        } catch (_: Exception) {
            null
        }
    }
}
