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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.utils.Hex

/**
 * A lower-case hexadecimal string. This is the canonical wire format for the
 * 32-byte values Nostr deals with everywhere: public keys, event ids, and the
 * 64-byte Schnorr signature. It is only a [String] alias — it documents intent
 * and does no validation on its own — so pair it with [isValid] (or [Hex.isHex])
 * whenever the value comes from an untrusted source.
 *
 * Convert with the extension functions below rather than reaching for a byte
 * loop or a third-party codec:
 *
 * ```kotlin
 * import com.vitorpamplona.quartz.nip01Core.core.toHexKey
 * import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
 *
 * val hex: HexKey = pubKeyBytes.toHexKey() // ByteArray -> hex
 * val bytes: ByteArray = hex.hexToByteArray() // hex -> ByteArray
 * ```
 *
 * For byte-array-free, allocation-light checks use [Hex.isHex64] (32-byte keys
 * and ids) or [Hex.isEqual] (compare a hex string to raw bytes without decoding).
 */
typealias HexKey = String

/** Encodes these bytes as a lower-case [HexKey]. Inverse of [hexToByteArray]. */
fun ByteArray.toHexKey(): HexKey = Hex.encode(this)

/**
 * Decodes this hex string into its bytes. Inverse of [toHexKey].
 *
 * Accepts upper- or lower-case input but requires an even length; throws
 * [IllegalArgumentException] on odd-length input. Use [hexToByteArrayOrNull]
 * when the string may contain non-hex characters.
 */
fun HexKey.hexToByteArray(): ByteArray = Hex.decode(this)

/** Like [hexToByteArray] but returns null instead of throwing when this is not valid hex. */
fun HexKey.hexToByteArrayOrNull(): ByteArray? = if (Hex.isHex(this)) Hex.decode(this) else null

/** True when this is a 64-char (32-byte) hex string — the shape of a pubkey or event id. */
fun HexKey.isValid(): Boolean = length == PUBKEY_LENGTH && Hex.isHex(this)

/** Length in hex chars of a 32-byte public key. */
const val PUBKEY_LENGTH = 64

/** Length in hex chars of a 32-byte event id. */
const val EVENT_ID_LENGTH = 64
