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
package com.vitorpamplona.quartz.experimental.decoupling.setup.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * One `n` entry of a kind 10044 encryption-key list.
 *
 * NIP-4E (draft, nips#1647) writes `["n", <encryption pubkey>]`: the encryption key is a random
 * keypair, and devices that lack its secret obtain it through the 4454/4455 transfer
 * (`transfer/`), keeping it in an `EncryptionKeyStore`. Every 10044 seen on relays (2026-10) uses
 * this form.
 *
 * Quartz's earlier design added a third value, `["n", <pubkey>, <nonce>]`, whose secret is derived
 * from the identity key and the nonce (`EncryptionKeyDerivation`, `NostrSigner.deriveKey`). That
 * form is still read so existing lists keep working, but [nonce] is null for the draft form and
 * nothing writes it by default.
 */
class KeyTag(
    val pubkey: HexKey,
    val nonce: HexKey? = null,
) {
    fun toTagArray() = assemble(pubkey, nonce)

    companion object {
        const val TAG_NAME = "n"

        fun isSameKey(
            tag1: Array<String>,
            tag2: Array<String>,
        ): Boolean {
            ensure(tag1.has(1)) { return false }
            ensure(tag2.has(1)) { return false }
            ensure(tag1[0] == tag2[0]) { return false }
            ensure(tag1[1] == tag2[1]) { return false }
            return true
        }

        /**
         * Reads both `["n", <pubkey>]` (the draft) and the legacy `["n", <pubkey>, <nonce>]`. The
         * key must be a 32-byte hex public key (NIP-4E: "a 32-byte lowercase hexadecimal secp256k1
         * public key"); an empty third value is treated as no nonce.
         */
        fun parse(tag: Array<String>): KeyTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex64(tag[1])) { return null }
            val nonce = tag.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return KeyTag(tag[1].lowercase(), nonce)
        }

        /** The draft form `["n", key]`, or the legacy `["n", key, nonce]` when [nonce] is given. */
        fun assemble(
            key: HexKey,
            nonce: HexKey? = null,
        ) = if (nonce == null) arrayOf(TAG_NAME, key) else arrayOf(TAG_NAME, key, nonce)
    }
}
