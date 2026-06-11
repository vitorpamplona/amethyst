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
package com.vitorpamplona.quartz.experimental.clink.pointers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * CLINK ("Common Lightning Interface for Nostr Keys") addresses a remote Lightning
 * service through a bech32-encoded pointer that carries a small TLV payload. There
 * are three pointer kinds — one per CLINK spec — and they all share the first three
 * TLV fields (pubkey, relay, pointer-id). See https://github.com/shocknet/clink.
 *
 * Pointers use **standard** bech32 (not bech32m) with their own human-readable
 * prefixes (`noffer`, `ndebit`, `nmanage`), so they are intentionally parsed by
 * [ClinkPointerParser] rather than the NIP-19 [com.vitorpamplona.quartz.nip19Bech32.Nip19Parser].
 */
sealed interface ClinkPointer {
    /** TLV 0 — 32-byte public key of the service that listens for requests. */
    val pubKey: HexKey

    /** TLV 1 — relay(s) where the service listens. May be empty when shared out-of-band. */
    val relays: List<NormalizedRelayUrl>

    /** TLV 2 — opaque routing pointer the service uses to disambiguate (optional). */
    val pointer: String?

    /** Re-encodes this pointer to its `noffer1…` / `ndebit1…` / `nmanage1…` string. */
    fun encode(): String
}

/**
 * Shared TLV field indices across the CLINK pointer specs. Indices 0–2 are common;
 * 3–4 are reused with different meanings per spec (priceType/price for offers, k1
 * for debit sessions), mirroring `@shocknet/clink-sdk`.
 */
internal object ClinkTlv {
    const val PUBKEY: Byte = 0
    const val RELAY: Byte = 1
    const val POINTER: Byte = 2

    // Offers
    const val PRICE_TYPE: Byte = 3
    const val PRICE: Byte = 4

    // Debits
    const val K1: Byte = 3
}

/** Encodes a small unsigned value as a single-byte hex string for one-byte TLV values. */
internal fun Int.toSingleByteHex(): String = (this and 0xFF).toString(16).padStart(2, '0')
