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
package com.vitorpamplona.quartz.marmot.mls.schedule

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.tree.BinaryTree

/**
 * MLS Secret Tree (RFC 9420 Section 9).
 *
 * Derives per-sender encryption keys and nonces from the epoch's
 * encryption_secret using a binary tree structure.
 *
 * The secret tree has the same shape as the ratchet tree. Starting from the
 * encryption_secret at the root, each node's secret is split into left and
 * right child secrets. The leaf secrets are then used to derive per-sender
 * handshake and application ratchets.
 *
 * Each sender maintains a generation counter. For each message sent,
 * the ratchet advances:
 *
 * ```
 * application_nonce_[N] = ExpandWithLabel(application_secret_[N], "nonce", "", nonce_len)
 * application_key_[N]   = ExpandWithLabel(application_secret_[N], "key", "", key_len)
 * application_secret_[N+1] = ExpandWithLabel(application_secret_[N], "secret", "", Hash.length)
 * ```
 */
class SecretTree(
    private val encryptionSecret: ByteArray,
    private val leafCount: Int,
) {
    /** Cached tree node secrets, computed lazily */
    private val treeSecrets = mutableMapOf<Int, ByteArray>()

    /** Per-sender ratchet state: (handshake generation, handshake secret, app generation, app secret) */
    private val senderState = mutableMapOf<Int, SenderRatchetState>()

    init {
        // Seed the root
        treeSecrets[BinaryTree.root(leafCount)] = encryptionSecret
    }

    /**
     * Get the next (key, nonce) for application messages from a given sender.
     */
    fun nextApplicationKeyNonce(leafIndex: Int): KeyNonceGeneration {
        val state = getOrInitSender(leafIndex)
        val result = deriveKeyNonce(state.applicationSecret, state.applicationGeneration)

        // Advance ratchet: DeriveTreeSecret(secret_[N], "secret", N, Nh)
        val nextSecret =
            MlsCryptoProvider.expandWithLabel(
                state.applicationSecret,
                "secret",
                generationContext(state.applicationGeneration),
                MlsCryptoProvider.HASH_OUTPUT_LENGTH,
            )
        senderState[leafIndex] =
            state.copy(
                applicationSecret = nextSecret,
                applicationGeneration = state.applicationGeneration + 1,
            )

        return result
    }

    /**
     * Get the next (key, nonce) for handshake messages from a given sender.
     */
    fun nextHandshakeKeyNonce(leafIndex: Int): KeyNonceGeneration {
        val state = getOrInitSender(leafIndex)
        val result = deriveKeyNonce(state.handshakeSecret, state.handshakeGeneration)

        val nextSecret =
            MlsCryptoProvider.expandWithLabel(
                state.handshakeSecret,
                "secret",
                generationContext(state.handshakeGeneration),
                MlsCryptoProvider.HASH_OUTPUT_LENGTH,
            )
        senderState[leafIndex] =
            state.copy(
                handshakeSecret = nextSecret,
                handshakeGeneration = state.handshakeGeneration + 1,
            )

        return result
    }

    /**
     * Get the (key, nonce) for a specific generation, consuming secrets up to that point.
     * Used when decrypting an out-of-order message.
     */
    fun applicationKeyNonceForGeneration(
        leafIndex: Int,
        generation: Int,
    ): KeyNonceGeneration {
        val state = getOrInitSender(leafIndex)

        require(generation >= state.applicationGeneration) {
            "Generation $generation already consumed (current: ${state.applicationGeneration})"
        }

        // Fast-forward the ratchet
        var secret = state.applicationSecret
        var gen = state.applicationGeneration
        while (gen < generation) {
            secret = MlsCryptoProvider.expandWithLabel(secret, "secret", generationContext(gen), MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            gen++
        }

        val result = deriveKeyNonce(secret, generation)

        // Advance past this generation
        val nextSecret = MlsCryptoProvider.expandWithLabel(secret, "secret", generationContext(generation), MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        senderState[leafIndex] =
            state.copy(
                applicationSecret = nextSecret,
                applicationGeneration = generation + 1,
            )

        return result
    }

    /**
     * Encode a generation counter as a 4-byte big-endian uint32 for DeriveTreeSecret context.
     */
    private fun generationContext(generation: Int): ByteArray =
        byteArrayOf(
            (generation shr 24).toByte(),
            (generation shr 16).toByte(),
            (generation shr 8).toByte(),
            generation.toByte(),
        )

    /**
     * DeriveTreeSecret(Secret, Label, Generation, Length) =
     *     ExpandWithLabel(Secret, Label, uint32(Generation), Length)
     */
    private fun deriveKeyNonce(
        secret: ByteArray,
        generation: Int,
    ): KeyNonceGeneration {
        val genCtx = generationContext(generation)
        val key =
            MlsCryptoProvider.expandWithLabel(
                secret,
                "key",
                genCtx,
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val nonce =
            MlsCryptoProvider.expandWithLabel(
                secret,
                "nonce",
                genCtx,
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )
        return KeyNonceGeneration(key, nonce, generation)
    }

    private fun getOrInitSender(leafIndex: Int): SenderRatchetState =
        senderState.getOrPut(leafIndex) {
            val leafSecret = getLeafSecret(leafIndex)
            val handshakeSecret =
                MlsCryptoProvider.expandWithLabel(
                    leafSecret,
                    "handshake",
                    ByteArray(0),
                    MlsCryptoProvider.HASH_OUTPUT_LENGTH,
                )
            val applicationSecret =
                MlsCryptoProvider.expandWithLabel(
                    leafSecret,
                    "application",
                    ByteArray(0),
                    MlsCryptoProvider.HASH_OUTPUT_LENGTH,
                )
            SenderRatchetState(handshakeSecret, 0, applicationSecret, 0)
        }

    /**
     * Derive the leaf secret from the encryption secret using the tree structure.
     */
    private fun getLeafSecret(leafIndex: Int): ByteArray {
        val nodeIndex = BinaryTree.leafToNode(leafIndex)
        return getNodeSecret(nodeIndex)
    }

    /**
     * Recursively derive a node's secret from its parent in the secret tree.
     */
    private fun getNodeSecret(nodeIndex: Int): ByteArray {
        treeSecrets[nodeIndex]?.let { return it }

        val parentIdx = BinaryTree.parent(nodeIndex, BinaryTree.nodeCount(leafCount))
        val parentSecret = getNodeSecret(parentIdx)

        // Derive left and right children secrets
        val leftIdx = BinaryTree.left(parentIdx)
        val rightIdx = BinaryTree.right(parentIdx)

        val leftSecret = MlsCryptoProvider.expandWithLabel(parentSecret, "tree", byteArrayOf(0), MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        val rightSecret = MlsCryptoProvider.expandWithLabel(parentSecret, "tree", byteArrayOf(1), MlsCryptoProvider.HASH_OUTPUT_LENGTH)

        treeSecrets[leftIdx] = leftSecret
        treeSecrets[rightIdx] = rightSecret

        // Clear parent secret for forward secrecy
        treeSecrets.remove(parentIdx)

        return treeSecrets[nodeIndex]!!
    }
}

data class SenderRatchetState(
    val handshakeSecret: ByteArray,
    val handshakeGeneration: Int,
    val applicationSecret: ByteArray,
    val applicationGeneration: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SenderRatchetState) return false
        return handshakeGeneration == other.handshakeGeneration &&
            applicationGeneration == other.applicationGeneration
    }

    override fun hashCode(): Int = 31 * handshakeGeneration + applicationGeneration
}

data class KeyNonceGeneration(
    val key: ByteArray,
    val nonce: ByteArray,
    val generation: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyNonceGeneration) return false
        return key.contentEquals(other.key) && nonce.contentEquals(other.nonce) && generation == other.generation
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + generation
        return result
    }
}
