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
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519KeyPair
import com.vitorpamplona.quartz.marmot.mls.crypto.Hpke
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.messages.Commit
import com.vitorpamplona.quartz.marmot.mls.messages.CommitResult
import com.vitorpamplona.quartz.marmot.mls.messages.EncryptedGroupSecrets
import com.vitorpamplona.quartz.marmot.mls.messages.GroupContext
import com.vitorpamplona.quartz.marmot.mls.messages.GroupInfo
import com.vitorpamplona.quartz.marmot.mls.messages.GroupSecrets
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.messages.Proposal
import com.vitorpamplona.quartz.marmot.mls.messages.ProposalOrRef
import com.vitorpamplona.quartz.marmot.mls.messages.UpdatePath
import com.vitorpamplona.quartz.marmot.mls.messages.Welcome
import com.vitorpamplona.quartz.marmot.mls.schedule.EpochSecrets
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree
import com.vitorpamplona.quartz.marmot.mls.tree.BinaryTree
import com.vitorpamplona.quartz.marmot.mls.tree.Capabilities
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNodeSource
import com.vitorpamplona.quartz.marmot.mls.tree.Lifetime
import com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
import com.vitorpamplona.quartz.marmot.mls.tree.UpdatePathNode
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.mac.MacInstance

/**
 * MLS Group state and operations (RFC 9420 Section 8, 12).
 *
 * This is the main entry point for the MLS engine. It manages:
 * - Group membership (ratchet tree)
 * - Epoch transitions (key schedule)
 * - Message encryption/decryption
 * - Proposal/Commit processing
 * - Welcome message creation and processing
 *
 * Usage:
 * ```kotlin
 * // Create a new group
 * val group = MlsGroup.create(identity, signingKey)
 *
 * // Add a member
 * val result = group.addMember(keyPackageBytes)
 * // Send result.commitBytes to the group
 * // Send result.welcomeBytes to the new member
 *
 * // Join a group via Welcome
 * val group = MlsGroup.processWelcome(welcomeBytes, keyPackageBundle)
 *
 * // Encrypt application message
 * val encrypted = group.encrypt(plaintext)
 *
 * // Decrypt application message
 * val decrypted = group.decrypt(encrypted)
 *
 * // Export key for Marmot outer encryption
 * val key = group.exporterSecret("marmot", "group-event", 32)
 * ```
 */
