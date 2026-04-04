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
package com.vitorpamplona.quartz.marmot.mls.messages

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import com.vitorpamplona.quartz.marmot.mls.tree.UpdatePathNode

/**
 * MLS Commit (RFC 9420 Section 12.4).
 *
 * A Commit message applies a set of proposals and optionally updates
 * the committer's path in the ratchet tree (UpdatePath).
 *
 * ```
 * struct {
 *     ProposalOrRef proposals<V>;
 *     optional<UpdatePath> path;
 * } Commit;
 * ```
 */
data class Commit(
    val proposals: List<ProposalOrRef>,
    val updatePath: UpdatePath?,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putVectorVarInt(proposals)
        writer.putOptional(updatePath)
    }

    companion object {
        fun decodeTls(reader: TlsReader): Commit =
            Commit(
                proposals = reader.readVectorVarInt { ProposalOrRef.decodeTls(it) },
                updatePath = reader.readOptional { UpdatePath.decodeTls(it) },
            )
    }
}

/**
 * MLS UpdatePath (RFC 9420 Section 12.4.1).
 *
 * Sent by the committer to update their direct path in the ratchet tree.
 * Contains the new LeafNode and encrypted path secrets for each
 * node on the direct path.
 *
 * ```
 * struct {
 *     LeafNode leaf_node;
 *     UpdatePathNode nodes<V>;
 * } UpdatePath;
 * ```
 */
data class UpdatePath(
    val leafNode: LeafNode,
    val nodes: List<UpdatePathNode>,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        leafNode.encodeTls(writer)
        writer.putVectorVarInt(nodes)
    }

    companion object {
        fun decodeTls(reader: TlsReader): UpdatePath =
            UpdatePath(
                leafNode = LeafNode.decodeTls(reader),
                nodes = reader.readVectorVarInt { UpdatePathNode.decodeTls(it) },
            )
    }
}

/**
 * Result of creating a Commit: the MLS messages to distribute.
 */
data class CommitResult(
    val commitBytes: ByteArray,
    val welcomeBytes: ByteArray?,
    val groupInfoBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommitResult) return false
        return commitBytes.contentEquals(other.commitBytes)
    }

    override fun hashCode(): Int = commitBytes.contentHashCode()
}
