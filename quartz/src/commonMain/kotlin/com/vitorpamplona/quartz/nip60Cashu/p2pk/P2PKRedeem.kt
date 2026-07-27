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
package com.vitorpamplona.quartz.nip60Cashu.p2pk

import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof

/**
 * Thrown when a proof set contains a NUT-11 P2PK-locked proof that this wallet
 * has no private key to sign for. [lockPubKeyHex] is the pubkey the proof is
 * locked to, exactly as it appears in the secret (32-byte x-only or 33-byte
 * compressed) — callers may compare it against the user's own keys to craft a
 * tailored message (e.g. "locked to your identity key, redeem it elsewhere").
 */
class P2PKUnredeemableException(
    val lockPubKeyHex: String,
) : RuntimeException("This ecash is locked to a public key this wallet can't sign for ($lockPubKeyHex).")

/**
 * Attach NUT-11 unlock witnesses to any P2PK-locked proofs in [proofs] so the
 * set can be spent at `/v1/swap`.
 *
 * Each proof's secret is inspected via [P2PK.parseSecret]:
 *  - a plain (non-P2PK) secret passes through unchanged;
 *  - a P2PK secret is signed with the private key returned by [signingKeyFor],
 *    which is invoked with the lock's 32-byte **x-only** pubkey hex (the parity
 *    prefix of a 33-byte compressed `data` is stripped first, since BIP-340
 *    verification — what the mint runs — is x-only).
 *
 * When [signingKeyFor] returns null for a locked proof, we hold no key for it
 * and [P2PKUnredeemableException] is thrown (naming the original lock pubkey)
 * rather than sending an unsigned swap the mint would reject with an opaque
 * `witness is missing for p2pk signature` 400.
 */
fun signP2pkWitnesses(
    proofs: List<CashuProof>,
    signingKeyFor: (lockPubKeyXOnly: String) -> String?,
): List<CashuProof> =
    proofs.map { proof ->
        val parsed = P2PK.parseSecret(proof.secret) ?: return@map proof
        val privKeyHex = signingKeyFor(parsed.pubKeyHex.xOnly()) ?: throw P2PKUnredeemableException(parsed.pubKeyHex)
        proof.copy(witness = P2PK.signWitness(proof.secret, privKeyHex))
    }

/**
 * The first P2PK lock across [proofs] that [signingKeyFor] can't resolve, or
 * null if every locked proof is signable (plain proofs are ignored). Lets a
 * caller pre-flight a multi-group token so it never swaps some groups and then
 * discovers a later group is unredeemable — leaving a half-redeemed state.
 */
fun firstUnsignableP2pkLock(
    proofs: List<CashuProof>,
    signingKeyFor: (lockPubKeyXOnly: String) -> String?,
): String? {
    proofs.forEach { proof ->
        val parsed = P2PK.parseSecret(proof.secret) ?: return@forEach
        if (signingKeyFor(parsed.pubKeyHex.xOnly()) == null) return parsed.pubKeyHex
    }
    return null
}

/** True when any proof in the set carries a NUT-11 P2PK-locked secret. */
fun List<CashuProof>.anyP2pkLocked(): Boolean = any { P2PK.parseSecret(it.secret) != null }

/**
 * Drop a 33-byte compressed pubkey's parity prefix, yielding the 32-byte x-only
 * hex, and lowercase it. The `data` field is formatted by the sender and NUT-11
 * doesn't mandate a case, so normalize before matching against our (lowercase)
 * key index — otherwise an uppercase lock we *can* sign for is falsely rejected.
 */
private fun String.xOnly(): String = (if (length == 66) substring(2) else this).lowercase()
