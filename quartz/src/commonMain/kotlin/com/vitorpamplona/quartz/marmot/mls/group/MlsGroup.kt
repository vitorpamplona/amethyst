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
    private val pendingProposals: MutableList<PendingProposal> = mutableListOf(),
    private val sentKeys: MutableMap<Int, com.vitorpamplona.quartz.marmot.mls.schedule.KeyNonceGeneration> = mutableMapOf(),
) {
    val groupId: ByteArray get() = groupContext.groupId
    val epoch: Long get() = groupContext.epoch
    val leafIndex: Int get() = myLeafIndex

    val memberCount: Int
        get() {
            var count = 0
            for (i in 0 until tree.leafCount) {
                if (tree.getLeaf(i) != null) count++
            }
            return count
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

        val kp =
            MlsKeyPackage(
                initKey = initKp.publicKey,
                leafNode = leafNode,
                signature = MlsCryptoProvider.signWithLabel(sigKp.privateKey, "KeyPackageTBS", leafNode.toTlsBytes()),
            )

        return KeyPackageBundle(kp, initKp.privateKey, encKp.privateKey, sigKp.privateKey)
    }

    // --- Proposal Creation ---

    /**
     * Create an Add proposal for a new member.
     */
    fun proposeAdd(keyPackageBytes: ByteArray): Proposal.Add {
        val kp = MlsKeyPackage.decodeTls(TlsReader(keyPackageBytes))
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
            when (val p = pending.proposal) {
                is Proposal.Add -> {
                    val leafIdx = tree.addLeaf(p.keyPackage.leafNode)
                    addedMembers.add(leafIdx to p.keyPackage)
                }

                is Proposal.Remove -> {
                    tree.removeLeaf(p.removedLeafIndex)
                }

                is Proposal.SelfRemove -> {
                    tree.removeLeaf(pending.senderLeafIndex)
                }

                is Proposal.Update -> {
                    tree.setLeaf(pending.senderLeafIndex, p.leafNode)
                }

                is Proposal.GroupContextExtensions -> {
                    groupContext =
                        groupContext.copy(extensions = p.extensions)
                }

                is Proposal.Psk -> {}

                // PSK handling
                is Proposal.ReInit -> {}

                // Handled at a higher level
                is Proposal.ExternalInit -> {} // Handled in external commit flow
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
        // For the committer, we use the commit content to update the transcript
        val commitTlsBytes = commit.toTlsBytes()
        val confirmedInput = TlsWriter()
        confirmedInput.putBytes(interimTranscriptHash)
        confirmedInput.putBytes(commitTlsBytes) // Simplified: use commit bytes as ConfirmedTranscriptHashInput
        val newConfirmedTranscriptHash = MlsCryptoProvider.hash(confirmedInput.toByteArray())

        val newTreeHash = tree.treeHash()
        val newEpoch = groupContext.epoch + 1

        groupContext =
            groupContext.copy(
                epoch = newEpoch,
                treeHash = newTreeHash,
                confirmedTranscriptHash = newConfirmedTranscriptHash,
            )

        val keySchedule = KeySchedule(groupContext.toTlsBytes())
        epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, initSecret)
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
     */
    fun decrypt(messageBytes: ByteArray): DecryptedMessage {
        val mlsMsg = MlsMessage.decodeTls(TlsReader(messageBytes))
        require(mlsMsg.wireFormat == WireFormat.PRIVATE_MESSAGE) { "Expected PrivateMessage" }

        val privMsg = PrivateMessage.decodeTls(TlsReader(mlsMsg.payload))

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
     */
    fun processCommit(
        commitBytes: ByteArray,
        senderLeafIndex: Int,
    ) {
        val commit = Commit.decodeTls(TlsReader(commitBytes))

        // Apply proposals
        for (proposalOrRef in commit.proposals) {
            when (proposalOrRef) {
                is ProposalOrRef.Inline -> {
                    applyProposal(proposalOrRef.proposal, senderLeafIndex)
                }

                is ProposalOrRef.Reference -> {
                    // Look up by reference hash in pending proposals
                    // For now, skip reference-based proposals
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
        val commitTlsBytes = commit.toTlsBytes()
        val confirmedInput = TlsWriter()
        confirmedInput.putBytes(interimTranscriptHash)
        confirmedInput.putBytes(commitTlsBytes) // Simplified: use commit bytes as ConfirmedTranscriptHashInput
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

        val keySchedule = KeySchedule(groupContext.toTlsBytes())
        epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, initSecret)
        initSecret = epochSecrets.initSecret
        secretTree = SecretTree(epochSecrets.encryptionSecret, tree.leafCount)

        // TODO: Verify confirmation tag (RFC 9420 Section 8.1)
        // Full verification requires the PublicMessage's confirmation_tag,
        // which would be passed as a parameter in a complete implementation.
        // confirmation_tag = MAC(confirmation_key, confirmed_transcript_hash)

        // Update interim_transcript_hash for next epoch
        val confirmationTag = computeConfirmationTag(epochSecrets.confirmationKey, newConfirmedTranscriptHash)
        val interimInput = TlsWriter()
        interimInput.putBytes(newConfirmedTranscriptHash)
        interimInput.putOpaqueVarInt(confirmationTag)
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

    // --- Private Helpers ---

    private fun computeConfirmationTag(
        confirmationKey: ByteArray,
        confirmedTranscriptHash: ByteArray,
    ): ByteArray {
        val mac = MacInstance("HmacSHA256", confirmationKey)
        mac.update(confirmedTranscriptHash)
        return mac.doFinal()
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
        return expectedTag.contentEquals(membershipTag)
    }

    private fun applyProposal(
        proposal: Proposal,
        senderLeafIndex: Int,
    ) {
        when (proposal) {
            is Proposal.Add -> {
                tree.addLeaf(proposal.keyPackage.leafNode)
            }

            is Proposal.Remove -> {
                tree.removeLeaf(proposal.removedLeafIndex)
            }

            is Proposal.SelfRemove -> {
                tree.removeLeaf(senderLeafIndex)
            }

            is Proposal.Update -> {
                tree.setLeaf(senderLeafIndex, proposal.leafNode)
            }

            is Proposal.GroupContextExtensions -> {
                groupContext = groupContext.copy(extensions = proposal.extensions)
            }

            is Proposal.Psk -> {}

            is Proposal.ReInit -> {}

            // Handled at a higher level
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

        // Build GroupInfo
        val groupInfo =
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
                signature =
                    MlsCryptoProvider.signWithLabel(
                        signingPrivateKey,
                        "GroupInfoTBS",
                        groupContext.toTlsBytes(),
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
        private const val RATCHET_TREE_EXTENSION_TYPE = 0x0001

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
            val tree =
                if (ratchetTreeExt != null) {
                    RatchetTree.decodeTls(TlsReader(ratchetTreeExt.extensionData))
                } else {
                    // Fallback: minimal tree (for groups delivered without inline tree)
                    RatchetTree(1)
                }

            // Find our leaf index by matching our signature key
            val mySignatureKey = Ed25519.publicFromPrivate(bundle.signaturePrivateKey)
            var myLeafIndex = 0
            for (i in 0 until tree.leafCount) {
                val leaf = tree.getLeaf(i)
                if (leaf != null && leaf.signatureKey.contentEquals(mySignatureKey)) {
                    myLeafIndex = i
                    break
                }
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

            return MlsGroup(
                groupContext = groupContext,
                tree = tree,
                myLeafIndex = myLeafIndex,
                epochSecrets = epochSecrets,
                secretTree = secretTree,
                initSecret = epochSecrets.initSecret,
                signingPrivateKey = bundle.signaturePrivateKey,
                encryptionPrivateKey = bundle.encryptionPrivateKey,
                interimTranscriptHash = ByteArray(0),
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
