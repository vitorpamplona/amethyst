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
package com.vitorpamplona.amethyst.commons.prodbench.nip19

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.utils.internIfPossible

/**
 * Prototype of a single-pass Bech32 + TLV codec for quartz's `nip19Bech32` package, kept here
 * only so [Nip19Benchmark] can measure it against the published one. Pure common Kotlin (no JVM
 * API), so it can move into quartz's `commonMain` as is.
 *
 * What it does differently from quartz 2.1.0's `Bech32` / `TlvBuilder` / `Tlv`:
 *
 * - **No boxing.** 5-bit groups are never stored: they are produced, fed to the checksum and
 *   written as a char in the same step. Quartz builds `ArrayList<Byte>` / `Array<Byte>` (an
 *   `Object[]` of boxed `java.lang.Byte`), then copies them again for `expand(hrp) + data`.
 * - **One output buffer.** The exact length is known up front, so the string is written into a
 *   single ASCII `ByteArray`, including any `nostr:` prefix. Quartz concatenates `hrp + "1" + data`
 *   and the caller then adds `"nostr:" + …`: two more full copies.
 * - **Table polymod.** One lookup into a 32-entry table per step instead of five branches.
 * - **Exact-size TLV.** The TLV is sized first and written into one `ByteArray`; hex values are
 *   decoded straight into it and ASCII strings copied without `encodeToByteArray()`.
 * - **TLV read in place.** Decoding walks the records once, hex-encoding and UTF-8-decoding the
 *   slices directly, instead of building a `Map<Byte, List<ByteArray>>` of copies.
 *
 * Output is byte-for-byte identical to quartz; [Nip19Benchmark] asserts that for every entity.
 */
object FastBech32 {
    private const val ALPHABET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    const val BECH32_CONST = 1
    const val BECH32M_CONST = 0x2bc830a3

    private val ALPHABET_BYTES = ByteArray(32) { ALPHABET[it].code.toByte() }

    // GEN_TABLE[b] is the xor of the generator constants for each bit set in the 5-bit value b.
    private val GEN_TABLE =
        IntArray(32) { b ->
            val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
            var x = 0
            for (i in 0..4) if ((b shr i) and 1 != 0) x = x xor gen[i]
            x
        }

    // char code -> 5-bit value, or -1. Both cases map, as in quartz.
    private val CHAR_TO_INT5 =
        ByteArray(128) { -1 }.apply {
            for (i in ALPHABET.indices) {
                this[ALPHABET[i].code] = i.toByte()
                this[ALPHABET[i].uppercaseChar().code] = i.toByte()
            }
        }

    private const val HEX = "0123456789abcdef"
    private val HEX_BYTES = ByteArray(16) { HEX[it].code.toByte() }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun step(
        chk: Int,
        v: Int,
    ): Int = ((chk and 0x1ffffff) shl 5) xor v xor GEN_TABLE[chk ushr 25]

    /** The checksum state after the expanded [hrp], as `polymod(expand(hrp), …)` starts. */
    fun hrpSeed(hrp: String): Int {
        var chk = 1
        for (i in hrp.indices) chk = step(chk, hrp[i].code shr 5)
        chk = step(chk, 0)
        for (i in hrp.indices) chk = step(chk, hrp[i].code and 31)
        return chk
    }

    fun encodedLength(
        prefix: String,
        hrp: String,
        dataBytes: Int,
    ): Int = prefix.length + hrp.length + 1 + (dataBytes * 8 + 4) / 5 + 6

