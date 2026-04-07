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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Capabilities
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNodeSource
import com.vitorpamplona.quartz.marmot.mls.tree.Lifetime
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages KeyPackage creation and rotation lifecycle (MIP-00).
 *
 * Key responsibilities:
 * - Generate fresh KeyPackages for publishing
 * - Track consumed KeyPackages that need rotation after Welcome processing
 * - Provide bundles for group joins
 * - Handle periodic rotation for long-lived KeyPackages
 *
 * After a KeyPackage is consumed by a Welcome message:
 * 1. The init_key is effectively spent — cannot be reused
 * 2. A new KeyPackage MUST be published to the same d-tag slot
 * 3. The old KeyPackageBundle MUST be discarded
 *
 * Per MIP-00 spec, each user should maintain up to [KeyPackageUtils.MAX_SLOTS]
 * KeyPackage slots, rotating consumed ones promptly.
 */
class KeyPackageRotationManager {
    private val mutex = Mutex()
    private val activeBundles = mutableMapOf<String, KeyPackageBundle>()
    private val pendingRotations = mutableSetOf<String>()

    /**
     * Generate a new KeyPackage and its associated private bundle.
     *
     * @param identity the user's identity bytes (typically 32-byte Nostr pubkey)
     * @param dTagSlot the d-tag slot for addressable replacement
     * @return a [KeyPackageBundle] containing the KeyPackage and all private keys
     */
    fun generateKeyPackage(
        identity: ByteArray,
        dTagSlot: String = KeyPackageUtils.PRIMARY_SLOT,
    ): KeyPackageBundle {
        val initKp = X25519.generateKeyPair()
        val encKp = X25519.generateKeyPair()
        val sigKp = Ed25519.generateKeyPair()

        val leafNode =
            buildKeyPackageLeafNode(
                encryptionKey = encKp.publicKey,
                signatureKey = sigKp.publicKey,
                identity = identity,
                signingKey = sigKp.privateKey,
            )

        val unsigned =
            MlsKeyPackage(
                initKey = initKp.publicKey,
                leafNode = leafNode,
                signature = ByteArray(0),
            )
        val keyPackage =
            unsigned.copy(
                signature =
                    MlsCryptoProvider.signWithLabel(
                        sigKp.privateKey,
                        "KeyPackageTBS",
                        unsigned.encodeTbs(),
                    ),
            )

        val bundle = KeyPackageBundle(keyPackage, initKp.privateKey, encKp.privateKey, sigKp.privateKey)
        activeBundles[dTagSlot] = bundle
        return bundle
    }

    /**
     * Get the active bundle for a d-tag slot.
     * Used when processing a Welcome that references one of our KeyPackages.
     */
    fun getBundle(dTagSlot: String): KeyPackageBundle? = activeBundles[dTagSlot]

    /**
     * Find the bundle whose KeyPackage reference matches the given ref.
     * Used when we receive a Welcome and need to find the matching bundle.
     */
    fun findBundleByRef(keyPackageRef: ByteArray): KeyPackageBundle? =
        activeBundles.values.find { bundle ->
            bundle.keyPackage.reference().contentEquals(keyPackageRef)
        }

    /**
     * Mark a KeyPackage slot as consumed (used in a Welcome).
     * The slot will be included in [pendingRotationSlots] and should be
     * rotated by the caller.
     */
    fun markConsumed(dTagSlot: String) {
        activeBundles.remove(dTagSlot)
        pendingRotations.add(dTagSlot)
    }

    /**
     * Mark a slot as consumed by looking up the KeyPackage reference.
     */
    fun markConsumedByRef(keyPackageRef: ByteArray) {
        val entry =
            activeBundles.entries.find { (_, bundle) ->
                bundle.keyPackage.reference().contentEquals(keyPackageRef)
            }
        if (entry != null) {
            activeBundles.remove(entry.key)
            pendingRotations.add(entry.key)
        }
    }

    /**
     * Get the d-tag slots that need rotation (KeyPackage was consumed).
     */
    fun pendingRotationSlots(): Set<String> = pendingRotations.toSet()

    /**
     * Clear a slot from the pending rotation set after a new KeyPackage
     * has been published.
     */
    fun clearPendingRotation(dTagSlot: String) {
        pendingRotations.remove(dTagSlot)
    }

    /**
     * Check if any slots need rotation.
     */
    fun needsRotation(): Boolean = pendingRotations.isNotEmpty()

    /**
     * Check if there are any active (non-consumed) KeyPackage bundles.
     * Returns true if at least one slot has been generated and not yet consumed.
     */
    fun hasActiveKeyPackages(): Boolean = activeBundles.isNotEmpty()

    /**
     * Rotate a consumed slot: generate a new KeyPackage for the same d-tag.
     *
     * @param identity the user's identity bytes
     * @param dTagSlot the slot to rotate
     * @return the new [KeyPackageBundle] ready for publishing
     */
    fun rotateSlot(
        identity: ByteArray,
        dTagSlot: String,
    ): KeyPackageBundle {
        val bundle = generateKeyPackage(identity, dTagSlot)
        pendingRotations.remove(dTagSlot)
        return bundle
    }

    /**
     * Check if a KeyPackage should be proactively rotated based on age.
     *
     * MIP-00 recommends rotating KeyPackages periodically even if they
     * haven't been consumed, to limit the exposure window.
     *
     * @param createdAtSeconds the KeyPackage event's created_at timestamp
     * @param nowSeconds current time in seconds
     * @return true if the KeyPackage should be rotated
     */
    fun shouldRotateByAge(
        createdAtSeconds: Long,
        nowSeconds: Long,
    ): Boolean = (nowSeconds - createdAtSeconds) > MAX_KEY_PACKAGE_AGE_SECONDS

    private fun buildKeyPackageLeafNode(
        encryptionKey: ByteArray,
        signatureKey: ByteArray,
        identity: ByteArray,
        signingKey: ByteArray,
    ): LeafNode {
        val now = TimeUtils.now()
        val unsigned =
            LeafNode(
                encryptionKey = encryptionKey,
                signatureKey = signatureKey,
                credential = Credential.Basic(identity),
                capabilities = Capabilities(),
                leafNodeSource = LeafNodeSource.KEY_PACKAGE,
                lifetime = Lifetime(notBefore = now, notAfter = now + KEY_PACKAGE_LIFETIME_SECONDS),
                extensions = emptyList(),
                signature = ByteArray(0),
            )

        val tbs = unsigned.encodeTbs(groupId = null, leafIndex = null)
        val signature = MlsCryptoProvider.signWithLabel(signingKey, "LeafNodeTBS", tbs)
        return unsigned.copy(signature = signature)
    }

    companion object {
        /** KeyPackage lifetime: 30 days */
        const val KEY_PACKAGE_LIFETIME_SECONDS = 30L * 24 * 60 * 60

        /** Proactive rotation after 7 days even if not consumed */
        const val MAX_KEY_PACKAGE_AGE_SECONDS = 7L * 24 * 60 * 60
    }
}
