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
package com.vitorpamplona.quartz.cordn.appGroupRef

import com.vitorpamplona.quartz.cordn.tlv.CordnStrictTlv
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder

/**
 * A `cordn1…` group reference — `spec/applications/group-ref.md`.
 *
 * One checksummed string carrying the three coordinates needed to reach a
 * group: the delivery `gid`, optionally which coordinator serves it, and
 * optionally where that coordinator is reachable. Bech32 with NIP-19's TLV
 * layout, so existing Nostr tooling reads it.
 *
 * It is a locator and authorizes nothing. Holding one lets you ask to join
 * (see `spec/applications/join-requests.md`); it does not make you a member.
 */
data class CordnGroupRef(
    /**
     * The delivery group identifier, exactly as the producing client uses it.
     *
     * Opaque: not a UUID, not a hash, and specifically **not** the MLS
     * `group_id`. §4.1 requires it to round-trip byte for byte with no
     * trimming or re-encoding, because the coordinator keys its cursor space
     * on these bytes.
     */
    val gid: String,
    /** The coordinator serving this group, as lowercase hex. */
    val coordinatorPubKey: HexKey? = null,
    /** Where to reach that coordinator. Meaningless, and invalid, without one. */
    val relays: List<String> = emptyList(),
) {
    init {
        require(gid.isNotEmpty()) { "cordn group ref gid must not be empty" }
        require(gid.encodeToByteArray().size <= MAX_TLV_VALUE) {
            "cordn group ref gid must be at most $MAX_TLV_VALUE bytes (the TLV length field is one byte)"
        }
        require(coordinatorPubKey == null || coordinatorPubKey.length == PUBKEY_HEX_LENGTH) {
            "cordn group ref coordinator pubkey must be 32 bytes"
        }
        require(relays.isEmpty() || coordinatorPubKey != null) {
            "cordn group ref carries relays with no coordinator pubkey: a relay names where to reach a coordinator"
        }
    }

    /**
     * Encodes as `cordn1…`.
     *
     * TLV elements go out in descending type order to match NIP-19's reference
     * encoder, though §3 requires decoders to accept any order.
     */
    fun encode(): String {
        val builder = TlvBuilder()
        relays.forEach { builder.addString(TLV_RELAY, it) }
        coordinatorPubKey?.let { builder.addHex(TLV_COORDINATOR, it) }
        builder.addString(TLV_GID, gid)
        return Bech32.encodeBytes(HRP, builder.build(), Bech32.Encoding.Bech32)
    }

    companion object {
        /** Encoded refs begin `cordn1`. */
        const val HRP = "cordn"

        /** UTF-8 `gid`. Exactly one. */
        const val TLV_GID: Byte = 0

        /** Raw 32-byte coordinator pubkey. At most one. */
        const val TLV_COORDINATOR: Byte = 1

        /** UTF-8 relay URL. Any number, but only alongside a coordinator. */
        const val TLV_RELAY: Byte = 2

        /** §2: the NIP-19 bound, present only to cap decoding work. */
        const val MAX_LENGTH = 5000

        private const val MAX_TLV_VALUE = 255
        private const val PUBKEY_HEX_LENGTH = 64

        /** Names this type in decode errors. */
        private const val SUBJECT = "cordn group ref"
        private const val PUBKEY_SIZE = 32

        /** Decodes a `cordn1…` reference, or throws with the rule it broke. */
        fun decode(encoded: String): CordnGroupRef {
            require(encoded.length <= MAX_LENGTH) {
                "cordn group ref is longer than $MAX_LENGTH characters"
            }
            // Bech32 forbids mixed case, and Bech32.decode lowercases rather
            // than rejecting, so the check has to happen before the call.
            require(encoded == encoded.lowercase() || encoded == encoded.uppercase()) {
                "cordn group ref must be all lowercase or all uppercase"
            }

            val (hrp, bytes, encoding) = Bech32.decodeBytes(encoded.lowercase(), false)
            require(hrp == HRP) { "not a cordn group ref: prefix is '$hrp', expected '$HRP'" }
            // §2 pins bech32, not bech32m. Accepting either would let two
            // encodings of the same ref exist, and NIP-19 made the same choice.
            require(encoding == Bech32.Encoding.Bech32) {
                "cordn group ref must use bech32, not bech32m"
            }

            val tlv = CordnStrictTlv.parse(bytes, SUBJECT)

            val gids = tlv[TLV_GID].orEmpty()
            require(gids.size == 1) { "cordn group ref must carry exactly one gid, found ${gids.size}" }
            require(gids[0].isNotEmpty()) { "cordn group ref gid must not be empty" }

            val coordinators = tlv[TLV_COORDINATOR].orEmpty()
            require(coordinators.size <= 1) { "cordn group ref must carry at most one coordinator pubkey" }
            coordinators.forEach {
                require(it.size == PUBKEY_SIZE) {
                    "cordn group ref coordinator pubkey must be $PUBKEY_SIZE bytes, was ${it.size}"
                }
            }

            // §5 lets a consumer discard an empty relay rather than reject the
            // whole reference, which is the kinder reading of a producer bug.
            val relays = tlv[TLV_RELAY].orEmpty().map { CordnStrictTlv.utf8(it, SUBJECT, "relay") }.filter { it.isNotEmpty() }
            require(relays.isEmpty() || coordinators.isNotEmpty()) {
                "cordn group ref carries a relay with no coordinator pubkey"
            }

            return CordnGroupRef(
                gid = CordnStrictTlv.utf8(gids[0], SUBJECT, "gid"),
                coordinatorPubKey = coordinators.firstOrNull()?.toHexKey(),
                relays = relays,
            )
        }

        /** Decodes, or null when the string is not a valid reference. */
        fun decodeOrNull(encoded: String): CordnGroupRef? =
            try {
                decode(encoded)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
