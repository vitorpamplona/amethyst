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
package com.vitorpamplona.quartz.cordn.appMultiDevice

import com.vitorpamplona.quartz.cordn.tlv.CordnStrictTlv
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder

/**
 * Everything a new phone needs to find the old phone's tip — the §6/§11
 * connection string, as one scannable `cordndev1…` code.
 *
 * ## What it carries, and what it deliberately does not
 *
 * The tip lives at `(ephemeral pubkey, d)` on some relays, so the code carries
 * those. It carries **no owner key material**: §4.3 is explicit that the
 * documents do not provision identity, and neither does this. The user signs
 * in on the new phone first, by whatever means they already use — nsec, Amber,
 * a bunker — and only then migrates cordn state onto it.
 *
 * ## Why the write key is usually absent
 *
 * §11's connection string bundles the ephemeral `nsec` so a newly added device
 * can publish its own tip moves. A device being *migrated to* has no such need:
 * there is no fleet to stay in step with, and when that phone is itself
 * replaced one day it mints a fresh ephemeral keypair for its own handoff.
 *
 * Leaving the key out shrinks what a photographed QR is worth. Without it the
 * code is a locator for an event anyone could already fetch and nobody but the
 * owner can decrypt — the tip's content is NIP-44-sealed to the owner `npub`
 * — so a leak reveals nothing. With it, a leak also buys the ability to repoint
 * the tip at a stale-but-valid inventory: denial of service, per §13, but a
 * real one. [writeKey] therefore defaults to null and is populated only when a
 * caller genuinely wants the §11 semantics.
 */
