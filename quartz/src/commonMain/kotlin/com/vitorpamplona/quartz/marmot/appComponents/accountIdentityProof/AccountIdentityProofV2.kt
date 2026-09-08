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
package com.vitorpamplona.quartz.marmot.appComponents.accountIdentityProof

import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.foundation.authorizationProofs.MarmotAuthorizationProof
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `marmot.member.account-identity-proof.v2`, app component `0x8009`
 * (spec `app-components/account-identity-proof-v2.md`).
 *
 * ## What it proves, and why it exists
 *
 * A Marmot member leaf carries two unrelated keys: the MLS `BasicCredential`
 * identity, which is the member's raw 32-byte Nostr account key, and the MLS
 * leaf signature key, which is an Ed25519 key MLS generates per device. MLS
 * itself never checks that the account named by the credential agreed to the
 * leaf key sitting next to it — so without this component, anyone able to
 * author a leaf could claim any account's identity. That is what the proof
 * closes: the account key signs a statement naming this exact leaf signature
 * key, under this exact ciphersuite.
 *
 * ## Where it lives
 *
 * In the `app_data_dictionary` of a **LeafNode** — including the LeafNode
 * embedded in a KeyPackage. It is invalid in a GroupContext, in a
 * KeyPackage-level dictionary, in a GroupInfo, in an `AppEphemeral` proposal,
 * or in a SafeAAD item. A GroupContext must *require* `0x8009` in its
 * `app_components` list, but must never carry proof data itself.
 *
 * Consequently the component never changes through `AppDataUpdate`: it is
 * created with a new or replacement LeafNode, and disappears only when that
 * leaf is removed from the tree.
 *
 * ## Relationship to v1
 *
 * This is a clean break from `marmot.account-identity-proof.v1`, the custom
 * MLS extension type `0xf2f1`. There is no fallback and no in-place migration:
 * a v2 client rejects a v1-only leaf, and a group that requires `0xf2f1` but
 * not `0x8009` is a legacy group outside this profile. MDK classifies groups
 * on exactly that distinction, and a group requiring neither is rejected
 * outright.
 *
 * ## Freshness
 *
 * There is none, deliberately. [MarmotAuthorizationProof.createdAt] is signed
 * but never compared against a receiver's clock: the proof authorizes a
 * long-lived key binding, not a one-time operation, and a clock-based rule
 * would let skew make two members disagree about the same Commit. A proof may
 * be reused across KeyPackages and leaves for as long as every signed input
 * stays byte-identical; a new leaf signature key, ciphersuite, signature
 * scheme, or account identity requires a new proof.
 */
object AccountIdentityProofV2 {
    const val COMPONENT_ID = AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2
    const val COMPONENT_NAME = "marmot.member.account-identity-proof.v2"

    /** Local signing template only — clients MUST NOT publish this kind to relays. */
    const val KIND = 450

    /**
     * The fixed proof-domain label in the `d` tag. Note it is
     * `marmot.account-identity-proof.v2`, NOT [COMPONENT_NAME]: the spec pins
     * the `d` values of proof events as opaque domain labels precisely so they
     * are never derived from a component name.
     */
    const val D_TAG_VALUE = "marmot.account-identity-proof.v2"

    const val CONTENT = "Authorize this MLS leaf key for my Marmot account"

    /**
     * The exact ordered tag array of the proof event.
     *
     * Every element is signed, so tag order, name, arity and value all have to
     * match on both sides or the reconstructed event id differs and
     * verification fails. There are no other tags.
     */
    fun tags(
        ciphersuite: MlsCiphersuite,
        mlsSignatureKey: ByteArray,
    ): Array<Array<String>> =
        arrayOf(
            arrayOf("d", D_TAG_VALUE),
            arrayOf("component", AppComponentIds.toHex(COMPONENT_ID)),
            arrayOf("ciphersuite", ciphersuite.code),
            arrayOf("signature_scheme", ciphersuite.signatureScheme),
            arrayOf("mls_signature_key", mlsSignatureKey.toHexKey()),
        )

    /**
     * The unsigned kind-450 event an account signer is asked to sign.
     *
     * [mlsSignatureKey] is the LeafNode `signature_key` opaque vector's
     * contents WITHOUT its TLS length prefix.
     */
    fun signingTemplate(
        ciphersuite: MlsCiphersuite,
        mlsSignatureKey: ByteArray,
        createdAt: Long = TimeUtils.now(),
    ): EventTemplate<Event> =
        EventTemplate(
            createdAt = createdAt,
            kind = KIND,
            tags = tags(ciphersuite, mlsSignatureKey),
            content = CONTENT,
        )

