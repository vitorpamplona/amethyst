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
package com.vitorpamplona.quartz.marmot.mls.interop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- crypto-basics.json ---

@Serializable
data class CryptoBasicsVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("ref_hash") val refHash: RefHashVector,
    @SerialName("expand_with_label") val expandWithLabel: ExpandWithLabelVector,
    @SerialName("derive_secret") val deriveSecret: DeriveSecretVector,
    @SerialName("derive_tree_secret") val deriveTreeSecret: DeriveTreeSecretVector,
    @SerialName("sign_with_label") val signWithLabel: SignWithLabelVector,
    @SerialName("encrypt_with_label") val encryptWithLabel: EncryptWithLabelVector,
)

@Serializable
data class RefHashVector(
    val label: String,
    val value: String,
    val out: String,
)

@Serializable
data class ExpandWithLabelVector(
    val secret: String,
    val label: String,
    val context: String,
    val length: Int,
    val out: String,
)

@Serializable
data class DeriveSecretVector(
    val label: String,
    val secret: String,
    val out: String,
)

@Serializable
data class DeriveTreeSecretVector(
    val secret: String,
    val label: String,
    val generation: Long,
    val length: Int,
    val out: String,
)

@Serializable
data class SignWithLabelVector(
    val priv: String,
    val pub: String,
    val content: String,
    val label: String,
    val signature: String,
)

@Serializable
data class EncryptWithLabelVector(
    val priv: String,
    val pub: String,
    val label: String,
    val context: String,
    val plaintext: String,
    @SerialName("kem_output") val kemOutput: String,
    val ciphertext: String,
)

// --- tree-math.json ---

@Serializable
data class TreeMathVector(
    @SerialName("n_leaves") val nLeaves: Int,
    @SerialName("n_nodes") val nNodes: Int,
    val root: Int,
    val left: List<Int?>,
    val right: List<Int?>,
    val parent: List<Int?>,
    val sibling: List<Int?>,
)

// --- key-schedule.json ---

@Serializable
data class KeyScheduleVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("group_id") val groupId: String,
    @SerialName("initial_init_secret") val initialInitSecret: String,
    val epochs: List<KeyScheduleEpoch>,
)

@Serializable
data class KeyScheduleEpoch(
    @SerialName("group_context") val groupContext: String,
    @SerialName("commit_secret") val commitSecret: String,
    @SerialName("psk_secret") val pskSecret: String,
    @SerialName("joiner_secret") val joinerSecret: String,
    @SerialName("welcome_secret") val welcomeSecret: String,
    @SerialName("init_secret") val initSecret: String,
    @SerialName("sender_data_secret") val senderDataSecret: String,
    @SerialName("encryption_secret") val encryptionSecret: String,
    @SerialName("exporter_secret") val exporterSecret: String,
    @SerialName("epoch_authenticator") val epochAuthenticator: String,
    @SerialName("external_secret") val externalSecret: String,
    @SerialName("confirmation_key") val confirmationKey: String,
    @SerialName("membership_key") val membershipKey: String,
    @SerialName("resumption_psk") val resumptionPsk: String,
    @SerialName("external_pub") val externalPub: String,
    val exporter: ExporterVector,
    @SerialName("tree_hash") val treeHash: String,
    @SerialName("confirmed_transcript_hash") val confirmedTranscriptHash: String,
)

@Serializable
data class ExporterVector(
    val label: String,
    val context: String,
    val length: Int,
    val secret: String,
)

// --- secret-tree.json ---

@Serializable
data class SecretTreeVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("encryption_secret") val encryptionSecret: String,
    @SerialName("sender_data") val senderData: SenderDataVector,
    val leaves: List<List<LeafGenerationVector>>,
)

@Serializable
data class SenderDataVector(
    @SerialName("sender_data_secret") val senderDataSecret: String,
    val ciphertext: String,
    val key: String,
    val nonce: String,
)

@Serializable
data class LeafGenerationVector(
    val generation: Int,
    @SerialName("application_key") val applicationKey: String,
    @SerialName("application_nonce") val applicationNonce: String,
    @SerialName("handshake_key") val handshakeKey: String,
    @SerialName("handshake_nonce") val handshakeNonce: String,
)

// --- message-protection.json ---

@Serializable
data class MessageProtectionVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("group_id") val groupId: String,
    val epoch: Long,
    @SerialName("tree_hash") val treeHash: String,
    @SerialName("confirmed_transcript_hash") val confirmedTranscriptHash: String,
    @SerialName("signature_priv") val signaturePriv: String,
    @SerialName("signature_pub") val signaturePub: String,
    @SerialName("encryption_secret") val encryptionSecret: String,
    @SerialName("sender_data_secret") val senderDataSecret: String,
    @SerialName("membership_key") val membershipKey: String,
    val proposal: String,
    @SerialName("proposal_priv") val proposalPriv: String,
    @SerialName("proposal_pub") val proposalPub: String,
    val commit: String,
    @SerialName("commit_priv") val commitPriv: String,
    @SerialName("commit_pub") val commitPub: String,
    val application: String,
    @SerialName("application_priv") val applicationPriv: String,
)

