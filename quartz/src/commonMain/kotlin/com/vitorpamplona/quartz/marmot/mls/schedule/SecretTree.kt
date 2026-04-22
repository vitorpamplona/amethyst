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
    /** Per-sender ratchet state: (handshake generation, handshake secret, app generation, app secret) */
    private val senderState = mutableMapOf<Int, SenderRatchetState>()

    /** Consumed (sender, generation) pairs for replay detection on the APPLICATION ratchet. */
    private val consumedGenerations = mutableMapOf<Int, MutableSet<Int>>()

    /** Same replay tracker, but for the HANDSHAKE ratchet (commits / proposals). */
    private val consumedHandshakeGenerations = mutableMapOf<Int, MutableSet<Int>>()

    /**
     * Cache of key/nonce pairs for skipped APPLICATION generations.
     * Key: (leafIndex, generation) -> derived KeyNonceGeneration.
     */
    private val skippedKeys = mutableMapOf<Pair<Int, Int>, KeyNonceGeneration>()

    /** Same cache for the HANDSHAKE ratchet. */
    private val handshakeSkippedKeys = mutableMapOf<Pair<Int, Int>, KeyNonceGeneration>()

    private companion object {
        /** Maximum number of skipped key entries to retain (prevents unbounded memory growth). */
        const val MAX_SKIPPED_KEYS = 1000

        /** Maximum consumed generation entries to track per sender before pruning. */
        const val MAX_CONSUMED_GENERATIONS_PER_SENDER = 1000
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
     *
     * When fast-forwarding past intermediate generations, their key/nonce pairs
     * are cached in [skippedKeys] so that out-of-order messages arriving later
     * can still be decrypted.
     */
    fun applicationKeyNonceForGeneration(
        leafIndex: Int,
        generation: Int,
    ): KeyNonceGeneration {
        // Check skipped keys cache first (out-of-order message for a previously skipped generation)
        val cachedKey = skippedKeys.remove(Pair(leafIndex, generation))
        if (cachedKey != null) {
            // Still mark as consumed for replay detection
            val senderConsumed = consumedGenerations.getOrPut(leafIndex) { mutableSetOf() }
            require(generation !in senderConsumed) {
                "Replay detected: generation $generation from sender $leafIndex already consumed"
            }
            senderConsumed.add(generation)
            return cachedKey
        }

        val state = getOrInitSender(leafIndex)

        require(generation >= state.applicationGeneration) {
            "Generation $generation already consumed (current: ${state.applicationGeneration})"
        }

        // Replay detection: reject if this (sender, generation) was already used
        val senderConsumed = consumedGenerations.getOrPut(leafIndex) { mutableSetOf() }
        require(generation !in senderConsumed) {
            "Replay detected: generation $generation from sender $leafIndex already consumed"
        }
        senderConsumed.add(generation)

        // Prune consumed generations below the current minimum for this sender
        if (senderConsumed.size > MAX_CONSUMED_GENERATIONS_PER_SENDER) {
            val minGeneration = state.applicationGeneration
            senderConsumed.removeAll { it < minGeneration }
        }

        // Fast-forward the ratchet, caching intermediate key/nonce pairs
        var secret = state.applicationSecret
        var gen = state.applicationGeneration
        while (gen < generation) {
            // Save the intermediate generation's key/nonce for later out-of-order retrieval
            val intermediateKng = deriveKeyNonce(secret, gen)
            val cacheKey = Pair(leafIndex, gen)
            if (skippedKeys.size < MAX_SKIPPED_KEYS) {
                skippedKeys[cacheKey] = intermediateKng
            }
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
     * Handshake-ratchet counterpart to [applicationKeyNonceForGeneration].
     *
     * PrivateMessage commits / proposals (RFC 9420 §6.3.2) are encrypted
     * with a separate handshake ratchet per sender leaf — NOT the
     * application ratchet. openmls / mdk use this path by default
     * (`MIXED_CIPHERTEXT_WIRE_FORMAT_POLICY`), so quartz must also
     * ratchet-forward the handshake chain when decrypting an inbound
     * PrivateMessage commit.
     */
    fun handshakeKeyNonceForGeneration(
        leafIndex: Int,
        generation: Int,
    ): KeyNonceGeneration {
        val cachedKey = handshakeSkippedKeys.remove(Pair(leafIndex, generation))
        if (cachedKey != null) {
            val senderConsumed = consumedHandshakeGenerations.getOrPut(leafIndex) { mutableSetOf() }
            require(generation !in senderConsumed) {
                "Replay detected: handshake generation $generation from sender $leafIndex already consumed"
            }
            senderConsumed.add(generation)
            return cachedKey
        }

        val state = getOrInitSender(leafIndex)

        require(generation >= state.handshakeGeneration) {
            "Handshake generation $generation already consumed (current: ${state.handshakeGeneration})"
        }

        val senderConsumed = consumedHandshakeGenerations.getOrPut(leafIndex) { mutableSetOf() }
        require(generation !in senderConsumed) {
            "Replay detected: handshake generation $generation from sender $leafIndex already consumed"
        }
        senderConsumed.add(generation)

        if (senderConsumed.size > MAX_CONSUMED_GENERATIONS_PER_SENDER) {
            val minGeneration = state.handshakeGeneration
            senderConsumed.removeAll { it < minGeneration }
        }

        var secret = state.handshakeSecret
        var gen = state.handshakeGeneration
        while (gen < generation) {
            val intermediateKng = deriveKeyNonce(secret, gen)
            val cacheKey = Pair(leafIndex, gen)
            if (handshakeSkippedKeys.size < MAX_SKIPPED_KEYS) {
                handshakeSkippedKeys[cacheKey] = intermediateKng
            }
            secret = MlsCryptoProvider.expandWithLabel(secret, "secret", generationContext(gen), MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            gen++
        }

        val result = deriveKeyNonce(secret, generation)
        val nextSecret = MlsCryptoProvider.expandWithLabel(secret, "secret", generationContext(generation), MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        senderState[leafIndex] =
            state.copy(
                handshakeSecret = nextSecret,
                handshakeGeneration = generation + 1,
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
     * Derive the leaf secret from the encryption secret by walking DOWN the
     * binary tree from the root to the target leaf (RFC 9420 §9).
     *
     * At each step we pick left or right based on which subtree contains the
     * target. In an MLS left-balanced tree the left-subtree node indices are
     * always strictly less than the current node, and the right-subtree
     * indices strictly greater — so the direction is simply
     * `targetNode < currentNode`.
     *
     * This also correctly handles non-power-of-2 leaf counts (3, 5, 6, 7, 9
     * …) where the rightmost leaves live beneath "virtual" intermediate
     * nodes that are beyond `nodeCount`. Walking down still succeeds because
     * [BinaryTree.left] / [BinaryTree.right] give the correct descendants
     * even for virtual nodes, and the target leaf is reachable via a chain
     * of left/right steps that mixes real and virtual intermediates.
     *
     * The previous implementation derived the target's secret from
     * `BinaryTree.parent(target)` which, for leaves on the right edge of a
     * non-full tree, returned a high ancestor (often the root) that is NOT
     * the target's direct parent. Its `left()` and `right()` then pointed
     * at different nodes entirely, the target stayed un-cached, and the
     * follow-up `return treeSecrets[nodeIndex]!!` either threw NPE (on
     * JVM) or — when repeatedly re-entered — recursed into
     * [getNodeSecret] until the stack was exhausted (on ART).
     */
    private fun getLeafSecret(leafIndex: Int): ByteArray {
        val targetNode = BinaryTree.leafToNode(leafIndex)
        val rootIdx = BinaryTree.root(leafCount)
        var currentSecret = encryptionSecret
        var currentNode = rootIdx

        while (currentNode != targetNode) {
            val goLeft = targetNode < currentNode
            val label = if (goLeft) "left" else "right"
            currentSecret =
                MlsCryptoProvider.expandWithLabel(
                    currentSecret,
                    "tree",
                    label.encodeToByteArray(),
                    MlsCryptoProvider.HASH_OUTPUT_LENGTH,
                )
            currentNode = if (goLeft) BinaryTree.left(currentNode) else BinaryTree.right(currentNode)
        }

        return currentSecret
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
