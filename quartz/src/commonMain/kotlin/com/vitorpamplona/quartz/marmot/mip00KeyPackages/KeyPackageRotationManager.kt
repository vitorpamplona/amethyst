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

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
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
class KeyPackageRotationManager(
    private val store: KeyPackageBundleStore? = null,
) {
    private val mutex = Mutex()
    private val activeBundles = mutableMapOf<String, KeyPackageBundle>()
    private val pendingRotations = mutableSetOf<String>()

    /**
     * Maps the Nostr event id (kind:30443) of a published KeyPackage to the
     * d-tag slot whose bundle backs it. The welcome event references its
     * consumed KeyPackage by Nostr event id (the MIP-02 "e" tag), but
     * [activeBundles] is keyed by d-tag slot — so we need this side index
     * to recover the matching bundle on the receive side.
     *
     * Populated by [recordPublishedEventId], which is called right after
     * the kind:30443 event is signed (the event id is only known then).
     * Also persisted in the snapshot so it survives app restart.
     */
    private val eventIdToSlot = mutableMapOf<String, String>()

    /**
     * Restore previously persisted bundles + rotation state from [store].
     * Call once at startup before any other use of this manager.
     * Safe to call when no store is configured (no-op in that case).
     *
     * NOTE: v1 snapshots are deliberately NOT loaded. v1 predates the
     * `eventIdToSlot` index, so any bundles it holds would be unreachable
     * to the Welcome path (which looks up bundles by Nostr event id). The
     * cleanest upgrade is to discard the v1 state, let
     * `Account.ensureMarmotKeyPackagePublished` regenerate + republish
     * fresh KeyPackages, and rely on d-tag replacement on relays to
     * replace the stale kind:30443 events.
     */
    suspend fun restoreFromStore() {
        val store = store ?: return
        val bytes =
            try {
                store.load()
            } catch (e: Exception) {
                Log.w("KeyPackageRotationManager", "Failed to load persisted KeyPackages: ${e.message}")
                null
            } ?: return
        try {
            val decoded = decodeSnapshot(bytes)
            if (decoded == null) {
                Log.w("KeyPackageRotationManager") {
                    "Discarding legacy v1 KeyPackage snapshot — bundles will be regenerated and republished"
                }
                // Drop any state the caller may have set and force
                // `ensureMarmotKeyPackagePublished` to publish fresh ones.
                try {
                    store.delete()
                } catch (e: Exception) {
                    Log.w("KeyPackageRotationManager", "Failed to delete legacy snapshot: ${e.message}")
                }
                return
            }

            // v2 snapshot: if bundles were restored but the eventId
            // index is empty (upgrade corner case, or a corrupted save),
            // the bundles are effectively unreachable — wipe them too
            // so a fresh publish happens.
            if (decoded.bundles.isNotEmpty() && decoded.eventIdToSlot.isEmpty()) {
                Log.w("KeyPackageRotationManager") {
                    "Restored ${decoded.bundles.size} bundle(s) but no eventId→slot mapping — discarding, will republish"
                }
                try {
                    store.delete()
                } catch (e: Exception) {
                    Log.w("KeyPackageRotationManager", "Failed to delete stale snapshot: ${e.message}")
                }
                return
            }

            mutex.withLock {
                activeBundles.clear()
                activeBundles.putAll(decoded.bundles)
                pendingRotations.clear()
                pendingRotations.addAll(decoded.pending)
                eventIdToSlot.clear()
                eventIdToSlot.putAll(decoded.eventIdToSlot)
            }
            Log.d("KeyPackageRotationManager") {
                "Restored ${decoded.bundles.size} active KeyPackage bundle(s), " +
                    "${decoded.pending.size} pending rotation, ${decoded.eventIdToSlot.size} eventId mapping(s)"
            }
        } catch (e: Exception) {
            Log.w("KeyPackageRotationManager", "Failed to decode persisted KeyPackages: ${e.message}")
        }
    }

    private data class Snapshot(
        val bundles: Map<String, KeyPackageBundle>,
        val pending: Set<String>,
        val eventIdToSlot: Map<String, String>,
    )

    /**
     * Encode the current rotation manager state to opaque bytes for
     * persistence. Caller must hold the mutex.
     */
    private fun snapshotBytesUnlocked(): ByteArray {
        val writer = TlsWriter()
        // version
        writer.putUint16(SNAPSHOT_VERSION)
        // active bundles
        writer.putUint32(activeBundles.size.toLong())
        for ((slot, bundle) in activeBundles) {
            writer.putOpaque2(slot.encodeToByteArray())
            writer.putOpaque4(bundle.keyPackage.toTlsBytes())
            writer.putOpaque2(bundle.initPrivateKey)
            writer.putOpaque2(bundle.encryptionPrivateKey)
            writer.putOpaque2(bundle.signaturePrivateKey)
        }
        // pending rotations
        writer.putUint32(pendingRotations.size.toLong())
        for (slot in pendingRotations) {
            writer.putOpaque2(slot.encodeToByteArray())
        }
        // eventId → slot (added in v2)
        writer.putUint32(eventIdToSlot.size.toLong())
        for ((eventId, slot) in eventIdToSlot) {
            writer.putOpaque2(eventId.encodeToByteArray())
            writer.putOpaque2(slot.encodeToByteArray())
        }
        return writer.toByteArray()
    }

    /**
     * Decode the persisted snapshot. Returns null if the on-disk version
     * is older than the current [SNAPSHOT_VERSION] and should be discarded
     * by the caller (see [restoreFromStore] for the rationale).
     */
    private fun decodeSnapshot(bytes: ByteArray): Snapshot? {
        val reader = TlsReader(bytes)
        val version = reader.readUint16()
        if (version != SNAPSHOT_VERSION) {
            // Older / unknown snapshot version — tell the caller to
            // discard it rather than loading partial state.
            return null
        }
        val numBundles = reader.readUint32().toInt()
        val bundles = mutableMapOf<String, KeyPackageBundle>()
        repeat(numBundles) {
            val slot = reader.readOpaque2().decodeToString()
            val kpBytes = reader.readOpaque4()
            val keyPackage = MlsKeyPackage.decodeTls(TlsReader(kpBytes))
            val initPriv = reader.readOpaque2()
            val encPriv = reader.readOpaque2()
            val sigPriv = reader.readOpaque2()
            bundles[slot] = KeyPackageBundle(keyPackage, initPriv, encPriv, sigPriv)
        }
        val numPending = reader.readUint32().toInt()
        val pending = mutableSetOf<String>()
        repeat(numPending) {
            pending.add(reader.readOpaque2().decodeToString())
        }
        val eventIdMap = mutableMapOf<String, String>()
        if (reader.hasRemaining) {
            val numEventIds = reader.readUint32().toInt()
            repeat(numEventIds) {
                val eventId = reader.readOpaque2().decodeToString()
                val slot = reader.readOpaque2().decodeToString()
                eventIdMap[eventId] = slot
            }
        }
        return Snapshot(bundles, pending, eventIdMap)
    }

    /**
     * Persist the current state to [store]. Caller must hold the mutex.
     * Best-effort: persistence failures are logged but don't propagate, so
     * a failing disk write never breaks an in-flight Welcome / addMember.
     */
    private suspend fun persistUnlocked() {
        val store = store ?: return
        try {
            store.save(snapshotBytesUnlocked())
        } catch (e: Exception) {
            Log.w("KeyPackageRotationManager", "Failed to persist KeyPackages: ${e.message}")
        }
    }

    /**
     * Generate a new KeyPackage and its associated private bundle.
     *
     * @param identity the user's identity bytes (typically 32-byte Nostr pubkey)
     * @param dTagSlot the d-tag slot for addressable replacement
     * @return a [KeyPackageBundle] containing the KeyPackage and all private keys
     */
    suspend fun generateKeyPackage(
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
        mutex.withLock {
            activeBundles[dTagSlot] = bundle
            persistUnlocked()
        }
        return bundle
    }

    /**
     * Get the active bundle for a d-tag slot.
     * Used when processing a Welcome that references one of our KeyPackages.
     */
    suspend fun getBundle(dTagSlot: String): KeyPackageBundle? = mutex.withLock { activeBundles[dTagSlot] }

    /**
     * Find the bundle whose KeyPackage reference matches the given ref.
     * Used when we receive a Welcome and need to find the matching bundle
     * via the MLS-spec KeyPackageRef hash.
     *
     * NOTE: a Marmot Welcome's "e" tag actually carries the *Nostr event id*
     * of the kind:30443 event, NOT the MLS reference hash, so the welcome
     * receive path must use [findBundleByEventId] instead. This function
     * remains for any callers that genuinely need the MLS-ref lookup.
     */
    suspend fun findBundleByRef(keyPackageRef: ByteArray): KeyPackageBundle? =
        mutex.withLock {
            activeBundles.values.find { bundle ->
                bundle.keyPackage.reference().contentEquals(keyPackageRef)
            }
        }

    /**
     * Find the bundle for a KeyPackage that was published as the given
     * Nostr event id (kind:30443). This is the lookup used by Welcome
     * processing — the "e" tag in a [WelcomeEvent] is the Nostr event id.
     *
     * Requires [recordPublishedEventId] to have been called for the slot
     * after the kind:30443 event was signed.
     */
    suspend fun findBundleByEventId(eventId: HexKey): KeyPackageBundle? =
        mutex.withLock {
            val slot = eventIdToSlot[eventId] ?: return@withLock null
            activeBundles[slot]
        }

    /**
     * Record that the bundle for [dTagSlot] has been published as the
     * kind:30443 event with [eventId]. Call this immediately after signing
     * the event template (the Nostr event id is only known once the event
     * has been signed). The mapping is persisted alongside the bundles so
     * it survives app restart.
     */
    suspend fun recordPublishedEventId(
        dTagSlot: String,
        eventId: HexKey,
    ) = mutex.withLock {
        eventIdToSlot[eventId] = dTagSlot
        persistUnlocked()
    }

    /**
     * Mark a KeyPackage slot as consumed (used in a Welcome).
     * The slot will be included in [pendingRotationSlots] and should be
     * rotated by the caller.
     */
    suspend fun markConsumed(dTagSlot: String) =
        mutex.withLock {
            activeBundles.remove(dTagSlot)
            // Drop any eventId mappings that pointed at this slot.
            val staleEventIds = eventIdToSlot.entries.filter { it.value == dTagSlot }.map { it.key }
            staleEventIds.forEach { eventIdToSlot.remove(it) }
            pendingRotations.add(dTagSlot)
            persistUnlocked()
        }

    /**
     * Mark a slot as consumed by looking up the KeyPackage reference.
     */
    suspend fun markConsumedByRef(keyPackageRef: ByteArray) =
        mutex.withLock {
            val entry =
                activeBundles.entries.find { (_, bundle) ->
                    bundle.keyPackage.reference().contentEquals(keyPackageRef)
                }
            if (entry != null) {
                val consumedSlot = entry.key
                activeBundles.remove(consumedSlot)
                val staleEventIds = eventIdToSlot.entries.filter { it.value == consumedSlot }.map { it.key }
                staleEventIds.forEach { eventIdToSlot.remove(it) }
                pendingRotations.add(consumedSlot)
                persistUnlocked()
            }
        }

    /**
     * Mark a slot as consumed by Nostr event id (the value carried by the
     * Welcome's "e" tag). Companion to [findBundleByEventId].
     */
    suspend fun markConsumedByEventId(eventId: HexKey) =
        mutex.withLock {
            val slot = eventIdToSlot[eventId] ?: return@withLock
            activeBundles.remove(slot)
            val staleEventIds = eventIdToSlot.entries.filter { it.value == slot }.map { it.key }
            staleEventIds.forEach { eventIdToSlot.remove(it) }
            pendingRotations.add(slot)
            persistUnlocked()
        }

    /**
     * Get the d-tag slots that need rotation (KeyPackage was consumed).
     */
    suspend fun pendingRotationSlots(): Set<String> = mutex.withLock { pendingRotations.toSet() }

    /**
     * Clear a slot from the pending rotation set after a new KeyPackage
     * has been published.
     */
    suspend fun clearPendingRotation(dTagSlot: String) =
        mutex.withLock {
            pendingRotations.remove(dTagSlot)
            persistUnlocked()
        }

    /**
     * Check if any slots need rotation.
     */
    suspend fun needsRotation(): Boolean = mutex.withLock { pendingRotations.isNotEmpty() }

    /**
     * Check if there are any active (non-consumed) KeyPackage bundles.
     * Returns true if at least one slot has been generated and not yet consumed.
     */
    suspend fun hasActiveKeyPackages(): Boolean = mutex.withLock { activeBundles.isNotEmpty() }

    /**
     * Rotate a consumed slot: generate a new KeyPackage for the same d-tag.
     *
     * @param identity the user's identity bytes
     * @param dTagSlot the slot to rotate
     * @return the new [KeyPackageBundle] ready for publishing
     */
    suspend fun rotateSlot(
        identity: ByteArray,
        dTagSlot: String,
    ): KeyPackageBundle {
        val bundle = generateKeyPackage(identity, dTagSlot)
        mutex.withLock {
            pendingRotations.remove(dTagSlot)
            persistUnlocked()
        }
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

        /**
         * On-disk snapshot format version for [KeyPackageBundleStore].
         * v1: bundles + pendingRotations
         * v2: + eventIdToSlot map (so welcome lookup by Nostr event id works)
         */
        private const val SNAPSHOT_VERSION = 2
    }
}
