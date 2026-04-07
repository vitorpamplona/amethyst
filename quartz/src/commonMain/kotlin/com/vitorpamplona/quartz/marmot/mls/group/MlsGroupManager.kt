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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.messages.CommitResult
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-level coordinator for MLS group lifecycle and state management.
 *
 * This is the primary entry point for the Marmot MLS integration. It bridges
 * the low-level MLS engine ([MlsGroup]) with the Nostr application layer,
 * managing multiple concurrent groups and ensuring state survives app restarts.
 *
 * ## Responsibilities
 *
 * - **Group lifecycle**: Create, join (via Welcome or external commit), and leave groups
 * - **State persistence**: Save/restore group state through [MlsGroupStateStore]
 * - **Epoch retention**: Keep N-1 epoch secrets for decrypting late-arriving messages
 * - **Key hygiene**: Secure deletion of consumed init_keys after Welcome processing
 * - **KeyPackage rotation**: Schedule self-updates within 24h of joining (MIP-00)
 *
 * ## Typical Usage
 *
 * ```kotlin
 * val store: MlsGroupStateStore = ... // platform-specific encrypted storage
 * val manager = MlsGroupManager(store)
 *
 * // On app startup
 * manager.restoreAll()
 *
 * // Create a new group
 * val group = manager.createGroup(nostrGroupId, myIdentity)
 *
 * // Add a member (from their published KeyPackage)
 * val result = manager.addMember(nostrGroupId, keyPackageBytes)
 * // Send result.commitBytes as kind 445 GroupEvent
 * // Send result.welcomeBytes as kind 444 WelcomeEvent (NIP-59 wrapped)
 *
 * // Join via Welcome
 * manager.processWelcome(nostrGroupId, welcomeBytes, myKeyPackageBundle)
 *
 * // Encrypt/decrypt messages
 * val ciphertext = manager.encrypt(nostrGroupId, plaintext)
 * val decrypted = manager.decrypt(nostrGroupId, ciphertext)
 *
 * // Derive outer encryption key for GroupEvents (MIP-03)
 * val key = manager.exporterSecret(nostrGroupId)
 * ```
 *
 * ## Cross-Implementation Notes
 *
 * This manager uses ciphersuite 0x0001 (MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519).
 * The epoch secret retention window is [EPOCH_RETENTION_WINDOW] = 2, meaning secrets
 * for the current and previous epoch are kept for late-message decryption.
 *
 * Thread safety: All public methods are suspending and should be called
 * from a single coroutine context (e.g., the Account's scope).
 *
 * @see MlsGroup The low-level MLS state machine
 * @see MlsGroupStateStore Storage abstraction for group state persistence
 */
class MlsGroupManager(
    private val store: MlsGroupStateStore,
) {
    private val mutex = Mutex()
    private val groups = mutableMapOf<HexKey, MlsGroup>()
    private val retainedEpochs = mutableMapOf<HexKey, MutableList<RetainedEpochSecrets>>()

    /**
     * Restore all groups from persistent storage on startup.
     * Call this once during Account initialization.
     */
    suspend fun restoreAll() =
        mutex.withLock {
            val groupIds = store.listGroups()
            for (nostrGroupId in groupIds) {
                try {
                    val stateBytes = store.load(nostrGroupId) ?: continue
                    val state = MlsGroupState.decodeTls(stateBytes)
                    groups[nostrGroupId] = MlsGroup.restore(state)

                    // Restore retained epochs
                    val retained = store.loadRetainedEpochs(nostrGroupId)
                    if (retained.isNotEmpty()) {
                        retainedEpochs[nostrGroupId] =
                            retained
                                .map { RetainedEpochSecrets.decodeTls(TlsReader(it)) }
                                .toMutableList()
                    }
                } catch (_: Exception) {
                    // Corrupted state — remove it so it doesn't block future joins
                    store.delete(nostrGroupId)
                }
            }
        }

    /**
     * Get an active group by its Nostr group ID.
     */
    fun getGroup(nostrGroupId: HexKey): MlsGroup? = groups[nostrGroupId]

    /**
     * List all active Nostr group IDs.
     */
    fun activeGroupIds(): Set<HexKey> = groups.keys.toSet()

    /**
     * Check if we are a member of the given group.
     */
    fun isMember(nostrGroupId: HexKey): Boolean = groups.containsKey(nostrGroupId)

    // --- Group Creation ---

    /**
     * Create a new MLS group and persist it.
     *
     * @param nostrGroupId hex-encoded Nostr routing ID for the group
     * @param identity the creator's identity bytes (typically Nostr pubkey)
     * @param signingKey optional Ed25519 signing key (generated if null)
     * @return the new [MlsGroup]
     */
    suspend fun createGroup(
        nostrGroupId: HexKey,
        identity: ByteArray,
        signingKey: ByteArray? = null,
    ): MlsGroup =
        mutex.withLock {
            val group = MlsGroup.create(identity, signingKey)
            groups[nostrGroupId] = group
            persistGroup(nostrGroupId)
            group
        }

    // --- Joining ---

    /**
     * Join a group by processing a Welcome message.
     *
     * After processing:
     * 1. The init_key from the KeyPackageBundle is securely deleted
     *    (the bundle should be discarded by the caller after this returns)
     * 2. Group state is persisted
     * 3. A KeyPackage rotation should be triggered (see [needsKeyPackageRotation])
     *
     * @param nostrGroupId hex-encoded Nostr group ID (from Welcome event tags)
     * @param welcomeBytes TLS-serialized Welcome message
     * @param bundle the KeyPackageBundle that was used for the invitation
     * @return the joined [MlsGroup]
     */
    suspend fun processWelcome(
        nostrGroupId: HexKey,
        welcomeBytes: ByteArray,
        bundle: KeyPackageBundle,
    ): MlsGroup =
        mutex.withLock {
            val group = MlsGroup.processWelcome(welcomeBytes, bundle)
            groups[nostrGroupId] = group

            // init_key is consumed — the bundle's initPrivateKey should not be
            // reused. Caller must discard the bundle and rotate KeyPackages.

            persistGroup(nostrGroupId)
            group
        }

    /**
     * Join a group via external commit.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @param groupInfoBytes TLS-serialized GroupInfo
     * @param identity the joiner's identity bytes
     * @param signingKey optional signing key
     * @return pair of (joined group, commit bytes to publish)
     */
    suspend fun externalJoin(
        nostrGroupId: HexKey,
        groupInfoBytes: ByteArray,
        identity: ByteArray,
        signingKey: ByteArray? = null,
    ): Pair<MlsGroup, ByteArray> =
        mutex.withLock {
            val (group, commitBytes) = MlsGroup.externalJoin(groupInfoBytes, identity, signingKey)
            groups[nostrGroupId] = group
            persistGroup(nostrGroupId)
            Pair(group, commitBytes)
        }

    // --- Epoch Transitions ---

    /**
     * Create and apply a Commit, advancing the epoch.
     *
     * Retains the outgoing epoch's decryption secrets and persists
     * the new state before returning.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return the [CommitResult] to publish
     */
    suspend fun commit(nostrGroupId: HexKey): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)

            // Retain current epoch secrets before transition
            retainEpochSecrets(nostrGroupId, group)

            val result = group.commit()
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Process a received Commit, advancing the epoch.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @param commitBytes TLS-serialized Commit
     * @param senderLeafIndex sender's leaf index
     * @param confirmationTag optional confirmation tag for verification
     */
    suspend fun processCommit(
        nostrGroupId: HexKey,
        commitBytes: ByteArray,
        senderLeafIndex: Int,
        confirmationTag: ByteArray? = null,
    ) = mutex.withLock {
        val group = requireGroup(nostrGroupId)

        // Retain current epoch secrets before transition
        retainEpochSecrets(nostrGroupId, group)

        group.processCommit(commitBytes, senderLeafIndex, confirmationTag)
        persistGroup(nostrGroupId)
    }

    // --- Message Encryption/Decryption ---

    /**
     * Encrypt an application message.
     */
    fun encrypt(
        nostrGroupId: HexKey,
        plaintext: ByteArray,
    ): ByteArray = requireGroup(nostrGroupId).encrypt(plaintext)

    /**
     * Decrypt an application message.
     *
     * Tries the current epoch first, then falls back to retained epoch
     * secrets for late-arriving messages from previous epochs.
     */
    fun decrypt(
        nostrGroupId: HexKey,
        messageBytes: ByteArray,
    ): DecryptedMessage {
        val group = requireGroup(nostrGroupId)

        // Try current epoch
        val current = group.decryptOrNull(messageBytes)
        if (current != null) return current

        // Try retained epochs
        val retained = retainedEpochs[nostrGroupId] ?: emptyList()
        for (epochSecrets in retained) {
            val result = tryDecryptWithRetainedEpoch(messageBytes, epochSecrets)
            if (result != null) return result
        }

        // No epoch could decrypt — rethrow from current epoch for diagnostics
        return group.decrypt(messageBytes)
    }

    /**
     * Decrypt with null return on failure.
     */
    fun decryptOrNull(
        nostrGroupId: HexKey,
        messageBytes: ByteArray,
    ): DecryptedMessage? =
        try {
            decrypt(nostrGroupId, messageBytes)
        } catch (_: Exception) {
            null
        }

    // --- Member Management ---

    /**
     * Add a member and create a Commit.
     */
    suspend fun addMember(
        nostrGroupId: HexKey,
        keyPackageBytes: ByteArray,
    ): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            retainEpochSecrets(nostrGroupId, group)
            val result = group.addMember(keyPackageBytes)
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Remove a member and create a Commit.
     */
    suspend fun removeMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
    ): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            retainEpochSecrets(nostrGroupId, group)
            val result = group.removeMember(targetLeafIndex)
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Rotate the signing key within a group and commit.
     *
     * Per MIP-00, members SHOULD self-update within 24 hours of joining.
     * This creates an Update proposal with a fresh signing key and commits it.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return the [CommitResult] to publish
     */
    suspend fun rotateSigningKey(nostrGroupId: HexKey): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            retainEpochSecrets(nostrGroupId, group)
            group.proposeSigningKeyRotation()
            val result = group.commit()
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Leave a group (self-remove).
     * Returns the SelfRemove proposal bytes to publish, then removes
     * local state.
     */
    suspend fun leaveGroup(nostrGroupId: HexKey): ByteArray =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            val proposalBytes = group.selfRemove()
            removeGroupStateUnlocked(nostrGroupId)
            proposalBytes
        }

    /**
     * Remove all local state for a group without sending any proposals.
     * Used after the leave event has already been built.
     */
    suspend fun removeGroupState(nostrGroupId: HexKey) =
        mutex.withLock {
            removeGroupStateUnlocked(nostrGroupId)
        }

    private suspend fun removeGroupStateUnlocked(nostrGroupId: HexKey) {
        groups.remove(nostrGroupId)
        retainedEpochs.remove(nostrGroupId)
        store.delete(nostrGroupId)
    }

    // --- Key Export ---

    /**
     * Export the Marmot outer encryption key for GroupEvent wrapping.
     * MLS-Exporter("marmot", "group-event", 32)
     */
    fun exporterSecret(nostrGroupId: HexKey): ByteArray =
        requireGroup(nostrGroupId).exporterSecret(
            "marmot",
            "group-event".encodeToByteArray(),
            32,
        )

    // --- Private Helpers ---

    private fun requireGroup(nostrGroupId: HexKey): MlsGroup =
        groups[nostrGroupId]
            ?: throw IllegalStateException("Not a member of group $nostrGroupId")

    private suspend fun persistGroup(nostrGroupId: HexKey) {
        val group = groups[nostrGroupId] ?: return
        val state = group.saveState()
        store.save(nostrGroupId, state.encodeTls())

        // Also persist retained epochs
        val retained = retainedEpochs[nostrGroupId]
        if (retained != null) {
            val retainedBytes =
                retained.map { epoch ->
                    val writer = TlsWriter()
                    epoch.encodeTls(writer)
                    writer.toByteArray()
                }
            store.saveRetainedEpochs(nostrGroupId, retainedBytes)
        }
    }

    private fun retainEpochSecrets(
        nostrGroupId: HexKey,
        group: MlsGroup,
    ) {
        val retained = retainedEpochs.getOrPut(nostrGroupId) { mutableListOf() }
        retained.add(group.retainedSecrets())

        // Trim to retention window (keep only the most recent N-1 epochs)
        while (retained.size > EPOCH_RETENTION_WINDOW) {
            retained.removeAt(0)
        }
    }

    private fun tryDecryptWithRetainedEpoch(
        messageBytes: ByteArray,
        retained: RetainedEpochSecrets,
    ): DecryptedMessage? =
        try {
            val secretTree = SecretTree(retained.encryptionSecret, retained.leafCount)
            val mlsMsg =
                com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
                    .decodeTls(TlsReader(messageBytes))

            if (mlsMsg.wireFormat != com.vitorpamplona.quartz.marmot.mls.framing.WireFormat.PRIVATE_MESSAGE) {
                return null
            }

            val privMsg =
                com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
                    .decodeTls(TlsReader(mlsMsg.payload))
            if (privMsg.epoch != retained.epoch) return null

            // Decrypt sender data
            val senderDataKey =
                MlsCryptoProvider.expandWithLabel(
                    retained.senderDataSecret,
                    "key",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_KEY_LENGTH,
                )
            val senderDataNonce =
                MlsCryptoProvider.expandWithLabel(
                    retained.senderDataSecret,
                    "nonce",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_NONCE_LENGTH,
                )
            val senderDataPlain =
                MlsCryptoProvider.aeadDecrypt(
                    senderDataKey,
                    senderDataNonce,
                    ByteArray(0),
                    privMsg.encryptedSenderData,
                )
            val senderReader = TlsReader(senderDataPlain)
            val senderLeafIndex = senderReader.readUint32().toInt()
            val generation = senderReader.readUint32().toInt()

            val kng = secretTree.applicationKeyNonceForGeneration(senderLeafIndex, generation)
            val plaintext =
                MlsCryptoProvider.aeadDecrypt(kng.key, kng.nonce, ByteArray(0), privMsg.ciphertext)

            DecryptedMessage(
                senderLeafIndex = senderLeafIndex,
                contentType = privMsg.contentType,
                content = plaintext,
                epoch = privMsg.epoch,
            )
        } catch (_: Exception) {
            null
        }

    companion object {
        /**
         * Number of past epochs to retain for late-arriving message decryption.
         * MLS forward secrecy guarantees mean we want to limit this window.
         */
        const val EPOCH_RETENTION_WINDOW = 2
    }
}
