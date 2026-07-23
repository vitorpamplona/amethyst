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
package com.vitorpamplona.quartz.buzz.aeEngrams

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.mac.MacInstance

/**
 * Derivation of the blinded `d` tag for an Agent Engram (NIP-AE, `kind:30174`).
 *
 * The address is `(pubkey_a, kind, d)` where
 * `d = lower_hex(HMAC-SHA256(K_c, "agent-memory/v1/d-tag" || 0x00 || slug))`,
 * 64 hex characters. `K_c` is the NIP-44 v2 conversation key shared by the agent
 * and its owner — blinding the slug so a relay can address the record without
 * learning it. Ground truth: `buzz-core/src/engram.rs` (`d_tag`, `D_TAG_DOMAIN`).
 *
 * ## Limitation
 * Quartz's [com.vitorpamplona.quartz.nip01Core.signers.NostrSigner] does **not**
 * expose the NIP-44 conversation key (it is held inside the signer, and remote
 * NIP-46 / external NIP-55 signers never reveal it), so the d tag cannot be
 * derived from a signer alone. Callers that hold the raw conversation key bytes
 * (e.g. a local keypair path that computes `K_c` out of band) can use [derive];
 * otherwise the d tag must be supplied to
 * [EngramEvent.create] as an opaque 64-hex string obtained from wherever `K_c`
 * is available.
 */
object EngramDTag {
    /**
     * Domain-separation prefix for the `d`-tag HMAC, followed by a `0x00` byte and
     * the slug. Versioned independently of the NIP number.
     */
    const val DOMAIN = "agent-memory/v1/d-tag"

    /**
     * Computes the blinded `d` tag for [slug] under the raw NIP-44 v2 conversation
     * key [conversationKey] (32 bytes). Returns 64 lowercase hex characters.
     */
    fun derive(
        conversationKey: ByteArray,
        slug: String,
    ): HexKey {
        val mac = MacInstance("HmacSHA256", conversationKey)
        mac.update(DOMAIN.encodeToByteArray())
        mac.update(0x00)
        mac.update(slug.encodeToByteArray())
        return mac.doFinal().toHexKey()
    }
}
