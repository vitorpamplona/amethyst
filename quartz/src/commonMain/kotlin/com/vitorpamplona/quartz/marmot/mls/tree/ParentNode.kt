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
package com.vitorpamplona.quartz.marmot.mls.tree

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/**
 * MLS ParentNode (RFC 9420 Section 7.1).
 *
 * Internal node in the ratchet tree, containing:
 * - HPKE public key derived from the path secret
 * - Parent hash linking to the child node's state
 * - Unmerged leaves list (leaves added after this node was last updated)
 *
 * ```
 * struct {
 *     HPKEPublicKey encryption_key;
 *     opaque parent_hash<V>;
 *     uint32 unmerged_leaves<V>;
 * } ParentNode;
 * ```
 */
data class ParentNode(
    val encryptionKey: ByteArray,
    val parentHash: ByteArray,
    val unmergedLeaves: List<Int>,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaque2(encryptionKey)
        writer.putOpaque1(parentHash)

        val ulWriter = TlsWriter()
        for (leaf in unmergedLeaves) {
            ulWriter.putUint32(leaf.toLong())
        }
        writer.putOpaque4(ulWriter.toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParentNode) return false
        return encryptionKey.contentEquals(other.encryptionKey) &&
            parentHash.contentEquals(other.parentHash) &&
            unmergedLeaves == other.unmergedLeaves
    }

    override fun hashCode(): Int {
        var result = encryptionKey.contentHashCode()
        result = 31 * result + parentHash.contentHashCode()
        result = 31 * result + unmergedLeaves.hashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): ParentNode {
            val encryptionKey = reader.readOpaque2()
            val parentHash = reader.readOpaque1()
            val ulBytes = reader.readOpaque4()
            val ulReader = TlsReader(ulBytes)
            val unmergedLeaves = mutableListOf<Int>()
            while (ulReader.hasRemaining) {
                unmergedLeaves.add(ulReader.readUint32().toInt())
            }
            return ParentNode(encryptionKey, parentHash, unmergedLeaves)
        }
    }
}