    /**
     * Encodes `[offset, offset + length)` of [data] as `prefix + hrp + "1" + data + checksum`.
     * [hrp] must be lowercase ASCII (every NIP-19 prefix is). [prefix] is copied verbatim
     * (`"nostr:"` for a NIP-21 uri, `""` for a bare entity).
     */
    fun encode(
        hrp: String,
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
        prefix: String = "",
        encodingConst: Int = BECH32_CONST,
    ): String {
        val out = ByteArray(encodedLength(prefix, hrp, length))
        var p = writePrefix(out, prefix, hrp)
        var chk = hrpSeed(hrp)

        var acc = 0
        var bits = 0
        val end = offset + length
        for (i in offset until end) {
            acc = (acc shl 8) or (data[i].toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                val v = (acc ushr bits) and 31
                chk = step(chk, v)
                out[p++] = ALPHABET_BYTES[v]
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (bits > 0) {
            val v = (acc shl (5 - bits)) and 31
            chk = step(chk, v)
            out[p++] = ALPHABET_BYTES[v]
        }

        writeChecksum(out, p, chk, encodingConst)
        return out.decodeToString()
    }

    /**
     * Encodes a hex string (a 32-byte key or id) without first decoding it into a `ByteArray`:
     * nibbles go straight into the 5-bit regrouping.
     */
    fun encodeHex(
        hrp: String,
        hex: String,
        prefix: String = "",
    ): String {
        require(hex.length and 1 == 0) { "odd hex length" }
        val out = ByteArray(encodedLength(prefix, hrp, hex.length / 2))
        var p = writePrefix(out, prefix, hrp)
        var chk = hrpSeed(hrp)

        var acc = 0
        var bits = 0
        for (i in hex.indices) {
            acc = (acc shl 4) or hexNibble(hex[i])
            bits += 4
            if (bits >= 5) {
                bits -= 5
                val v = (acc ushr bits) and 31
                chk = step(chk, v)
                out[p++] = ALPHABET_BYTES[v]
                acc = acc and ((1 shl bits) - 1)
            }
        }
        if (bits > 0) {
            val v = (acc shl (5 - bits)) and 31
            chk = step(chk, v)
            out[p++] = ALPHABET_BYTES[v]
        }

        writeChecksum(out, p, chk, BECH32_CONST)
        return out.decodeToString()
    }

    private fun writePrefix(
        out: ByteArray,
        prefix: String,
        hrp: String,
    ): Int {
        var p = 0
        for (i in prefix.indices) out[p++] = prefix[i].code.toByte()
        for (i in hrp.indices) out[p++] = hrp[i].code.toByte()
        out[p++] = '1'.code.toByte()
        return p
    }

    private fun writeChecksum(
        out: ByteArray,
        start: Int,
        chkIn: Int,
        encodingConst: Int,
    ) {
        var chk = chkIn
        repeat(6) { chk = step(chk, 0) }
        chk = chk xor encodingConst
        var p = start
        for (i in 0 until 6) out[p++] = ALPHABET_BYTES[(chk ushr (5 * (5 - i))) and 31]
    }

    private val HEX_TO_INT =
        IntArray(128) { -1 }.apply {
            for (i in 0..9) this['0'.code + i] = i
            for (i in 0..5) {
                this['a'.code + i] = 10 + i
                this['A'.code + i] = 10 + i
            }
        }

    fun hexNibble(c: Char): Int {
        val v = if (c.code < 128) HEX_TO_INT[c.code] else -1
        if (v < 0) throw IllegalArgumentException("invalid hex char $c")
        return v
    }

    /**
     * Decodes [bech32] (`hrp1data+checksum`, either case) to its data bytes, or null when it is
     * not valid Bech32/Bech32m or its hrp is not [expectedHrp] (case-insensitive). One pass:
     * the checksum and the 5→8 regrouping run over the same chars, into an exact-size array.
     *
     * Same acceptance as quartz's `Bech32.decodeBytes`: chars outside `33..126` are ignored
     * (quartz filters them first), and the separator is the last `1`.
     */
    fun decode(
        bech32: String,
        expectedHrp: String? = null,
        start: Int = 0,
        end: Int = bech32.length,
    ): ByteArray? {
        for (i in start until end) {
            val c = bech32[i].code
            if (c < 33 || c > 126) {
                // Rare: whitespace or invisible chars inside a pasted entity. Match quartz by
                // dropping them, then decode the clean copy.
                val clean = bech32.substring(start, end).filter { it.code in 33..126 }
                return decode(clean, expectedHrp)
            }
        }

        val sep = bech32.lastIndexOf('1', end - 1)
        if (sep < start + 1) return null
        val hrpLen = sep - start
        if (hrpLen > 83) return null
        if (expectedHrp != null) {
            if (expectedHrp.length != hrpLen) return null
            for (k in 0 until hrpLen) {
                if (lowerAscii(bech32[start + k]) != expectedHrp[k]) return null
            }
        }

        val dataChars = end - sep - 1
        if (dataChars < 6) return null
        val payloadChars = dataChars - 6

        var chk = 1
        for (k in 0 until hrpLen) chk = step(chk, lowerAscii(bech32[start + k]).code shr 5)
        chk = step(chk, 0)
        for (k in 0 until hrpLen) chk = step(chk, lowerAscii(bech32[start + k]).code and 31)

        val out = ByteArray(payloadChars * 5 / 8)
        var o = 0
        var acc = 0
        var bits = 0
        var i = sep + 1
        val payloadEnd = sep + 1 + payloadChars
        while (i < payloadEnd) {
            val v = CHAR_TO_INT5[bech32[i].code].toInt()
            if (v < 0) return null
            chk = step(chk, v)
            acc = ((acc shl 5) or v) and 0xfff
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[o++] = (acc ushr bits).toByte()
            }
            i++
        }
        // Same padding rules as quartz's five2eight: at most 4 bits, all zero.
        if (bits > 4) return null
        if (acc and ((1 shl bits) - 1) != 0) return null

        while (i < end) {
            val v = CHAR_TO_INT5[bech32[i].code].toInt()
            if (v < 0) return null
            chk = step(chk, v)
            i++
        }
        if (chk != BECH32_CONST && chk != BECH32M_CONST) return null
        return out
    }

    private fun lowerAscii(c: Char): Char = if (c in 'A'..'Z') c + 32 else c

    /** Lowercase hex of `[from, to)` of [bytes], without copying the slice out. */
    fun hex(
        bytes: ByteArray,
        from: Int,
        to: Int,
    ): String {
        val out = ByteArray((to - from) * 2)
        var p = 0
        for (i in from until to) {
            val b = bytes[i].toInt()
            out[p++] = HEX_BYTES[(b ushr 4) and 0xf]
            out[p++] = HEX_BYTES[b and 0xf]
        }
        return out.decodeToString()
    }
}

/**
 * Exact-size TLV writer: the caller declares the total size with the static `*Size` helpers,
 * then fills it. Hex is decoded straight into the buffer, ASCII copied char by char.
 *
 * Lengths over 255 bytes are written as `length.toByte()` exactly like quartz's `TlvBuilder`, so
 * the outputs stay identical; that truncation is a pre-existing bug (see the benchmark notes).
 */
class FastTlvWriter(
    size: Int,
) {
    val buf = ByteArray(size)
    var pos = 0

    fun hex(
        type: Byte,
        hex: String,
    ) {
        val n = hex.length / 2
        buf[pos++] = type
        buf[pos++] = n.toByte()
        var i = 0
        while (i < hex.length) {
            buf[pos++] = ((FastBech32.hexNibble(hex[i]) shl 4) or FastBech32.hexNibble(hex[i + 1])).toByte()
            i += 2
        }
    }

    fun string(
        type: Byte,
        s: String,
        utf8Len: Int,
    ) {
        buf[pos++] = type
        buf[pos++] = utf8Len.toByte()
        if (utf8Len == s.length) {
            for (i in s.indices) buf[pos++] = s[i].code.toByte()
        } else {
            val bytes = s.encodeToByteArray()
            bytes.copyInto(buf, pos)
            pos += bytes.size
        }
    }

    fun int32(
        type: Byte,
        v: Int,
    ) {
        buf[pos++] = type
        buf[pos++] = 4
        buf[pos++] = (v ushr 24).toByte()
        buf[pos++] = (v ushr 16).toByte()
        buf[pos++] = (v ushr 8).toByte()
        buf[pos++] = v.toByte()
    }

    companion object {
        /** UTF-8 byte length of [s] without encoding it. */
        fun utf8Length(s: String): Int {
            var n = 0
            var i = 0
            while (i < s.length) {
                val c = s[i].code
                when {
                    c < 0x80 -> n += 1
                    c < 0x800 -> n += 2
                    c in 0xD800..0xDBFF && i + 1 < s.length && s[i + 1].code in 0xDC00..0xDFFF -> {
                        n += 4
                        i++
                    }
                    else -> n += 3
                }
                i++
            }
            return n
        }
    }
}

/** NIP-19 encoders/decoders on top of [FastBech32] and [FastTlvWriter], same outputs as quartz. */
object FastNip19 {
    private const val SPECIAL: Byte = 0
    private const val RELAY: Byte = 1
    private const val AUTHOR: Byte = 2
    private const val KIND: Byte = 3