data class CordnHandoffCode(
    /** The tip's author. Independent of the owner identity, per §6. */
    val ephemeralPubKey: HexKey,
    /** The tip's `d` value: random, opaque, stable across republishes. */
    val dTag: String,
    /** Where to look for the tip. */
    val relays: List<String>,
    /** The outer event kind, carried so a later change cannot strand old codes. */
    val kind: Int = CordnDeviceTip.OUTER_KIND,
    /** The ephemeral private key, when this code grants tip writes. See the class doc. */
    val writeKey: HexKey? = null,
) {
    init {
        require(ephemeralPubKey.length == PUBKEY_HEX_LENGTH) {
            "a handoff code's ephemeral pubkey must be 32 bytes of hex"
        }
        require(writeKey == null || writeKey.length == PUBKEY_HEX_LENGTH) {
            "a handoff code's write key must be 32 bytes of hex"
        }
        require(dTag.isNotEmpty()) { "a handoff code must carry the tip's d tag" }
        require(relays.isNotEmpty()) { "a handoff code with no relays cannot find the tip" }
    }

    /** Whether this code lets its holder move the tip, not merely read it. */
    val grantsWrite: Boolean get() = writeKey != null

    /**
     * The same code with the write key removed.
     *
     * What a device shares when it wants the other side to read and nothing
     * more — which for a migration is every time.
     */
    fun readOnly(): CordnHandoffCode = if (writeKey == null) this else copy(writeKey = null)

    /** Encodes as `cordndev1…`. TLV order mirrors NIP-19's reference encoder. */
    fun encode(): String {
        val builder = TlvBuilder()
        builder.addHexIfNotNull(TLV_WRITE_KEY, writeKey)
        relays.forEach { builder.addString(TLV_RELAY, it) }
        builder.addInt(TLV_KIND, kind)
        builder.addString(TLV_D, dTag)
        builder.addHex(TLV_PUBKEY, ephemeralPubKey)
        return Bech32.encodeBytes(HRP, builder.build(), Bech32.Encoding.Bech32)
    }

    companion object {
        /** Encoded codes begin `cordndev1`. */
        const val HRP = "cordndev"

        /** Raw 32-byte ephemeral pubkey. Exactly one. */
        const val TLV_PUBKEY: Byte = 0

        /** UTF-8 `d` value. Exactly one. */
        const val TLV_D: Byte = 1

        /** UTF-8 relay URL. At least one. */
        const val TLV_RELAY: Byte = 2

        /** Big-endian outer event kind. At most one; defaults when absent. */
        const val TLV_KIND: Byte = 3

        /** Raw 32-byte ephemeral private key. At most one; usually absent. */
        const val TLV_WRITE_KEY: Byte = 4

        const val MAX_LENGTH = 5000

        private const val PUBKEY_HEX_LENGTH = 64
        private const val PUBKEY_SIZE = 32

        /** Names this type in decode errors. */
        private const val SUBJECT = "handoff code"

        private fun ByteArray.toInt32(): Int? =
            if (size != Int.SIZE_BYTES) {
                null
            } else {
                (this[0].toInt() and 0xFF shl 24) or
                    (this[1].toInt() and 0xFF shl 16) or
                    (this[2].toInt() and 0xFF shl 8) or
                    (this[3].toInt() and 0xFF)
            }

        /** Decodes a `cordndev1…` code, or throws with the rule it broke. */
        fun decode(encoded: String): CordnHandoffCode {
            require(encoded.length <= MAX_LENGTH) {
                "a handoff code is longer than $MAX_LENGTH characters"
            }
            // Bech32.decode lowercases rather than rejecting mixed case, so the
            // check has to happen before the call.
            require(encoded == encoded.lowercase() || encoded == encoded.uppercase()) {
                "a handoff code must be all lowercase or all uppercase"
            }

            val (hrp, bytes, encoding) = Bech32.decodeBytes(encoded.lowercase(), false)
            require(hrp == HRP) { "not a $SUBJECT: prefix is '$hrp', expected '$HRP'" }
            // bech32, never bech32m — the same choice CordnGroupRef makes, so
            // one code never has two valid spellings.
            require(encoding == Bech32.Encoding.Bech32) { "a $SUBJECT must use bech32, not bech32m" }

            val tlv = CordnStrictTlv.parse(bytes, SUBJECT)

            val pubKeys = tlv[TLV_PUBKEY].orEmpty()
            require(pubKeys.size == 1) { "a $SUBJECT must carry exactly one ephemeral pubkey, found ${pubKeys.size}" }
            require(pubKeys[0].size == PUBKEY_SIZE) { "a $SUBJECT ephemeral pubkey must be $PUBKEY_SIZE bytes" }

            val dTags = tlv[TLV_D].orEmpty()
            require(dTags.size == 1) { "a $SUBJECT must carry exactly one d tag, found ${dTags.size}" }

            val writeKeys = tlv[TLV_WRITE_KEY].orEmpty()
            require(writeKeys.size <= 1) { "a $SUBJECT must carry at most one write key" }
            writeKeys.forEach { require(it.size == PUBKEY_SIZE) { "a $SUBJECT write key must be $PUBKEY_SIZE bytes" } }

            val kinds = tlv[TLV_KIND].orEmpty()
            require(kinds.size <= 1) { "a $SUBJECT must carry at most one kind" }

            return CordnHandoffCode(
                ephemeralPubKey = pubKeys[0].toHexKey(),
                dTag = CordnStrictTlv.utf8(dTags[0], SUBJECT, "d tag"),
                // An empty relay entry is a producer bug, not a reason to
                // refuse the whole code — the same reading CordnGroupRef takes.
                relays = tlv[TLV_RELAY].orEmpty().map { CordnStrictTlv.utf8(it, SUBJECT, "relay") }.filter { it.isNotEmpty() },
                kind = kinds.firstOrNull()?.toInt32() ?: CordnDeviceTip.OUTER_KIND,
                writeKey = writeKeys.firstOrNull()?.toHexKey(),
            )
        }

        /** Decodes, or null when [encoded] is not a well-formed handoff code. */
        fun decodeOrNull(encoded: String): CordnHandoffCode? =
            try {
                decode(encoded)
            } catch (e: Exception) {
                null
            }
    }
}