    /**
     * Ask [signer] to authorize [mlsSignatureKey] for its own account.
     *
     * The signed event is validated before its signature is extracted, because
     * an external signer is free to return something other than what it was
     * asked to sign. Anything the signer substituted — a different pubkey,
     * timestamp, tag set, content, or a stale cached response — fails here
     * rather than becoming a proof that silently authorizes the wrong thing.
     */
    suspend fun create(
        signer: NostrSigner,
        ciphersuite: MlsCiphersuite,
        mlsSignatureKey: ByteArray,
        createdAt: Long = TimeUtils.now(),
    ): MarmotAuthorizationProof {
        require(createdAt in 1..MarmotAuthorizationProof.MAX_CREATED_AT) {
            "account identity proof created_at must be in 1..${MarmotAuthorizationProof.MAX_CREATED_AT}"
        }
        val template = signingTemplate(ciphersuite, mlsSignatureKey, createdAt)
        val signed: Event = signer.sign(template)

        require(signed.pubKey == signer.pubKey) {
            "signer returned an account identity proof event authored by a different account"
        }
        require(signed.createdAt == createdAt && signed.kind == KIND && signed.content == CONTENT) {
            "signer returned a different account identity proof event than requested"
        }
        require(tagsEqual(signed.tags, template.tags)) {
            "signer altered the account identity proof event tags"
        }
        require(
            EventHasher.hashIdCheck(
                signed.id,
                signed.pubKey,
                signed.createdAt,
                signed.kind,
                signed.tags,
                signed.content,
            ),
        ) {
            "signer returned an account identity proof event whose id does not match its fields"
        }

        val proof =
            MarmotAuthorizationProof(
                signerPubKey = signed.pubKey.hexToByteArray(),
                createdAt = signed.createdAt,
                signature = signed.sig.hexToByteArray(),
            )
        require(proof.verifySignatureOver(KIND, template.tags, CONTENT)) {
            "signer returned an account identity proof event with an invalid signature"
        }
        return proof
    }

    /**
     * Validate a proof taken from a LeafNode dictionary against the rest of
     * that leaf.
     *
     * [credentialIdentity] is the LeafNode `BasicCredential` identity;
     * [mlsSignatureKey] its signature key; [ciphersuite] the KeyPackage's
     * ciphersuite when validating a KeyPackage, or the group's when validating
     * a member leaf. Passing the wrong one is not a benign mismatch — it is
     * how a leaf gets bound to the context it is actually used in.
     */
    fun validate(
        componentData: ByteArray?,
        credentialIdentity: ByteArray,
        mlsSignatureKey: ByteArray,
        ciphersuite: MlsCiphersuite,
    ): Result {
        if (componentData == null) return Result.MISSING
        if (componentData.size != MarmotAuthorizationProof.SIZE) return Result.MALFORMED
        val proof = MarmotAuthorizationProof.decodeOrNull(componentData) ?: return Result.MALFORMED

        if (credentialIdentity.size != MarmotAuthorizationProof.PUBKEY_SIZE) {
            return Result.CREDENTIAL_IDENTITY_MISMATCH
        }
        if (!proof.signerPubKey.contentEquals(credentialIdentity)) {
            return Result.CREDENTIAL_IDENTITY_MISMATCH
        }
        if (!proof.verifySignatureOver(KIND, tags(ciphersuite, mlsSignatureKey), CONTENT)) {
            return Result.BAD_SIGNATURE
        }
        return Result.VALID
    }

    /** True only for [Result.VALID]; use [validate] when the reason matters. */
    fun isValid(
        componentData: ByteArray?,
        credentialIdentity: ByteArray,
        mlsSignatureKey: ByteArray,
        ciphersuite: MlsCiphersuite,
    ): Boolean = validate(componentData, credentialIdentity, mlsSignatureKey, ciphersuite) == Result.VALID

    /**
     * Why a leaf or KeyPackage was rejected.
     *
     * The spec lists a longer set of rejection conditions than this enum has
     * cases, because several of them are not decidable from the proof bytes
     * alone: an absent `0x8009` in the leaf's support list, more than one
     * `0x8009` dictionary entry, and the component appearing at an invalid
     * location are all properties of the surrounding `app_data_dictionary`.
     * Those belong to the dictionary layer and are checked there.
     *
     * [BAD_SIGNATURE] deliberately absorbs every mismatch in a signed input —
     * a wrong ciphersuite, a wrong signature scheme, or a signature over a
     * different leaf key all surface identically, because all three mean the
     * reconstructed event id was not what the account signed. There is nothing
     * to distinguish: the verifier has no way to know which input the signer
     * actually used.
     */
    enum class Result {
        VALID,

        /** No `0x8009` entry in the LeafNode dictionary. */
        MISSING,

        /** Present but not exactly one 104-byte [MarmotAuthorizationProof]. */
        MALFORMED,

        /** `signer_pubkey` is not the LeafNode's `BasicCredential` identity. */
        CREDENTIAL_IDENTITY_MISMATCH,

        /** The BIP-340 signature does not verify over the reconstructed event id. */
        BAD_SIGNATURE,
    }

    /** The proof event id, exposed for diagnostics and test vectors. */
    fun proofEventId(
        signerPubKey: HexKey,
        createdAt: Long,
        ciphersuite: MlsCiphersuite,
        mlsSignatureKey: ByteArray,
    ): ByteArray = EventHasher.hashIdBytes(signerPubKey, createdAt, KIND, tags(ciphersuite, mlsSignatureKey), CONTENT)

    private fun tagsEqual(
        a: Array<Array<String>>,
        b: Array<Array<String>>,
    ): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!a[i].contentEquals(b[i])) return false
        }
        return true
    }
}