    const val NOSTR = "nostr:"

    fun npub(
        hex: String,
        prefix: String = "",
    ) = FastBech32.encodeHex("npub", hex, prefix)

    fun nsec(hex: String) = FastBech32.encodeHex("nsec", hex)

    fun note(hex: String) = FastBech32.encodeHex("note", hex)

    fun nevent(
        idHex: String,
        author: String?,
        kind: Int?,
        relay: NormalizedRelayUrl?,
        prefix: String = "",
    ): String {
        val relayLen = relay?.let { FastTlvWriter.utf8Length(it.url) } ?: -1
        var size = 2 + idHex.length / 2
        if (relayLen >= 0) size += 2 + relayLen
        if (author != null) size += 2 + author.length / 2
        if (kind != null) size += 6
        val w = FastTlvWriter(size)
        w.hex(SPECIAL, idHex)
        if (relay != null) w.string(RELAY, relay.url, relayLen)
        if (author != null) w.hex(AUTHOR, author)
        if (kind != null) w.int32(KIND, kind)
        return FastBech32.encode("nevent", w.buf, prefix = prefix)
    }

    fun nprofile(
        hex: String,
        relays: List<NormalizedRelayUrl>,
        prefix: String = "",
    ): String {
        var size = 2 + hex.length / 2
        val lens = IntArray(relays.size)
        for (i in relays.indices) {
            lens[i] = FastTlvWriter.utf8Length(relays[i].url)
            size += 2 + lens[i]
        }
        val w = FastTlvWriter(size)
        w.hex(SPECIAL, hex)
        for (i in relays.indices) w.string(RELAY, relays[i].url, lens[i])
        return FastBech32.encode("nprofile", w.buf, prefix = prefix)
    }

