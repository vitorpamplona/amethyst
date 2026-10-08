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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users.index

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex

/**
 * Where an index came from and how far its sync has got. Stored in the index file's header so
 * a provider change (a new 10040, a re-registration) is detected on load.
 */
data class TrustNetworkHeader(
    val provider: HexKey,
    val relay: String,
    /** Newest `created_at` applied; the next small update asks for `since` this (minus overlap). */
    val syncCursor: Long,
    /** When the last full check (or cold sync) finished, unix seconds. */
    val lastFullCheck: Long,
    /** When the last small update finished, unix seconds. */
    val lastUpdate: Long,
    /**
     * Cards held (entries and tombstones) created at or after [syncCursor], or -1 when unknown.
     * Lets an update compare it with the relay's count without reading the ids file.
     */
    val heldAtCursor: Int = -1,
)

/**
 * Binary formats for the two files of a trust network: big-endian, fixed-width sections, so
 * a platform can also memory-map and binary-search the key section in place.
 *
 * Index file (`network-v1.bin`):
 * ```
 * "AWOT" | u16 version | 32B provider | u16 relayLen + utf8 relay
 * | i64 syncCursor | i64 lastFullCheck | i64 lastUpdate | i32 heldAtCursor | i32 N
 * | keys N×16 | rank N×1 | hops N×1 | followers N×4
 * ```
 * Ids file (`network-ids-v1.bin`), aligned with the index, then the tombstones:
 * ```
 * "AWID" | u16 version | i32 N | ids N×32 | createdAt N×8
 * | i32 T | keys T×16 | ids T×32 | createdAt T×8
 * ```
 */
object TrustNetworkCodec {
    /** Version 2 added `heldAtCursor`; a version 1 file is ignored (and re-downloaded). */
    const val VERSION = 2

    /** The ids file gained tombstones in version 2; a version 1 file is ignored (and re-downloaded). */
    const val IDS_VERSION = 2
    private val INDEX_MAGIC = "AWOT".encodeToByteArray()
    private val IDS_MAGIC = "AWID".encodeToByteArray()

    fun encodeIndex(
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
    ): ByteArray {
        val relay = header.relay.encodeToByteArray()
        val n = index.size
        val out = Writer(4 + 2 + 32 + 2 + relay.size + 8 * 3 + 4 + 4 + n * 22)
        out.bytes(INDEX_MAGIC)
        out.short(VERSION)
        out.bytes(Hex.decode(header.provider))
        out.short(relay.size)
        out.bytes(relay)
        out.long(header.syncCursor)
        out.long(header.lastFullCheck)
        out.long(header.lastUpdate)
        out.int(header.heldAtCursor)
        out.int(n)
        for (v in index.keys) out.long(v)
        out.bytes(index.rank)
        out.bytes(index.hops)
        for (v in index.followers) out.int(v)
        return out.buffer
    }

    /** Reads only the header: cheap, for checking which provider a file belongs to. */
    fun decodeHeader(bytes: ByteArray): TrustNetworkHeader? = runCatching { Reader(bytes).header() }.getOrNull()

    /** Null when the bytes are not a complete, readable index of this version. */
    fun decodeIndex(bytes: ByteArray): Pair<TrustNetworkHeader, TrustNetworkIndex>? =
        runCatching {
            val reader = Reader(bytes)
            val header = reader.header()
            val n = reader.int()
            require(n >= 0 && reader.remaining() == n * 22) { "truncated index" }
            val keys = LongArray(2 * n) { reader.long() }
            val rank = reader.bytes(n)
            val hops = reader.bytes(n)
            val followers = IntArray(n) { reader.int() }
            header to TrustNetworkIndex(keys, rank, hops, followers)
        }.getOrNull()

    fun encodeIds(ids: TrustNetworkIds): ByteArray {
        val n = ids.size
        val t = ids.tombstones
        val out = Writer(4 + 2 + 4 + n * 40 + 4 + t * 56)
        out.bytes(IDS_MAGIC)
        out.short(IDS_VERSION)
        out.int(n)
        out.bytes(ids.ids)
        for (v in ids.createdAt) out.long(v)
        out.int(t)
        for (v in ids.tombstoneKeys) out.long(v)
        out.bytes(ids.tombstoneIds)
        for (v in ids.tombstoneCreatedAt) out.long(v)
        return out.buffer
    }

    fun decodeIds(bytes: ByteArray): TrustNetworkIds? =
        runCatching {
            val reader = Reader(bytes)
            require(reader.bytes(4).contentEquals(IDS_MAGIC)) { "not an ids file" }
            require(reader.short() == IDS_VERSION) { "unknown version" }
            val n = reader.int()
            require(n >= 0 && reader.remaining() >= n * 40 + 4) { "truncated ids" }
            val idBytes = reader.bytes(32 * n)
            val createdAt = LongArray(n) { reader.long() }
            val t = reader.int()
            require(t >= 0 && reader.remaining() == t * 56) { "truncated tombstones" }
            val graveKeys = LongArray(2 * t) { reader.long() }
            val graveIds = reader.bytes(32 * t)
            val graveCreatedAt = LongArray(t) { reader.long() }
            TrustNetworkIds(idBytes, createdAt, graveKeys, graveIds, graveCreatedAt)
        }.getOrNull()

    private class Writer(
        size: Int,
    ) {
        val buffer = ByteArray(size)
        private var pos = 0

        fun bytes(value: ByteArray) {
            value.copyInto(buffer, pos)
            pos += value.size
        }

        fun short(value: Int) {
            buffer[pos++] = (value ushr 8).toByte()
            buffer[pos++] = value.toByte()
        }

        fun int(value: Int) {
            buffer[pos++] = (value ushr 24).toByte()
            buffer[pos++] = (value ushr 16).toByte()
            buffer[pos++] = (value ushr 8).toByte()
            buffer[pos++] = value.toByte()
        }

        fun long(value: Long) {
            for (shift in 56 downTo 0 step 8) buffer[pos++] = (value ushr shift).toByte()
        }
    }

    private class Reader(
        val buffer: ByteArray,
    ) {
        private var pos = 0

        fun remaining() = buffer.size - pos

        fun bytes(n: Int): ByteArray {
            require(n <= remaining()) { "truncated" }
            return buffer.copyOfRange(pos, pos + n).also { pos += n }
        }

        fun short(): Int {
            require(remaining() >= 2) { "truncated" }
            return ((buffer[pos++].toInt() and 0xFF) shl 8) or (buffer[pos++].toInt() and 0xFF)
        }

        fun int(): Int {
            require(remaining() >= 4) { "truncated" }
            var v = 0
            repeat(4) { v = (v shl 8) or (buffer[pos++].toInt() and 0xFF) }
            return v
        }

        fun long(): Long {
            require(remaining() >= 8) { "truncated" }
            var v = 0L
            repeat(8) { v = (v shl 8) or (buffer[pos++].toLong() and 0xFF) }
            return v
        }

        fun header(): TrustNetworkHeader {
            require(bytes(4).contentEquals(INDEX_MAGIC)) { "not an index file" }
            require(short() == VERSION) { "unknown version" }
            val provider = Hex.encode(bytes(32))
            val relay = bytes(short()).decodeToString()
            return TrustNetworkHeader(provider, relay, syncCursor = long(), lastFullCheck = long(), lastUpdate = long(), heldAtCursor = int())
        }
    }
}
