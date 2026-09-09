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

import com.vitorpamplona.quartz.marmot.appComponents.AdminPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupState
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamCrypto
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamQuicPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRoles
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519KeyPair
import com.vitorpamplona.quartz.marmot.mls.crypto.Hpke
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.Sender
import com.vitorpamplona.quartz.marmot.mls.framing.SenderType
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.framing.encodeSender
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup.Companion.externalJoin
import com.vitorpamplona.quartz.marmot.mls.messages.Commit
import com.vitorpamplona.quartz.marmot.mls.messages.CommitResult
import com.vitorpamplona.quartz.marmot.mls.messages.EncryptedGroupSecrets
import com.vitorpamplona.quartz.marmot.mls.messages.ExternalJoinResult
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
import com.vitorpamplona.quartz.marmot.mls.tree.PathSecretAndKey
import com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
import com.vitorpamplona.quartz.marmot.mls.tree.UpdatePathNode
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
    /**
     * HPKE private keys for the PARENT nodes on our own direct path, keyed by
     * node index.
     *
     * RFC 9420 §7.6 does not say "the committer encrypts to your leaf" — it
     * says the committer encrypts one path secret per node in the copath
     * resolution, and you decrypt at whichever of those nodes you hold a key
     * for. A merged subtree resolves to its PARENT, so as soon as a group has
     * three members the commits addressed to us stop naming our leaf at all.
     * Keeping only the leaf key is why every MDK commit after a three-member
     * Add failed with "UpdatePath at common ancestor carries no ciphertext
     * for us".
     */
    private val pathPrivateKeys: MutableMap<Int, ByteArray> = mutableMapOf(),
) {
    val groupId: ByteArray get() = groupContext.groupId
    val epoch: Long get() = groupContext.epoch
    val leafIndex: Int get() = myLeafIndex
    val extensions: List<com.vitorpamplona.quartz.marmot.mls.tree.Extension> get() = groupContext.extensions

    /**
     * True while our own leaf is still live in the tree.
     *
     * After `processCommit` applies a Remove proposal that targets us, the
     * group is intentionally kept in the manager so callers can inspect the
     * final tree state — but our leaf is null and `leafCount` may have
     * shrunk past `myLeafIndex`. Either condition means we can't propose,
     * commit, encrypt, or decrypt any further; surface that here so callers
     * get a clean answer instead of poking at private tree internals.
     */
    fun isLocalMember(): Boolean = myLeafIndex < tree.leafCount && tree.getLeaf(myLeafIndex) != null

    /**
     * Read-only snapshot of the staged-proposal pool. Exposed at module
     * scope so tests can inspect what `proposeAdd` / `proposeRemove` /
     * `buildSelfRemoveProposalMessage` etc. actually stage (notably the
     * `authenticatedContentBytes` we capture for ProposalRef matching).
     */
    internal fun pendingProposalsSnapshot(): List<PendingProposal> = pendingProposals.toList()

    /**
     * The GroupContext extension list as it stands. Test-only: callers
     * that want the dictionary should use [appDataDictionary], which
     * cannot distinguish an absent extension from an empty one — a
     * distinction the wire format does make.
     */
    internal fun groupContextExtensionsSnapshot(): List<Extension> = groupContext.extensions.toList()

    /**
     * Encode the current ratchet tree the same way it's serialized into
     * the GroupInfo's `ratchet_tree` extension on a Welcome — a freshly-
     * decoded copy is what a joiner sees, so this is the right input for
     * exercising [verifyTreeParentHashesForJoin] in tests.
     */
    internal fun exportTreeBytes(): ByteArray {
        val w = TlsWriter()
        tree.encodeTls(w)
        return w.toByteArray()
    }

    // --- Marmot admin helpers (MIP-01 / MIP-03) ---

    /** Raw BasicCredential identity bytes of the member at the given leaf, or null. */
    fun memberIdentity(leafIndex: Int): ByteArray? = (tree.getLeaf(leafIndex)?.credential as? Credential.Basic)?.identity

    /** Lowercase hex of the member's BasicCredential identity, or null. */
    fun memberIdentityHex(leafIndex: Int): String? = memberIdentity(leafIndex)?.toHexKey()

    /** Lowercase hex of the local member's BasicCredential identity, or null. */
    fun myIdentityHex(): String? = memberIdentityHex(myLeafIndex)

    /** Parsed Marmot Group Data Extension from the current GroupContext, or null. */
    fun currentMarmotData(): MarmotGroupData? = MarmotGroupData.fromExtensions(groupContext.extensions)

    /** The current profile's component view of this GroupContext. */
    fun currentGroupState(): MarmotGroupState = MarmotGroupState.fromExtensions(groupContext.extensions)

    /**
     * The `nostr_group_id` this group routes kind-445 traffic under, from
     * whichever profile the group is actually using.
     *
     * A current-profile group carries it in the `marmot.transport.nostr.routing.v1`
     * component (`0x8004`); a legacy group carries it inside the monolithic
     * `0xF2EE` extension. Reading only the legacy one leaves us unable to join
     * any group a current-profile client created — the routing id is required
     * to subscribe at all, so the failure is total rather than partial.
     */
    fun currentNostrGroupId(): HexKey? =
        currentGroupState().routing?.nostrGroupIdHex
            ?: currentMarmotData()?.nostrGroupId

    /**
     * The group's configured admin account identities, as lowercase hex.
     *
     * Reads whichever profile this group is on: the current profile's
     * `marmot.group.admin-policy.v1` component (`0x8003`) when present,
     * otherwise MIP-01's `admin_pubkeys` field inside `marmot_group_data`
     * (`0xF2EE`). Empty means the group names no admins at all, which happens
     * during bootstrap and in groups that carry neither.
     *
     * The current profile is checked first because a group can only be one of
     * the two — MDK rejects a group that requires both proof profiles — and a
     * current-profile group is the one whose authorization we must not skip.
     */
    fun currentAdminIdentities(): Set<String> = adminIdentitiesIn(groupContext.extensions)

    /**
     * The admin set named by [extensions], preferring the current profile.
     *
     * Decodes ONLY the admin policy, never the whole component set. Authorization
     * must not depend on the validity of components it does not read: a
     * malformed group profile is a defect worth surfacing where the profile is
     * used, but it must not make the group un-committable by taking the admin
     * check down with it.
     */
    private fun adminIdentitiesIn(extensions: List<Extension>): Set<String> {
        val policyBytes = AppDataDictionary.fromExtensionsOrEmpty(extensions)[AdminPolicyV1.COMPONENT_ID]
        if (policyBytes != null) return AdminPolicyV1.decode(policyBytes).adminHexKeys.toSet()
        return MarmotGroupData
            .fromExtensions(extensions)
            ?.adminPubkeys
            ?.toSet()
            .orEmpty()
    }

    /**
     * Account identities holding at least one current member leaf, as hex.
     *
     * Admin authority is per ACCOUNT, not per leaf: a multi-device account
     * shares one admin entry across all of its leaves.
     */
    fun currentMemberIdentities(): Set<String> = (0 until tree.leafCount).mapNotNullTo(mutableSetOf()) { memberIdentityHex(it) }

    /** True if the local member is an active admin. */
    fun isLocalAdmin(): Boolean = isLeafAdmin(myLeafIndex)

    /**
     * True if the member at [leafIndex] is an ACTIVE admin: listed in the
     * group's admin set and still holding a leaf. The leaf lookup satisfies
     * the second half by construction.
     */
    fun isLeafAdmin(leafIndex: Int): Boolean {
        val id = memberIdentityHex(leafIndex) ?: return false
        return id in currentAdminIdentities()
    }

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
            // Preserve the SecretTree ratchet positions so a restore doesn't
            // rewind our own generation counter to 0 and reuse an AEAD
            // key+nonce within this epoch (RFC 9420 §9).
            senderRatchetStates = secretTree.exportSenderStates(),
            pathPrivateKeys = pathPrivateKeys.toMap(),
        )
    }

    /**
     * Record the HPKE private keys our direct path gained from [pathSecret] at
     * [fromNodeIndex] and every node above it, up to the root.
     *
     * A path secret ratchets one KDF step per level regardless of filtering,
     * and each level's node keypair is `DeriveKeyPair(DeriveSecret(secret,
     * "node"))` — the same derivation the committer used, which is what makes
     * the keys we store here the ones a later committer will encrypt to.
     */
    private fun rememberPathKeys(
        fromNodeIndex: Int,
        pathSecret: ByteArray,
    ) {
        val fullPath = BinaryTree.directPath(myLeafIndex, tree.leafCount)
        val start = fullPath.indexOf(fromNodeIndex)
        if (start < 0) return
        var secret = pathSecret
        for (i in start until fullPath.size) {
            val nodeSecret = MlsCryptoProvider.deriveSecret(secret, "node")
            pathPrivateKeys[fullPath[i]] = Hpke.deriveKeyPair(nodeSecret).privateKey
            secret = MlsCryptoProvider.deriveSecret(secret, "path")
        }
        // Anything no longer on our direct path (the tree reshaped under us)
        // can never be addressed to us again; holding it would only make a
        // stale key look usable at the next resolution scan.
        pathPrivateKeys.keys.retainAll(fullPath.toSet())
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
        /**
         * The leaf signature keypair to use, when the caller had to generate it
         * up front.
         *
         * A current-profile leaf must carry an account identity proof over its
         * OWN signature key, and that proof is produced by an account signer
         * that may be remote. So the caller generates the keypair, signs the
         * proof against its public half, and hands both back here — the proof
         * cannot be computed after the fact by code that only sees the leaf.
         */
        leafSignatureKeyPair: Ed25519KeyPair? = null,
        leafExtensions: List<Extension> = emptyList(),
        capabilities: Capabilities = marmotLeafCapabilities(),
        keyPackageExtensions: List<Extension> = emptyList(),
    ): KeyPackageBundle {
        val initKp = X25519.generateKeyPair()
        val encKp = X25519.generateKeyPair()
        val sigKp = leafSignatureKeyPair ?: Ed25519.generateKeyPair()

        val leafNode =
            buildLeafNode(
                encryptionKey = encKp.publicKey,
                signatureKey = sigKp.publicKey,
                identity = identity,
                source = LeafNodeSource.KEY_PACKAGE,
                signingKey = sigKp.privateKey,
                capabilities = capabilities,
                leafExtensions = leafExtensions,
            )

        val unsigned =
            MlsKeyPackage(
                initKey = initKp.publicKey,
                leafNode = leafNode,
                extensions = keyPackageExtensions,
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
     *
     * Per MIP-01/MIP-03, members listed in `admin_pubkeys` MUST NOT issue a
     * SelfRemove — they have to first publish a GroupContextExtensions proposal
     * removing themselves from the admin list (self-demotion). This guard
     * enforces that rule at the local sender.
     */
    fun proposeSelfRemove(): Proposal.SelfRemove {
        check(!isLocalAdmin()) {
            "Admin must self-demote via GroupContextExtensions before SelfRemove (MIP-01)"
        }
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

        // An Update replaces our leaf with fresh key material and nothing
        // else. Capabilities and leaf extensions (the account identity proof
        // among them) describe the member, not the keys, so they carry over.
        val newLeafNode =
            buildLeafNode(
                encryptionKey = newEncKp.publicKey,
                signatureKey = newSigKp.publicKey,
                identity = identity,
                source = LeafNodeSource.UPDATE,
                signingKey = newSigKp.privateKey,
                groupId = groupId,
                leafIndex = myLeafIndex,
                capabilities = currentLeaf?.capabilities ?: marmotLeafCapabilities(),
                leafExtensions = currentLeaf?.extensions ?: emptyList(),
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

    /** The GroupContext `app_data_dictionary`, empty when the group carries none. */
    fun appDataDictionary(): AppDataDictionary = AppDataDictionary.fromExtensionsOrEmpty(groupContext.extensions)

    /**
     * Propose setting one GroupContext app component to [data].
     *
     * Marmot components define their update payload as a full replacement
     * state, so [data] is the component's new value, not a diff.
     *
     * This is the MLS mechanism only. Marmot's own authorization — most
     * component changes are admin-gated, and the resulting state still has to
     * satisfy every component's validation rules — is layered on top and is
     * not enforced here.
     */
    fun proposeAppDataUpdate(
        componentId: Int,
        data: ByteArray,
    ): Proposal.AppDataUpdate {
        val proposal = Proposal.AppDataUpdate.update(componentId, data)
        pendingProposals.add(PendingProposal(proposal, myLeafIndex))
        return proposal
    }

    /** Propose dropping one GroupContext app component entirely. */
    fun proposeAppDataRemoval(componentId: Int): Proposal.AppDataUpdate {
        val proposal = Proposal.AppDataUpdate.remove(componentId)
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

        // --- MIP-03 authorization gate -----------------------------------------
        //
        // Non-admin senders may only issue one of two restricted commit shapes:
        //   (a) a single self-Update targeting their own leaf, or
        //   (b) one or more SelfRemove proposals, all by themselves (no mixing).
        //
        // Admins may commit any proposal type.
        enforceAuthorizedProposalSet(proposals)

        // Reject commits that would leave the group without a usable admin
        // (i.e. no remaining member appears in the post-commit admin list).
        enforceNoAdminDepletion(proposals)

        // Capture the pre-commit exporter secret BEFORE any mutation.
        // Publishers of the outbound kind:445 MUST outer-encrypt with this
        // key (epoch N) so that other existing members at epoch N can decrypt
        // and process the commit. See CommitResult.preCommitExporterSecret.
        val preCommitExporterSecret =
            exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        // Snapshot the pre-proposal extensions. GroupContextExtensions proposals
        // mutate `groupContext.extensions` the moment they're applied, but
        // openmls signs the commit's FramedContentTBS over the UNMUTATED
        // context (the one in `public_group` before `diff` applies proposals).
        // Without this snapshot, a GCE commit on the quartz side uses the new
        // extensions for TBS + membership_tag, and openmls rejects it with
        // `ValidationError(InvalidMembershipTag)`.
        val preCommitExtensions = groupContext.extensions

        val proposalOrRefs = proposals.map { ProposalOrRef.Inline(it.proposal) }

        // Check if we need an UpdatePath. RFC 9420 §12.4.1: the path value
        // MUST be populated if the proposal list is empty (pure forward-
        // secrecy / self-update commit) or if it contains any Update or
        // Remove proposal. A commit whose only non-SelfRemove proposals are
        // Adds MAY omit the path — we still include one for extra forward
        // secrecy, which is spec-compliant.
        val needsPath =
            proposals.isEmpty() ||
                proposals.any { it.proposal !is Proposal.SelfRemove }

        // Apply proposals to tree FIRST (RFC 9420 Section 12.4.2)
        // Order: Updates/Removes first, then Adds (so blank slots are freed before reuse)
        val addedMembers = mutableListOf<Pair<Int, MlsKeyPackage>>()
        val addProposals = mutableListOf<PendingProposal>()
        val appDataUpdates = mutableListOf<Proposal.AppDataUpdate>()
        for (pending in proposals) {
            when (val p = pending.proposal) {
                is Proposal.Add -> addProposals.add(pending)
                is Proposal.AppDataUpdate -> appDataUpdates.add(p)
                else -> applyProposal(p, pending.senderLeafIndex)
            }
        }
        applyAppDataUpdateProposals(appDataUpdates)
        // Apply Adds after Removes/Updates
        for (pending in addProposals) {
            val p = pending.proposal as Proposal.Add
            val leafIndex = applyProposalAdd(p)
            addedMembers.add(leafIndex to p.keyPackage)
        }

        // Generate new path secrets on the updated tree
        val leafSecret = MlsCryptoProvider.randomBytes(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        val pathSecrets = tree.derivePathSecrets(myLeafIndex, leafSecret)

        // We just minted the keys for our whole direct path. Keep the private
        // halves: the next committer will address us at one of these nodes,
        // not at our leaf, as soon as our subtree is merged.
        run {
            val fullPath = BinaryTree.directPath(myLeafIndex, tree.leafCount)
            pathPrivateKeys.keys.retainAll(fullPath.toSet())
            for ((i, nodeIdx) in fullPath.withIndex()) {
                pathSecrets.getOrNull(i)?.let { pathPrivateKeys[nodeIdx] = it.privateKey }
            }
        }

        // RFC 9420 §12.4.1: newly-added leaves (from Add proposals in THIS commit)
        // MUST be excluded from the copath resolution — they join via the Welcome
        // at epoch N+1 and don't need the path secret. Keeping them in the list
        // shifts every other resolution index by one, so strict receivers
        // (openmls/mdk) pick the wrong ciphertext and fail with
        // UpdatePathError(UnableToDecrypt).
        val newLeafIndices = addedMembers.map { it.first }.toSet()

        // Build the UpdatePath in three stages so the HPKE info used for path-
        // secret encryption matches what openmls/mdk compute:
        //   1. derive path-secret keypairs and stage the committer's new leaf
        //   2. apply the path locally, patch parent_hashes, swap in the leaf
        //   3. compute the post-mutation tree_hash + bump epoch so
        //      `serialized_group_context` has the new tree_hash, the new epoch,
        //      and the old confirmed_transcript_hash — then HPKE-encrypt each
        //      copath resolution under that intermediate context
        // Without this ordering the committer uses the PRE-commit context and
        // every strict-validating member derives a different AEAD key, turning
        // the decryption into `UpdatePathError(UnableToDecrypt)`.
        val updatePath: UpdatePath? =
            if (needsPath && pathSecrets.isNotEmpty()) {
                // RFC 9420 §7.9: UpdatePath carries one node per entry in the
                // **filtered** direct path — parents whose copath subtree has
                // empty resolution are omitted (encrypting to them is
                // equivalent to encrypting to their only non-blank child).
                // Sender and receiver must agree on filtering or the
                // UpdatePath length check fails and openmls/wn reject the
                // commit with "UpdatePath node count doesn't match".
                val (filteredDp, filteredCp) = tree.filteredDirectPath(myLeafIndex)
                val directPath = BinaryTree.directPath(myLeafIndex, tree.leafCount)

                // path_secrets are derived one per UNFILTERED level so the
                // KDF chain reaches the root regardless of filtering
                // (commit_secret = DeriveSecret(root_path_secret, "path")
                // must be computable even when the root is in a filtered-out
                // position). Select the subset aligned to the filtered
                // direct path for UpdatePath node generation.
                val filteredPathSecrets =
                    directPath.indices.mapNotNull { i ->
                        val idxInFiltered = filteredDp.indexOf(directPath[i])
                        if (idxInFiltered >= 0) pathSecrets[i] else null
                    }

                // Capture sibling tree hashes BEFORE applying the UpdatePath —
                // parent_hash computation (RFC 9420 §7.9.2) uses the
                // ORIGINAL sibling-subtree tree hashes. Only needed at the
                // filtered positions (those are the nodes whose parent_hash
                // we're going to compute).
                val preUpdateSiblingHashes = capturePreUpdateSiblingHashes(myLeafIndex)

                // Stage path-keys into the tree so subsequent parent_hash /
                // tree_hash computations reflect the new keys. We'll fill in
                // the HPKE-encrypted secrets once we know the post-commit
                // context bytes. One staged node per FILTERED level.
                val stagedPathNodes =
                    filteredPathSecrets.map { pathKey ->
                        UpdatePathNode(pathKey.publicKey, emptyList())
                    }
                tree.applyUpdatePath(myLeafIndex, stagedPathNodes)

                // Compute parent_hash for every FILTERED-direct-path parent
                // node and for the committer's leaf (RFC 9420 §7.9.2).
                val (parentNodeHashes, leafParentHash) =
                    computeSenderParentHashes(myLeafIndex, preUpdateSiblingHashes)
                for (nodeIdx in filteredDp) {
                    val existing = tree.getNode(nodeIdx)
                    if (existing is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                        tree.setParent(
                            nodeIdx,
                            existing.parentNode.copy(
                                parentHash = parentNodeHashes[nodeIdx] ?: ByteArray(0),
                            ),
                        )
                    }
                }

                // If a signing-key rotation is pending (from
                // proposeSigningKeyRotation), the UpdatePath's leaf must be
                // sealed with the NEW signing identity — otherwise the
                // receiver's copy of our leaf keeps the pre-rotation
                // signature_key and every post-commit FramedContentTBS
                // signature we mint fails to verify.
                val effectiveSigningKey = pendingSigningKey ?: signingPrivateKey
                val newEncKp = X25519.generateKeyPair()
                // RFC 9420 §7.1: an UpdatePath leaf REPLACES our leaf. It is
                // the same member, so everything about that member that is not
                // key material carries over — capabilities and the leaf
                // extensions. Rebuilding from defaults instead is not a
                // cosmetic loss: a current-profile leaf keeps its
                // `account-identity-proof` in an `app_data_dictionary` LEAF
                // extension, and that extension can never be re-added by a
                // proposal, so dropping it here silently demotes us out of the
                // current profile at our very first commit. It also drops the
                // `app_data_dictionary` capability the group's own
                // `required_capabilities` demands, which makes the resulting
                // tree fail RFC 9420 §7.3 leaf validation for every receiver —
                // openmls reports `LeafNodeValidation(UnsupportedExtensions)`
                // and the Welcome we just minted is unjoinable.
                val previousLeaf = tree.getLeaf(myLeafIndex)
                val newLeafNode =
                    buildLeafNode(
                        encryptionKey = newEncKp.publicKey,
                        signatureKey = Ed25519.publicFromPrivate(effectiveSigningKey),
                        identity =
                            (previousLeaf?.credential as? Credential.Basic)?.identity
                                ?: ByteArray(0),
                        source = LeafNodeSource.COMMIT,
                        signingKey = effectiveSigningKey,
                        groupId = groupId,
                        leafIndex = myLeafIndex,
                        parentHash = leafParentHash,
                        capabilities = previousLeaf?.capabilities ?: marmotLeafCapabilities(),
                        leafExtensions = previousLeaf?.extensions ?: emptyList(),
                    )
                encryptionPrivateKey = newEncKp.privateKey
                tree.setLeaf(myLeafIndex, newLeafNode)

                // Path-encryption context (RFC 9420 §7.6 / openmls `compute_path`):
                // serialize the GroupContext AFTER applying the tree mutations
                // and bumping the epoch, but BEFORE the confirmed_transcript_hash
                // gets folded in. We don't commit this copy to the field yet —
                // the transcript-hash update below needs the current value.
                val pathEncContextBytes =
                    groupContext
                        .copy(
                            epoch = groupContext.epoch + 1,
                            treeHash = tree.treeHash(),
                        ).toTlsBytes()

                val pathNodes =
                    stagedPathNodes.zip(filteredCp).zip(filteredPathSecrets) { (staged, copathNode), pathKey ->
                        val resolution =
                            tree.resolution(copathNode).filterNot { resNode ->
                                BinaryTree.isLeaf(resNode) &&
                                    BinaryTree.nodeToLeaf(resNode) in newLeafIndices
                            }
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
                                    pathEncContextBytes,
                                    pathKey.pathSecret,
                                )
                            }
                        UpdatePathNode(staged.encryptionKey, encryptedSecrets)
                    }

                UpdatePath(newLeafNode, pathNodes)
            } else {
                null
            }

        val commit = Commit(proposalOrRefs, updatePath)
        val commitBytes = commit.toTlsBytes()

        // Advance epoch.
        // RFC 9420 §9.2: commit_secret is the path_secret for the "virtual" node
        // one step past the root — i.e. `DeriveSecret(path_secret_at_root, "path")`,
        // NOT the path_secret at the root itself. Openmls/mdk derives exactly this
        // value; quartz was using the root's own path_secret, which is the
        // encryption-key seed rather than the key-schedule contribution. That
        // one-step gap silently diverged the two sides' epoch_secret and made
        // every cross-impl commit fail `ConfirmationTagMismatch`.
        val commitSecret =
            if (pathSecrets.isNotEmpty()) {
                MlsCryptoProvider.deriveSecret(pathSecrets.last().pathSecret, "path")
            } else {
                ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            }

        val newTreeHash = tree.treeHash()
        val oldEpoch = groupContext.epoch
        val preCommitGroupId = groupContext.groupId
        val committerLeafIndex = myLeafIndex
        val newEpoch = oldEpoch + 1

        // Capture pre-commit values needed to sign and membership-MAC the
        // outbound PublicMessage (RFC 9420 §6.1 / §6.2). The signature and
        // membership_tag are computed under the epoch in which the commit
        // is sent — the one we're about to leave. Receivers (openmls/mdk)
        // strict-verify both, so we must use the leaf signing key that's
        // still in the pre-commit tree and the membership_key derived from
        // the pre-commit epoch secrets. Extensions need explicit rewind to
        // pre-proposal state for GroupContextExtensions commits.
        val preCommitContextBytes =
            groupContext.copy(extensions = preCommitExtensions).toTlsBytes()
        val preCommitMembershipKey = epochSecrets.membershipKey
        val preCommitSigningKey = signingPrivateKey

        // Sign FramedContentTBS BEFORE folding the commit into the transcript
        // hash. RFC 9420 §8.2: ConfirmedTranscriptHashInput contains the real
        // signature — using `ByteArray(0)` here produces a confirmed_transcript_hash
        // that the receiver cannot reproduce, so strict peers reject the commit
        // with `ConfirmationTagMismatch`.
        val commitTbsBytes =
            buildCommitFramedContentTbs(
                groupId = preCommitGroupId,
                epoch = oldEpoch,
                senderLeafIndex = committerLeafIndex,
                commitBytes = commitBytes,
                groupContextBytes = preCommitContextBytes,
            )
        val commitSignature =
            MlsCryptoProvider.signWithLabel(preCommitSigningKey, "FramedContentTBS", commitTbsBytes)

        // Update transcript hashes (RFC 9420 §8.2) with the real signature.
        val confirmedTranscriptHashInput =
            buildConfirmedTranscriptHashInput(commit, myLeafIndex, commitSignature)
        val confirmedInput = TlsWriter()
        confirmedInput.putBytes(interimTranscriptHash)
        confirmedInput.putBytes(confirmedTranscriptHashInput)
        val newConfirmedTranscriptHash = MlsCryptoProvider.hash(confirmedInput.toByteArray())

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
                buildWelcome(addedMembers, pathSecrets)
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

        val framedCommitBytes =
            framePublicMessageCommit(
                groupId = preCommitGroupId,
                epoch = oldEpoch,
                senderLeafIndex = committerLeafIndex,
                commitBytes = commitBytes,
                confirmationTag = confirmationTag,
                signature = commitSignature,
                membershipKey = preCommitMembershipKey,
                tbsBytes = commitTbsBytes,
            )
        return CommitResult(
            commitBytes = commitBytes,
            welcomeBytes = welcomeBytes,
            groupInfoBytes = null,
            framedCommitBytes = framedCommitBytes,
            preCommitExporterSecret = preCommitExporterSecret,
        )
    }

    // --- Message Encryption ---

    /**
     * Encrypt an application message as a PrivateMessage (RFC 9420 §6.3).
     *
     * The AEAD plaintext is the serialized [PrivateMessageContent] for
     * application messages (§6.3.1):
     *
     *     opaque application_data<V>;
     *     FramedContentAuthData auth;   // = opaque signature<V>
     *     opaque padding[N];            // zero padding
     *
     * The signature is computed with `SignWithLabel(., "FramedContentTBS",
     * FramedContentTBS)` using the member's signature private key.
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

        // Generate 4-byte reuse_guard and XOR into the first 4 nonce bytes (RFC 9420 §6.3.1)
        val reuseGuard = MlsCryptoProvider.randomBytes(REUSE_GUARD_LENGTH)
        val guardedNonce = kng.nonce.copyOf()
        for (i in 0 until REUSE_GUARD_LENGTH) {
            guardedNonce[i] = (guardedNonce[i].toInt() xor reuseGuard[i].toInt()).toByte()
        }

        // Sign FramedContentTBS for this application message (RFC 9420 §6.1).
        val signature =
            MlsCryptoProvider.signWithLabel(
                signingPrivateKey,
                "FramedContentTBS",
                buildApplicationFramedContentTbs(
                    groupId = groupId,
                    epoch = epoch,
                    senderLeafIndex = myLeafIndex,
                    authenticatedData = ByteArray(0),
                    applicationData = plaintext,
                    groupContext = groupContext,
                ),
            )

        // Build PrivateMessageContent plaintext (RFC 9420 §6.3.1).
        // Padding length is zero; RFC allows any non-negative padding
        // provided the padding bytes themselves are zero.
        val pmcWriter = TlsWriter()
        pmcWriter.putOpaqueVarInt(plaintext) // application_data<V>
        pmcWriter.putOpaqueVarInt(signature) // FramedContentAuthData.signature<V>
        // (application messages have no confirmation_tag field)
        val pmcPlaintext = pmcWriter.toByteArray()

        // Build PrivateContentAAD (RFC 9420 §6.3.2)
        val contentAad = buildPrivateContentAAD(groupId, epoch, ContentType.APPLICATION, ByteArray(0))
        val ciphertext = MlsCryptoProvider.aeadEncrypt(kng.key, guardedNonce, contentAad, pmcPlaintext)

        // Build sender data plaintext: leaf_index || generation || reuse_guard
        val senderDataWriter = TlsWriter()
        senderDataWriter.putUint32(myLeafIndex.toLong())
        senderDataWriter.putUint32(kng.generation.toLong())
        senderDataWriter.putBytes(reuseGuard)
        val senderDataPlain = senderDataWriter.toByteArray()

        // Derive sender data key/nonce using ciphertext sample (RFC 9420 §6.3.2:
        // "the first KDF.Nh bytes of the ciphertext").
        val ciphertextSample = ciphertext.copyOfRange(0, minOf(ciphertext.size, MlsCryptoProvider.HASH_OUTPUT_LENGTH))
        val senderDataKey =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "key",
                ciphertextSample,
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val senderDataNonce =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "nonce",
                ciphertextSample,
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )

        // Build SenderDataAAD (RFC 9420 §6.3.2)
        val senderDataAad = buildSenderDataAAD(groupId, epoch, ContentType.APPLICATION)
        val encryptedSenderData =
            MlsCryptoProvider.aeadEncrypt(senderDataKey, senderDataNonce, senderDataAad, senderDataPlain)

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
     * Encrypt a standalone Proposal as a PrivateMessage (RFC 9420 §6.3.2,
     * content_type = PROPOSAL). Mirrors [encrypt] but writes a Proposal
     * struct + signature in the PrivateMessageContent instead of
     * application_data. The receiver-side counterpart lives in [decrypt]
     * under `ContentType.PROPOSAL`.
     *
     * Used today only for tests — production callers publish SelfRemove
     * via [buildSelfRemoveProposalMessage] (PublicMessage) — but exposing
     * the encode path keeps the wire format symmetric so a future caller
     * that wants a fully-encrypted proposal envelope doesn't have to
     * re-derive it.
     */
    internal fun encryptProposalAsPrivateMessage(proposal: Proposal): ByteArray {
        if (sentKeys.size > MAX_SENT_KEYS) {
            val sortedKeys = sentKeys.keys.sorted()
            val toRemove = sortedKeys.take(sentKeys.size - MAX_SENT_KEYS)
            for (key in toRemove) sentKeys.remove(key)
        }

        // PROPOSALs ride the handshake ratchet (RFC 9420 §6.3.2), not
        // the application ratchet — same as PrivateMessage commits.
        val kng = secretTree.nextHandshakeKeyNonce(myLeafIndex)

        val reuseGuard = MlsCryptoProvider.randomBytes(REUSE_GUARD_LENGTH)
        val guardedNonce = kng.nonce.copyOf()
        for (i in 0 until REUSE_GUARD_LENGTH) {
            guardedNonce[i] = (guardedNonce[i].toInt() xor reuseGuard[i].toInt()).toByte()
        }

        val proposalWriter = TlsWriter()
        proposal.encodeTls(proposalWriter)
        val proposalBytes = proposalWriter.toByteArray()

        // FramedContentTBS for a member-sender PROPOSAL over PRIVATE_MESSAGE.
        val tbsWriter = TlsWriter()
        tbsWriter.putUint16(MlsMessage.MLS_VERSION_10)
        tbsWriter.putUint16(WireFormat.PRIVATE_MESSAGE.value)
        tbsWriter.putOpaqueVarInt(groupId)
        tbsWriter.putUint64(epoch)
        encodeSender(tbsWriter, Sender(SenderType.MEMBER, myLeafIndex))
        tbsWriter.putOpaqueVarInt(ByteArray(0)) // authenticated_data
        tbsWriter.putUint8(ContentType.PROPOSAL.value)
        tbsWriter.putBytes(proposalBytes)
        tbsWriter.putBytes(groupContext.toTlsBytes())
        val signature = MlsCryptoProvider.signWithLabel(signingPrivateKey, "FramedContentTBS", tbsWriter.toByteArray())

        // PrivateMessageContent for PROPOSAL: proposal struct (no length
        // prefix) || signature<V> || padding.
        val pmcWriter = TlsWriter()
        pmcWriter.putBytes(proposalBytes)
        pmcWriter.putOpaqueVarInt(signature)
        val pmcPlaintext = pmcWriter.toByteArray()

        val contentAad = buildPrivateContentAAD(groupId, epoch, ContentType.PROPOSAL, ByteArray(0))
        val ciphertext = MlsCryptoProvider.aeadEncrypt(kng.key, guardedNonce, contentAad, pmcPlaintext)

        val senderDataWriter = TlsWriter()
        senderDataWriter.putUint32(myLeafIndex.toLong())
        senderDataWriter.putUint32(kng.generation.toLong())
        senderDataWriter.putBytes(reuseGuard)
        val senderDataPlain = senderDataWriter.toByteArray()

        val ciphertextSample = ciphertext.copyOfRange(0, minOf(ciphertext.size, MlsCryptoProvider.HASH_OUTPUT_LENGTH))
        val senderDataKey =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "key",
                ciphertextSample,
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val senderDataNonce =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "nonce",
                ciphertextSample,
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )
        val senderDataAad = buildSenderDataAAD(groupId, epoch, ContentType.PROPOSAL)
        val encryptedSenderData =
            MlsCryptoProvider.aeadEncrypt(senderDataKey, senderDataNonce, senderDataAad, senderDataPlain)

        return MlsMessage
            .fromPrivateMessage(
                PrivateMessage(
                    groupId = groupId,
                    epoch = epoch,
                    contentType = ContentType.PROPOSAL,
                    authenticatedData = ByteArray(0),
                    encryptedSenderData = encryptedSenderData,
                    ciphertext = ciphertext,
                ),
            ).toTlsBytes()
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
     * Decrypt an application message from a PrivateMessage (RFC 9420 Section 6.3).
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

        // Derive sender data key/nonce using ciphertext sample (RFC 9420 §6.3.1)
        // RFC 9420 §6.3.2: ciphertext_sample is the first KDF.Nh bytes
        // (32 for HKDF-SHA256), not AEAD.Nk (16). Using AEAD.Nk here made
        // sender-data decryption fail against every spec-compliant sender.
        val ciphertextSample =
            privMsg.ciphertext.copyOfRange(0, minOf(privMsg.ciphertext.size, MlsCryptoProvider.HASH_OUTPUT_LENGTH))
        val senderDataKey =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "key",
                ciphertextSample,
                MlsCryptoProvider.AEAD_KEY_LENGTH,
            )
        val senderDataNonce =
            MlsCryptoProvider.expandWithLabel(
                epochSecrets.senderDataSecret,
                "nonce",
                ciphertextSample,
                MlsCryptoProvider.AEAD_NONCE_LENGTH,
            )

        // Build SenderDataAAD and decrypt sender data (RFC 9420 §6.3.1)
        val senderDataAad = buildSenderDataAAD(privMsg.groupId, privMsg.epoch, privMsg.contentType)
        val senderDataPlain =
            MlsCryptoProvider.aeadDecrypt(senderDataKey, senderDataNonce, senderDataAad, privMsg.encryptedSenderData)
        val senderReader = TlsReader(senderDataPlain)
        val senderLeafIndex = senderReader.readUint32().toInt()
        val generation = senderReader.readUint32().toInt()
        val reuseGuard = senderReader.readBytes(REUSE_GUARD_LENGTH)

        // Validate sender leaf index and membership (RFC 9420 §6.3.1)
        require(senderLeafIndex in 0 until tree.leafCount) {
            "Sender leaf index $senderLeafIndex out of range [0, ${tree.leafCount})"
        }
        require(tree.getLeaf(senderLeafIndex) != null) {
            "Sender leaf is blank at index $senderLeafIndex (not a group member)"
        }

        // Get the key/nonce for this sender+generation from the correct ratchet.
        // PrivateMessage Application content uses the application ratchet (RFC 9420
        // §6.3.2); PrivateMessage Commit / Proposal handshakes use a separate
        // per-sender handshake ratchet. openmls / mdk default to AlwaysCiphertext
        // outgoing, so every B→A commit quartz receives lands here with
        // content_type == COMMIT.
        val kng =
            if (senderLeafIndex == myLeafIndex && sentKeys.containsKey(generation)) {
                sentKeys.remove(generation)!!
            } else {
                when (privMsg.contentType) {
                    ContentType.APPLICATION -> {
                        secretTree.applicationKeyNonceForGeneration(senderLeafIndex, generation)
                    }

                    ContentType.COMMIT, ContentType.PROPOSAL -> {
                        secretTree.handshakeKeyNonceForGeneration(senderLeafIndex, generation)
                    }
                }
            }

        // Apply reuse_guard XOR to nonce (RFC 9420 §6.3.1)
        val guardedNonce = kng.nonce.copyOf()
        for (i in 0 until REUSE_GUARD_LENGTH) {
            guardedNonce[i] = (guardedNonce[i].toInt() xor reuseGuard[i].toInt()).toByte()
        }

        // Decrypt content with PrivateContentAAD (RFC 9420 §6.3.2)
        val contentAad = buildPrivateContentAAD(privMsg.groupId, privMsg.epoch, privMsg.contentType, privMsg.authenticatedData)
        val pmcPlaintext = MlsCryptoProvider.aeadDecrypt(kng.key, guardedNonce, contentAad, privMsg.ciphertext)

        // Parse PrivateMessageContent (RFC 9420 §6.3.1). The layout depends on
        // content_type — application payloads carry `opaque application_data<V>`
        // whereas commit / proposal payloads carry the struct directly (no
        // outer length prefix).
        val pmcReader = TlsReader(pmcPlaintext)
        when (privMsg.contentType) {
            ContentType.APPLICATION -> {
                val applicationData = pmcReader.readOpaqueVarInt()
                val signature = pmcReader.readOpaqueVarInt()
                while (pmcReader.hasRemaining) {
                    require(pmcReader.readBytes(1)[0] == 0.toByte()) {
                        "PrivateMessageContent padding must be zero"
                    }
                }

                val senderLeaf =
                    requireNotNull(tree.getLeaf(senderLeafIndex)) {
                        "Sender leaf is blank at index $senderLeafIndex"
                    }
                require(
                    MlsCryptoProvider.verifyWithLabel(
                        senderLeaf.signatureKey,
                        "FramedContentTBS",
                        buildApplicationFramedContentTbs(
                            groupId = privMsg.groupId,
                            epoch = privMsg.epoch,
                            senderLeafIndex = senderLeafIndex,
                            authenticatedData = privMsg.authenticatedData,
                            applicationData = applicationData,
                            groupContext = groupContext,
                        ),
                        signature,
                    ),
                ) { "FramedContentTBS signature verification failed" }

                return DecryptedMessage(
                    senderLeafIndex = senderLeafIndex,
                    contentType = privMsg.contentType,
                    content = applicationData,
                    epoch = privMsg.epoch,
                )
            }

            ContentType.COMMIT -> {
                // PrivateMessageContent for a Commit: the Commit struct
                // (no length prefix) followed by signature<V> and
                // confirmation_tag<V>, then zero padding. The caller drives
                // processCommit with (commitBytes, signature, confirmationTag)
                // — all available here once we re-serialize the parsed Commit
                // back to bytes (openmls does the same round-trip).
                val commit = Commit.decodeTls(pmcReader)
                val commitWriter = TlsWriter()
                commit.encodeTls(commitWriter)
                val commitBytes = commitWriter.toByteArray()
                val signature = pmcReader.readOpaqueVarInt()
                val confirmationTag = pmcReader.readOpaqueVarInt()
                while (pmcReader.hasRemaining) {
                    require(pmcReader.readBytes(1)[0] == 0.toByte()) {
                        "PrivateMessageContent padding must be zero"
                    }
                }
                // Apply the commit inline — after this returns, the caller
                // only needs to know the epoch advanced. The signature + wire
                // format ride into the transcript-hash computation inside
                // processCommit (RFC 9420 §8.2 requires the real wire format
                // for ConfirmedTranscriptHashInput).
                processCommit(commitBytes, senderLeafIndex, confirmationTag, signature, WireFormat.PRIVATE_MESSAGE)

                return DecryptedMessage(
                    senderLeafIndex = senderLeafIndex,
                    contentType = privMsg.contentType,
                    content = commitBytes,
                    epoch = privMsg.epoch,
                )
            }

            ContentType.PROPOSAL -> {
                // PrivateMessageContent for a Proposal: the Proposal struct
                // (no length prefix) followed by signature<V>, then zero
                // padding. There's no membership_tag — PrivateMessage is
                // already AEAD-protected by sender membership in the
                // secret tree, so RFC 9420 §6.2's HMAC isn't applied.
                val proposal = Proposal.decodeTls(pmcReader)
                val proposalWriter = TlsWriter()
                proposal.encodeTls(proposalWriter)
                val proposalBytes = proposalWriter.toByteArray()
                val signature = pmcReader.readOpaqueVarInt()
                while (pmcReader.hasRemaining) {
                    require(pmcReader.readBytes(1)[0] == 0.toByte()) {
                        "PrivateMessageContent padding must be zero"
                    }
                }

                // Mirror the same defensive policy as
                // [receivePublicMessageProposal]: only SelfRemove proposals
                // legitimately arrive standalone today (openmls/mdk emit
                // it as a separate PROPOSAL message because non-admins
                // can't self-remove via a commit). Reject anything else
                // here so a peer can't pre-stage e.g. an Add we never asked
                // to fold in. Widen if/when interop demands it.
                require(proposal is Proposal.SelfRemove) {
                    "Only SelfRemove is accepted as a standalone PrivateMessage proposal " +
                        "(got ${proposal::class.simpleName}); other proposal types must be folded into a commit"
                }

                // Verify the FramedContent signature with wire_format =
                // PRIVATE_MESSAGE (not PUBLIC_MESSAGE) so the TBS bytes
                // match what the sender signed.
                val tbsWriter = TlsWriter()
                tbsWriter.putUint16(MlsMessage.MLS_VERSION_10)
                tbsWriter.putUint16(WireFormat.PRIVATE_MESSAGE.value)
                tbsWriter.putOpaqueVarInt(privMsg.groupId)
                tbsWriter.putUint64(privMsg.epoch)
                encodeSender(tbsWriter, Sender(SenderType.MEMBER, senderLeafIndex))
                tbsWriter.putOpaqueVarInt(privMsg.authenticatedData)
                tbsWriter.putUint8(ContentType.PROPOSAL.value)
                tbsWriter.putBytes(proposalBytes)
                tbsWriter.putBytes(groupContext.toTlsBytes()) // member sender appends context
                val tbs = tbsWriter.toByteArray()

                val senderLeaf =
                    requireNotNull(tree.getLeaf(senderLeafIndex)) {
                        "Sender leaf is blank at index $senderLeafIndex"
                    }
                require(
                    MlsCryptoProvider.verifyWithLabel(
                        senderLeaf.signatureKey,
                        "FramedContentTBS",
                        tbs,
                        signature,
                    ),
                ) { "FramedContentTBS signature verification failed on PrivateMessage proposal" }

                // RFC 9420 §5.2: ProposalRef hashes the encoded
                // AuthenticatedContent. For PrivateMessage proposals the
                // AC carries no membership_tag — auth = signature only.
                // Stash these bytes so a future commit referencing this
                // proposal by hash resolves against our pool.
                val acWriter = TlsWriter()
                acWriter.putUint16(WireFormat.PRIVATE_MESSAGE.value)
                acWriter.putOpaqueVarInt(privMsg.groupId)
                acWriter.putUint64(privMsg.epoch)
                encodeSender(acWriter, Sender(SenderType.MEMBER, senderLeafIndex))
                acWriter.putOpaqueVarInt(privMsg.authenticatedData)
                acWriter.putUint8(ContentType.PROPOSAL.value)
                acWriter.putBytes(proposalBytes)
                acWriter.putOpaqueVarInt(signature)
                pendingProposals.add(
                    PendingProposal(
                        proposal = proposal,
                        senderLeafIndex = senderLeafIndex,
                        authenticatedContentBytes = acWriter.toByteArray(),
                    ),
                )

                return DecryptedMessage(
                    senderLeafIndex = senderLeafIndex,
                    contentType = privMsg.contentType,
                    content = proposalBytes,
                    epoch = privMsg.epoch,
                )
            }
        }
    }

    /**
     * Build FramedContentTBS for an application-content PrivateMessage
     * (RFC 9420 §6.1). The signature over this is what lives in the
     * PrivateMessageContent's FramedContentAuthData.
     *
     *     struct {
     *         ProtocolVersion version = mls10;
     *         WireFormat wire_format;     // = mls_private_message
     *         FramedContent content;       // member sender, app body
     *         GroupContext context;        // for member senders
     *     } FramedContentTBS;
     */
    private fun buildApplicationFramedContentTbs(
        groupId: ByteArray,
        epoch: Long,
        senderLeafIndex: Int,
        authenticatedData: ByteArray,
        applicationData: ByteArray,
        groupContext: GroupContext,
    ): ByteArray {
        val w = TlsWriter()
        w.putUint16(MlsMessage.MLS_VERSION_10)
        w.putUint16(WireFormat.PRIVATE_MESSAGE.value)
        // FramedContent
        w.putOpaqueVarInt(groupId)
        w.putUint64(epoch)
        encodeSender(w, Sender(SenderType.MEMBER, senderLeafIndex))
        w.putOpaqueVarInt(authenticatedData)
        w.putUint8(ContentType.APPLICATION.value)
        w.putOpaqueVarInt(applicationData) // application_data<V>
        // GroupContext (member sender)
        groupContext.encodeTls(w)
        return w.toByteArray()
    }

    // --- Commit Processing ---

    /**
     * Process a fully-framed `MlsMessage(PublicMessage(Commit))` produced by
     * [CommitResult.framedCommitBytes]. Unpacks the wire envelope, verifies the
     * RFC 9420 §6.2 membership_tag, and delegates to [processCommit] with the
     * correct signature, confirmation_tag, and sender leaf index. This is the
     * symmetric peer-side entry point for commits returned by [commit] /
     * [addMember] / [removeMember] — tests and any caller that holds the
     * framed bytes should prefer this over the low-level [processCommit].
     *
     * Mirrors the production unwrap done by `MarmotInboundProcessor` for
     * inbound kind:445 PublicMessage commits.
     */
    fun processFramedCommit(framedCommitBytes: ByteArray) {
        val mlsMessage = MlsMessage.decodeTls(TlsReader(framedCommitBytes))
        require(mlsMessage.wireFormat == WireFormat.PUBLIC_MESSAGE) {
            "processFramedCommit expects a PublicMessage envelope, got ${mlsMessage.wireFormat}"
        }
        val pubMsg = PublicMessage.decodeTls(TlsReader(mlsMessage.payload))
        require(pubMsg.contentType == ContentType.COMMIT) {
            "Framed commit must contain ContentType.COMMIT, got ${pubMsg.contentType}"
        }
        val tag =
            requireNotNull(pubMsg.confirmationTag) {
                "PublicMessage commit missing confirmation_tag"
            }
        // External commits don't carry a membership_tag (sender isn't yet a
        // member). For member-sender commits, RFC 9420 §6.2 requires the tag.
        if (pubMsg.sender.senderType == SenderType.MEMBER) {
            require(verifyPublicMessageCommitMembershipTag(pubMsg)) {
                "Invalid membership_tag on PublicMessage commit"
            }
        }
        // RFC 9420 §6: `new_member_commit` senders carry no leaf_index field
        // on the wire — the joiner's slot is determined by the receiver
        // using the same algorithm the joiner used in [RatchetTree.addLeaf]:
        // first blank leaf, else append past the current leaf_count.
        val senderLeafIndex =
            when (pubMsg.sender.senderType) {
                SenderType.NEW_MEMBER_COMMIT -> {
                    (0 until tree.leafCount).firstOrNull { tree.getLeaf(it) == null }
                        ?: tree.leafCount
                }

                else -> {
                    pubMsg.sender.leafIndex
                }
            }
        processCommit(
            commitBytes = pubMsg.content,
            senderLeafIndex = senderLeafIndex,
            confirmationTag = tag,
            signature = pubMsg.signature,
            wireFormat = WireFormat.PUBLIC_MESSAGE,
        )
    }

    /**
     * Process a received Commit message, advancing the epoch.
     *
     * @param commitBytes TLS-serialized Commit
     * @param senderLeafIndex the sender's leaf index in the ratchet tree
     * @param confirmationTag optional confirmation tag from the PublicMessage for verification
     * @param signature FramedContentAuthData.signature from the PublicMessage wrapper;
     *   required to reproduce the ConfirmedTranscriptHashInput (RFC 9420 §8.2) exactly.
     *   Callers that don't have access to the PublicMessage envelope (test fixtures)
     *   may pass `ByteArray(0)` — that path only interoperates with senders that
     *   also pass an empty signature.
     */
    fun processCommit(
        commitBytes: ByteArray,
        senderLeafIndex: Int,
        confirmationTag: ByteArray,
        signature: ByteArray = ByteArray(0),
        wireFormat: WireFormat = WireFormat.PUBLIC_MESSAGE,
    ) {
        // Snapshot all mutable state BEFORE applying any proposals or
        // UpdatePath mutations. If any step throws (bad signature, parent
        // hash mismatch, decrypt failure, confirmation-tag divergence), we
        // restore the snapshot so callers observe the group in the same
        // state as if processCommit had never run. Without this rollback
        // a partial mutation leaves the tree one epoch ahead of
        // `groupContext.epoch` and every subsequent decrypt fails with
        // `Message epoch X doesn't match current epoch Y`.
        val treeSnapshot = tree.snapshot()
        val ctxSnapshot = groupContext
        val secretsSnapshot = epochSecrets
        val initSnapshot = initSecret
        val secretTreeSnapshot = secretTree
        val interimSnapshot = interimTranscriptHash
        val pendingSnapshot = pendingProposals.toList()
        val sentKeysSnapshot = sentKeys.toMap()

        try {
            processCommitInner(commitBytes, senderLeafIndex, confirmationTag, signature, wireFormat)
        } catch (t: Throwable) {
            tree.restoreFrom(treeSnapshot)
            groupContext = ctxSnapshot
            epochSecrets = secretsSnapshot
            initSecret = initSnapshot
            secretTree = secretTreeSnapshot
            interimTranscriptHash = interimSnapshot
            pendingProposals.clear()
            pendingProposals.addAll(pendingSnapshot)
            sentKeys.clear()
            sentKeys.putAll(sentKeysSnapshot)
            throw t
        }
    }

    private fun processCommitInner(
        commitBytes: ByteArray,
        senderLeafIndex: Int,
        confirmationTag: ByteArray,
        signature: ByteArray,
        wireFormat: WireFormat,
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

        // Verify the FramedContentTBS signature (RFC 9420 §6.1) against
        // the sender's pre-commit leaf signature_key. Any non-external
        // commit MUST carry a non-empty signature from the sender's leaf
        // — without this check anyone with the outer exporter key (in a
        // compromised relay scenario) could forge commits.
        if (!isExternalCommit) {
            require(signature.isNotEmpty()) {
                "FramedContentTBS signature missing on commit from leaf $senderLeafIndex"
            }
            val senderLeaf =
                requireNotNull(tree.getLeaf(senderLeafIndex)) {
                    "Sender leaf is blank at index $senderLeafIndex"
                }
            val tbs =
                buildCommitFramedContentTbs(
                    groupId = groupContext.groupId,
                    epoch = groupContext.epoch,
                    senderLeafIndex = senderLeafIndex,
                    commitBytes = commitBytes,
                    groupContextBytes = groupContext.toTlsBytes(),
                    wireFormat = wireFormat,
                )
            require(MlsCryptoProvider.verifyWithLabel(senderLeaf.signatureKey, "FramedContentTBS", tbs, signature)) {
                "Invalid FramedContentTBS signature on commit from leaf $senderLeafIndex"
            }
        }

        // Resolve proposal references against our pending pool BEFORE
        // applying anything, so MIP-03 authorization can run on a static
        // snapshot of (proposal, original-sender-leaf) pairs and so the
        // depletion guard can simulate the post-commit tree shape from the
        // pre-commit state.
        val resolvedPending = mutableListOf<PendingProposal>()
        for (proposalOrRef in commit.proposals) {
            when (proposalOrRef) {
                is ProposalOrRef.Inline -> {
                    // Inline proposals are authored by the committer.
                    resolvedPending.add(PendingProposal(proposalOrRef.proposal, senderLeafIndex))
                }

                is ProposalOrRef.Reference -> {
                    val refHash = proposalOrRef.proposalRef
                    val resolved =
                        pendingProposals.find { pending ->
                            // RFC 9420 §5.2: a ProposalRef hashes the encoded
                            // AuthenticatedContent that delivered the proposal,
                            // not just the bare Proposal struct. For inbound
                            // proposals (e.g. C's standalone SelfRemove) we
                            // captured those bytes at receive time. Locally-
                            // proposed entries that have been published as a
                            // standalone PublicMessage also carry their AC
                            // bytes; the bare-proposal fallback below covers
                            // legacy local entries that pre-date that capture.
                            val refValue = pending.authenticatedContentBytes ?: pending.proposal.toTlsBytes()
                            val hash = MlsCryptoProvider.refHash("MLS 1.0 Proposal Reference", refValue)
                            hash.contentEquals(refHash)
                        }
                    requireNotNull(resolved) {
                        "Commit references unknown proposal (ref not found in pending proposals)"
                    }
                    resolvedPending.add(resolved)
                }
            }
        }

        // MIP-03 authorization & admin-depletion gates on inbound commits
        // (mirror what `commit()` enforces locally — without these a peer
        // could send us a non-admin GCE rename, a non-admin Remove, or a
        // commit that empties `admin_pubkeys` and we'd silently apply it).
        // External commits get a pass: the sender doesn't have a leaf yet,
        // so the admin lookup is moot, and an external joiner can't include
        // arbitrary proposals — only Add/Remove/PSK/ExternalInit per
        // RFC 9420 §12.4.3.2.
        if (!isExternalCommit) {
            enforceAuthorizedProposalSet(resolvedPending, committerLeafIndex = senderLeafIndex)
            enforceNoAdminDepletion(resolvedPending)
        }

        // Apply the resolved proposals. Matches the committer's order: apply
        // non-Add proposals first, then Adds, so leaves freed by Remove are
        // available for Add reuse (RFC 9420 §12.4.2). Also track the
        // post-Add leaf indices so the UpdatePath resolution filter can
        // exclude them (mirrors the encryption-side exclusion).
        //
        // `resolvedPending[i].senderLeafIndex` is already the correct
        // author for both inline (committer) and reference (original
        // proposer) entries, since we stamped inline entries with
        // `senderLeafIndex` when building the snapshot above.
        val resolvedProposals = mutableListOf<Proposal>()
        val inlineAdds = mutableListOf<Proposal.Add>()
        val referenceAddSenders = mutableListOf<Pair<Proposal.Add, Int>>()
        val inboundAppDataUpdates = mutableListOf<Proposal.AppDataUpdate>()
        for ((idx, pending) in resolvedPending.withIndex()) {
            val isInline = commit.proposals[idx] is ProposalOrRef.Inline
            when (val p = pending.proposal) {
                is Proposal.Add -> {
                    if (isInline) {
                        inlineAdds.add(p)
                    } else {
                        referenceAddSenders.add(p to pending.senderLeafIndex)
                    }
                }

                is Proposal.AppDataUpdate -> inboundAppDataUpdates.add(p)

                else -> applyProposal(p, pending.senderLeafIndex)
            }
            resolvedProposals.add(pending.proposal)
        }
        applyAppDataUpdateProposals(inboundAppDataUpdates)
        val newLeavesInCommit = mutableSetOf<Int>()
        for (add in inlineAdds) {
            newLeavesInCommit.add(applyProposalAdd(add))
        }
        for ((add, _) in referenceAddSenders) {
            newLeavesInCommit.add(applyProposalAdd(add))
        }

        // If the proposals just removed *us*, there is no path-decrypt to do
        // and no confirmation_tag to verify against our (now bogus) commit
        // secret — we have no valid leaf and our copy of `commit_secret`
        // would be all-zeros, so the keyschedule would diverge from every
        // remaining member's. Without this short-circuit, the path-decrypt
        // block calls `BinaryTree.directPath(myLeafIndex, tree.leafCount)`
        // with an out-of-range index and OOMs the JVM (interop test 14).
        //
        // The proposals have already mutated the tree; preserve those
        // mutations on return so the outer state machine knows we are out
        // of the group. `pendingProposals` are cleared because they were
        // tied to the previous epoch.
        val removedSelf =
            myLeafIndex >= tree.leafCount || tree.getLeaf(myLeafIndex) == null
        if (removedSelf) {
            pendingProposals.clear()
            sentKeys.clear()
            return
        }

        // Process UpdatePath
        var commitSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        if (commit.updatePath != null) {
            val updatePath = commit.updatePath
            // Verify LeafNode signature (RFC 9420 Section 7.3)
            require(verifyLeafNodeSignature(updatePath.leafNode, groupId, senderLeafIndex)) {
                "Invalid LeafNode signature in UpdatePath"
            }
            // RFC 9420 §8.4: LeafNode lifetime bounds must be current.
            // An expired leaf is a revoked key — accepting it here would
            // let a peer replay old UpdatePath material past the window
            // the signer authorized.
            val lifetime = updatePath.leafNode.lifetime
            if (lifetime != null) {
                val now = TimeUtils.now()
                require(now >= lifetime.notBefore && now <= lifetime.notAfter) {
                    "LeafNode lifetime expired or not yet valid in UpdatePath"
                }
            }

            // For external commits the sender's leaf is not yet in the tree.
            // Grow the tree with a blank slot so directPath / sibling-hash
            // calculations don't walk past the current tree bounds and loop
            // forever (BinaryTree.parent has no termination when the node
            // index exceeds nodeCount from an out-of-bounds start).
            if (isExternalCommit && senderLeafIndex >= tree.leafCount) {
                tree.setLeaf(senderLeafIndex, null)
            }

            // Capture sibling tree hashes BEFORE applying UpdatePath (needed for parent hash verification)
            val preUpdateSiblingHashes = capturePreUpdateSiblingHashes(senderLeafIndex)

            tree.setLeaf(senderLeafIndex, updatePath.leafNode)
            tree.applyUpdatePath(senderLeafIndex, updatePath.nodes)

            // Verify parent hash chain (RFC 9420 Section 7.9.2) using pre-update sibling hashes
            require(verifyParentHash(senderLeafIndex, updatePath, preUpdateSiblingHashes)) {
                "Parent hash verification failed for UpdatePath"
            }

            // After verification, patch the computed parent_hash values into
            // the FILTERED-direct-path parent nodes. The sender's tree has these
            // values filled in; receivers must match for treeHash() to agree
            // (and thus for the epoch key schedule to converge). Unfiltered
            // parents have been blanked by the proposal or were never on the
            // chain — skip them.
            val (recvParentHashes, _) =
                computeSenderParentHashes(senderLeafIndex, preUpdateSiblingHashes)
            val (filteredDp, filteredCp) = tree.filteredDirectPath(senderLeafIndex)
            for (nodeIdx in filteredDp) {
                val existing = tree.getNode(nodeIdx)
                if (existing is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                    tree.setParent(
                        nodeIdx,
                        existing.parentNode.copy(
                            parentHash = recvParentHashes[nodeIdx] ?: ByteArray(0),
                        ),
                    )
                }
            }

            // Decrypt path secret from our copath node.
            //
            // RFC 9420 §7.9: UpdatePath nodes align to the sender's FILTERED
            // direct path, so `commonAncestorIdx` must be computed against
            // that filtered list (not the unfiltered directPath). Using the
            // unfiltered index picks the wrong `updatePath.nodes[i]` and
            // HPKE-decrypt fails silently — causing `ConfirmationTagMismatch`
            // at best, or a completely wrong commit_secret at worst.
            val unfilteredDirectPath = BinaryTree.directPath(senderLeafIndex, tree.leafCount)
            val myPath = BinaryTree.directPath(myLeafIndex, tree.leafCount)

            // Path-decryption context (RFC 9420 §7.6): matches what the
            // committer used to encrypt — post-tree-mutation tree_hash and
            // bumped epoch, but pre-commit confirmed_transcript_hash.
            val pathDecContextBytes =
                groupContext
                    .copy(
                        epoch = groupContext.epoch + 1,
                        treeHash = tree.treeHash(),
                    ).toTlsBytes()

            // Find the unfiltered common-ancestor index (we need this for the
            // KDF step count: commit_secret = DeriveSecret(path_secret[root])
            // requires walking one KDF step per unfiltered level from the
            // common ancestor to the root).
            val commonAncestorUnfilteredIdx = unfilteredDirectPath.indexOfFirst { it in myPath }
            // Find the filtered common-ancestor index (for picking the right
            // UpdatePath node + copath resolution).
            val commonAncestorNode =
                if (commonAncestorUnfilteredIdx >= 0) unfilteredDirectPath[commonAncestorUnfilteredIdx] else -1
            val commonAncestorFilteredIdx = filteredDp.indexOf(commonAncestorNode)

            // RFC 9420 §7.6: every existing member that survives the commit
            // MUST be able to decrypt the path_secret at the common ancestor
            // — otherwise the sender's tree shape doesn't match ours and
            // we'd silently advance the epoch with `commit_secret = 0`,
            // diverging from the sender's key schedule. The confirmation_tag
            // check below catches that downstream and rolls back, but the
            // mismatch surfaces as a generic "ConfirmationTagMismatch"
            // instead of pointing at the real cause. Hard-fail here with a
            // specific error so an interop break is immediately diagnosable.
            check(commonAncestorFilteredIdx in 0 until updatePath.nodes.size) {
                "UpdatePath has no node at the common ancestor of leaves $senderLeafIndex / $myLeafIndex " +
                    "(filtered_dp_idx=$commonAncestorFilteredIdx, update_path_nodes=${updatePath.nodes.size})"
            }
            val pathNode = updatePath.nodes[commonAncestorFilteredIdx]
            val copathNodeIdx = filteredCp[commonAncestorFilteredIdx]
            val resolution =
                tree.resolution(copathNodeIdx).filterNot { resNode ->
                    BinaryTree.isLeaf(resNode) &&
                        BinaryTree.nodeToLeaf(resNode) in newLeavesInCommit
                }

            // RFC 9420 §7.6: the committer encrypts one path secret per node
            // in the copath resolution, and we decrypt at whichever of those
            // nodes we hold a private key for. That is usually NOT our leaf —
            // a merged subtree resolves to its parent, so from three members
            // on we are addressed at an ancestor. Scan the resolution for a
            // key we actually have rather than assuming our own leaf node.
            val myNodeIdx = BinaryTree.leafToNode(myLeafIndex)
            val candidates =
                resolution.withIndex().mapNotNull { (i, resNode) ->
                    if (i >= pathNode.encryptedPathSecret.size) {
                        null
                    } else {
                        val key = if (resNode == myNodeIdx) encryptionPrivateKey else pathPrivateKeys[resNode]
                        key?.let { Triple(i, resNode, it) }
                    }
                }
            check(candidates.isNotEmpty()) {
                "UpdatePath at common ancestor carries no ciphertext for us " +
                    "(my_leaf=$myLeafIndex, my_node=$myNodeIdx, resolution=$resolution, " +
                    "held_path_nodes=${pathPrivateKeys.keys.sorted()}, " +
                    "encrypted_path_secrets=${pathNode.encryptedPathSecret.size})"
            }

            // Try each node we hold a key for rather than committing to the
            // first. An Add or Remove renumbers nodes, so a retained key can
            // outlive the node it belonged to; a stale one fails the AEAD
            // rather than producing a wrong secret, so trying the next
            // candidate is exact, not a guess.
            var pathSecret: ByteArray? = null
            var decryptedAt = -1
            for ((i, _, key) in candidates) {
                val ct = pathNode.encryptedPathSecret[i]
                pathSecret =
                    try {
                        MlsCryptoProvider.decryptWithLabel(
                            key,
                            "UpdatePathNode",
                            pathDecContextBytes,
                            ct.kemOutput,
                            ct.ciphertext,
                        )
                    } catch (_: Exception) {
                        null
                    }
                if (pathSecret != null) {
                    decryptedAt = i
                    break
                }
            }
            val recoveredPathSecret =
                checkNotNull(pathSecret) {
                    "UpdatePath at common ancestor did not decrypt with any key we hold " +
                        "(my_leaf=$myLeafIndex, resolution=$resolution, slot_tried=$decryptedAt, " +
                        "tried=${candidates.map { it.second }})"
                }

            // The path secret we just recovered belongs to the common ancestor
            // and ratchets up to the root, so it hands us the private key for
            // every node above it on our own direct path. Those are exactly
            // the nodes a later committer may address us at.
            rememberPathKeys(commonAncestorNode, recoveredPathSecret)

            // Derive remaining path secrets from common ancestor up to root,
            // then one more step to reach the `commit_secret` (RFC 9420 §9.2:
            // commit_secret = DeriveSecret(root_path_secret, "path")). Openmls
            // advances one step past the root; quartz was stopping at the root
            // and diverging — that's what caused `ConfirmationTagMismatch` on
            // every cross-impl commit.
            //
            // Step count is measured against the UNFILTERED direct
            // path — the path_secret chain advances one KDF step per
            // level regardless of whether that level emits a
            // UpdatePath node, so filtering changes which nodes carry
            // ciphertext but not the number of KDF steps.
            val stepsToRoot = unfilteredDirectPath.size - commonAncestorUnfilteredIdx - 1
            var currentSecret = recoveredPathSecret
            repeat(stepsToRoot) {
                currentSecret = MlsCryptoProvider.deriveSecret(currentSecret, "path")
            }
            commitSecret = MlsCryptoProvider.deriveSecret(currentSecret, "path")
        }

        // Update transcript hashes (RFC 9420 Section 8.2)
        // ConfirmedTranscriptHashInput = wire_format || FramedContent || signature
        val confirmedTranscriptHashInput = buildConfirmedTranscriptHashInput(commit, senderLeafIndex, signature, wireFormat)
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

        // Compute PSK secret from all resolved proposals (both inline and by-reference)
        val pskSecret = computePskSecret(resolvedProposals)

        // Check for ExternalInit proposal — overrides init_secret (RFC 9420 Section 8.3)
        val externalInitProposal =
            resolvedProposals.filterIsInstance<Proposal.ExternalInit>().firstOrNull()
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

        // Verify confirmation tag (RFC 9420 Section 6.1). Every commit on
        // the wire MUST carry a confirmation_tag that matches what the
        // receiver derives from the post-commit confirmation_key — this
        // is the last line of defense against a committer with the
        // correct signing key but a forged tree state.
        val expectedTag = computeConfirmationTag(epochSecrets.confirmationKey, newConfirmedTranscriptHash)
        require(confirmationTag.isNotEmpty()) {
            "Confirmation tag missing on commit from leaf $senderLeafIndex"
        }
        require(constantTimeEquals(confirmationTag, expectedTag)) {
            "Confirmation tag verification failed"
        }

        // Update interim_transcript_hash for next epoch (reuse verified expectedTag)
        val interimInput = TlsWriter()
        interimInput.putBytes(newConfirmedTranscriptHash)
        interimInput.putOpaqueVarInt(expectedTag)
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

    /**
     * `MLS-Exporter("marmot", "agent-text-stream-quic", 32)` — the secret every
     * member of this epoch derives per-stream record keys from. Per-stream and
     * per-record separation is entirely in the HKDF key context, so this one
     * secret covers every stream in the epoch.
     */
    fun agentTextStreamSecret(): ByteArray =
        exporterSecret(
            AgentTextStreamCrypto.EXPORTER_LABEL,
            AgentTextStreamCrypto.EXPORTER_CONTEXT,
            AgentTextStreamCrypto.SECRET_LENGTH,
        )

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
     * Derive `psk_secret` per RFC 9420 §5.3.
     *
     * For a list of `n` proposed PSKs, the `i`-th step is:
     *
     * ```
     * psk_extracted_i = HKDF.Extract(salt = 0, ikm = psk_i)
     * psk_input_i     = ExpandWithLabel(psk_extracted_i, "derived psk",
     *                                   PSKLabel(psk_id_i, i, n), Nh)
     * psk_secret_i    = HKDF.Extract(salt = psk_secret_{i-1}, ikm = psk_input_i)
     * ```
     *
     * The `PSKLabel` carries the full `PreSharedKeyID` (psktype, type-
     * specific fields, psk_nonce) plus the `index, count` pair — without
     * those, every member that resolves the PSK list in a different order
     * (or with a different total count) would derive a different
     * `psk_secret` and the post-commit confirmation_tag would silently
     * mismatch.
     *
     * The previous implementation HKDF-Extracted the bare PSK value with
     * the running pskSecret as salt and ignored psktype / psk_nonce
     * entirely — non-conformant with §5.3 and incompatible with any peer
     * that follows the spec. Returns zeros when no PSK proposals are
     * present (the `default_psk_secret` per §8.1).
     */
    internal fun computePskSecret(proposals: List<Proposal>): ByteArray {
        val pskProposals = proposals.filterIsInstance<Proposal.Psk>()
        if (pskProposals.isEmpty()) {
            return ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        }

        val zero = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
        val count = pskProposals.size
        var pskSecret = zero
        for ((index, p) in pskProposals.withIndex()) {
            val pskValue =
                pskStore[p.pskId.toHexKey()]
                    ?: throw IllegalStateException("PSK not found in store: ${p.pskId.toHexKey()}")
            val pskExtracted = MlsCryptoProvider.hkdfExtract(zero, pskValue)
            val pskLabel = buildPskLabel(p, index, count)
            val pskInput =
                MlsCryptoProvider.expandWithLabel(
                    secret = pskExtracted,
                    label = "derived psk",
                    context = pskLabel,
                    length = MlsCryptoProvider.HASH_OUTPUT_LENGTH,
                )
            pskSecret = MlsCryptoProvider.hkdfExtract(pskSecret, pskInput)
        }
        return pskSecret
    }

    /**
     * Encode the `PSKLabel` struct used as the `context` argument to
     * ExpandWithLabel during `psk_secret` derivation (RFC 9420 §5.3):
     *
     * ```
     * struct {
     *     PreSharedKeyID id;
     *     uint16 index;
     *     uint16 count;
     * } PSKLabel;
     *
     * struct {
     *     PSKType psktype;
     *     select (PreSharedKeyID.psktype) {
     *         case external:    opaque psk_id<V>;
     *         case resumption:  ResumptionPSKUsage usage;
     *                           opaque psk_group_id<V>;
     *                           uint64 psk_epoch;
     *     };
     *     opaque psk_nonce<V>;
     * } PreSharedKeyID;
     * ```
     *
     * Resumption PSK (`psktype == 2`) carries `usage / psk_group_id /
     * psk_epoch` fields that aren't representable on `Proposal.Psk` today —
     * the on-wire schema there is just `(pskType, pskId, pskNonce)`. Reject
     * loudly until the proposal type is widened, rather than silently
     * encoding a broken PSKLabel that would diverge from peers.
     */
    private fun buildPskLabel(
        psk: Proposal.Psk,
        index: Int,
        count: Int,
    ): ByteArray {
        val w = TlsWriter()
        // PreSharedKeyID
        w.putUint8(psk.pskType)
        when (psk.pskType) {
            PSK_TYPE_EXTERNAL -> {
                w.putOpaqueVarInt(psk.pskId)
            }

            PSK_TYPE_RESUMPTION -> {
                throw IllegalStateException(
                    "Resumption PSKs are not supported yet — Proposal.Psk lacks " +
                        "(usage, psk_group_id, psk_epoch) per RFC 9420 §5.3.",
                )
            }

            else -> {
                throw IllegalStateException("Unknown PSKType ${psk.pskType}")
            }
        }
        w.putOpaqueVarInt(psk.pskNonce)
        // PSKLabel tail
        w.putUint16(index)
        w.putUint16(count)
        return w.toByteArray()
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
        signature: ByteArray = ByteArray(0),
        wireFormat: WireFormat = WireFormat.PUBLIC_MESSAGE,
    ): ByteArray = buildConfirmedTranscriptHashInput(commit, senderLeafIndex, groupId, epoch, signature, wireFormat)

    /**
     * Compute the RFC 9420 §7.9.2 parent_hash chain for a commit we are
     * producing: returns the parent_hash value that should be stored in
     * each parent node on our direct path, plus the parent_hash value
     * that belongs in the committer's LeafNode (for its TBS signature).
     *
     * Must be called AFTER [RatchetTree.applyUpdatePath] (so parent
     * nodes carry the new encryption keys) and with [preUpdateSiblingHashes]
     * captured BEFORE applyUpdatePath (so the "original sibling tree hash"
     * really is from the pre-update tree).
     */
    private fun computeSenderParentHashes(
        senderLeafIndex: Int,
        preUpdateSiblingHashes: Map<Int, ByteArray>,
    ): Pair<Map<Int, ByteArray>, ByteArray> = computeSenderParentHashes(tree, senderLeafIndex, preUpdateSiblingHashes)

    private fun computeSenderParentHashes(
        tree: com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree,
        senderLeafIndex: Int,
        preUpdateSiblingHashes: Map<Int, ByteArray>,
    ): Pair<Map<Int, ByteArray>, ByteArray> {
        // RFC 9420 §7.9.2: parent_hash chain walks the FILTERED direct path.
        // A filtered-out parent is never on the chain because its copath
        // subtree is blank — there's no sibling tree hash to fold into the
        // next hop. Using the unfiltered path here produces a chain that
        // doesn't match what openmls/wn compute on the other side.
        val (filteredDp, _) = tree.filteredDirectPath(senderLeafIndex)
        val unfilteredDp = BinaryTree.directPath(senderLeafIndex, tree.leafCount)
        if (filteredDp.isEmpty()) return Pair(emptyMap(), ByteArray(0))

        // preUpdateSiblingHashes is indexed by level in the UNFILTERED path —
        // map each filtered node to its unfiltered level so we pick the
        // correct sibling tree hash.
        val filteredLevels = filteredDp.map { unfilteredDp.indexOf(it) }

        val hashes = mutableMapOf<Int, ByteArray>()
        // Root's parent_hash is empty by convention (RFC 9420 §7.9.2).
        hashes[filteredDp.last()] = ByteArray(0)

        // Walk top-down (root has no parent → already set). For each node
        // X below root on the filtered path, parent_hash(X) is computed
        // with parent(X)'s fields, where "parent" is the NEXT entry on the
        // filtered direct path.
        for (i in filteredDp.size - 2 downTo 0) {
            val xIdx = filteredDp[i]
            val parentIdx = filteredDp[i + 1]
            val parentLevel = filteredLevels[i + 1]
            val parentNode = tree.getNode(parentIdx)
            if (parentNode !is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                hashes[xIdx] = ByteArray(0)
                continue
            }
            val siblingTreeHash =
                preUpdateSiblingHashes[parentLevel]
                    ?: error("missing pre-update sibling tree hash at level $parentLevel")
            hashes[xIdx] =
                MlsCryptoProvider.hash(
                    encodeParentHashInput(
                        encryptionKey = parentNode.parentNode.encryptionKey,
                        parentHash = hashes[parentIdx] ?: ByteArray(0),
                        originalSiblingTreeHash = siblingTreeHash,
                    ),
                )
        }

        // The committer's leaf carries parent_hash(parent(leaf)) = parent_hash
        // computed with the immediate parent's (filteredDp[0]) fields.
        val immediateParentIdx = filteredDp.first()
        val immediateParentLevel = filteredLevels.first()
        val immediateParent = tree.getNode(immediateParentIdx)
        val leafParentHash =
            if (immediateParent is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                val siblingTreeHash =
                    preUpdateSiblingHashes[immediateParentLevel]
                        ?: error("missing pre-update sibling tree hash at level $immediateParentLevel")
                MlsCryptoProvider.hash(
                    encodeParentHashInput(
                        encryptionKey = immediateParent.parentNode.encryptionKey,
                        parentHash = hashes[immediateParentIdx] ?: ByteArray(0),
                        originalSiblingTreeHash = siblingTreeHash,
                    ),
                )
            } else {
                ByteArray(0)
            }
        return Pair(hashes, leafParentHash)
    }

    /**
     * Serialize RFC 9420 §7.9.2 ParentHashInput:
     *   struct {
     *     HPKEPublicKey encryption_key;
     *     opaque parent_hash<V>;
     *     opaque original_sibling_tree_hash<V>;
     *   } ParentHashInput;
     */
    private fun encodeParentHashInput(
        encryptionKey: ByteArray,
        parentHash: ByteArray,
        originalSiblingTreeHash: ByteArray,
    ): ByteArray = Companion.encodeParentHashInput(encryptionKey, parentHash, originalSiblingTreeHash)

    private fun capturePreUpdateSiblingHashes(senderLeafIndex: Int): Map<Int, ByteArray> {
        val directPath = BinaryTree.directPath(senderLeafIndex, tree.leafCount)
        val nodeCount = BinaryTree.nodeCount(tree.leafCount)
        val result = mutableMapOf<Int, ByteArray>()
        for ((i, _) in directPath.withIndex()) {
            val childIdx =
                if (i == 0) BinaryTree.leafToNode(senderLeafIndex) else directPath[i - 1]
            val siblingIdx = BinaryTree.sibling(childIdx, nodeCount)
            result[i] = tree.treeHashNode(siblingIdx)
        }
        return result
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
     *
     * @param preUpdateSiblingHashes sibling hashes captured BEFORE the UpdatePath was applied
     */
    private fun verifyParentHash(
        senderLeafIndex: Int,
        updatePath: UpdatePath,
        preUpdateSiblingHashes: Map<Int, ByteArray>,
    ): Boolean {
        // Filtered direct path drives the parent_hash chain — a sender
        // with an empty filtered direct path has no parents to hash so
        // the leaf's parent_hash field should be an empty byte string
        // and we can short-circuit verification.
        val (filteredDp, _) = tree.filteredDirectPath(senderLeafIndex)
        if (filteredDp.isEmpty()) return true

        // RFC 9420 §7.9.2: compute what the sender's parent_hash chain
        // SHOULD have been given the post-update tree state, then compare
        // the leaf's stored parent_hash to the expected value. We can't
        // rely on the stored parent_hash of the parent nodes on our own
        // tree because applyUpdatePath writes them as placeholder empty
        // bytes — the sender never transmits them on the wire.
        val (_, expectedLeafParentHash) =
            computeSenderParentHashes(senderLeafIndex, preUpdateSiblingHashes)
        val leafParentHash = updatePath.leafNode.parentHash ?: ByteArray(0)
        return leafParentHash.contentEquals(expectedLeafParentHash)
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
     * Resolve a commit's proposals as THIS state sees them, or null when it
     * references a proposal this state does not hold.
     *
     * Convergence needs this without applying anything: a candidate parent is
     * tried by several competing commits, and judging authorization must not
     * advance the state being judged against.
     */
    fun resolveCommitProposals(pubMsg: PublicMessage): List<PendingProposal>? {
        val commit =
            try {
                Commit.decodeTls(TlsReader(pubMsg.content))
            } catch (_: Exception) {
                return null
            }
        val resolved = mutableListOf<PendingProposal>()
        for (proposalOrRef in commit.proposals) {
            when (proposalOrRef) {
                is ProposalOrRef.Inline ->
                    resolved.add(PendingProposal(proposalOrRef.proposal, pubMsg.sender.leafIndex))

                is ProposalOrRef.Reference -> {
                    val match =
                        pendingProposals.find { pending ->
                            val refValue = pending.authenticatedContentBytes ?: pending.proposal.toTlsBytes()
                            MlsCryptoProvider
                                .refHash("MLS 1.0 Proposal Reference", refValue)
                                .contentEquals(proposalOrRef.proposalRef)
                        } ?: return null
                    resolved.add(match)
                }
            }
        }
        return resolved
    }

    /**
     * Whether [pubMsg]'s committer is authorized to make it against THIS state,
     * without applying anything.
     *
     * Runs the same two gates the apply path runs, so an inbound commit and one
     * we authored are held to one rule rather than two that drift. Authorization
     * is parent-relative: this answer is only meaningful once MLS
     * authentication has already established that this state IS the commit's
     * candidate parent.
     */
    fun isCommitAuthorized(pubMsg: PublicMessage): Boolean {
        val proposals = resolveCommitProposals(pubMsg) ?: return false
        return try {
            enforceAuthorizedProposalSet(proposals, committerLeafIndex = pubMsg.sender.leafIndex)
            enforceNoAdminDepletion(proposals)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Whether every proposal in [pubMsg] is one its own sender may make without
     * admin authority — a self-Update, or SelfRemove of itself.
     *
     * This is the `ordinary` / `privileged` distinction convergence compares on:
     * a commit is `privileged` exactly when its applicable rule REQUIRES an
     * active admin, so one an ordinary member could also have made stays
     * ordinary even when an admin happened to send it.
     */
    fun isSelfOnlyCommit(pubMsg: PublicMessage): Boolean {
        val proposals = resolveCommitProposals(pubMsg) ?: return false
        if (proposals.isEmpty()) return false
        val committer = pubMsg.sender.leafIndex
        val allSelfRemove =
            proposals.all { it.proposal is Proposal.SelfRemove && it.senderLeafIndex == committer }
        if (allSelfRemove) return true
        return proposals.size == 1 &&
            proposals[0].proposal is Proposal.Update &&
            proposals[0].senderLeafIndex == committer
    }

    /**
     * Verify RFC 9420 §6.2 membership_tag on an inbound PublicMessage Commit.
     * The tag binds the whole `(TBS || FramedContentAuthData)` payload to
     * the sender's epoch — if it's missing or wrong, the sender either
     * isn't a member or the message was tampered with. Returns false for
     * either case; callers should reject the commit before advancing state.
     */
    fun verifyPublicMessageCommitMembershipTag(pubMsg: PublicMessage): Boolean {
        val membershipTag = pubMsg.membershipTag ?: return false
        if (membershipTag.isEmpty()) return false
        val confirmationTag = pubMsg.confirmationTag ?: return false
        val tbs =
            buildCommitFramedContentTbs(
                groupId = pubMsg.groupId,
                epoch = pubMsg.epoch,
                senderLeafIndex = pubMsg.sender.leafIndex,
                commitBytes = pubMsg.content,
                groupContextBytes = groupContext.toTlsBytes(),
                wireFormat = WireFormat.PUBLIC_MESSAGE,
            )
        val tbmWriter = TlsWriter()
        tbmWriter.putBytes(tbs)
        tbmWriter.putOpaqueVarInt(pubMsg.signature)
        tbmWriter.putOpaqueVarInt(confirmationTag)
        return verifyMembershipTag(tbmWriter.toByteArray(), membershipTag)
    }

    /**
     * Build PrivateContentAAD (RFC 9420 §6.3.2):
     * ```
     * struct {
     *     opaque group_id<V>;
     *     uint64 epoch;
     *     ContentType content_type;
     *     opaque authenticated_data<V>;
     * } PrivateContentAAD;
     * ```
     */
    private fun buildPrivateContentAAD(
        groupId: ByteArray,
        epoch: Long,
        contentType: ContentType,
        authenticatedData: ByteArray,
    ): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(groupId)
        writer.putUint64(epoch)
        writer.putUint8(contentType.value)
        writer.putOpaqueVarInt(authenticatedData)
        return writer.toByteArray()
    }

    /**
     * Build SenderDataAAD (RFC 9420 §6.3.1):
     * ```
     * struct {
     *     opaque group_id<V>;
     *     uint64 epoch;
     *     ContentType content_type;
     * } SenderDataAAD;
     * ```
     */
    private fun buildSenderDataAAD(
        groupId: ByteArray,
        epoch: Long,
        contentType: ContentType,
    ): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(groupId)
        writer.putUint64(epoch)
        writer.putUint8(contentType.value)
        return writer.toByteArray()
    }

    /**
     * Apply an Add proposal and return the assigned leaf index.
     */
    private fun applyProposalAdd(proposal: Proposal.Add): Int {
        val leafNode = proposal.keyPackage.leafNode

        // Validate lifetime
        val lifetime = leafNode.lifetime
        if (lifetime != null) {
            val now = TimeUtils.now()
            require(now >= lifetime.notBefore && now <= lifetime.notAfter) {
                "KeyPackage lifetime expired or not yet valid"
            }
        }

        // Validate capabilities (RFC 9420 §12.1.1)
        val caps = leafNode.capabilities
        require(1 in caps.versions) {
            "KeyPackage does not support MLS protocol version 1"
        }
        require(1 in caps.ciphersuites) {
            "KeyPackage does not support ciphersuite 0x0001"
        }

        // RFC 9420 §12.4.2: an Add proposal MUST be rejected if the new
        // member's leaf capabilities don't advertise every type listed in
        // the group's `required_capabilities` extension. Without this gate
        // a non-conformant member silently joins, and any subsequent commit
        // that touches their leaf is rejected by peers that DO enforce the
        // requirement — splitting the group on the next epoch.
        findRequiredCapabilities(groupContext.extensions)?.let { req ->
            requireCapabilitiesMeetRequirements(caps, req, "Add KeyPackage leaf")
        }

        return tree.addLeaf(leafNode)
    }

    /**
     * MIP-03 authorization gate.
     *
     * Once the group has at least one admin configured in `admin_pubkeys`,
     * non-admin senders may only issue:
     *   - a single self-Update proposal, or
     *   - one-or-more SelfRemove proposals authored by the committer.
     *
     * Admins may commit any proposal type. Before any admin is configured
     * (group bootstrap) the check is relaxed, mirroring the bootstrap policy
     * in [MlsGroupManager.updateGroupExtensions].
     *
     * [committerLeafIndex] is the leaf that signed the commit — `myLeafIndex`
     * for our own outbound commits, `pubMsg.sender.leafIndex` for inbound
     * commits. The "self-only" rule is checked against the committer; when
     * the committer is an admin the rule is skipped entirely so admin-folded
     * inbound proposals (e.g. another member's `SelfRemove` referenced by
     * an admin's GCE commit) are accepted.
     */
    internal fun enforceAuthorizedProposalSet(
        proposals: List<PendingProposal>,
        committerLeafIndex: Int = myLeafIndex,
    ) {
        if (proposals.isEmpty()) return
        // Reads whichever profile the group is on: the admin-policy component
        // (0x8003) for current-profile groups, `marmot_group_data` (0xF2EE)
        // for legacy ones. An empty set means bootstrap — no admins named yet —
        // and the gate stays open, mirroring MlsGroupManager.updateGroupExtensions.
        val admins = currentAdminIdentities()
        if (admins.isEmpty() || isLeafAdmin(committerLeafIndex)) return

        val allSelfRemove =
            proposals.all { it.proposal is Proposal.SelfRemove && it.senderLeafIndex == committerLeafIndex }
        if (allSelfRemove) return

        val singleSelfUpdate =
            proposals.size == 1 &&
                proposals[0].proposal is Proposal.Update &&
                proposals[0].senderLeafIndex == committerLeafIndex
        if (singleSelfUpdate) return

        throw IllegalStateException(
            "MIP-03: non-admin members may only commit a single self-Update or SelfRemove-only " +
                "proposals; got ${proposals.map { it.proposal::class.simpleName }} from leaf $committerLeafIndex",
        )
    }

    /**
     * Reject any commit that would leave the group without at least one member
     * still listed in `admin_pubkeys` (MIP-03 admin depletion guard).
     *
     * We simulate the post-commit member set and the post-commit `admin_pubkeys`
     * list, then require a non-empty intersection. The guard is only active
     * once the group has a configured admin set — it does not kick in during
     * bootstrap before any admin is named.
     */
    internal fun enforceNoAdminDepletion(proposals: List<PendingProposal>) {
        val currentAdmins = currentAdminIdentities()
        if (currentAdmins.isEmpty()) return // Bootstrap: no admins yet, nothing to deplete.

        // Resolve the effective admin list after this commit. Three carriers can
        // change it, and they are checked in the order the commit applies them:
        // an AppDataUpdate on 0x8003 (current profile), then a
        // GroupContextExtensions proposal replacing the whole extension list
        // (either profile). AppDataUpdate is resolved last because
        // `applyAppDataUpdateProposals` runs after the rest of the list.
        val gce =
            proposals
                .asSequence()
                .map { it.proposal }
                .filterIsInstance<Proposal.GroupContextExtensions>()
                .lastOrNull()
        val extensionsAfterGce = gce?.extensions ?: groupContext.extensions

        val adminUpdate =
            proposals
                .asSequence()
                .map { it.proposal }
                .filterIsInstance<Proposal.AppDataUpdate>()
                .lastOrNull { it.componentId == AdminPolicyV1.COMPONENT_ID }

        val adminSet =
            when (val operation = adminUpdate?.operation) {
                is Proposal.AppDataUpdate.Operation.Update ->
                    AdminPolicyV1.decode(operation.data).adminHexKeys.toSet()

                // Removing the admin policy is never valid — it is the sole
                // admin authority for the group's lifetime — so an empty set
                // here trips the depletion check below, which is the outcome
                // we want.
                Proposal.AppDataUpdate.Operation.Remove -> emptySet()

                null -> adminIdentitiesIn(extensionsAfterGce)
            }
        check(adminSet.isNotEmpty()) {
            "commit would leave the group with no admins (admin depletion)"
        }

        // Compute which leaves remain after applying Removes/SelfRemoves.
        val removedLeaves = mutableSetOf<Int>()
        for (pending in proposals) {
            when (val p = pending.proposal) {
                is Proposal.Remove -> removedLeaves.add(p.removedLeafIndex)
                is Proposal.SelfRemove -> removedLeaves.add(pending.senderLeafIndex)
                else -> Unit
            }
        }

        val remainingAdminIdentities = mutableSetOf<String>()
        for (i in 0 until tree.leafCount) {
            if (i in removedLeaves) continue
            val id = memberIdentityHex(i) ?: continue
            if (id in adminSet) remainingAdminIdentities.add(id)
        }

        check(remainingAdminIdentities.isNotEmpty()) {
            "MIP-03: commit would leave the group without any admin members"
        }
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

            is Proposal.AppDataUpdate -> {
                // Applied by [applyAppDataUpdateProposals] after the rest of
                // the proposal list, so a GroupContextExtensions proposal in
                // the same commit is already reflected. Reaching it here would
                // mean a caller bypassed that ordering.
                error("AppDataUpdate must be applied through applyAppDataUpdateProposals")
            }
        }
    }

    /**
     * Fold every `AppDataUpdate` proposal in a commit into the GroupContext
     * `app_data_dictionary`.
     *
     * Two ordering rules matter, and both change the resulting GroupContext
     * bytes — and therefore the epoch's key schedule — if we get them wrong:
     *
     *  1. These run AFTER the rest of the proposal list, so a
     *     `GroupContextExtensions` proposal in the same commit is already
     *     applied and we update the dictionary it produced.
     *  2. The dictionary extension is added-or-replaced in place and is never
     *     dropped, even when the last component is removed and the dictionary
     *     ends up empty. An absent extension and an empty one are different
     *     GroupContexts.
     *
     * MLS deliberately leaves the meaning of an update payload to the
     * application — openmls hands the proposals back for the app to resolve —
     * because a component's payload can be an arbitrary diff. Every Marmot
     * component document defines its update as a full replacement state, so
     * here resolution is the identity function. A future component that wanted
     * true diff semantics would have to resolve them before this point.
     */
    private fun applyAppDataUpdateProposals(updates: List<Proposal.AppDataUpdate>) {
        if (updates.isEmpty()) return

        var dictionary = AppDataDictionary.fromExtensionsOrEmpty(groupContext.extensions)
        for (update in updates) {
            dictionary =
                when (val operation = update.operation) {
                    is Proposal.AppDataUpdate.Operation.Update ->
                        dictionary.with(update.componentId, operation.data)

                    Proposal.AppDataUpdate.Operation.Remove ->
                        dictionary.without(update.componentId)
                }
        }

        val extension = dictionary.toExtension()
        val existing = groupContext.extensions.indexOfFirst { it.extensionType == AppDataDictionary.EXTENSION_TYPE }
        val newExtensions =
            if (existing >= 0) {
                groupContext.extensions.toMutableList().also { it[existing] = extension }
            } else {
                groupContext.extensions + extension
            }
        groupContext = groupContext.copy(extensions = newExtensions)
    }

    /**
     * Lowest common ancestor of [myLeafIndex] and [otherLeafIndex] expressed as
     * an index INTO our own direct path, or -1 when there is none.
     *
     * Our direct path runs leaf-ward to root-ward, so the first node it shares
     * with the other leaf's direct path is their lowest common ancestor — and
     * its position is also the index of that node's path secret in
     * `derivePathSecrets`, which walks the same list.
     */
    private fun directPathIndexOfAncestorWith(otherLeafIndex: Int): Int {
        val mine = BinaryTree.directPath(myLeafIndex, tree.leafCount)
        val theirs = BinaryTree.directPath(otherLeafIndex, tree.leafCount).toSet()
        return mine.indexOfFirst { it in theirs }
    }

    /**
     * @param committerPathSecrets the path secrets this commit minted for the
     *   committer's own direct path, in direct-path order. RFC 9420 §12.4.3.1:
     *   when the Commit carries an UpdatePath, each new member's GroupSecrets
     *   MUST carry the path secret at the lowest common ancestor of that
     *   member's leaf and the committer's. Without it the joiner holds no key
     *   for any ancestor, and the FIRST later commit that addresses it at one —
     *   which is every commit once its subtree is merged — is undecryptable.
     */
    private fun buildWelcome(
        addedMembers: List<Pair<Int, MlsKeyPackage>>,
        committerPathSecrets: List<PathSecretAndKey>,
    ): ByteArray {
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
                    computeConfirmationTag(
                        epochSecrets.confirmationKey,
                        groupContext.confirmedTranscriptHash,
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
                val ancestorIdx = directPathIndexOfAncestorWith(leafIdx)
                val groupSecrets =
                    GroupSecrets(
                        joinerSecret = epochSecrets.joinerSecret,
                        pathSecret = committerPathSecrets.getOrNull(ancestorIdx)?.pathSecret,
                    )
                val gsBytes = groupSecrets.toTlsBytes()

                // HPKE-encrypt to the member's init_key. Per RFC 9420
                // §12.4.3.1, the HPKE context is the encrypted_group_info.
                val hpkeCt =
                    MlsCryptoProvider.encryptWithLabel(
                        kp.initKey,
                        "Welcome",
                        encryptedGroupInfo,
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
        private const val REUSE_GUARD_LENGTH = 4

        // RFC 9420 §13.3 IANA registry: 0x0002 is ratchet_tree.
        // (0x0001 is application_id — using it here makes Welcomes
        // unreadable to OpenMLS/MDK/whitenoise.)
        private const val RATCHET_TREE_EXTENSION_TYPE = 0x0002

        // RFC 9420 §5.3 PSKType registry.
        private const val PSK_TYPE_EXTERNAL = 1
        private const val PSK_TYPE_RESUMPTION = 2

        /**
         * Build the FramedContentTBS bytes for a member-sender commit
         * (RFC 9420 §6.1). The signature over this value is the
         * `FramedContentAuthData.signature` that rides both in the
         * on-the-wire PublicMessage/PrivateMessage AND in the
         * ConfirmedTranscriptHashInput.
         *
         * The wire_format argument MUST match the envelope actually used;
         * mixing PUBLIC_MESSAGE here with a PRIVATE_MESSAGE envelope (or
         * vice versa) produces a signature that receivers can't verify
         * because they recompute the TBS with the real wire_format byte.
         */
        internal fun buildCommitFramedContentTbs(
            groupId: ByteArray,
            epoch: Long,
            senderLeafIndex: Int,
            commitBytes: ByteArray,
            groupContextBytes: ByteArray,
            wireFormat: WireFormat = WireFormat.PUBLIC_MESSAGE,
        ): ByteArray {
            val writer = TlsWriter()
            writer.putUint16(MlsMessage.MLS_VERSION_10)
            writer.putUint16(wireFormat.value)
            writer.putOpaqueVarInt(groupId)
            writer.putUint64(epoch)
            encodeSender(writer, Sender(SenderType.MEMBER, senderLeafIndex))
            writer.putOpaqueVarInt(ByteArray(0)) // authenticated_data
            writer.putUint8(ContentType.COMMIT.value)
            writer.putBytes(commitBytes) // Commit struct — no outer length prefix
            writer.putBytes(groupContextBytes) // member sender appends context raw
            return writer.toByteArray()
        }

        internal fun framePublicMessageCommit(
            groupId: ByteArray,
            epoch: Long,
            senderLeafIndex: Int,
            commitBytes: ByteArray,
            confirmationTag: ByteArray,
            signature: ByteArray,
            membershipKey: ByteArray,
            tbsBytes: ByteArray,
        ): ByteArray {
            // AuthenticatedContentTBM = TBS || FramedContentAuthData, where
            // FramedContentAuthData = signature<V> || (for commits) confirmation_tag<V>.
            // Caller already signed the TBS so we reuse the exact bytes they
            // produced — any drift here would desync the membership_tag from
            // the signature.
            val tbmWriter = TlsWriter()
            tbmWriter.putBytes(tbsBytes)
            tbmWriter.putOpaqueVarInt(signature)
            tbmWriter.putOpaqueVarInt(confirmationTag)
            val tbm = tbmWriter.toByteArray()

            // membership_tag = MAC(membership_key, TBM) — HMAC-SHA256 for
            // ciphersuite 0x0001. Length = 32; openmls's equal_ct logs
            // "Incompatible values" when an empty tag is compared to this.
            val macInstance = MacInstance("HmacSHA256", membershipKey)
            macInstance.update(tbm)
            val membershipTag = macInstance.doFinal()

            val publicMessage =
                PublicMessage(
                    groupId = groupId,
                    epoch = epoch,
                    sender = Sender(SenderType.MEMBER, senderLeafIndex),
                    authenticatedData = ByteArray(0),
                    contentType = ContentType.COMMIT,
                    content = commitBytes,
                    signature = signature,
                    confirmationTag = confirmationTag,
                    membershipTag = membershipTag,
                )
            return MlsMessage.fromPublicMessage(publicMessage).toTlsBytes()
        }

        /**
         * Build ConfirmedTranscriptHashInput (RFC 9420 Section 8.2) — static version
         * usable from both instance methods and companion object factory methods.
         */
        private fun buildConfirmedTranscriptHashInput(
            commit: Commit,
            senderLeafIndex: Int,
            groupId: ByteArray,
            epoch: Long,
            signature: ByteArray = ByteArray(0),
            wireFormat: WireFormat = WireFormat.PUBLIC_MESSAGE,
        ): ByteArray {
            // RFC 9420 §8.2: ConfirmedTranscriptHashInput carries the wire_format
            // of the actual AuthenticatedContent — openmls passes
            // `mls_content.wire_format()` through. When B sends a commit as a
            // PrivateMessage (mdk default = AlwaysCiphertext outgoing), amy
            // MUST recompute the transcript hash with wire_format=2, or the
            // resulting confirmed_transcript_hash — and thus the
            // confirmation_tag derived from it — silently diverges from B's.
            val writer = TlsWriter()
            writer.putUint16(wireFormat.value)
            writer.putOpaqueVarInt(groupId)
            writer.putUint64(epoch)
            writer.putUint8(1) // SenderType.MEMBER
            writer.putUint32(senderLeafIndex.toLong())
            writer.putOpaqueVarInt(ByteArray(0)) // authenticated_data
            writer.putUint8(ContentType.COMMIT.value)
            commit.encodeTls(writer)
            writer.putOpaqueVarInt(signature) // FramedContentAuthData.signature
            return writer.toByteArray()
        }

        /**
         * Companion-accessible clone of [computeSenderParentHashes].
         * Used by [externalJoin] which builds its own tree locally
         * (without constructing an [MlsGroup] first).
         */
        private fun computeExternalSenderParentHashes(
            tree: com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree,
            senderLeafIndex: Int,
            preUpdateSiblingHashes: Map<Int, ByteArray>,
        ): Pair<Map<Int, ByteArray>, ByteArray> {
            val directPath = BinaryTree.directPath(senderLeafIndex, tree.leafCount)
            if (directPath.isEmpty()) return Pair(emptyMap(), ByteArray(0))

            val hashes = mutableMapOf<Int, ByteArray>()
            hashes[directPath.last()] = ByteArray(0)
            for (i in directPath.size - 2 downTo 0) {
                val xIdx = directPath[i]
                val parentIdx = directPath[i + 1]
                val parentNode = tree.getNode(parentIdx)
                if (parentNode !is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                    hashes[xIdx] = ByteArray(0)
                    continue
                }
                val siblingTreeHash =
                    preUpdateSiblingHashes[i + 1]
                        ?: error("missing pre-update sibling tree hash at level ${i + 1}")
                val w = TlsWriter()
                w.putOpaqueVarInt(parentNode.parentNode.encryptionKey)
                w.putOpaqueVarInt(hashes[parentIdx] ?: ByteArray(0))
                w.putOpaqueVarInt(siblingTreeHash)
                hashes[xIdx] = MlsCryptoProvider.hash(w.toByteArray())
            }
            val immediateParentIdx = directPath.first()
            val immediateParent = tree.getNode(immediateParentIdx)
            val leafParentHash =
                if (immediateParent is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                    val siblingTreeHash =
                        preUpdateSiblingHashes[0]
                            ?: error("missing pre-update sibling tree hash at leaf level")
                    val w = TlsWriter()
                    w.putOpaqueVarInt(immediateParent.parentNode.encryptionKey)
                    w.putOpaqueVarInt(hashes[immediateParentIdx] ?: ByteArray(0))
                    w.putOpaqueVarInt(siblingTreeHash)
                    MlsCryptoProvider.hash(w.toByteArray())
                } else {
                    ByteArray(0)
                }
            return Pair(hashes, leafParentHash)
        }

        /**
         * Compute confirmation_tag = MAC(confirmation_key, confirmed_transcript_hash).
         * Static version usable from companion object factory methods.
         */
        private fun computeConfirmationTag(
            confirmationKey: ByteArray,
            confirmedTranscriptHash: ByteArray,
        ): ByteArray {
            val mac = MacInstance("HmacSHA256", confirmationKey)
            mac.update(confirmedTranscriptHash)
            return mac.doFinal()
        }

        // RFC 9420 §13.3 IANA registry: 0x0003 is required_capabilities.
        // (0x0002 is ratchet_tree — putting it here makes GroupContext
        // unreadable to OpenMLS/MDK, which type-validates extensions by
        // context.)
        private const val REQUIRED_CAPABILITIES_EXTENSION_TYPE = 0x0003

        // RFC 9420 §13.3 IANA registry: 0x0004 is external_pub.
        // (0x0003 is required_capabilities — using it here makes
        // external-join GroupInfos unreadable to OpenMLS/MDK.)
        private const val EXTERNAL_PUB_EXTENSION_TYPE = 0x0004
        private const val EXTERNAL_SENDERS_EXTENSION_TYPE = 0x0004

        /** MLS self_remove proposal type (MIP-00 / MIP-03). */
        private const val SELF_REMOVE_PROPOSAL_TYPE = 0x000A

        /** MLS extensions draft `app_data_update` proposal type. */
        private const val APP_DATA_UPDATE_PROPOSAL_TYPE = 0x0008

        /** How far back a fresh KeyPackage LeafNode's `not_before` is set. */
        private const val LIFETIME_SKEW_SECONDS = 3_600L

        /**
         * 84 days. The spec's ceiling is 84 days plus one hour of skew, so this
         * leaves the whole skew allowance as headroom rather than sitting
         * exactly on the limit.
         */
        private const val LIFETIME_SPAN_SECONDS = 84L * 24 * 60 * 60

        /** Marmot Group Data Extension type (MIP-01). */
        private const val MARMOT_GROUP_DATA_EXTENSION_TYPE = 0xF2EE

        /** Known extension types that this implementation accepts. */
        private val KNOWN_EXTENSION_TYPES =
            setOf(
                RATCHET_TREE_EXTENSION_TYPE,
                REQUIRED_CAPABILITIES_EXTENSION_TYPE,
                EXTERNAL_PUB_EXTENSION_TYPE,
                EXTERNAL_SENDERS_EXTENSION_TYPE,
                MARMOT_GROUP_DATA_EXTENSION_TYPE,
                // The current profile's carrier for all app-owned group state.
                // A group can arrive at one either by being created with it or
                // by a GroupContextExtensions proposal that installs it.
                AppDataDictionary.EXTENSION_TYPE,
            )

        /**
         * Build an MLS `required_capabilities` extension that marks Marmot's
         * mandatory interop set as required for all members (RFC 9420 §7.2):
         *   extensions  = [marmot_group_data (0xF2EE)]
         *   proposals   = [self_remove (0x000A)]
         *   credentials = [Basic (0x0001)]
         */
        private fun buildMarmotRequiredCapabilitiesExtension(): Extension {
            val writer = TlsWriter()
            // extensions<V>: uint16 each
            val exts = TlsWriter()
            exts.putUint16(MARMOT_GROUP_DATA_EXTENSION_TYPE)
            writer.putOpaqueVarInt(exts.toByteArray())
            // proposals<V>: uint16 each
            val props = TlsWriter()
            props.putUint16(SELF_REMOVE_PROPOSAL_TYPE)
            writer.putOpaqueVarInt(props.toByteArray())
            // credentials<V>: uint16 each
            val creds = TlsWriter()
            creds.putUint16(Credential.CREDENTIAL_TYPE_BASIC)
            writer.putOpaqueVarInt(creds.toByteArray())
            return Extension(
                extensionType = REQUIRED_CAPABILITIES_EXTENSION_TYPE,
                extensionData = writer.toByteArray(),
            )
        }

        /**
         * Parsed view of the RFC 9420 §7.2 `required_capabilities` extension.
         *
         * The on-wire struct is three `uint16<V>` vectors —
         * `extensions / proposals / credentials` — that name the types every
         * member of the group MUST advertise in their leaf [Capabilities].
         */
        internal data class RequiredCapabilities(
            val extensions: List<Int>,
            val proposals: List<Int>,
            val credentials: List<Int>,
        )

        /**
         * Decode the `required_capabilities` extension from the GroupContext
         * extension list, or `null` if the group hasn't installed one (some
         * peers may omit it; treat missing as "no restriction").
         */
        internal fun findRequiredCapabilities(extensions: List<Extension>): RequiredCapabilities? {
            val ext = extensions.find { it.extensionType == REQUIRED_CAPABILITIES_EXTENSION_TYPE } ?: return null
            val r = TlsReader(ext.extensionData)

            fun readUint16List(data: ByteArray): List<Int> {
                val rr = TlsReader(data)
                val list = mutableListOf<Int>()
                while (rr.hasRemaining) list.add(rr.readUint16())
                return list
            }
            val exts = readUint16List(r.readOpaqueVarInt())
            val props = readUint16List(r.readOpaqueVarInt())
            val creds = readUint16List(r.readOpaqueVarInt())
            return RequiredCapabilities(exts, props, creds)
        }

        /**
         * RFC 9420 §7.2 + §12.4.2: every member's leaf [Capabilities] MUST
         * advertise every type listed in the group's `required_capabilities`
         * extension. Adding a member whose KeyPackage doesn't meet the
         * requirement leaves the group in a non-conformant state where
         * peers that DO enforce the requirement will reject any commit that
         * touches that leaf.
         *
         * Throws [IllegalStateException] with a precise diff so debugging
         * an interop break against another implementation is one log line.
         */
        internal fun requireCapabilitiesMeetRequirements(
            caps: Capabilities,
            req: RequiredCapabilities,
            who: String,
        ) {
            val missingExts = req.extensions.filter { it !in caps.extensions }
            val missingProps = req.proposals.filter { it !in caps.proposals }
            val missingCreds = req.credentials.filter { it !in caps.credentials }
            if (missingExts.isNotEmpty() || missingProps.isNotEmpty() || missingCreds.isNotEmpty()) {
                throw IllegalStateException(
                    "$who capabilities don't meet required_capabilities: " +
                        "missing extensions=$missingExts proposals=$missingProps credentials=$missingCreds",
                )
            }
        }

        /**
         * RFC 9420 §7.9.2 ParentHashInput TLS encoding:
         *
         * ```
         * struct {
         *     HPKEPublicKey encryption_key;
         *     opaque parent_hash<V>;
         *     opaque original_sibling_tree_hash<V>;
         * } ParentHashInput;
         * ```
         */
        internal fun encodeParentHashInput(
            encryptionKey: ByteArray,
            parentHash: ByteArray,
            originalSiblingTreeHash: ByteArray,
        ): ByteArray {
            val w = TlsWriter()
            w.putOpaqueVarInt(encryptionKey)
            w.putOpaqueVarInt(parentHash)
            w.putOpaqueVarInt(originalSiblingTreeHash)
            return w.toByteArray()
        }

        /**
         * RFC 9420 §7.9.2 "Verifying Parent Hashes", over the STATIC tree a
         * joiner reconstructs from a Welcome's GroupInfo.
         *
         * Without it a malicious or misconfigured GroupInfo signer could ship
         * a tree whose stored parent_hash values do not match its shape; peers
         * that DO validate would reject every commit produced from it, and the
         * joiner would not notice until an epoch silently rolled back.
         * Returns `null` on success, or a human-readable failure reason.
         *
         * The rule is per PARENT node, not per leaf: for each non-blank parent
         * P, EXACTLY ONE of its two subtrees must contain a node whose
         * `parent_hash` equals `ParentHash(P, other_subtree)`. That node is the
         * child the committer descended through when it set P; the other
         * subtree supplies the sibling hash.
         *
         * We used to re-derive every COMMIT-source leaf's `parent_hash`
         * top-down from the CURRENT tree and demand a match. That is a much
         * stronger claim than the RFC makes, and a false one: a later commit
         * refreshes ancestors and a later Add changes the tree's shape, so a
         * leaf set two epochs ago legitimately no longer re-derives. It
         * rejected every tree where the inviter was not the last committer —
         * in practice, every group invitation sent by anyone but the creator.
         *
         * Both sibling hashes and both resolutions exclude P's
         * `unmerged_leaves`: those are precisely the leaves added after P was
         * populated, so removing them reconstructs the tree as P's author saw
         * it.
         */
        internal fun verifyTreeParentHashesForJoin(tree: RatchetTree): String? {
            if (tree.leafCount == 0) return null
            for (parentIdx in tree.parentNodeIndices()) {
                val key = tree.parentEncryptionKeyOf(parentIdx) ?: continue
                val storedParentHash = tree.parentHashOf(parentIdx) ?: ByteArray(0)
                val excluded = tree.unmergedLeavesOf(parentIdx)
                val leftIdx = BinaryTree.left(parentIdx)
                val rightIdx = BinaryTree.right(parentIdx)

                fun hashWithSibling(siblingIdx: Int) =
                    MlsCryptoProvider.hash(
                        encodeParentHashInput(
                            encryptionKey = key,
                            parentHash = storedParentHash,
                            originalSiblingTreeHash = tree.originalTreeHash(siblingIdx, excluded),
                        ),
                    )

                val expectedInLeft = hashWithSibling(rightIdx)
                val expectedInRight = hashWithSibling(leftIdx)

                val foundLeft =
                    tree.resolutionExcluding(leftIdx, excluded).any {
                        tree.parentHashOf(it)?.contentEquals(expectedInLeft) == true
                    }
                val foundRight =
                    tree.resolutionExcluding(rightIdx, excluded).any {
                        tree.parentHashOf(it)?.contentEquals(expectedInRight) == true
                    }

                if (foundLeft == foundRight) {
                    return "parent node $parentIdx is not parent-hash valid " +
                        "(matched left=$foundLeft right=$foundRight)"
                }
            }
            return null
        }

        /**
         * Default MLS leaf Capabilities that advertise support for Marmot's
         * required extensions and proposals so new members can join a group
         * whose `required_capabilities` lists them.
         */
        private fun marmotLeafCapabilities(): Capabilities =
            Capabilities(
                extensions = listOf(MARMOT_GROUP_DATA_EXTENSION_TYPE),
                proposals = listOf(SELF_REMOVE_PROPOSAL_TYPE),
            )

        /**
         * Enforce the `0x8006` component's `required_member_roles` mask over
         * the joining tree.
         *
         * A group carrying the agent-text-stream component requires each named
         * role as an MLS leaf capability (`0xF2D1` receive, `0xF2D2` send,
         * `0xF2D4` fanout). Advertising the component id alone is not enough —
         * that only says "understands the component"; the role capability says
         * "can actually do this".
         */
        private fun requireAgentTextStreamRoles(
            extensions: List<Extension>,
            tree: RatchetTree,
            myLeafIndex: Int,
        ) {
            val policy =
                AppDataDictionary
                    .fromExtensionsOrEmpty(extensions)[AgentTextStreamQuicPolicyV1.COMPONENT_ID]
                    ?.let { AgentTextStreamQuicPolicyV1.decode(it) } ?: return
            val required = policy.requiredRoleCapabilities()
            if (required.isEmpty()) return

            val myLeaf = tree.getLeaf(myLeafIndex)
            requireNotNull(myLeaf) { "Joiner's leaf is blank after tree reconstruction" }
            val missing = required.filterNot { myLeaf.capabilities.extensions.contains(it) }
            require(missing.isEmpty()) {
                "Joiner does not advertise agent text stream roles this group requires: " +
                    missing.joinToString { AppComponentIds.toHex(it) }
            }
        }

        /**
         * Leaf capabilities for the current profile.
         *
         * RFC 9420 §7.2 forbids advertising DEFAULT extension types, so only
         * the draft `app_data_dictionary` extension and the `app_data_update`
         * proposal appear — `required_capabilities` support is implicit.
         *
         * The legacy `0xF2EE` group-data extension is advertised alongside
         * them, and that is not a hedge. A capability says "this client can
         * handle it", not "this group uses it", and a group that REQUIRES
         * `0xF2EE` refuses to add a leaf that does not advertise it. Without
         * this line a current-profile KeyPackage would be un-addable to every
         * legacy group that already exists — the exact mirror of the interop
         * failure the current profile was adopted to fix.
         *
         * `0xF2D1` is the agent-text-stream RECEIVE role, for the same reason:
         * a group carrying component `0x8006` with `required_member_roles`
         * naming `receive` refuses a leaf that does not advertise it. The
         * reference client puts exactly that policy into EVERY group it
         * creates, so without this line an Amethyst KeyPackage cannot be
         * invited into one at all.
         *
         * We stop at receive. `send` and `fanout` are not here because we do
         * not originate previews from the app, and a capability is a standing
         * promise rather than a hedge.
         */
        fun currentProfileLeafCapabilities(): Capabilities =
            Capabilities(
                extensions =
                    listOf(
                        AppDataDictionary.EXTENSION_TYPE,
                        MarmotGroupData.EXTENSION_ID_INT,
                        AgentTextStreamRoles.RECEIVE_CAPABILITY,
                    ),
                proposals = listOf(APP_DATA_UPDATE_PROPOSAL_TYPE, SELF_REMOVE_PROPOSAL_TYPE),
            )

        /**
         * `required_capabilities` for a new current-profile group: extension
         * `0x0006` and proposal `0x0008`.
         *
         * The Marmot components a group requires are negotiated in the
         * upstream `app_components` component INSIDE the dictionary, not here —
         * MLS `RequiredCapabilities` carries only MLS-level primitives.
         */
        fun buildCurrentProfileRequiredCapabilitiesExtension(): Extension {
            val writer = TlsWriter()
            val exts = TlsWriter()
            exts.putUint16(AppDataDictionary.EXTENSION_TYPE)
            writer.putOpaqueVarInt(exts.toByteArray())
            val props = TlsWriter()
            props.putUint16(APP_DATA_UPDATE_PROPOSAL_TYPE)
            writer.putOpaqueVarInt(props.toByteArray())
            val creds = TlsWriter()
            creds.putUint16(Credential.CREDENTIAL_TYPE_BASIC)
            writer.putOpaqueVarInt(creds.toByteArray())
            return Extension(REQUIRED_CAPABILITIES_EXTENSION_TYPE, writer.toByteArray())
        }

        /**
         * Create a new MLS group with a single member (the creator).
         */
        fun create(
            identity: ByteArray,
            signingKey: ByteArray? = null,
            initialExtensions: List<Extension> = emptyList(),
            /**
             * LeafNode extensions for the creator's own leaf. A current-profile
             * group MUST put its `app_data_dictionary` here, carrying the
             * account identity proof — the proof is leaf-only and can never be
             * added later by a proposal.
             */
            leafExtensions: List<Extension> = emptyList(),
            capabilities: Capabilities = marmotLeafCapabilities(),
            /**
             * The `required_capabilities` extension for epoch 0. Defaults to
             * the MIP-era set; a current-profile group passes
             * [buildCurrentProfileRequiredCapabilitiesExtension].
             */
            requiredCapabilities: Extension = buildMarmotRequiredCapabilitiesExtension(),
        ): MlsGroup {
            val sigKp =
                signingKey?.let { key ->
                    val pub = Ed25519.publicFromPrivate(key)
                    Ed25519KeyPair(key, pub)
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
                    capabilities = capabilities,
                    leafExtensions = leafExtensions,
                )

            val tree = RatchetTree(1)
            tree.setLeaf(0, leafNode)

            val treeHash = tree.treeHash()
            // Start with required_capabilities + whatever the caller wants to
            // bake into epoch 0 (e.g. the MIP-01 MarmotGroupData extension so
            // new peers who join later can see the group name without first
            // decrypting a pre-membership bootstrap commit — see MIP-03).
            val baseExtensions = listOf(requiredCapabilities)
            val groupContext =
                GroupContext(
                    groupId = groupId,
                    epoch = 0,
                    treeHash = treeHash,
                    confirmedTranscriptHash = ByteArray(0),
                    extensions = baseExtensions + initialExtensions,
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

            // HPKE-decrypt group secrets. Per RFC 9420 §12.4.3.1, the HPKE
            // context is the encrypted_group_info field of the Welcome.
            val gsBytes =
                MlsCryptoProvider.decryptWithLabel(
                    bundle.initPrivateKey,
                    "Welcome",
                    welcome.encryptedGroupInfo,
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

            // RFC 9420 §12.4.3.1: the ratchet_tree extension the joiner
            // reconstructs MUST hash to the tree_hash committed in the
            // signed GroupContext. Otherwise a compromised signer could
            // feed the joiner a tree different from the one encoded in
            // the signed context, silently diverging their key schedule.
            require(tree.treeHash().contentEquals(groupContext.treeHash)) {
                "GroupInfo tree_hash does not match ratchet_tree extension"
            }

            // RFC 9420 §7.9 parent_hash integrity. The tree_hash check
            // above only proves the wire bytes match what the GroupInfo
            // signer signed — it does NOT validate that the stored
            // parent_hash values are consistent with the tree shape.
            // Without this, every COMMIT-source leaf could carry a
            // forged parent_hash and the group would accept it; every
            // commit produced from this tree would then be rejected by
            // peers that DO validate, splitting on the next epoch.
            verifyTreeParentHashesForJoin(tree)?.let { reason ->
                throw IllegalStateException("GroupInfo ratchet_tree fails parent_hash validation: $reason")
            }

            // RFC 9420 §7.2 + §12.4.3.1: a joiner MUST refuse to join a
            // group whose `required_capabilities` lists types the joiner's
            // own KeyPackage doesn't advertise — peers that DO enforce the
            // requirement would reject every commit the joiner produces
            // anyway. Catching this at join time turns a silent "your
            // commits get dropped forever" into an actionable error.
            findRequiredCapabilities(groupContext.extensions)?.let { req ->
                val myLeaf = tree.getLeaf(myLeafIndex)
                requireNotNull(myLeaf) { "Joiner's leaf is blank after tree reconstruction" }
                requireCapabilitiesMeetRequirements(myLeaf.capabilities, req, "Joiner KeyPackage")
                // Mirror the same check across every existing member —
                // if a peer's leaf doesn't meet the group's stated
                // requirements, the GroupInfo signer mis-installed the
                // requirement set and the group is incoherent.
                for (i in 0 until tree.leafCount) {
                    val leaf = tree.getLeaf(i) ?: continue
                    requireCapabilitiesMeetRequirements(leaf.capabilities, req, "Member leaf $i")
                }
            }

            // The agent-text-stream component (0x8006) states its own
            // per-member requirement OUTSIDE MLS `required_capabilities`:
            // `required_member_roles` names role capabilities every member
            // must advertise. MLS cannot enforce it, so a joiner that skipped
            // this check would join a group it can never satisfy and have
            // every one of its commits refused by peers that do check.
            requireAgentTextStreamRoles(groupContext.extensions, tree, myLeafIndex)

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

            // RFC 9420 §12.4.3.1: the confirmation_tag the joiner would
            // derive from the joiner_secret-sourced confirmation_key MUST
            // match the confirmation_tag embedded in the signed GroupInfo.
            // Without this check a tampered GroupInfo could supply a
            // mismatched confirmed_transcript_hash that still passes the
            // signature-over-(context, extensions, tag, signer) as long
            // as the attacker controls the signer — the confirmation_tag
            // binds the epoch secrets to the transcript.
            require(constantTimeEquals(groupInfo.confirmationTag, confirmationTag)) {
                "GroupInfo confirmation_tag does not match joiner-derived confirmation_key"
            }

            val interimInput = TlsWriter()
            interimInput.putBytes(groupContext.confirmedTranscriptHash)
            interimInput.putOpaqueVarInt(confirmationTag)
            val interimTranscriptHash = MlsCryptoProvider.hash(interimInput.toByteArray())

            // RFC 9420 §12.4.3.1: when the Commit that added us carried an
            // UpdatePath, GroupSecrets carries the path secret at the lowest
            // common ancestor of our leaf and the committer's. Deriving our
            // direct-path keys from it is not optional bookkeeping — our
            // subtree is already MERGED in the tree this Welcome hands us, so
            // the very next commit addresses us at an ancestor, and a joiner
            // that dropped this secret cannot decrypt a single one of them.
            val joined =
                MlsGroup(
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
            groupSecrets.pathSecret?.let { pathSecret ->
                val ancestorIdx = joined.directPathIndexOfAncestorWith(groupInfo.signer)
                val fullPath = BinaryTree.directPath(myLeafIndex, tree.leafCount)
                fullPath.getOrNull(ancestorIdx)?.let { joined.rememberPathKeys(it, pathSecret) }
            }
            return joined
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
         * @param capabilities the joiner leaf's capabilities. A current-profile
         *   join MUST pass [currentProfileLeafCapabilities]; the default only
         *   satisfies a legacy group's `required_capabilities`.
         * @param leafExtensions the joiner leaf's extensions. A current-profile
         *   join MUST pass the `app_data_dictionary` carrying its account
         *   identity proof — leaf extensions cannot be added after the fact.
         * @return the new MlsGroup along with the raw inner commit bytes and a
         *   wire-ready PublicMessage envelope. Existing group members consume
         *   the framed bytes via [MlsGroup.processFramedCommit].
         */
        fun externalJoin(
            groupInfoBytes: ByteArray,
            identity: ByteArray,
            signingKey: ByteArray? = null,
            capabilities: Capabilities = marmotLeafCapabilities(),
            leafExtensions: List<Extension> = emptyList(),
        ): ExternalJoinResult {
            val groupInfo = GroupInfo.decodeTls(TlsReader(groupInfoBytes))
            val groupContext = groupInfo.groupContext

            // Extract ratchet tree from extensions
            val ratchetTreeExt =
                groupInfo.extensions.find { it.extensionType == RATCHET_TREE_EXTENSION_TYPE }
                    ?: throw IllegalArgumentException("GroupInfo missing ratchet_tree extension")
            val tree = RatchetTree.decodeTls(TlsReader(ratchetTreeExt.extensionData))

            // Verify GroupInfo signature (RFC 9420 Section 12.4.3.1)
            val signerLeaf = tree.getLeaf(groupInfo.signer)
            requireNotNull(signerLeaf) {
                "Signer leaf is null at index ${groupInfo.signer} — cannot verify GroupInfo signature"
            }
            require(groupInfo.verifySignature(signerLeaf.signatureKey)) {
                "Invalid GroupInfo signature in externalJoin"
            }

            // RFC 9420 §12.4.3.1: enforce that the reconstructed
            // ratchet_tree hashes to the signed tree_hash. Without this,
            // a malicious signer could serve an externalJoin consumer
            // a tree that diverges from the signed context.
            require(tree.treeHash().contentEquals(groupContext.treeHash)) {
                "externalJoin tree_hash does not match ratchet_tree extension"
            }

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

            // Placeholder leaf so we can claim our slot and compute the
            // direct path for sibling-hash capture. The final leaf (with
            // parent_hash filled in + fresh signature) replaces this below.
            val placeholderLeaf =
                buildLeafNode(
                    encryptionKey = encKp.publicKey,
                    signatureKey = sigKp.publicKey,
                    identity = identity,
                    source = LeafNodeSource.COMMIT,
                    signingKey = sigKp.privateKey,
                    groupId = groupContext.groupId,
                    leafIndex = tree.leafCount,
                    capabilities = capabilities,
                    leafExtensions = leafExtensions,
                )
            val myLeafIndex = tree.addLeaf(placeholderLeaf)

            // Capture sibling tree hashes BEFORE applying the UpdatePath.
            val preUpdateSiblingHashes =
                run {
                    val dp = BinaryTree.directPath(myLeafIndex, tree.leafCount)
                    val nc = BinaryTree.nodeCount(tree.leafCount)
                    val map = mutableMapOf<Int, ByteArray>()
                    for ((i, _) in dp.withIndex()) {
                        val childIdx =
                            if (i == 0) BinaryTree.leafToNode(myLeafIndex) else dp[i - 1]
                        map[i] = tree.treeHashNode(BinaryTree.sibling(childIdx, nc))
                    }
                    map
                }

            // Build UpdatePath for our leaf using the same staging pattern
            // as regular commit() (RFC 9420 §7.6): stage public keys → apply →
            // patch parent_hash → rebuild leaf → compute post-mutation context
            // → HPKE-encrypt with that context. Encrypting against the
            // PRE-mutation groupContext (the bug we're fixing here) makes
            // existing members fail to open the path-secret AEAD because
            // their decryption context bumps the epoch and folds in the new
            // tree_hash.
            val leafSecret = MlsCryptoProvider.randomBytes(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            val pathSecrets = tree.derivePathSecrets(myLeafIndex, leafSecret)
            val copath = BinaryTree.copath(myLeafIndex, tree.leafCount)

            // Stage path-keys WITHOUT encrypted secrets so subsequent
            // parent_hash / tree_hash computations reflect the new keys.
            val stagedPathNodes =
                pathSecrets.map { pathKey -> UpdatePathNode(pathKey.publicKey, emptyList()) }
            tree.applyUpdatePath(myLeafIndex, stagedPathNodes)

            // Compute parent_hash chain + the leaf's parent_hash (RFC 9420
            // §7.9.2) on the post-update tree, then patch parent nodes and
            // rebuild the committer leaf with the computed parent_hash.
            val (extParentHashes, extLeafParentHash) =
                computeExternalSenderParentHashes(tree, myLeafIndex, preUpdateSiblingHashes)
            for (nodeIdx in BinaryTree.directPath(myLeafIndex, tree.leafCount)) {
                val existing = tree.getNode(nodeIdx)
                if (existing is com.vitorpamplona.quartz.marmot.mls.tree.TreeNode.Parent) {
                    tree.setParent(
                        nodeIdx,
                        existing.parentNode.copy(
                            parentHash = extParentHashes[nodeIdx] ?: ByteArray(0),
                        ),
                    )
                }
            }

            val leafNode =
                buildLeafNode(
                    encryptionKey = encKp.publicKey,
                    signatureKey = sigKp.publicKey,
                    identity = identity,
                    source = LeafNodeSource.COMMIT,
                    signingKey = sigKp.privateKey,
                    groupId = groupContext.groupId,
                    leafIndex = myLeafIndex,
                    parentHash = extLeafParentHash,
                    capabilities = capabilities,
                    leafExtensions = leafExtensions,
                )
            tree.setLeaf(myLeafIndex, leafNode)

            // Post-mutation path-encryption context: bump epoch, swap in the
            // post-update tree_hash. Matches what existing members compute
            // in processCommitInner when they HPKE-Open the path secret.
            val pathEncContextBytes =
                groupContext
                    .copy(
                        epoch = groupContext.epoch + 1,
                        treeHash = tree.treeHash(),
                    ).toTlsBytes()

            val pathNodes =
                stagedPathNodes.zip(copath).zip(pathSecrets) { (staged, copathNode), pathKey ->
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
                                pathEncContextBytes,
                                pathKey.pathSecret,
                            )
                        }
                    UpdatePathNode(staged.encryptionKey, encryptedSecrets)
                }

            val updatePath = UpdatePath(leafNode, pathNodes)

            // Build Commit
            val commit =
                Commit(
                    proposals = listOf(ProposalOrRef.Inline(externalInitProposal)),
                    updatePath = updatePath,
                )
            val commitBytes = commit.toTlsBytes()

            // Derive epoch secrets using external init_secret.
            // commit_secret = DeriveSecret(root_path_secret, "path") (RFC 9420 §9.2).
            val commitSecret =
                if (pathSecrets.isNotEmpty()) {
                    MlsCryptoProvider.deriveSecret(pathSecrets.last().pathSecret, "path")
                } else {
                    ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
                }

            // Update transcript hashes for external join commit
            val confirmedTranscriptHashInput =
                buildConfirmedTranscriptHashInput(commit, myLeafIndex, groupContext.groupId, groupContext.epoch)
            val confirmedInput = TlsWriter()
            confirmedInput.putBytes(ByteArray(0)) // initial interim transcript hash
            confirmedInput.putBytes(confirmedTranscriptHashInput)
            val newConfirmedTranscriptHash = MlsCryptoProvider.hash(confirmedInput.toByteArray())

            val newTreeHash = tree.treeHash()
            val newEpoch = groupContext.epoch + 1
            val newGroupContext =
                groupContext.copy(
                    epoch = newEpoch,
                    treeHash = newTreeHash,
                    confirmedTranscriptHash = newConfirmedTranscriptHash,
                )

            val keySchedule = KeySchedule(newGroupContext.toTlsBytes())
            val epochSecrets = keySchedule.deriveEpochSecrets(commitSecret, externalInitSecret)

            val secretTree = SecretTree(epochSecrets.encryptionSecret, tree.leafCount)

            // Compute interim transcript hash
            val confirmationTag = computeConfirmationTag(epochSecrets.confirmationKey, newConfirmedTranscriptHash)
            val interimInput = TlsWriter()
            interimInput.putBytes(newConfirmedTranscriptHash)
            interimInput.putOpaqueVarInt(confirmationTag)
            val interimTranscriptHash = MlsCryptoProvider.hash(interimInput.toByteArray())

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
                    interimTranscriptHash = interimTranscriptHash,
                )

            // Wrap the commit in a PublicMessage envelope so existing members
            // can extract sender / signature / confirmation_tag via
            // MlsGroup.processFramedCommit (the symmetric receive path).
            // External commits (RFC 9420 §12.4.3) carry sender =
            // new_member_commit and no membership_tag — the joiner is
            // claiming a slot that doesn't yet exist in the group's
            // membership_key MAC space.
            val publicMessage =
                PublicMessage(
                    groupId = groupContext.groupId,
                    epoch = groupContext.epoch,
                    sender = Sender(SenderType.NEW_MEMBER_COMMIT, 0),
                    authenticatedData = ByteArray(0),
                    contentType = ContentType.COMMIT,
                    content = commitBytes,
                    signature = ByteArray(0),
                    confirmationTag = confirmationTag,
                    membershipTag = null,
                )
            val framedCommitBytes = MlsMessage.fromPublicMessage(publicMessage).toTlsBytes()

            return ExternalJoinResult(
                group = group,
                commitBytes = commitBytes,
                framedCommitBytes = framedCommitBytes,
            )
        }

        /**
         * Restore a group from a previously saved [MlsGroupState].
         *
         * The SecretTree is reconstructed from the stored encryption_secret,
         * then seeded with the persisted per-sender ratchet positions
         * ([MlsGroupState.senderRatchetStates]). Seeding is what keeps the
         * local member's generation counter monotonic across a restart — a
         * fresh SecretTree would restart every sender at generation 0, so our
         * next send would reuse generation 0's AEAD key+nonce within the same
         * epoch and be rejected by strict receivers (openmls / MDK /
         * Whitenoise) that forbid generation reuse.
         *
         * Receive-only ratchets that weren't persisted (STATE_VERSION 1 blobs,
         * or senders we never decrypted) simply re-derive from generation 0 on
         * first use — safe, because those messages were already processed.
         */
        fun restore(state: MlsGroupState): MlsGroup {
            val tree = RatchetTree.decodeTls(TlsReader(state.treeBytes))
            val secretTree = SecretTree(state.encryptionSecret, tree.leafCount)
            secretTree.importSenderStates(state.senderRatchetStates)

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
                pathPrivateKeys = state.pathPrivateKeys.toMutableMap(),
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
            parentHash: ByteArray? = null,
            capabilities: Capabilities = marmotLeafCapabilities(),
            leafExtensions: List<Extension> = emptyList(),
        ): LeafNode {
            val unsigned =
                LeafNode(
                    encryptionKey = encryptionKey,
                    signatureKey = signatureKey,
                    credential = Credential.Basic(identity),
                    // Advertise the profile's required capabilities so we can be
                    // added to compliant groups that mark them as required.
                    capabilities = capabilities,
                    leafNodeSource = source,
                    lifetime =
                        if (source == LeafNodeSource.KEY_PACKAGE) {
                            // A real, bounded window. `Lifetime(0, Long.MAX_VALUE)`
                            // used to go here, which any receiver enforcing
                            // `foundation/key-packages.md` rejects outright: the
                            // extension must be current AND span at most
                            // 7,261,200s (84 days + an hour of clock skew).
                            // The one-hour backdate gives a peer with a slow
                            // clock a window in which the package is already
                            // valid.
                            val notBefore = TimeUtils.now() - LIFETIME_SKEW_SECONDS
                            Lifetime(notBefore, notBefore + LIFETIME_SPAN_SECONDS)
                        } else {
                            null
                        },
                    parentHash = parentHash,
                    extensions = leafExtensions,
                    signature = ByteArray(0), // Placeholder
                )

            val tbs = unsigned.encodeTbs(groupId, leafIndex)
            val signature = MlsCryptoProvider.signWithLabel(signingKey, "LeafNodeTBS", tbs)

            return unsigned.copy(signature = signature)
        }
    }

    /**
     * Add a member to the group by their KeyPackage.
     * Creates and applies a Commit with an Add proposal. The resulting
     * [CommitResult.preCommitExporterSecret] is the key the outer kind:445
     * MUST be encrypted with (RFC 9420 §12.4 + MDK parity).
     */
    fun addMember(keyPackageBytes: ByteArray): CommitResult {
        proposeAdd(keyPackageBytes)
        return commit()
    }

    /**
     * Remove a member from the group.
     * Creates and applies a Commit with a Remove proposal. See [addMember]
     * for the pre-commit exporter key contract on the returned [CommitResult].
     */
    fun removeMember(targetLeafIndex: Int): CommitResult {
        proposeRemove(targetLeafIndex)
        return commit()
    }

    /**
     * Build a standalone SelfRemove proposal framed as a PublicMessage MLS
     * message (RFC 9420 §6.2 + draft-ietf-mls-extensions).
     *
     * openmls/mdk explicitly treat SelfRemove as a STANDALONE proposal
     * message, not a commit body: a non-admin committer can't self-remove
     * (openmls returns `RequiredPathNotFound`/`AttemptedSelfRemoval`) so
     * quartz must publish it as a plain PROPOSAL instead. Admin receivers
     * (wn's mdk auto-commit path) pick up the staged proposal and fold it
     * into their next commit, which is what actually removes the sender.
     *
     * The bytes returned are the full `MlsMessage(PublicMessage(proposal))`
     * ready for outer ChaCha20 wrapping as kind:445 content. The second
     * return value is the epoch this message must be outer-encrypted under.
     */
    fun buildSelfRemoveProposalMessage(): Pair<ByteArray, ByteArray> {
        check(!isLocalAdmin()) {
            "Admin must self-demote via GroupContextExtensions before SelfRemove (MIP-01)"
        }

        val preCommitExporterSecret =
            exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        val proposal = Proposal.SelfRemove()
        val proposalBytes = proposal.toTlsBytes()
        val ctx = groupContext
        val ctxBytes = ctx.toTlsBytes()
        val preCommitMembershipKey = epochSecrets.membershipKey
        val preCommitSigningKey = signingPrivateKey

        // FramedContentTBS for a member-sender PROPOSAL over PublicMessage:
        // version || wire_format || FramedContent || serialized_context
        val tbsWriter = TlsWriter()
        tbsWriter.putUint16(MlsMessage.MLS_VERSION_10)
        tbsWriter.putUint16(WireFormat.PUBLIC_MESSAGE.value)
        tbsWriter.putOpaqueVarInt(ctx.groupId)
        tbsWriter.putUint64(ctx.epoch)
        encodeSender(tbsWriter, Sender(SenderType.MEMBER, myLeafIndex))
        tbsWriter.putOpaqueVarInt(ByteArray(0)) // authenticated_data
        tbsWriter.putUint8(ContentType.PROPOSAL.value)
        tbsWriter.putBytes(proposalBytes) // proposal struct, no outer length prefix
        tbsWriter.putBytes(ctxBytes) // member sender appends context
        val tbs = tbsWriter.toByteArray()

        val signature = MlsCryptoProvider.signWithLabel(preCommitSigningKey, "FramedContentTBS", tbs)

        // TBM = TBS || FramedContentAuthData. For PROPOSAL there's no
        // confirmation_tag, just the signature.
        val tbmWriter = TlsWriter()
        tbmWriter.putBytes(tbs)
        tbmWriter.putOpaqueVarInt(signature)
        val tbm = tbmWriter.toByteArray()

        val macInstance = MacInstance("HmacSHA256", preCommitMembershipKey)
        macInstance.update(tbm)
        val membershipTag = macInstance.doFinal()

        val publicMessage =
            PublicMessage(
                groupId = ctx.groupId,
                epoch = ctx.epoch,
                sender = Sender(SenderType.MEMBER, myLeafIndex),
                authenticatedData = ByteArray(0),
                contentType = ContentType.PROPOSAL,
                content = proposalBytes,
                signature = signature,
                confirmationTag = null,
                membershipTag = membershipTag,
            )

        // Stage the proposal in our own pending pool with the encoded
        // AuthenticatedContent (RFC 9420 §6.1: wire_format ‖ FramedContent ‖
        // FramedContentAuthData) so a subsequent inbound commit that folds
        // this SelfRemove in by ProposalRef can resolve the hash per §5.2.
        // Today's `leaveGroup` caller drops the group state immediately
        // after this returns and never sees that commit, but a future
        // caller that keeps the group around (to confirm the removal,
        // log the closing epoch, etc.) needs the entry here. The AC
        // bytes are bit-identical to what a peer reconstructs in
        // [receivePublicMessageProposal].
        val acWriter = TlsWriter()
        acWriter.putUint16(WireFormat.PUBLIC_MESSAGE.value)
        acWriter.putOpaqueVarInt(ctx.groupId)
        acWriter.putUint64(ctx.epoch)
        encodeSender(acWriter, Sender(SenderType.MEMBER, myLeafIndex))
        acWriter.putOpaqueVarInt(ByteArray(0)) // authenticated_data
        acWriter.putUint8(ContentType.PROPOSAL.value)
        acWriter.putBytes(proposalBytes)
        acWriter.putOpaqueVarInt(signature) // FramedContentAuthData (PROPOSAL: signature only)
        pendingProposals.add(
            PendingProposal(
                proposal = proposal,
                senderLeafIndex = myLeafIndex,
                authenticatedContentBytes = acWriter.toByteArray(),
            ),
        )

        return MlsMessage.fromPublicMessage(publicMessage).toTlsBytes() to preCommitExporterSecret
    }

    /**
     * Receive a standalone-proposal PublicMessage and stage it locally so a
     * subsequent commit that references it (via `ProposalRef`) can resolve.
     *
     * Sent today by `wn`/openmls when a non-admin member self-removes — the
     * member can't commit themselves out (admins reject `RequiredPathNotFound`),
     * so they publish a `SelfRemove` proposal as a `PublicMessage` and wait
     * for an admin to fold it into the next commit. Without this receiver
     * side every other member silently drops the proposal, and the admin's
     * subsequent commit then fails with "Commit references unknown proposal
     * (ref not found in pending proposals)" — which is exactly the failure
     * mode of marmot-interop test 15.
     *
     * Validation mirrors what every member-sender PublicMessage commit goes
     * through: epoch + group_id match the current epoch, FramedContentTBS
     * signature verifies against the sender's leaf signing key, and the
     * membership_tag verifies against the current epoch's membership key.
     * Anything missing or mismatched is a hard reject — the same threat
     * model as PublicMessage commit reception.
     */
    fun receivePublicMessageProposal(pubMsg: PublicMessage) {
        require(pubMsg.contentType == ContentType.PROPOSAL) {
            "Expected PublicMessage with content_type == PROPOSAL, got ${pubMsg.contentType}"
        }
        require(pubMsg.epoch == groupContext.epoch) {
            "Proposal epoch ${pubMsg.epoch} doesn't match current epoch ${groupContext.epoch}"
        }
        require(pubMsg.groupId.contentEquals(groupContext.groupId)) {
            "Proposal group_id doesn't match current group"
        }
        require(pubMsg.sender.senderType == SenderType.MEMBER) {
            "Standalone proposals from non-members are not accepted"
        }
        val senderLeafIndex = pubMsg.sender.leafIndex
        require(senderLeafIndex in 0 until tree.leafCount) {
            "Sender leaf index $senderLeafIndex out of range"
        }
        val senderLeaf =
            requireNotNull(tree.getLeaf(senderLeafIndex)) {
                "Sender leaf is blank at index $senderLeafIndex"
            }

        // Reconstruct FramedContentTBS exactly as the sender did in
        // `buildSelfRemoveProposalMessage` so the signature and the
        // membership_tag both verify against bit-identical bytes.
        val tbsWriter = TlsWriter()
        tbsWriter.putUint16(MlsMessage.MLS_VERSION_10)
        tbsWriter.putUint16(WireFormat.PUBLIC_MESSAGE.value)
        tbsWriter.putOpaqueVarInt(pubMsg.groupId)
        tbsWriter.putUint64(pubMsg.epoch)
        encodeSender(tbsWriter, pubMsg.sender)
        tbsWriter.putOpaqueVarInt(pubMsg.authenticatedData)
        tbsWriter.putUint8(ContentType.PROPOSAL.value)
        tbsWriter.putBytes(pubMsg.content) // proposal struct, no length prefix
        tbsWriter.putBytes(groupContext.toTlsBytes())
        val tbs = tbsWriter.toByteArray()

        require(MlsCryptoProvider.verifyWithLabel(senderLeaf.signatureKey, "FramedContentTBS", tbs, pubMsg.signature)) {
            "Invalid FramedContentTBS signature on PublicMessage proposal from leaf $senderLeafIndex"
        }

        val membershipTag =
            requireNotNull(pubMsg.membershipTag) {
                "PublicMessage proposal from leaf $senderLeafIndex is missing membership_tag"
            }
        val tbmWriter = TlsWriter()
        tbmWriter.putBytes(tbs)
        tbmWriter.putOpaqueVarInt(pubMsg.signature)
        require(verifyMembershipTag(tbmWriter.toByteArray(), membershipTag)) {
            "Invalid membership_tag on PublicMessage proposal from leaf $senderLeafIndex"
        }

        val proposal = Proposal.decodeTls(TlsReader(pubMsg.content))
        // SelfRemove is the only proposal type wn currently emits as a
        // standalone PublicMessage. Other types come bundled inside a
        // commit's `proposals` list. Defending against e.g. a standalone
        // Add/Remove here would also work — they'd land in pendingProposals
        // and be picked up by the next commit that references them — but we
        // refuse anything other than SelfRemove for now to keep the receive
        // side minimal until there's an interop reason to widen it.
        require(proposal is Proposal.SelfRemove) {
            "Only standalone SelfRemove proposals are accepted; got ${proposal::class.simpleName}"
        }

        // Capture the AuthenticatedContent bytes (RFC 9420 §6.1) so a
        // subsequent commit's `ProposalRef` lookup can hash them per §5.2:
        //
        //   AuthenticatedContent = wire_format || FramedContent || FramedContentAuthData
        //
        // FramedContentAuthData for a PROPOSAL is just `signature` (no
        // confirmation_tag). The TBS prefix (version + GroupContext) is
        // NOT part of the AuthenticatedContent — those go into the
        // signature input only.
        val acWriter = TlsWriter()
        acWriter.putUint16(WireFormat.PUBLIC_MESSAGE.value)
        // FramedContent
        acWriter.putOpaqueVarInt(pubMsg.groupId)
        acWriter.putUint64(pubMsg.epoch)
        encodeSender(acWriter, pubMsg.sender)
        acWriter.putOpaqueVarInt(pubMsg.authenticatedData)
        acWriter.putUint8(ContentType.PROPOSAL.value)
        acWriter.putBytes(pubMsg.content)
        // FramedContentAuthData (PROPOSAL: signature only)
        acWriter.putOpaqueVarInt(pubMsg.signature)
        val authenticatedContentBytes = acWriter.toByteArray()

        pendingProposals.add(
            PendingProposal(
                proposal = proposal,
                senderLeafIndex = senderLeafIndex,
                authenticatedContentBytes = authenticatedContentBytes,
            ),
        )
    }
}

data class PendingProposal(
    val proposal: Proposal,
    val senderLeafIndex: Int,
    /**
     * Encoded `AuthenticatedContent` bytes for the message that delivered
     * this proposal — the input to `MakeProposalRef` per RFC 9420 §5.2.
     * Null when the proposal was created locally and never went through
     * the MLS framing layer; in that case the lookup falls back to the
     * bare `proposal.toTlsBytes()` since local commits inline rather than
     * reference our own pending proposals.
     */
    val authenticatedContentBytes: ByteArray? = null,
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