    fun naddr(
        kind: Int,
        pubKeyHex: String,
        dTag: String,
        relay: NormalizedRelayUrl?,
        prefix: String = "",
    ): String {
        val dLen = FastTlvWriter.utf8Length(dTag)
        // quartz uses addStringIfNotBlank for the relay
        val relayUrl = relay?.url?.takeIf { it.isNotBlank() }
        val relayLen = relayUrl?.let { FastTlvWriter.utf8Length(it) } ?: -1
        var size = 2 + dLen + 2 + pubKeyHex.length / 2 + 6
        if (relayLen >= 0) size += 2 + relayLen
        val w = FastTlvWriter(size)
        w.string(SPECIAL, dTag, dLen)
        if (relayUrl != null) w.string(RELAY, relayUrl, relayLen)
        w.hex(AUTHOR, pubKeyHex)
        w.int32(KIND, kind)
        return FastBech32.encode("naddr", w.buf, prefix = prefix)
    }

    // ------------------------------------------------------------------------------- decoding

    /** Walks [tlv] once, calling [onRecord] with (type, valueStart, valueEnd) for each record. */
    private inline fun forEachTlv(
        tlv: ByteArray,
        onRecord: (type: Byte, from: Int, to: Int) -> Unit,
    ) {
        var pos = 0
        while (pos + 2 <= tlv.size) {
            val t = tlv[pos]
            val l = tlv[pos + 1].toInt() and 0xff
            if (pos + 2 + l > tlv.size) break
            onRecord(t, pos + 2, pos + 2 + l)
            pos += 2 + l
        }
    }

