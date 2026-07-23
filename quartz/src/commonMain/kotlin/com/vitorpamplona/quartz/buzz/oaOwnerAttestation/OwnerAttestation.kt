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
package com.vitorpamplona.quartz.buzz.oaOwnerAttestation

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags.AuthTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * A Buzz Owner Attestation (NIP-OA): an owner key's signed authorization for an
 * *agent* key to publish events under the agent's own authorship.
 *
 * This is the primitive that lets Buzz treat agents as first-class members without
 * enrolling every agent key: the agent presents an owner-signed attestation, and the
 * relay grants "virtual membership" as long as the owner is an active member.
 *
 * The signature does **not** cover a Nostr event — it covers a standalone commitment
 * string binding the owner to a specific agent pubkey and a set of [conditions]:
 *
 * ```text
 * commitment = "nostr:agent-auth:" + agentPubKey + ":" + conditions
 * message    = SHA-256(commitment)                        // UTF-8 bytes
 * sig        = BIP-340 Schnorr(message, ownerPrivKey)
 * ```
 *
 * (Ground truth: `buzz-sdk/src/nip_oa.rs`.) Because the signed message is not an
 * event, only a signer that holds the raw owner private key can produce it — a NIP-46
 * remote bunker or NIP-55 external signer cannot, which is why [sign] takes a
 * [KeyPair]/private key rather than a `NostrSigner`.
 *
 * Serialize onto any Buzz event with the `auth` tag via [toTag] (see [AuthTag]).
 */
data class OwnerAttestation(
    val ownerPubKey: HexKey,
    val conditions: String,
    val sig: HexKey,
) {
    /** The exact commitment string that was hashed and signed, for a given agent key. */
    fun commitment(agentPubKey: HexKey): String = "$COMMITMENT_PREFIX$agentPubKey:$conditions"

    /**
     * Verifies this attestation authorizes [agentPubKey]. Checks structural validity
     * (valid hex keys, canonical conditions, no self-attestation) and then the owner's
     * Schnorr signature over the commitment hash.
     */
    fun verify(agentPubKey: HexKey): Boolean {
        if (!agentPubKey.isValid() || !ownerPubKey.isValid()) return false
        // Self-attestation is explicitly prohibited: the owner must differ from the agent.
        if (ownerPubKey == agentPubKey) return false
        if (!AttestationConditions.isValid(conditions)) return false
        if (sig.length != 128 || !Hex.isHex(sig)) return false

        val message = sha256(commitment(agentPubKey).encodeToByteArray())
        return Nip01Crypto.verify(sig.hexToByteArray(), message, ownerPubKey.hexToByteArray())
    }

    /** Builds the NIP-OA `auth` tag for this attestation. */
    fun toTag(): Array<String> = AuthTag.assemble(this)

    companion object {
        const val COMMITMENT_PREFIX = "nostr:agent-auth:"

        /** SHA-256 of the commitment string — the 32-byte message the owner signs. */
        fun commitmentHash(
            agentPubKey: HexKey,
            conditions: String,
        ): ByteArray = sha256("$COMMITMENT_PREFIX$agentPubKey:$conditions".encodeToByteArray())

        /**
         * Produces an owner attestation for [agentPubKey] under [conditions], signed
         * with the owner's raw private key. Throws when [conditions] is not canonical
         * or when the owner key equals the agent key (self-attestation).
         */
        fun sign(
            agentPubKey: HexKey,
            conditions: String,
            ownerPrivKey: ByteArray,
        ): OwnerAttestation {
            require(AttestationConditions.isValid(conditions)) { "Non-canonical attestation conditions: $conditions" }
            val ownerPubKey = Nip01Crypto.pubKeyCreate(ownerPrivKey).toHexKey()
            require(ownerPubKey != agentPubKey) { "Self-attestation is not allowed" }

            val sig = Nip01Crypto.sign(commitmentHash(agentPubKey, conditions), ownerPrivKey).toHexKey()
            return OwnerAttestation(ownerPubKey, conditions, sig)
        }

        /** Convenience overload signing with an owner [KeyPair]; requires a writable key. */
        fun sign(
            agentPubKey: HexKey,
            conditions: AttestationConditions,
            ownerKey: KeyPair,
        ): OwnerAttestation {
            val priv = requireNotNull(ownerKey.privKey) { "Owner key is read-only; cannot sign an attestation" }
            return sign(agentPubKey, conditions.encode(), priv)
        }

        /** Parses a NIP-OA `auth` tag; see [AuthTag.parse]. Does not verify the signature. */
        fun parse(tag: Tag): OwnerAttestation? = AuthTag.parse(tag)
    }
}
