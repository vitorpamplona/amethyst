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
package com.vitorpamplona.quartz.nip34Git.git

import java.security.MessageDigest
import java.util.zip.Inflater

/**
 * Parser for a git version-2 packfile (`PACK` magic). Inflates each object,
 * resolves `OBJ_OFS_DELTA` / `OBJ_REF_DELTA` chains, and computes each object's
 * SHA-1 oid so callers can look objects up by id.
 *
 * We never request thin packs, so every delta base is present inside the same
 * pack; `OFS_DELTA` bases always precede their delta, and `REF_DELTA` bases are
 * resolved through a pre-built oid index.
 */
object Packfile {
    private const val OBJ_COMMIT = 1
    private const val OBJ_TREE = 2
    private const val OBJ_BLOB = 3
    private const val OBJ_TAG = 4
    private const val OBJ_OFS_DELTA = 6
    private const val OBJ_REF_DELTA = 7

    private class Raw(
        val offset: Int,
        val type: Int,
        val data: ByteArray,
        val baseOffset: Int,
        val baseOid: String?,
    )

    /** Parses [pack] and returns the resolved objects indexed by their oid (hex). */
    fun parse(pack: ByteArray): Map<String, GitObject> {
        require(pack.size >= 12) { "packfile too short" }
        require(pack[0] == 'P'.code.toByte() && pack[1] == 'A'.code.toByte() && pack[2] == 'C'.code.toByte() && pack[3] == 'K'.code.toByte()) {
            "missing PACK signature"
        }
        val version = readUInt32(pack, 4)
        require(version == 2L || version == 3L) { "unsupported packfile version $version" }
        val count = readUInt32(pack, 8).toInt()

        val raws = ArrayList<Raw>(count)
        val byOffset = HashMap<Int, Raw>(count * 2)
        var p = 12
        repeat(count) {
            val start = p
            var b = pack[p++].toInt() and 0xFF
            val type = (b ushr 4) and 0x07
            var size = (b and 0x0F).toLong()
            var shift = 4
            while (b and 0x80 != 0) {
                b = pack[p++].toInt() and 0xFF
                size = size or ((b and 0x7F).toLong() shl shift)
                shift += 7
            }

            var baseOffset = -1
            var baseOid: String? = null
            when (type) {
                OBJ_OFS_DELTA -> {
                    b = pack[p++].toInt() and 0xFF
                    var rel = (b and 0x7F).toLong()
                    while (b and 0x80 != 0) {
                        b = pack[p++].toInt() and 0xFF
                        rel = ((rel + 1) shl 7) or (b and 0x7F).toLong()
                    }
                    baseOffset = (start - rel).toInt()
                }
                OBJ_REF_DELTA -> {
                    baseOid = toHex(pack, p, 20)
                    p += 20
                }
            }

            val (data, consumed) = inflate(pack, p, size.toInt())
            p += consumed

            val raw = Raw(start, type, data, baseOffset, baseOid)
            raws.add(raw)
            byOffset[start] = raw
        }

        return resolve(raws, byOffset)
    }

    private fun resolve(
        raws: List<Raw>,
        byOffset: Map<Int, Raw>,
    ): Map<String, GitObject> {
        val memo = HashMap<Int, GitObject>(raws.size * 2)
        val oidToOffset = HashMap<String, Int>(raws.size * 2)

        // Pre-index oids of non-delta objects so REF_DELTA bases can be found
        // regardless of pack ordering.
        for (r in raws) {
            if (r.type != OBJ_OFS_DELTA && r.type != OBJ_REF_DELTA) {
                oidToOffset[oidOf(baseType(r.type), r.data)] = r.offset
            }
        }

        fun resolveAt(offset: Int): GitObject {
            memo[offset]?.let { return it }
            val r = byOffset[offset] ?: throw IllegalStateException("delta base offset $offset not in pack")
            val obj =
                when (r.type) {
                    OBJ_OFS_DELTA -> {
                        val base = resolveAt(r.baseOffset)
                        val data = GitDelta.apply(base.data, r.data)
                        GitObject(base.type, data, oidOf(base.type, data))
                    }
                    OBJ_REF_DELTA -> {
                        val baseOffset =
                            oidToOffset[r.baseOid]
                                ?: throw IllegalStateException("REF_DELTA base ${r.baseOid} missing from pack")
                        val base = resolveAt(baseOffset)
                        val data = GitDelta.apply(base.data, r.data)
                        GitObject(base.type, data, oidOf(base.type, data))
                    }
                    else -> GitObject(baseType(r.type), r.data, oidOf(baseType(r.type), r.data))
                }
            memo[offset] = obj
            oidToOffset[obj.oid] = offset
            return obj
        }

        val result = HashMap<String, GitObject>(raws.size * 2)
        for (r in raws) {
            val obj = resolveAt(r.offset)
            result[obj.oid] = obj
        }
        return result
    }

    private fun baseType(type: Int): GitObjectType =
        when (type) {
            OBJ_COMMIT -> GitObjectType.COMMIT
            OBJ_TREE -> GitObjectType.TREE
            OBJ_BLOB -> GitObjectType.BLOB
            OBJ_TAG -> GitObjectType.TAG
            else -> throw IllegalArgumentException("not a base object type: $type")
        }

    private fun oidOf(
        type: GitObjectType,
        data: ByteArray,
    ): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("${type.header} ${data.size}".encodeToByteArray())
        digest.update(0)
        digest.update(data)
        return digest.digest().toHex()
    }

    private fun inflate(
        src: ByteArray,
        offset: Int,
        expectedSize: Int,
    ): Pair<ByteArray, Int> {
        val inflater = Inflater()
        try {
            inflater.setInput(src, offset, src.size - offset)
            val out = ByteArray(expectedSize)
            var got = 0
            // Inflate until the stream is fully consumed (not just until we have all
            // output bytes): the trailing adler32 checksum must be read too, otherwise
            // bytesRead() is short and the next packed object's offset is misaligned.
            val scratch = ByteArray(64)
            while (!inflater.finished()) {
                if (got < expectedSize) {
                    val n = inflater.inflate(out, got, expectedSize - got)
                    if (n == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        got += n
                    }
                } else {
                    val n = inflater.inflate(scratch, 0, scratch.size)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                }
            }
            if (got != expectedSize) {
                throw IllegalStateException("inflate short read: $got of $expectedSize")
            }
            return out to inflater.bytesRead.toInt()
        } finally {
            inflater.end()
        }
    }

    private fun readUInt32(
        data: ByteArray,
        offset: Int,
    ): Long {
        var v = 0L
        for (i in 0 until 4) v = (v shl 8) or (data[offset + i].toLong() and 0xFF)
        return v
    }

    private fun toHex(
        data: ByteArray,
        offset: Int,
        len: Int,
    ): String {
        val sb = StringBuilder(len * 2)
        for (i in 0 until len) {
            val v = data[offset + i].toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