class MlsGroup private constructor(
    private var groupContext: GroupContext,
    private val tree: RatchetTree,
    private var myLeafIndex: Int,
    private var epochSecrets: EpochSecrets,
    private var secretTree: SecretTree,
    private var initSecret: ByteArray,
    private var signingPrivateKey: ByteArray,
    private var encryptionPrivateKey: ByteArray,
    private var interimTranscriptHash: ByteArray,
    private val pskStore: MutableMap<String, ByteArray> = mutableMapOf(),
    private val pendingProposals: MutableList<PendingProposal> = mutableListOf(),
    private val sentKeys: MutableMap<Int, com.vitorpamplona.quartz.marmot.mls.schedule.KeyNonceGeneration> = mutableMapOf(),
    /** Staged keys from proposeSigningKeyRotation — only promoted on successful commit */
    private var pendingSigningKey: ByteArray? = null,
    private var pendingEncryptionKey: ByteArray? = null,
) {
    val groupId: ByteArray get() = groupContext.groupId
    val epoch: Long get() = groupContext.epoch
    val leafIndex: Int get() = myLeafIndex
    val extensions: List<com.vitorpamplona.quartz.marmot.mls.tree.Extension> get() = groupContext.extensions

    // --- State Persistence ---

    /**
     * Capture the current group state as a serializable snapshot.
     *
     * The returned [MlsGroupState] contains all secret key material and
     * MUST be stored in encrypted local storage. Call this after every
     * epoch transition (commit, processCommit, processWelcome) to ensure
     * the group can be restored after an app restart.
     */
    fun saveState(): MlsGroupState {
        val treeWriter = TlsWriter()
        tree.encodeTls(treeWriter)

        return MlsGroupState(
            groupContext = groupContext,
            treeBytes = treeWriter.toByteArray(),
            myLeafIndex = myLeafIndex,
            epochSecrets = epochSecrets,
            initSecret = initSecret,
            signingPrivateKey = signingPrivateKey,
            encryptionPrivateKey = encryptionPrivateKey,
            interimTranscriptHash = interimTranscriptHash,
            encryptionSecret = epochSecrets.encryptionSecret,
        )
    }

    /**
     * Extract retained epoch secrets for late-message decryption.
     *
     * Call this BEFORE an epoch transition to capture the outgoing epoch's
     * decryption secrets. These are kept in a bounded window by [MlsGroupManager].
     */
    fun retainedSecrets(): RetainedEpochSecrets =
        RetainedEpochSecrets(
            epoch = epoch,
            senderDataSecret = epochSecrets.senderDataSecret,
            encryptionSecret = epochSecrets.encryptionSecret,
            leafCount = tree.leafCount,
            exporterSecret = epochSecrets.exporterSecret,
        )

    val memberCount: Int
        get() {
            var count = 0
            for (i in 0 until tree.leafCount) {
                if (tree.getLeaf(i) != null) count++
            }
            return count
        }

    /** Whether a ReInit proposal has been committed, requiring a new group to be created. */
    var reInitPending: Proposal.ReInit? = null
        private set

    /**
     * Register a pre-shared key for use in PSK proposals.
     */
    fun registerPsk(
        pskId: ByteArray,
        psk: ByteArray,
    ) {
        pskStore[pskId.toHexKey()] = psk
    }

    /**
     * Get the list of members (leaf index -> LeafNode).
     */
    fun members(): List<Pair<Int, LeafNode>> {
        val result = mutableListOf<Pair<Int, LeafNode>>()
        for (i in 0 until tree.leafCount) {
            val leaf = tree.getLeaf(i)
            if (leaf != null) {
                result.add(i to leaf)
            }
        }
        return result
    }

    // --- Key Package Generation ---

    /**
     * Create a new KeyPackage for publishing (MIP-00).
     * Used by others to add this user to groups.
     */
    fun createKeyPackage(
        identity: ByteArray,
        signingKey: ByteArray,
    ): KeyPackageBundle {
        val initKp = X25519.generateKeyPair()
        val encKp = X25519.generateKeyPair()
        val sigKp = Ed25519.generateKeyPair()

        val leafNode =
            buildLeafNode(
                encryptionKey = encKp.publicKey,
                signatureKey = sigKp.publicKey,
                identity = identity,
                source = LeafNodeSource.KEY_PACKAGE,
                signingKey = sigKp.privateKey,
            )

        val unsigned =
            MlsKeyPackage(
                initKey = initKp.publicKey,
                leafNode = leafNode,
                signature = ByteArray(0),
            )
        val kp =
            unsigned.copy(
                signature = MlsCryptoProvider.signWithLabel(sigKp.privateKey, "KeyPackageTBS", unsigned.encodeTbs()),
            )

        return KeyPackageBundle(kp, initKp.privateKey, encKp.privateKey, sigKp.privateKey)
    }

    // --- Proposal Creation ---

    /**
     * Create an Add proposal for a new member.
     */
    fun proposeAdd(keyPackageBytes: ByteArray): Proposal.Add {
        val kp = MlsKeyPackage.decodeTls(TlsReader(keyPackageBytes))
        // Verify KeyPackage signature (RFC 9420 Section 10.1)
        require(kp.verifySignature()) { "Invalid KeyPackage signature" }
        val proposal = Proposal.Add(kp)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    /**
     * Create a Remove proposal.
     */
    fun proposeRemove(targetLeafIndex: Int): Proposal.Remove {
        val proposal = Proposal.Remove(targetLeafIndex)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    /**
     * Create a SelfRemove proposal.
     */
    fun proposeSelfRemove(): Proposal.SelfRemove {
        val proposal = Proposal.SelfRemove()
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    /**
     * Create an Update proposal to rotate the signing key within the group.
     *
     * Per MIP-00, members SHOULD perform a self-update within 24 hours
     * of joining a group. This replaces the leaf node with fresh key material,
     * improving forward secrecy.
     *
     * The new signing key pair is generated internally and takes effect
     * after the next Commit.
     *
     * @return the Update proposal (already queued for the next commit)
     */
    fun proposeSigningKeyRotation(): Proposal.Update {
        val newSigKp = Ed25519.generateKeyPair()
        val newEncKp = X25519.generateKeyPair()

        val currentLeaf = tree.getLeaf(myLeafIndex)
        val identity =
            (currentLeaf?.credential as? Credential.Basic)?.identity
                ?: ByteArray(0)

        val newLeafNode =
            buildLeafNode(
                encryptionKey = newEncKp.publicKey,
                signatureKey = newSigKp.publicKey,
                identity = identity,
                source = LeafNodeSource.UPDATE,
                signingKey = newSigKp.privateKey,
                groupId = groupId,
                leafIndex = myLeafIndex,
            )

        val proposal = Proposal.Update(newLeafNode)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))

        // Stage the new keys — they take effect only when commit() succeeds
        pendingSigningKey = newSigKp.privateKey
        pendingEncryptionKey = newEncKp.privateKey

        return proposal
    }

    /**
     * Create a GroupContextExtensions proposal to update the group's extensions.
     * Used for changing group metadata (name, description, etc.) via MIP-01.
     */
    fun proposeGroupContextExtensions(extensions: List<Extension>): Proposal.GroupContextExtensions {
        val proposal = Proposal.GroupContextExtensions(extensions)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    /**
     * Create a PSK proposal to include a pre-shared key in the next epoch.
     * The PSK must be registered via registerPsk() before committing.
     */
    fun proposePsk(
        pskId: ByteArray,
        pskNonce: ByteArray = MlsCryptoProvider.randomBytes(MlsCryptoProvider.HASH_OUTPUT_LENGTH),
    ): Proposal.Psk {
        val proposal = Proposal.Psk(pskType = 1, pskId = pskId, pskNonce = pskNonce)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    /**
     * Create a ReInit proposal to reinitialize the group with new parameters.
     * After the commit containing this proposal is processed, the group enters
     * a reinitialization state and a new group must be created.
     */
    fun proposeReInit(
        newGroupId: ByteArray = MlsCryptoProvider.randomBytes(32),
        newVersion: Int = 1,
        newCipherSuite: Int = 1,
        newExtensions: List<Extension> = emptyList(),
    ): Proposal.ReInit {
        val proposal = Proposal.ReInit(newGroupId, newVersion, newCipherSuite, newExtensions)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    // --- Commit ---

    /**
     * Create a Commit that applies all pending proposals.
     * Returns the Commit bytes to send to the group, plus optional Welcome for new members.
     */
    fun commit(): CommitResult {
        val proposals = pendingProposals.toList()
        val proposalOrRefs = proposals.map { ProposalOrRef.Inline(it.proposal) }

        // Check if we need an UpdatePath (required unless only SelfRemove)
        val needsPath = proposals.any { it.proposal !is Proposal.SelfRemove }

        // Apply proposals to tree FIRST (RFC 9420 Section 12.4.1)
        // The UpdatePath is computed after proposals are applied.
        val addedMembers = mutableListOf<Pair<Int, MlsKeyPackage>>()
        for (pending in proposals) {
            val p = pending.proposal
            if (p is Proposal.Add) {
                // applyProposal calls tree.addLeaf() which returns the actual leaf index
                // (may reuse a blank slot instead of appending)
                val leafIndex = applyProposalAdd(p)
                addedMembers.add(leafIndex to p.keyPackage)
            } else {
                applyProposal(p, pending.senderLeafIndex)
            }
        }

        // Generate new path secrets on the updated tree
        val leafSecret = MlsCryptoProvider.randomBytes(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        val pathSecrets = tree.derivePathSecrets(myLeafIndex, leafSecret)

        // Build UpdatePath with HPKE-encrypted path secrets for each copath node
        val updatePath =
            if (needsPath && pathSecrets.isNotEmpty()) {
                val copath = BinaryTree.copath(myLeafIndex, tree.leafCount)
                val pathNodes =
                    pathSecrets.zip(copath).map { (pathKey, copathNode) ->
                        val resolution = tree.resolution(copathNode)
                        val encryptedSecrets =
                            resolution.mapNotNull { resNode ->
                                val node = tree.getNode(resNode) ?: return@mapNotNull null
                                val recipientPub =
                                    when (node) {
                                        is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Leaf -> {
                                            node.leafNode.encryptionKey
                                        }

                                        is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent -> {
                                            node.parentNode.encryptionKey
                                        }
                                    }
                                MlsCryptoProvider.encryptWithLabel(
                                    recipientPub,
                                    "UpdatePathNode",
                                    groupContext.toTlsBytes(),
                                    pathKey.pathSecret,
                                )
                            }
                        UpdatePathNode(pathKey.publicKey, encryptedSecrets)
                    }

                val newEncKp = X25519.generateKeyPair()
                val newLeafNode =
                    buildLeafNode(
                        encryptionKey = newEncKp.publicKey,
                        signatureKey = Ed25519.publicFromPrivate(signingPrivateKey),
                        identity =
                            (tree.getLeaf(myLeafIndex)?.credential as? Credential.Basic)?.identity
                                ?: ByteArray(0),
                        source = LeafNodeSource.COMMIT,
                        signingKey = signingPrivateKey,
                        groupId = groupId,
                        leafIndex = myLeafIndex,
                    )

                encryptionPrivateKey = newEncKp.privateKey
                tree.setLeaf(myLeafIndex, newLeafNode)

                UpdatePath(newLeafNode, pathNodes)
            } else {
                null
            }

        val commit = Commit(proposalOrRefs, updatePath)

        // Apply UpdatePath to tree
        if (updatePath != null) {
            tree.applyUpdatePath(myLeafIndex, updatePath.nodes)
        }

        // Advance epoch
        val commitSecret =
            if (pathSecrets.isNotEmpty()) {
                pathSecrets.last().pathSecret
            } else {
                ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            }

        // Update transcript hashes (RFC 9420 Section 8.2)
        val confirmedTranscriptHashInput = buildConfirmedTranscriptHashInput(commit, myLeafIndex)
        val confirmedInput = TlsWriter()
        confirmedInput.putBytes(interimTranscriptHash)
        confirmedInput.putBytes(confirmedTranscriptHashInput)
        val newConfirmedTranscriptHash = MlsCryptoProvider.hash(confirmedInput.toByteArray())

        val newTreeHash = tree.treeHash()
        val newEpoch = groupContext.epoch + 1

        groupContext =
            groupContext.copy(
                epoch = newEpoch,
                treeHash = newTreeHash,
                confirmedTranscriptHash = newConfirmedTranscriptHash,
            )

        // Compute PSK secret from any PSK proposals (RFC 9420 Section 8.4)
        val pskSecret = computePskSecret(proposals.map { it.proposal })

        val keySchedule = KeySchedule(groupContext.toTlsBytes())
        epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, initSecret, pskSecret)
        initSecret = epochSecrets.initSecret
        secretTree = SecretTree(epochSecrets.encryptionSecret, tree.leafCount)

        // Compute confirmation_tag and interim_transcript_hash
        val confirmationTag = computeConfirmationTag(epochSecrets.confirmationKey, newConfirmedTranscriptHash)
        val interimInput = TlsWriter()
        interimInput.putBytes(newConfirmedTranscriptHash)
        interimInput.putOpaqueVarInt(confirmationTag)
        interimTranscriptHash = MlsCryptoProvider.hash(interimInput.toByteArray())

        // Build Welcome for added members
        val welcomeBytes =
            if (addedMembers.isNotEmpty()) {
                buildWelcome(addedMembers)
            } else {
                null
            }

        // Promote staged keys from proposeSigningKeyRotation if present
        pendingSigningKey?.let { signingPrivateKey = it }
        pendingEncryptionKey?.let { encryptionPrivateKey = it }
        pendingSigningKey = null
        pendingEncryptionKey = null

        pendingProposals.clear()
        sentKeys.clear()

        val commitBytes = commit.toTlsBytes()
        return CommitResult(commitBytes, welcomeBytes, null)
    }

    // --- Message Encryption ---

    /**
     * Encrypt an application message as a PrivateMessage.
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        // Trim sentKeys if it grows too large
        if (sentKeys.size > MAX_SENT_KEYS) {
            val sortedKeys = sentKeys.keys.sorted()
            val toRemove = sortedKeys.take(sentKeys.size - MAX_SENT_KEYS)
            for (key in toRemove) {
                sentKeys.remove(key)
            }
        }

        val kng = secretTree.nextApplicationKeyNonce(myLeafIndex)
        sentKeys[kng.generation] = kng
        val ciphertext = MlsCryptoProvider.aeadEncrypt(kng.key, kng.nonce, ByteArray(0), plaintext)

        // Encrypt sender data
        val senderDataKey =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "key",
                ByteArray(0),
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val senderDataNonce =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "nonce",
                ByteArray(0),
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )

        val senderDataWriter =
            com.vitorpamplona.quartz.marmot.mls.codec
                .TlsWriter()
        senderDataWriter.putUint32(myLeafIndex.toLong())
        senderDataWriter.putUint32(kng.generation.toLong())
        val senderDataPlain = senderDataWriter.toByteArray()
        val encryptedSenderData =
            MlsCryptoProvider.aeadEncrypt(senderDataKey, senderDataNonce, ByteArray(0), senderDataPlain)

        val msg =
            PrivateMessage(
                groupId = groupId,
                epoch = epoch,
                contentType = ContentType.APPLICATION,
                authenticatedData = ByteArray(0),
                encryptedSenderData = encryptedSenderData,
                ciphertext = ciphertext,
            )

        return MlsMessage.fromPrivateMessage(msg).toTlsBytes()
    }

    /**
     * Decrypt an application message from a PrivateMessage.
     * Returns null if decryption fails (e.g., corrupted message, wrong epoch).
     */
    fun decryptOrNull(messageBytes: ByteArray): DecryptedMessage? =
        try {
            decrypt(messageBytes)
        } catch (_: Exception) {
            null
        }

    /**
     * Decrypt an application message from a PrivateMessage.
     * @throws IllegalArgumentException if the message format is invalid
     * @throws javax.crypto.AEADBadTagException if decryption fails
     */
    fun decrypt(messageBytes: ByteArray): DecryptedMessage {
        val mlsMsg = MlsMessage.decodeTls(TlsReader(messageBytes))
        require(mlsMsg.wireFormat == WireFormat.PRIVATE_MESSAGE) { "Expected PrivateMessage" }

        val privMsg = PrivateMessage.decodeTls(TlsReader(mlsMsg.payload))

        // Verify epoch and group ID match current state (RFC 9420 Section 6.1)
        require(privMsg.epoch == epoch) {
            "Message epoch ${privMsg.epoch} doesn't match current epoch $epoch"
        }
        require(privMsg.groupId.contentEquals(groupId)) {
            "Message group ID doesn't match current group"
        }

        // Decrypt sender data
        val senderDataKey =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "key",
                ByteArray(0),
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val senderDataNonce =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "nonce",
                ByteArray(0),
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )
        val senderDataPlain =
            MlsCryptoProvider.aeadDecrypt(senderDataKey, senderDataNonce, ByteArray(0), privMsg.encryptedSenderData)
        val senderReader = TlsReader(senderDataPlain)
        val senderLeafIndex = senderReader.readUint32().toInt()
        val generation = senderReader.readUint32().toInt()

        // Get the key/nonce for this sender+generation
        // If we sent this message ourselves, use the cached key to avoid ratchet conflict
        val kng =
            if (senderLeafIndex == myLeafIndex && sentKeys.containsKey(generation)) {
                sentKeys.remove(generation)!!
            } else {
                secretTree.applicationKeyNonceForGeneration(senderLeafIndex, generation)
            }

        // Decrypt content
        val plaintext = MlsCryptoProvider.aeadDecrypt(kng.key, kng.nonce, ByteArray(0), privMsg.ciphertext)

        return DecryptedMessage(
            senderLeafIndex = senderLeafIndex,
            contentType = privMsg.contentType,
            content = plaintext,
            epoch = privMsg.epoch,
        )
    }

    // --- Commit Processing ---

    /**
     * Process a received Commit message, advancing the epoch.
     *
     * @param commitBytes TLS-serialized Commit
     * @param senderLeafIndex the sender's leaf index in the ratchet tree
     * @param confirmationTag optional confirmation tag from the PublicMessage for verification
     */
    fun processCommit(
        commitBytes: ByteArray,
        senderLeafIndex: Int,
        confirmationTag: ByteArray? = null,
    ) {
        val commit = Commit.decodeTls(TlsReader(commitBytes))

        // External commits (containing ExternalInit) have a sender that is not
        // yet in the tree — their leaf will be added via the UpdatePath below.
        val isExternalCommit =
            commit.proposals.any {
                it is ProposalOrRef.Inline && it.proposal is Proposal.ExternalInit
            }

        if (isExternalCommit) {
            require(senderLeafIndex >= 0 && senderLeafIndex <= tree.leafCount) {
                "Invalid sender leaf index for external commit: $senderLeafIndex"
            }
        } else {
            require(senderLeafIndex >= 0 && senderLeafIndex < tree.leafCount) {
                "Invalid sender leaf index: $senderLeafIndex"
            }
            require(tree.getLeaf(senderLeafIndex) != null) {
                "Sender leaf is blank at index $senderLeafIndex"
            }
        }

        // Apply proposals (resolve references from pending pool)
        for (proposalOrRef in commit.proposals) {
            when (proposalOrRef) {
                is ProposalOrRef.Inline -> {
                    applyProposal(proposalOrRef.proposal, senderLeafIndex)
                }

                is ProposalOrRef.Reference -> {
                    // Resolve proposal by reference hash from pending proposals
                    val refHash = proposalOrRef.proposalRef
                    val resolved =
                        pendingProposals.find { pending ->
                            val proposalBytes = pending.proposal.toTlsBytes()
                            val hash = MlsCryptoProvider.refHash("MLS 1.0 Proposal Reference", proposalBytes)
                            hash.contentEquals(refHash)
                        }
                    if (resolved != null) {
                        applyProposal(resolved.proposal, resolved.senderLeafIndex)
                    }
                }
            }
        }

        // Process UpdatePath
        var commitSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        if (commit.updatePath != null) {
            val updatePath = commit.updatePath
            // Verify LeafNode signature (RFC 9420 Section 7.3)
            require(verifyLeafNodeSignature(updatePath.leafNode, groupId, senderLeafIndex)) {
                "Invalid LeafNode signature in UpdatePath"
            }
            tree.setLeaf(senderLeafIndex, updatePath.leafNode)
            tree.applyUpdatePath(senderLeafIndex, updatePath.nodes)

            // Verify parent hash chain (RFC 9420 Section 7.9.2)
            require(verifyParentHash(senderLeafIndex, updatePath)) {
                "Parent hash verification failed for UpdatePath"
            }

            // Decrypt path secret from our copath node
            val directPath = BinaryTree.directPath(senderLeafIndex, tree.leafCount)
            val myPath = BinaryTree.directPath(myLeafIndex, tree.leafCount)

            // Find the common ancestor
            val commonAncestorIdx = directPath.indexOfFirst { it in myPath }
            if (commonAncestorIdx >= 0 && commonAncestorIdx < updatePath.nodes.size) {
                val pathNode = updatePath.nodes[commonAncestorIdx]
                val copathNodeIdx = BinaryTree.copath(senderLeafIndex, tree.leafCount)[commonAncestorIdx]
                val resolution = tree.resolution(copathNodeIdx)

                // Find which encrypted secret corresponds to our position
                val myNodeIdx = BinaryTree.leafToNode(myLeafIndex)
                val myResIdx = resolution.indexOf(myNodeIdx)

                if (myResIdx >= 0 && myResIdx < pathNode.encryptedPathSecret.size) {
                    val ct = pathNode.encryptedPathSecret[myResIdx]
                    val pathSecret =
                        MlsCryptoProvider.decryptWithLabel(
                            encryptionPrivateKey,
                            "UpdatePathNode",
                            groupContext.toTlsBytes(),
                            ct.kemOutput,
                            ct.ciphertext,
                        )

                    // Derive remaining path secrets up to root
                    val remainingPath = directPath.drop(commonAncestorIdx)
                    var currentSecret = pathSecret
                    for (nodeIdx in remainingPath) {
                        currentSecret = MlsCryptoProvider.deriveSecret(currentSecret, "path")
                    }
                    commitSecret = currentSecret
                }
            }
        }

        // Update transcript hashes (RFC 9420 Section 8.2)
        // ConfirmedTranscriptHashInput = wire_format || FramedContent || signature
        // Simplified: use commit TLS bytes as the transcript input
        val confirmedTranscriptHashInput = buildConfirmedTranscriptHashInput(commit, senderLeafIndex)
        val confirmedInput = TlsWriter()
        confirmedInput.putBytes(interimTranscriptHash)
        confirmedInput.putBytes(confirmedTranscriptHashInput)
        val newConfirmedTranscriptHash = MlsCryptoProvider.hash(confirmedInput.toByteArray())

        // Advance epoch
        val newTreeHash = tree.treeHash()
        val newEpoch = groupContext.epoch + 1

        groupContext =
            groupContext.copy(
                epoch = newEpoch,
                treeHash = newTreeHash,
                confirmedTranscriptHash = newConfirmedTranscriptHash,
            )

        // Compute PSK secret from any PSK proposals in the commit
        val commitPskProposals =
            commit.proposals.mapNotNull {
                when (it) {
                    is ProposalOrRef.Inline -> it.proposal
                    is ProposalOrRef.Reference -> null
                }
            }
        val pskSecret = computePskSecret(commitPskProposals)

        // Check for ExternalInit proposal — overrides init_secret (RFC 9420 Section 8.3)
        val externalInitProposal =
            commitPskProposals.filterIsInstance<Proposal.ExternalInit>().firstOrNull()
        val effectiveInitSecret =
            if (externalInitProposal != null) {
                deriveExternalInitSecret(externalInitProposal.kemOutput)
            } else {
                initSecret
            }

        val keySchedule = KeySchedule(groupContext.toTlsBytes())
        epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, effectiveInitSecret, pskSecret)
        initSecret = epochSecrets.initSecret
        secretTree = SecretTree(epochSecrets.encryptionSecret, tree.leafCount)

        // Verify confirmation tag (RFC 9420 Section 6.1)
        if (confirmationTag != null) {
            val expectedTag = computeConfirmationTag(epochSecrets.confirmationKey, newConfirmedTranscriptHash)
            require(constantTimeEquals(confirmationTag, expectedTag)) {
                "Confirmation tag verification failed"
            }
        }

        // Update interim_transcript_hash for next epoch
        val computedTag = computeConfirmationTag(epochSecrets.confirmationKey, newConfirmedTranscriptHash)
        val interimInput = TlsWriter()
        interimInput.putBytes(newConfirmedTranscriptHash)
        interimInput.putOpaqueVarInt(computedTag)
        interimTranscriptHash = MlsCryptoProvider.hash(interimInput.toByteArray())

        pendingProposals.clear()
        sentKeys.clear()
    }

    // --- Exporter ---

    /**
     * MLS-Exporter function for deriving application-specific keys.
     *
     * Marmot uses:
     *   exporterSecret("marmot", "group-event".toByteArray(), 32)
     * to derive the outer ChaCha20-Poly1305 key for GroupEvents.
     */
    fun exporterSecret(
        label: String,
        context: ByteArray,
        length: Int,
    ): ByteArray = KeySchedule.mlsExporter(epochSecrets.exporterSecret, label, context, length)

    // --- External Join Support (RFC 9420 Section 8.3, 12.4.3.2) ---

    /**
     * Get the group's external public key for external commits.
     * Non-members use this to join the group via external commit.
     *
     * external_priv, external_pub = KEM.DeriveKeyPair(external_secret)
     */
    fun externalPub(): ByteArray {
        val kp = Hpke.deriveKeyPair(epochSecrets.externalSecret)
        return kp.publicKey
    }

    /**
     * Get the GroupInfo for publishing (needed by external joiners).
     * Includes the ratchet tree in extensions.
     */
    fun groupInfo(): GroupInfo {
        val treeWriter = TlsWriter()
        tree.encodeTls(treeWriter)
        val ratchetTreeExtension =
            Extension(
                extensionType = RATCHET_TREE_EXTENSION_TYPE,
                extensionData = treeWriter.toByteArray(),
            )
        val externalPubExtension =
            Extension(
                extensionType = EXTERNAL_PUB_EXTENSION_TYPE,
                extensionData = externalPub(),
            )

        // Build unsigned GroupInfo first, then sign its TBS encoding
        // (RFC 9420 Section 12.4.3.1: signature covers GroupInfoTBS =
        //  GroupContext || extensions || confirmationTag || signer)
        val unsigned =
            GroupInfo(
                groupContext = groupContext,
                extensions = listOf(ratchetTreeExtension, externalPubExtension),
                confirmationTag =
                    computeConfirmationTag(
                        epochSecrets.confirmationKey,
                        groupContext.confirmedTranscriptHash,
                    ),
                signer = myLeafIndex,
                signature = ByteArray(0),
            )

        return unsigned.copy(
            signature =
                MlsCryptoProvider.signWithLabel(
                    signingPrivateKey,
                    "GroupInfoTBS",
                    unsigned.encodeTbs(),
                ),
        )
    }

    /**
     * Derive the external init_secret from an ExternalInit proposal's kem_output.
     * Used by existing members when processing an external commit.
     */
    internal fun deriveExternalInitSecret(kemOutput: ByteArray): ByteArray {
        val externalKp = Hpke.deriveKeyPair(epochSecrets.externalSecret)
        val context = Hpke.setupBaseRExport(kemOutput, externalKp.privateKey, ByteArray(0))
        return context.export(
            "MLS 1.0 external init secret".encodeToByteArray(),
            MlsCryptoProvider.HASH_OUTPUT_LENGTH,
        )
    }

    // --- Private Helpers ---

    /**
     * Compute the PSK secret from PSK proposals (RFC 9420 Section 8.4).
     *
     * psk_secret is derived by chaining Extract calls over all PSK values.
     * If no PSK proposals, returns zeros (default PSK secret).
     */
    private fun computePskSecret(proposals: List<Proposal>): ByteArray {
        val pskProposals = proposals.filterIsInstance<Proposal.Psk>()
        if (pskProposals.isEmpty()) {
            return ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        }

        // Chain: psk_secret = Extract(Extract(...Extract(0, psk_1), psk_2)..., psk_n)
        var pskSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        for (pskProposal in pskProposals) {
            val pskValue =
                pskStore[pskProposal.pskId.toHexKey()]
                    ?: throw IllegalStateException("PSK not found in store: ${pskProposal.pskId.toHexKey()}")
            pskSecret = MlsCryptoProvider.hkdfExtract(pskSecret, pskValue)
        }
        return pskSecret
    }

    /**
     * Build the ConfirmedTranscriptHashInput (RFC 9420 Section 8.2).
     *
     * ConfirmedTranscriptHashInput = wire_format || FramedContent || signature
     * This is the AuthenticatedContent minus the confirmation_tag.
     */
    private fun buildConfirmedTranscriptHashInput(
        commit: Commit,
        senderLeafIndex: Int,
    ): ByteArray {
        val writer = TlsWriter()
        // wire_format: PublicMessage = 1
        writer.putUint16(WireFormat.PUBLIC_MESSAGE.value)
        // FramedContent: group_id, epoch, sender, authenticated_data, content_type, content
        writer.putOpaqueVarInt(groupId)
        writer.putUint64(epoch)
        // Sender: member type (1) + leaf_index
        writer.putUint8(1) // SenderType.MEMBER
        writer.putUint32(senderLeafIndex.toLong())
        writer.putOpaqueVarInt(ByteArray(0)) // authenticated_data (empty)
        writer.putUint8(ContentType.COMMIT.value) // content_type
        commit.encodeTls(writer) // content = Commit
        // signature (placeholder — in a full implementation this would be the actual signature)
        writer.putOpaqueVarInt(ByteArray(0))
        return writer.toByteArray()
    }

    private fun computeConfirmationTag(
        confirmationKey: ByteArray,
        confirmedTranscriptHash: ByteArray,
    ): ByteArray {
        val mac = MacInstance("HmacSHA256", confirmationKey)
        mac.update(confirmedTranscriptHash)
        return mac.doFinal()
    }

    /**
     * Verify parent hash chain in UpdatePath (RFC 9420 Section 7.9.2).
     *
     * For each node on the sender's direct path, verifies that the parent_hash
     * in the child matches Hash(ParentHashInput) computed from the parent node.
     *
     * ParentHashInput = {
     *   HPKEPublicKey encryption_key;    // parent's new HPKE key
     *   opaque parent_hash<V>;           // parent's own parent_hash
     *   opaque original_sibling_tree_hash<V>; // tree hash of the sibling subtree
     * }
     */
    private fun verifyParentHash(
        senderLeafIndex: Int,
        updatePath: UpdatePath,
    ): Boolean {
        val directPath = BinaryTree.directPath(senderLeafIndex, tree.leafCount)
        if (directPath.isEmpty()) return true

        val leafParentHash = updatePath.leafNode.parentHash ?: return true
        if (leafParentHash.isEmpty()) return true

        val nodeCount = BinaryTree.nodeCount(tree.leafCount)

        // Walk up the direct path, verifying each parent_hash link
        var expectedParentHash = leafParentHash
        for ((i, pathNodeIdx) in directPath.withIndex()) {
            val pathNode = tree.getNode(pathNodeIdx) ?: continue

            if (pathNode is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                // Compute the parent hash for this node
                val childIdx =
                    if (i == 0) BinaryTree.leafToNode(senderLeafIndex) else directPath[i - 1]
                val siblingIdx = BinaryTree.sibling(childIdx, nodeCount)

                // original_sibling_tree_hash = tree hash of the sibling subtree
                // (computed BEFORE the UpdatePath was applied)
                val siblingHash = tree.treeHashNode(siblingIdx)

                // ParentHashInput
                val phi = TlsWriter()
                phi.putOpaqueVarInt(pathNode.parentNode.encryptionKey)
                phi.putOpaqueVarInt(pathNode.parentNode.parentHash)
                phi.putOpaqueVarInt(siblingHash)
                val computedHash = MlsCryptoProvider.hash(phi.toByteArray())

                if (!expectedParentHash.contentEquals(computedHash)) {
                    return false // Parent hash chain broken
                }

                // The next level's expected parent_hash is this node's parent_hash
                expectedParentHash = pathNode.parentNode.parentHash
            }
        }

        return true
    }

    private fun verifyLeafNodeSignature(
        leafNode: LeafNode,
        groupId: ByteArray,
        leafIndex: Int,
    ): Boolean {
        val tbs = leafNode.encodeTbs(groupId, leafIndex)
        return MlsCryptoProvider.verifyWithLabel(
            leafNode.signatureKey,
            "LeafNodeTBS",
            tbs,
            leafNode.signature,
        )
    }

    /**
     * Verify the membership_tag on a PublicMessage (RFC 9420 Section 6.2).
     * membership_tag = HMAC(membership_key, AuthenticatedContentTBM)
     * where AuthenticatedContentTBM = AuthenticatedContent with the membership_tag zeroed.
     *
     * @param authenticatedContentBytes the TLS-serialized AuthenticatedContent (without tag)
     * @param membershipTag the received membership_tag to verify
     * @return true if the tag is valid
     */
    fun verifyMembershipTag(
        authenticatedContentBytes: ByteArray,
        membershipTag: ByteArray,
    ): Boolean {
        val mac =
            com.vitorpamplona.quartz.utils.mac
                .MacInstance("HmacSHA256", epochSecrets.membershipKey)
        mac.update(authenticatedContentBytes)
        val expectedTag = mac.doFinal()
        return constantTimeEquals(expectedTag, membershipTag)
    }

    /**
     * Constant-time byte array comparison to prevent timing side-channels.
     * Returns true only if both arrays have the same length and contents.
     */
    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Apply an Add proposal and return the assigned leaf index.
     */
    private fun applyProposalAdd(proposal: Proposal.Add): Int {
        val lifetime = proposal.keyPackage.leafNode.lifetime
        if (lifetime != null) {
            val now = TimeUtils.now()
            require(now >= lifetime.notBefore && now <= lifetime.notAfter) {
                "KeyPackage lifetime expired or not yet valid"
            }
        }
        return tree.addLeaf(proposal.keyPackage.leafNode)
    }

    private fun applyProposal(
        proposal: Proposal,
        senderLeafIndex: Int,
    ) {
        when (proposal) {
            is Proposal.Add -> {
                applyProposalAdd(proposal)
            }

            is Proposal.Remove -> {
                // Validate: sender must be a member and cannot remove themselves via Remove
                // (use SelfRemove for that). The committer is authorized to include Remove proposals.
                require(proposal.removedLeafIndex != senderLeafIndex) {
                    "Use SelfRemove to remove yourself, not Remove"
                }
                require(proposal.removedLeafIndex < tree.leafCount) {
                    "Remove target leaf index ${proposal.removedLeafIndex} out of range"
                }
                require(tree.getLeaf(proposal.removedLeafIndex) != null) {
                    "Cannot remove blank leaf at index ${proposal.removedLeafIndex}"
                }
                tree.removeLeaf(proposal.removedLeafIndex)
            }

            is Proposal.SelfRemove -> {
                tree.removeLeaf(senderLeafIndex)
            }

            is Proposal.Update -> {
                // Validate: Update can only update the sender's own leaf (RFC 9420 Section 12.1.2)
                // Verify the new LeafNode is signed by the sender's key
                require(
                    verifyLeafNodeSignature(proposal.leafNode, groupId, senderLeafIndex),
                ) { "Invalid LeafNode signature in Update proposal" }
                tree.setLeaf(senderLeafIndex, proposal.leafNode)
            }

            is Proposal.GroupContextExtensions -> {
                // Validate extension types are supported (RFC 9420 Section 12.1.7)
                for (ext in proposal.extensions) {
                    require(ext.extensionType in KNOWN_EXTENSION_TYPES) {
                        "Unsupported extension type: ${ext.extensionType}"
                    }
                }
                groupContext = groupContext.copy(extensions = proposal.extensions)
            }

            is Proposal.Psk -> {
                // PSK proposals are collected and included in the key schedule
                // The actual PSK value is resolved from the PSK store
            }

            is Proposal.ReInit -> {
                // Mark the group as pending reinitialization.
                // After this commit is processed, the application must create a new group
                // with the parameters specified in the ReInit proposal.
                reInitPending = proposal
            }

            is Proposal.ExternalInit -> {} // Handled in external commit flow
        }
    }

    private fun buildWelcome(addedMembers: List<Pair<Int, MlsKeyPackage>>): ByteArray {
        // Add ratchet tree as GroupInfo extension (RFC 9420 Section 12.4.3.3)
        val treeWriter = TlsWriter()
        tree.encodeTls(treeWriter)
        val ratchetTreeExtension =
            Extension(
                extensionType = RATCHET_TREE_EXTENSION_TYPE,
                extensionData = treeWriter.toByteArray(),
            )

        // Build unsigned GroupInfo first, then sign its TBS encoding
        // (RFC 9420 Section 12.4.3.1: signature covers GroupInfoTBS =
        //  GroupContext || extensions || confirmationTag || signer)
        val unsignedGroupInfo =
            GroupInfo(
                groupContext = groupContext,
                extensions = listOf(ratchetTreeExtension),
                confirmationTag =
                    MlsCryptoProvider.expandWithLabel(
                        epochSecrets.confirmationKey,
                        "confirmation",
                        groupContext.confirmedTranscriptHash,
                        MlsCryptoProvider.HASH_OUTPUT_LENGTH,
                    ),
                signer = myLeafIndex,
                signature = ByteArray(0),
            )
        val groupInfo =
            unsignedGroupInfo.copy(
                signature =
                    MlsCryptoProvider.signWithLabel(
                        signingPrivateKey,
                        "GroupInfoTBS",
                        unsignedGroupInfo.encodeTbs(),
                    ),
            )

        // Encrypt GroupInfo with welcome_secret
        val welcomeKey =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.welcomeSecret,
                "key",
                ByteArray(0),
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val welcomeNonce =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.welcomeSecret,
                "nonce",
                ByteArray(0),
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )
        val groupInfoBytes = groupInfo.toTlsBytes()
        val encryptedGroupInfo = MlsCryptoProvider.aeadEncrypt(welcomeKey, welcomeNonce, ByteArray(0), groupInfoBytes)

        // Build per-member encrypted group secrets
        val secrets =
            addedMembers.map { (leafIdx, kp) ->
                val groupSecrets =
                    GroupSecrets(
                        joinerSecret = epochSecrets.joinerSecret,
                        pathSecret = null,
                    )
                val gsBytes = groupSecrets.toTlsBytes()

                // HPKE-encrypt to the member's init_key
                val hpkeCt =
                    MlsCryptoProvider.encryptWithLabel(
                        kp.initKey,
                        "Welcome",
                        ByteArray(0),
                        gsBytes,
                    )

                EncryptedGroupSecrets(kp.reference(), hpkeCt)
            }

        val welcome =
            Welcome(
                cipherSuite = 1,
                secrets = secrets,
                encryptedGroupInfo = encryptedGroupInfo,
            )

        return MlsMessage(
            wireFormat = WireFormat.WELCOME,
            payload = welcome.toTlsBytes(),
        ).toTlsBytes()
    }

    companion object {
        private const val MAX_SENT_KEYS = 10_000
        private const val RATCHET_TREE_EXTENSION_TYPE = 0x0001
        private const val REQUIRED_CAPABILITIES_EXTENSION_TYPE = 0x0002
        private const val EXTERNAL_PUB_EXTENSION_TYPE = 0x0003
        private const val EXTERNAL_SENDERS_EXTENSION_TYPE = 0x0004

        /** Known extension types that this implementation accepts. */
        private val KNOWN_EXTENSION_TYPES =
            setOf(
                RATCHET_TREE_EXTENSION_TYPE,
                REQUIRED_CAPABILITIES_EXTENSION_TYPE,
                EXTERNAL_PUB_EXTENSION_TYPE,
                EXTERNAL_SENDERS_EXTENSION_TYPE,
                0xF2EE, // Marmot group data extension
            )

        /**
         * Create a new MLS group with a single member (the creator).
         */
        fun create(
            identity: ByteArray,
            signingKey: ByteArray? = null,
        ): MlsGroup {
            val sigKp =
                signingKey?.let { key ->
                    val pub = Ed25519.publicFromPrivate(key)
                    com.vitorpamplona.quartz.marmot.mls.crypto
                        .Ed25519KeyPair(key, pub)
                } ?: Ed25519.generateKeyPair()

            val encKp = X25519.generateKeyPair()
            val groupId = MlsCryptoProvider.randomBytes(32)

            val leafNode =
                buildLeafNode(
                    encryptionKey = encKp.publicKey,
                    signatureKey = sigKp.publicKey,
                    identity = identity,
                    source = LeafNodeSource.KEY_PACKAGE,
                    signingKey = sigKp.privateKey,
                )

            val tree = RatchetTree(1)
            tree.setLeaf(0, leafNode)

            val treeHash = tree.treeHash()
            val groupContext =
                GroupContext(
                    groupId = groupId,
                    epoch = 0,
                    treeHash = treeHash,
                    confirmedTranscriptHash = ByteArray(0),
                )

            // Initial key schedule with zero secrets
            val initSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            val commitSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)

            val keySchedule = KeySchedule(groupContext.toTlsBytes())
            val epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, initSecret)

            val secretTree = SecretTree(epochSecrets.encryptionSecret, 1)

            return MlsGroup(
                groupContext = groupContext,
                tree = tree,
                myLeafIndex = 0,
                epochSecrets = epochSecrets,
                secretTree = secretTree,
                initSecret = epochSecrets.initSecret,
                signingPrivateKey = sigKp.privateKey,
                encryptionPrivateKey = encKp.privateKey,
                interimTranscriptHash = ByteArray(0),
            )
        }

        /**
         * Join a group by processing a Welcome message.
         *
         * @param welcomeBytes the MLSMessage-wrapped Welcome
         * @param bundle the KeyPackageBundle that was used to create the invitation
         */
        fun processWelcome(
            welcomeBytes: ByteArray,
            bundle: KeyPackageBundle,
        ): MlsGroup {
            val mlsMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
            require(mlsMsg.wireFormat == WireFormat.WELCOME) { "Expected Welcome message" }

            val welcome = Welcome.decodeTls(TlsReader(mlsMsg.payload))

            // Verify ciphersuite match (RFC 9420 Section 12.4.3.1)
            require(welcome.cipherSuite == bundle.keyPackage.cipherSuite) {
                "Welcome ciphersuite ${welcome.cipherSuite} doesn't match KeyPackage ciphersuite ${bundle.keyPackage.cipherSuite}"
            }

            // Find our encrypted group secrets
            val myRef = bundle.keyPackage.reference()
            val mySecrets =
                welcome.secrets.find { it.newMember.contentEquals(myRef) }
                    ?: throw IllegalArgumentException("Welcome does not contain secrets for our KeyPackage")

            // HPKE-decrypt group secrets
            val gsBytes =
                MlsCryptoProvider.decryptWithLabel(
                    bundle.initPrivateKey,
                    "Welcome",
                    ByteArray(0),
                    mySecrets.encryptedGroupSecrets.kemOutput,
                    mySecrets.encryptedGroupSecrets.ciphertext,
                )
            val groupSecrets = GroupSecrets.decodeTls(TlsReader(gsBytes))

            // Decrypt GroupInfo with welcome_secret
            // We need to derive welcome_secret from joiner_secret
            // Since we don't have the full group context yet, use a placeholder
            // Then reconstruct after decrypting GroupInfo
            val pskSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)

            // Derive welcome_key/nonce from joiner_secret
            // welcome_secret = DeriveSecret(Extract(joiner_secret, psk_secret), "welcome")
            val memberSecret = MlsCryptoProvider.hkdfExtract(groupSecrets.joinerSecret, pskSecret)
            val welcomeSecret = MlsCryptoProvider.deriveSecret(memberSecret, "welcome")
            val welcomeKey =
                MlsCryptoProvider.expandWithLabel(
                    welcomeSecret,
                    "key",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_KEY_LENGTH,
                )
            val welcomeNonce =
                MlsCryptoProvider.expandWithLabel(
                    welcomeSecret,
                    "nonce",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_NONCE_LENGTH,
                )

            val groupInfoBytes =
                MlsCryptoProvider.aeadDecrypt(welcomeKey, welcomeNonce, ByteArray(0), welcome.encryptedGroupInfo)
            val groupInfo = GroupInfo.decodeTls(TlsReader(groupInfoBytes))
            val groupContext = groupInfo.groupContext

            // Reconstruct ratchet tree from GroupInfo extensions
            val ratchetTreeExt = groupInfo.extensions.find { it.extensionType == RATCHET_TREE_EXTENSION_TYPE }

            // Verify GroupInfo signature (RFC 9420 Section 12.4.3.1)
            // The signer's public key comes from the ratchet tree at the signer's leaf index
            val tree =
                if (ratchetTreeExt != null) {
                    RatchetTree.decodeTls(TlsReader(ratchetTreeExt.extensionData))
                } else {
                    // Fallback: minimal tree (for groups delivered without inline tree)
                    RatchetTree(1)
                }

            // Find our leaf index by matching our signature key
            val mySignatureKey = Ed25519.publicFromPrivate(bundle.signaturePrivateKey)
            var myLeafIndex = -1
            for (i in 0 until tree.leafCount) {
                val leaf = tree.getLeaf(i)
                if (leaf != null && leaf.signatureKey.contentEquals(mySignatureKey)) {
                    myLeafIndex = i
                    break
                }
            }
            require(myLeafIndex >= 0) { "Joiner's signature key not found in ratchet tree" }

            // Verify GroupInfo signature using signer's key from the tree
            val signerLeaf = tree.getLeaf(groupInfo.signer)
            requireNotNull(signerLeaf) {
                "Signer leaf is null at index ${groupInfo.signer} — cannot verify GroupInfo signature"
            }
            require(groupInfo.verifySignature(signerLeaf.signatureKey)) {
                "Invalid GroupInfo signature"
            }

            // Derive epoch secrets directly from memberSecret (RFC 9420 Section 8.3)
            // For Welcome, epoch_secret = ExpandWithLabel(member_secret, "epoch", GroupContext, Nh)
            val epochSecret =
                MlsCryptoProvider.expandWithLabel(memberSecret, "epoch", groupContext.toTlsBytes(), MlsCryptoProvider.HASH_OUTPUT_LENGTH)

            // Derive all sub-secrets from epochSecret
            val senderDataSecret = MlsCryptoProvider.deriveSecret(epochSecret, "sender data")
            val encryptionSecret = MlsCryptoProvider.deriveSecret(epochSecret, "encryption")
            val exporterSecret = MlsCryptoProvider.deriveSecret(epochSecret, "exporter")
            val epochAuthenticator = MlsCryptoProvider.deriveSecret(epochSecret, "authentication")
            val externalSecret = MlsCryptoProvider.deriveSecret(epochSecret, "external")
            val confirmationKey = MlsCryptoProvider.deriveSecret(epochSecret, "confirm")
            val membershipKey = MlsCryptoProvider.deriveSecret(epochSecret, "membership")
            val resumptionPsk = MlsCryptoProvider.deriveSecret(epochSecret, "resumption")
            val initSecret = MlsCryptoProvider.deriveSecret(epochSecret, "init")

            val epochSecrets =
                EpochSecrets(
                    joinerSecret = groupSecrets.joinerSecret,
                    welcomeSecret = welcomeSecret,
                    epochSecret = epochSecret,
                    senderDataSecret = senderDataSecret,
                    encryptionSecret = encryptionSecret,
                    exporterSecret = exporterSecret,
                    epochAuthenticator = epochAuthenticator,
                    externalSecret = externalSecret,
                    confirmationKey = confirmationKey,
                    membershipKey = membershipKey,
                    resumptionPsk = resumptionPsk,
                    initSecret = initSecret,
                )

            val secretTree = SecretTree(epochSecrets.encryptionSecret, tree.leafCount)

            // Compute interim_transcript_hash from confirmed_transcript_hash + confirmation_tag
            val confirmMac = MacInstance("HmacSHA256", epochSecrets.confirmationKey)
            confirmMac.update(groupContext.confirmedTranscriptHash)
            val confirmationTag = confirmMac.doFinal()
            val interimInput = TlsWriter()
            interimInput.putBytes(groupContext.confirmedTranscriptHash)
            interimInput.putOpaqueVarInt(confirmationTag)
            val interimTranscriptHash = MlsCryptoProvider.hash(interimInput.toByteArray())

            return MlsGroup(
                groupContext = groupContext,
                tree = tree,
                myLeafIndex = myLeafIndex,
                epochSecrets = epochSecrets,
                secretTree = secretTree,
                initSecret = epochSecrets.initSecret,
                signingPrivateKey = bundle.signaturePrivateKey,
                encryptionPrivateKey = bundle.encryptionPrivateKey,
                interimTranscriptHash = interimTranscriptHash,
            )
        }

        /**
         * Join a group via external commit (RFC 9420 Section 12.4.3.2).
         *
         * The joiner obtains the GroupInfo (with ratchet tree and external_pub),
         * creates an ExternalInit proposal with HPKE encapsulation, and produces
         * a Commit that existing members process to add the joiner.
         *
         * @param groupInfoBytes TLS-serialized GroupInfo
         * @param identity the joiner's identity
         * @param signingKey optional Ed25519 signing key (generated if null)
         * @return the new MlsGroup and the commit bytes to send to the group
         */
        fun externalJoin(
            groupInfoBytes: ByteArray,
            identity: ByteArray,
            signingKey: ByteArray? = null,
        ): Pair<MlsGroup, ByteArray> {
            val groupInfo = GroupInfo.decodeTls(TlsReader(groupInfoBytes))
            val groupContext = groupInfo.groupContext

            // Extract ratchet tree from extensions
            val ratchetTreeExt =
                groupInfo.extensions.find { it.extensionType == RATCHET_TREE_EXTENSION_TYPE }
                    ?: throw IllegalArgumentException("GroupInfo missing ratchet_tree extension")
            val tree = RatchetTree.decodeTls(TlsReader(ratchetTreeExt.extensionData))

            // Extract external_pub from extensions
            val externalPubExt =
                groupInfo.extensions.find { it.extensionType == EXTERNAL_PUB_EXTENSION_TYPE }
                    ?: throw IllegalArgumentException("GroupInfo missing external_pub extension")
            val externalPub = externalPubExt.extensionData

            // Generate key pairs
            val sigKp =
                signingKey?.let { key ->
                    val pub = Ed25519.publicFromPrivate(key)
                    Ed25519KeyPair(key, pub)
                } ?: Ed25519.generateKeyPair()
            val encKp = X25519.generateKeyPair()

            // HPKE encapsulation to external_pub to derive init_secret
            val (kemOutput, exportContext) = Hpke.setupBaseSExport(externalPub, ByteArray(0))
            val externalInitSecret =
                exportContext.export(
                    "MLS 1.0 external init secret".encodeToByteArray(),
                    MlsCryptoProvider.HASH_OUTPUT_LENGTH,
                )

            // Build ExternalInit proposal
            val externalInitProposal = Proposal.ExternalInit(kemOutput)

            // Add ourselves to the tree
            val leafNode =
                buildLeafNode(
                    encryptionKey = encKp.publicKey,
                    signatureKey = sigKp.publicKey,
                    identity = identity,
                    source = LeafNodeSource.COMMIT,
                    signingKey = sigKp.privateKey,
                    groupId = groupContext.groupId,
                    leafIndex = tree.leafCount, // We'll be the next leaf
                )
            val myLeafIndex = tree.addLeaf(leafNode)

            // Build UpdatePath for our leaf
            val leafSecret = MlsCryptoProvider.randomBytes(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            val pathSecrets = tree.derivePathSecrets(myLeafIndex, leafSecret)
            val copath = BinaryTree.copath(myLeafIndex, tree.leafCount)
            val pathNodes =
                pathSecrets.zip(copath).map { (pathKey, copathNode) ->
                    val resolution = tree.resolution(copathNode)
                    val encryptedSecrets =
                        resolution.mapNotNull { resNode ->
                            val node = tree.getNode(resNode) ?: return@mapNotNull null
                            val recipientPub =
                                when (node) {
                                    is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Leaf -> {
                                        node.leafNode.encryptionKey
                                    }

                                    is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent -> {
                                        node.parentNode.encryptionKey
                                    }
                                }
                            MlsCryptoProvider.encryptWithLabel(
                                recipientPub,
                                "UpdatePathNode",
                                groupContext.toTlsBytes(),
                                pathKey.pathSecret,
                            )
                        }
                    UpdatePathNode(pathKey.publicKey, encryptedSecrets)
                }

            val updatePath = UpdatePath(leafNode, pathNodes)
            tree.applyUpdatePath(myLeafIndex, updatePath.nodes)

            // Build Commit
            val commit =
                Commit(
                    proposals = listOf(ProposalOrRef.Inline(externalInitProposal)),
                    updatePath = updatePath,
                )
            val commitBytes = commit.toTlsBytes()

            // Derive epoch secrets using external init_secret
            val commitSecret =
                if (pathSecrets.isNotEmpty()) {
                    pathSecrets.last().pathSecret
                } else {
                    ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
                }

            val newTreeHash = tree.treeHash()
            val newEpoch = groupContext.epoch + 1
            val newGroupContext =
                groupContext.copy(
                    epoch = newEpoch,
                    treeHash = newTreeHash,
                )

            val keySchedule = KeySchedule(newGroupContext.toTlsBytes())
            val epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, externalInitSecret)

            val secretTree = SecretTree(epochSecrets.encryptionSecret, tree.leafCount)

            val group =
                MlsGroup(
                    groupContext = newGroupContext,
                    tree = tree,
                    myLeafIndex = myLeafIndex,
                    epochSecrets = epochSecrets,
                    secretTree = secretTree,
                    initSecret = epochSecrets.initSecret,
                    signingPrivateKey = sigKp.privateKey,
                    encryptionPrivateKey = encKp.privateKey,
                    interimTranscriptHash = ByteArray(0),
                )

            return Pair(group, commitBytes)
        }

        /**
         * Restore a group from a previously saved [MlsGroupState].
         *
         * The SecretTree is reconstructed from the stored encryption_secret.
         * Note: SecretTree ratchet state (per-sender generation counters) is
         * NOT preserved — messages sent/received before the save point cannot
         * be re-decrypted, which is acceptable because they would already
         * have been processed.
         */
        fun restore(state: MlsGroupState): MlsGroup {
            val tree = RatchetTree.decodeTls(TlsReader(state.treeBytes))
            val secretTree = SecretTree(state.encryptionSecret, tree.leafCount)

            return MlsGroup(
                groupContext = state.groupContext,
                tree = tree,
                myLeafIndex = state.myLeafIndex,
                epochSecrets = state.epochSecrets,
                secretTree = secretTree,
                initSecret = state.initSecret,
                signingPrivateKey = state.signingPrivateKey,
                encryptionPrivateKey = state.encryptionPrivateKey,
                interimTranscriptHash = state.interimTranscriptHash,
            )
        }

        /**
         * Build a LeafNode with signature.
         */
        private fun buildLeafNode(
            encryptionKey: ByteArray,
            signatureKey: ByteArray,
            identity: ByteArray,
            source: LeafNodeSource,
            signingKey: ByteArray,
            groupId: ByteArray? = null,
            leafIndex: Int? = null,
        ): LeafNode {
            val unsigned =
                LeafNode(
                    encryptionKey = encryptionKey,
                    signatureKey = signatureKey,
                    credential = Credential.Basic(identity),
                    capabilities = Capabilities(),
                    leafNodeSource = source,
                    lifetime =
                        if (source == LeafNodeSource.KEY_PACKAGE) {
                            Lifetime(0, Long.MAX_VALUE)
                        } else {
                            null
                        },
                    extensions = emptyList(),
                    signature = ByteArray(0), // Placeholder
                )

            val tbs = unsigned.encodeTbs(groupId, leafIndex)
            val signature = MlsCryptoProvider.signWithLabel(signingKey, "LeafNodeTBS", tbs)

            return unsigned.copy(signature = signature)
        }
    }

    /**
     * Add a member to the group by their KeyPackage.
     * Creates and applies a Commit with an Add proposal.
     */
    fun addMember(keyPackageBytes: ByteArray): CommitResult {
        proposeAdd(keyPackageBytes)
        return commit()
    }

    /**
     * Remove a member from the group.
     * Creates and applies a Commit with a Remove proposal.
     */
    fun removeMember(targetLeafIndex: Int): CommitResult {
        proposeRemove(targetLeafIndex)
        return commit()
    }

    /**
     * Remove self from the group.
     */
    fun selfRemove(): ByteArray {
        val proposal = Proposal.SelfRemove()
        return proposal.toTlsBytes()
    }
}

data class PendingProposal(
    val proposal: Proposal,
    val senderLeafIndex: Int,
)

data class DecryptedMessage(
    val senderLeafIndex: Int,
    val contentType: ContentType,
    val content: ByteArray,
    val epoch: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecryptedMessage) return false
        return senderLeafIndex == other.senderLeafIndex &&
            content.contentEquals(other.content) &&
            epoch == other.epoch
    }

    override fun hashCode(): Int {
        var result = senderLeafIndex
        result = 31 * result + content.contentHashCode()
        result = 31 * result + epoch.hashCode()
        return result
    }
}
