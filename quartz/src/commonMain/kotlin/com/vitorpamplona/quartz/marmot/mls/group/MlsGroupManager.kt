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
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
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
 * The epoch secret retention window is [EPOCH_RETENTION_WINDOW] = 5, matching the
 * `DEFAULT_EPOCH_LOOKBACK` used by the MDK reference implementation so that late-
 * arriving MIP-03 GroupEvents (and MLS application messages) whose outer ChaCha20-
 * Poly1305 key was derived from a prior epoch's exporter secret can still be
 * decrypted after a Commit advances the group.
 *
 * Thread safety: All suspending mutation methods are guarded by a [Mutex]
 * to prevent concurrent state corruption. Non-suspending read methods
 * ([getGroup], [isMember], [activeGroupIds], [encrypt], [decrypt],
 * [decryptOrNull], [exporterSecret]) must be called from the same
 * coroutine context that owns this manager (e.g., the Account's scope).
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
            Log.d(TAG) { "restoreAll(): store reports ${groupIds.size} groups: $groupIds" }
            for (nostrGroupId in groupIds) {
                try {
                    val stateBytes = store.load(nostrGroupId)
                    if (stateBytes == null) {
                        Log.w(TAG) { "restoreAll(): load returned null for $nostrGroupId, skipping" }
                        continue
                    }
                    val state = MlsGroupState.decodeTls(stateBytes)
                    groups[nostrGroupId] = MlsGroup.restore(state)
                    Log.d(TAG) { "restoreAll(): restored group $nostrGroupId (${stateBytes.size} bytes)" }

                    // Restore retained epochs
                    val retained = store.loadRetainedEpochs(nostrGroupId)
                    if (retained.isNotEmpty()) {
                        retainedEpochs[nostrGroupId] =
                            retained
                                .map { RetainedEpochSecrets.decodeTls(TlsReader(it)) }
                                .toMutableList()
                        Log.d(TAG) { "restoreAll(): restored ${retained.size} retained epochs for $nostrGroupId" }
                    }
                } catch (e: Exception) {
                    // Could be genuinely corrupted state, or a transient bug
                    // (e.g. wrong cipher param spec). Skip but DO NOT delete —
                    // a future restart after a fix should still be able to
                    // recover the group. If it really is corrupted the user
                    // can explicitly leave/delete the group from the UI.
                    Log.e(
                        TAG,
                        "restoreAll(): failed to restore group $nostrGroupId, skipping (file preserved): ${e.message}",
                        e,
                    )
                }
            }
            Log.d(TAG) { "restoreAll(): finished with ${groups.size} active groups in memory" }
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
        initialExtensions: List<com.vitorpamplona.quartz.marmot.mls.tree.Extension> = emptyList(),
    ): MlsGroup =
        mutex.withLock {
            Log.d(TAG) { "createGroup($nostrGroupId): creating new MLS group" }
            val group = MlsGroup.create(identity, signingKey, initialExtensions)
            groups[nostrGroupId] = group
            persistGroup(nostrGroupId)
            Log.d(TAG) { "createGroup($nostrGroupId): done, in-memory group count=${groups.size}" }
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
     * @param welcomeBytes TLS-serialized Welcome message
     * @param bundle the KeyPackageBundle that was used for the invitation
     * @param hintNostrGroupId optional nostrGroupId from the Welcome event's "h" tag;
     *   if provided and non-null, validated against the GroupContext's NostrGroupData extension.
     *   If absent (sender did not include an "h" tag), the ID is derived from the MLS content.
     * @return pair of (joined group, derived nostrGroupId)
     */
    suspend fun processWelcome(
        welcomeBytes: ByteArray,
        bundle: KeyPackageBundle,
        hintNostrGroupId: HexKey? = null,
    ): Pair<MlsGroup, HexKey> =
        mutex.withLock {
            val group = MlsGroup.processWelcome(welcomeBytes, bundle)

            val derivedId =
                group.currentMarmotData()?.nostrGroupId
                    ?: throw IllegalArgumentException(
                        "Welcome GroupContext is missing the NostrGroupData extension — cannot derive nostrGroupId",
                    )

            if (hintNostrGroupId != null && hintNostrGroupId != derivedId) {
                throw IllegalArgumentException(
                    "nostrGroupId mismatch: h-tag=$hintNostrGroupId, GroupContext=$derivedId",
                )
            }

            groups[derivedId] = group

            // init_key is consumed — the bundle's initPrivateKey should not be
            // reused. Caller must discard the bundle and rotate KeyPackages.

            persistGroup(derivedId)
            Pair(group, derivedId)
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
            val retainedBefore = group.retainedSecrets()
            val result = group.commit()
            pushRetainedEpoch(nostrGroupId, retainedBefore)
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
        confirmationTag: ByteArray,
        signature: ByteArray = ByteArray(0),
        wireFormat: com.vitorpamplona.quartz.marmot.mls.framing.WireFormat = com.vitorpamplona.quartz.marmot.mls.framing.WireFormat.PUBLIC_MESSAGE,
    ) = mutex.withLock {
        val group = requireGroup(nostrGroupId)

        // Capture the outgoing epoch's secrets BEFORE advancing, but only
        // commit them to the retention window once processCommit succeeds —
        // otherwise a failed commit (e.g. "Duplicate encryption key" on an
        // add-me relay echo) would pollute the window with a duplicate of
        // the current epoch key, wasting the finite retention slots.
        val retainedBefore = group.retainedSecrets()

        group.processCommit(commitBytes, senderLeafIndex, confirmationTag, signature, wireFormat)

        pushRetainedEpoch(nostrGroupId, retainedBefore)
        persistGroup(nostrGroupId)
    }

    // --- Message Encryption/Decryption ---

    /**
     * Encrypt an application message.
     * Synchronized to prevent nonce reuse from concurrent encryption.
     */
    suspend fun encrypt(
        nostrGroupId: HexKey,
        plaintext: ByteArray,
    ): ByteArray =
        mutex.withLock {
            requireGroup(nostrGroupId).encrypt(plaintext)
        }

    /**
     * Decrypt an application message.
     *
     * Tries the current epoch first, then falls back to retained epoch
     * secrets for late-arriving messages from previous epochs.
     * Synchronized to prevent concurrent state corruption.
     */
    suspend fun decrypt(
        nostrGroupId: HexKey,
        messageBytes: ByteArray,
    ): DecryptedMessage =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)

            // Try current epoch. If we hit an exception here we MUST surface
            // it — commits that throw mid-processCommit leave the in-memory
            // group half-mutated, and a retry via `group.decrypt(...)` will
            // just report a stale "epoch mismatch" from the partial advance,
            // hiding the real bug. Capture the original throwable, try
            // retained epochs as a fallback, and re-raise the captured one
            // if nothing decrypts.
            val retainedBefore = group.retainedSecrets()
            val preEpoch = group.epoch
            val currentFailure: Throwable? =
                try {
                    val result = group.decrypt(messageBytes)
                    // PrivateMessage commits apply inline through
                    // `MlsGroup.decrypt` → `processCommit`; the epoch advances
                    // in memory but the CLI reopens a fresh Context on every
                    // command, so we MUST persist here or reloaded state
                    // silently reverts to the pre-commit extensions (including
                    // admin list).
                    if (result.contentType == com.vitorpamplona.quartz.marmot.mls.framing.ContentType.COMMIT && group.epoch != preEpoch) {
                        pushRetainedEpoch(nostrGroupId, retainedBefore)
                        persistGroup(nostrGroupId)
                    }
                    return@withLock result
                } catch (t: Throwable) {
                    t
                }

            val retained = retainedEpochs[nostrGroupId] ?: emptyList()
            for (epochSecrets in retained) {
                val result = tryDecryptWithRetainedEpoch(messageBytes, epochSecrets)
                if (result != null) return@withLock result
            }

            throw currentFailure ?: IllegalStateException("Decrypt failed without captured cause")
        }

    /**
     * Decrypt with null return on failure.
     */
    suspend fun decryptOrNull(
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
     *
     * The returned [CommitResult.preCommitExporterSecret] is the key the
     * outbound kind:445 MUST be outer-encrypted with (RFC 9420 §12.4 + MDK
     * parity). The local group state advances to the new epoch before this
     * function returns; publishers get one shot at the correct pre-commit
     * key via the [CommitResult] payload.
     */
    suspend fun addMember(
        nostrGroupId: HexKey,
        keyPackageBytes: ByteArray,
    ): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            val retainedBefore = group.retainedSecrets()
            val result = group.addMember(keyPackageBytes)
            pushRetainedEpoch(nostrGroupId, retainedBefore)
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Remove a member and create a Commit. See [addMember] for the
     * pre-commit exporter key contract on the returned [CommitResult].
     */
    suspend fun removeMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
    ): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            val retainedBefore = group.retainedSecrets()
            val result = group.removeMember(targetLeafIndex)
            pushRetainedEpoch(nostrGroupId, retainedBefore)
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Rotate the signing key within a group and commit.
     *
     * Per MIP-00, members SHOULD self-update within 24 hours of joining.
     * See [addMember] for the pre-commit exporter key contract.
     */
    suspend fun rotateSigningKey(nostrGroupId: HexKey): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            val retainedBefore = group.retainedSecrets()
            group.proposeSigningKeyRotation()
            val result = group.commit()
            pushRetainedEpoch(nostrGroupId, retainedBefore)
            persistGroup(nostrGroupId)
            result
        }

    /**
     * Update group extensions (e.g., MIP-01 metadata) via a GroupContextExtensions proposal.
     * See [addMember] for the pre-commit exporter key contract.
     */
    suspend fun updateGroupExtensions(
        nostrGroupId: HexKey,
        extensions: List<Extension>,
    ): CommitResult =
        mutex.withLock {
            val group = requireGroup(nostrGroupId)
            val currentMarmot = group.currentMarmotData()
            val adminsConfigured = currentMarmot != null && currentMarmot.adminPubkeys.isNotEmpty()
            check(!adminsConfigured || group.isLocalAdmin()) {
                "MIP-01: only admins may update group extensions"
            }
            val retainedBefore = group.retainedSecrets()
            group.proposeGroupContextExtensions(extensions)
            val result = group.commit()
            pushRetainedEpoch(nostrGroupId, retainedBefore)
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
     * Hex-encoded BasicCredential identity of the member at [leafIndex] in
     * the given group, or null if the group is unknown or the leaf is blank /
     * not a Basic credential.
     *
     * Used by MIP-03 inner-event sender verification to confirm that the
     * `pubkey` in a decrypted application message matches the MLS sender's
     * credential identity.
     */
    fun memberIdentityHex(
        nostrGroupId: HexKey,
        leafIndex: Int,
    ): String? = groups[nostrGroupId]?.memberIdentityHex(leafIndex)

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

    /**
     * Export the MIP-04 encrypted media key for a group.
     * MLS-Exporter("marmot", "encrypted-media", 32)
     */
    fun mediaExporterSecret(nostrGroupId: HexKey): ByteArray =
        requireGroup(nostrGroupId).exporterSecret(
            "marmot",
            "encrypted-media".encodeToByteArray(),
            32,
        )

    /**
     * Return exporter secrets from retained epochs for a group.
     *
     * Used by the inbound processor to attempt outer decryption with
     * previous epoch keys when the current epoch's key fails (e.g.,
     * after a commit has advanced the epoch but late-arriving messages
     * still use the old exporter key).
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return list of retained exporter secrets (most recent first), each
     *         derived via MLS-Exporter("marmot", "group-event", 32)
     */
    fun retainedExporterSecrets(nostrGroupId: HexKey): List<ByteArray> {
        val retained = retainedEpochs[nostrGroupId] ?: return emptyList()
        return retained
            .filter { it.exporterSecret.isNotEmpty() }
            .sortedByDescending { it.epoch }
            .map { epochSecrets ->
                KeySchedule.mlsExporter(
                    epochSecrets.exporterSecret,
                    "marmot",
                    "group-event".encodeToByteArray(),
                    32,
                )
            }
    }

    // --- Private Helpers ---

    private fun requireGroup(nostrGroupId: HexKey): MlsGroup =
        groups[nostrGroupId]
            ?: throw IllegalStateException("Not a member of group $nostrGroupId")

    private suspend fun persistGroup(nostrGroupId: HexKey) {
        val group = groups[nostrGroupId]
        if (group == null) {
            Log.w(TAG) { "persistGroup($nostrGroupId): group not in memory, skipping" }
            return
        }
        try {
            val state = group.saveState()
            val encoded = state.encodeTls()
            Log.d(TAG) { "persistGroup($nostrGroupId): serialized ${encoded.size} bytes, calling store.save" }
            store.save(nostrGroupId, encoded)

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
                Log.d(TAG) { "persistGroup($nostrGroupId): persisted ${retainedBytes.size} retained epochs" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "persistGroup($nostrGroupId) FAILED: ${e.message}", e)
            throw e
        }
    }

    /**
     * Push a previously-captured [RetainedEpochSecrets] into the bounded
     * retention window. Call after the epoch advance has been applied
     * successfully so that failed commits don't pollute the window with
     * duplicate current-epoch keys.
     */
    private fun pushRetainedEpoch(
        nostrGroupId: HexKey,
        retainedBefore: RetainedEpochSecrets,
    ) {
        val retained = retainedEpochs.getOrPut(nostrGroupId) { mutableListOf() }
        retained.add(retainedBefore)

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

            // Derive sender data key/nonce using ciphertext sample (RFC 9420 §6.3.1)
            val ciphertextSample =
                privMsg.ciphertext.copyOfRange(0, minOf(privMsg.ciphertext.size, MlsCryptoProvider.AEAD_KEY_LENGTH))
            val senderDataKey =
                MlsCryptoProvider.expandWithLabel(
                    retained.senderDataSecret,
                    "key",
                    ciphertextSample,
                    MlsCryptoProvider.AEAD_KEY_LENGTH,
                )
            val senderDataNonce =
                MlsCryptoProvider.expandWithLabel(
                    retained.senderDataSecret,
                    "nonce",
                    ciphertextSample,
                    MlsCryptoProvider.AEAD_NONCE_LENGTH,
                )

            // Build SenderDataAAD
            val senderDataAad = TlsWriter()
            senderDataAad.putOpaqueVarInt(privMsg.groupId)
            senderDataAad.putUint64(privMsg.epoch)
            senderDataAad.putUint8(privMsg.contentType.value)

            val senderDataPlain =
                MlsCryptoProvider.aeadDecrypt(
                    senderDataKey,
                    senderDataNonce,
                    senderDataAad.toByteArray(),
                    privMsg.encryptedSenderData,
                )
            val senderReader = TlsReader(senderDataPlain)
            val senderLeafIndex = senderReader.readUint32().toInt()
            val generation = senderReader.readUint32().toInt()
            val reuseGuard = senderReader.readBytes(REUSE_GUARD_LENGTH)

            val kng = secretTree.applicationKeyNonceForGeneration(senderLeafIndex, generation)

            // Apply reuse_guard XOR to nonce
            val guardedNonce = kng.nonce.copyOf()
            for (i in 0 until REUSE_GUARD_LENGTH) {
                guardedNonce[i] = (guardedNonce[i].toInt() xor reuseGuard[i].toInt()).toByte()
            }

            // Build PrivateContentAAD
            val contentAad = TlsWriter()
            contentAad.putOpaqueVarInt(privMsg.groupId)
            contentAad.putUint64(privMsg.epoch)
            contentAad.putUint8(privMsg.contentType.value)
            contentAad.putOpaqueVarInt(privMsg.authenticatedData)

            val plaintext =
                MlsCryptoProvider.aeadDecrypt(kng.key, guardedNonce, contentAad.toByteArray(), privMsg.ciphertext)

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
        private const val TAG = "MlsGroupManager"

        /**
         * Number of past epochs to retain for late-arriving message decryption.
         * Matches MDK's `DEFAULT_EPOCH_LOOKBACK` so a message encrypted under
         * the prior N epochs' exporter secrets can still be decrypted after a
         * Commit advances the group. Capped for forward-secrecy reasons.
         */
        const val EPOCH_RETENTION_WINDOW = 5

        /** Size of reuse_guard in PrivateMessage (RFC 9420 §6.3.1) */
        private const val REUSE_GUARD_LENGTH = 4
    }
}