    private fun int32(
        b: ByteArray,
        from: Int,
    ): Int =
        ((b[from].toInt() and 0xff) shl 24) or
            ((b[from + 1].toInt() and 0xff) shl 16) or
            ((b[from + 2].toInt() and 0xff) shl 8) or
            (b[from + 3].toInt() and 0xff)

    fun parseNEvent(tlv: ByteArray): NEvent? {
        if (tlv.isEmpty()) return null
        var hex: String? = null
        var author: String? = null
        var authorSeen = false
        var kind: Int? = null
        var kindSeen = false
        var relays: ArrayList<NormalizedRelayUrl>? = null
        forEachTlv(tlv) { t, from, to ->
            when (t) {
                SPECIAL -> if (hex == null) hex = FastBech32.hex(tlv, from, to).internIfPossible()
                RELAY -> {
                    RelayUrlNormalizer.normalizeHintOrNull(tlv.decodeToString(from, to))?.let {
                        (relays ?: ArrayList<NormalizedRelayUrl>(2).also { relays = it }).add(it)
                    }
                }
                AUTHOR ->
                    if (!authorSeen) {
                        authorSeen = true
                        if (to - from == 32) author = FastBech32.hex(tlv, from, to).internIfPossible()
                    }
                KIND ->
                    if (!kindSeen) {
                        kindSeen = true
                        if (to - from == 4) kind = int32(tlv, from).takeIf { it in 0..65535 }
                    }
            }
        }
        val id = hex ?: return null
        if (id.length != 64) return null
        return NEvent(id, relays ?: emptyList(), author, kind)
    }

    fun parseNProfile(tlv: ByteArray): NProfile? {
        if (tlv.isEmpty()) return null
        var hex: String? = null
        var relays: ArrayList<NormalizedRelayUrl>? = null
        forEachTlv(tlv) { t, from, to ->
            when (t) {
                SPECIAL -> if (hex == null) hex = FastBech32.hex(tlv, from, to).internIfPossible()
                RELAY ->
                    RelayUrlNormalizer.normalizeHintOrNull(tlv.decodeToString(from, to))?.let {
                        (relays ?: ArrayList<NormalizedRelayUrl>(3).also { relays = it }).add(it)
                    }
            }
        }
        val pk = hex ?: return null
        if (pk.length != 64) return null
        return NProfile(pk, relays ?: emptyList())
    }

    fun parseNAddress(tlv: ByteArray): NAddress? {
        if (tlv.isEmpty()) return null
        var d: String? = null
        var author: String? = null
        var kind: Int? = null
        var kindSeen = false
        var relays: ArrayList<NormalizedRelayUrl>? = null
        forEachTlv(tlv) { t, from, to ->
            when (t) {
                SPECIAL -> if (d == null) d = tlv.decodeToString(from, to)
                RELAY ->
                    RelayUrlNormalizer.normalizeHintOrNull(tlv.decodeToString(from, to))?.let {
                        (relays ?: ArrayList<NormalizedRelayUrl>(2).also { relays = it }).add(it)
                    }
                AUTHOR -> if (author == null) author = FastBech32.hex(tlv, from, to).internIfPossible()
                KIND ->
                    if (!kindSeen) {
                        kindSeen = true
                        if (to - from == 4) kind = int32(tlv, from)
                    }
            }
        }
        val pk = author ?: return null
        val k = kind ?: return null
        if (pk.length != 64) return null
        if (k !in 0..65535) return null
        return NAddress(k, pk, d ?: "", relays ?: emptyList())
    }
}