// --- transcript-hashes.json ---

@Serializable
data class TranscriptHashVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("confirmation_key") val confirmationKey: String,
    @SerialName("authenticated_content") val authenticatedContent: String,
    @SerialName("interim_transcript_hash_before") val interimTranscriptHashBefore: String,
    @SerialName("confirmed_transcript_hash_after") val confirmedTranscriptHashAfter: String,
    @SerialName("interim_transcript_hash_after") val interimTranscriptHashAfter: String,
)

// --- messages.json ---

@Serializable
data class MessagesVector(
    @SerialName("mls_welcome") val mlsWelcome: String,
    @SerialName("mls_group_info") val mlsGroupInfo: String,
    @SerialName("mls_key_package") val mlsKeyPackage: String,
    @SerialName("ratchet_tree") val ratchetTree: String,
    @SerialName("group_secrets") val groupSecrets: String,
    @SerialName("add_proposal") val addProposal: String,
    @SerialName("update_proposal") val updateProposal: String,
    @SerialName("remove_proposal") val removeProposal: String,
    @SerialName("pre_shared_key_proposal") val preSharedKeyProposal: String,
    @SerialName("re_init_proposal") val reInitProposal: String,
    @SerialName("external_init_proposal") val externalInitProposal: String,
    @SerialName("group_context_extensions_proposal") val groupContextExtensionsProposal: String,
    val commit: String,
    @SerialName("public_message_application") val publicMessageApplication: String,
    @SerialName("public_message_proposal") val publicMessageProposal: String,
    @SerialName("public_message_commit") val publicMessageCommit: String,
    @SerialName("private_message") val privateMessage: String,
)

// --- tree-operations.json ---

@Serializable
data class TreeOperationsVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    val proposal: String,
    @SerialName("proposal_sender") val proposalSender: Int,
    @SerialName("tree_before") val treeBefore: String,
    @SerialName("tree_after") val treeAfter: String,
    @SerialName("tree_hash_before") val treeHashBefore: String,
    @SerialName("tree_hash_after") val treeHashAfter: String,
)

// --- tree-validation.json ---

@Serializable
data class TreeValidationVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    val tree: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("tree_hashes") val treeHashes: List<String>,
    val resolutions: List<List<Int>>,
)

// --- treekem.json ---

@Serializable
data class TreeKemVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("group_id") val groupId: String,
    val epoch: Long,
    @SerialName("confirmed_transcript_hash") val confirmedTranscriptHash: String,
    @SerialName("ratchet_tree") val ratchetTree: String,
    @SerialName("leaves_private") val leavesPrivate: List<TreeKemLeafPrivate>,
    @SerialName("update_paths") val updatePaths: List<TreeKemUpdatePath>,
)

@Serializable
data class TreeKemLeafPrivate(
    val index: Int,
    @SerialName("encryption_priv") val encryptionPriv: String,
    @SerialName("signature_priv") val signaturePriv: String,
    @SerialName("path_secrets") val pathSecrets: List<TreeKemPathSecret>,
)

@Serializable
data class TreeKemPathSecret(
    val node: Int,
    @SerialName("path_secret") val pathSecret: String,
)

@Serializable
data class TreeKemUpdatePath(
    val sender: Int,
    @SerialName("update_path") val updatePath: String,
    @SerialName("commit_secret") val commitSecret: String,
    @SerialName("tree_hash_after") val treeHashAfter: String,
    @SerialName("path_secrets") val pathSecrets: List<String?>,
)

// --- welcome.json ---

@Serializable
data class WelcomeVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("init_priv") val initPriv: String,
    @SerialName("signer_pub") val signerPub: String,
    @SerialName("key_package") val keyPackage: String,
    val welcome: String,
)

// --- passive-client-welcome.json / passive-client-handling-commit.json / passive-client-random.json ---

@Serializable
data class PassiveClientVector(
    @SerialName("cipher_suite") val cipherSuite: Int,
    @SerialName("external_psks") val externalPsks: List<PassiveClientPsk> = emptyList(),
    @SerialName("key_package") val keyPackage: String,
    @SerialName("signature_priv") val signaturePriv: String,
    @SerialName("encryption_priv") val encryptionPriv: String,
    @SerialName("init_priv") val initPriv: String,
    val welcome: String,
    @SerialName("ratchet_tree") val ratchetTree: String? = null,
    @SerialName("initial_epoch_authenticator") val initialEpochAuthenticator: String,
    val epochs: List<PassiveClientEpoch> = emptyList(),
)

@Serializable
data class PassiveClientPsk(
    @SerialName("psk_id") val pskId: String,
    val psk: String,
)

@Serializable
data class PassiveClientEpoch(
    val proposals: List<String> = emptyList(),
    val commit: String,
    @SerialName("epoch_authenticator") val epochAuthenticator: String,
)
