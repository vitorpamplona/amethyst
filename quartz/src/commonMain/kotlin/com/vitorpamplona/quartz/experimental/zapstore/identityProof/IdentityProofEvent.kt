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
package com.vitorpamplona.quartz.experimental.zapstore.identityProof

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.mls.crypto.Ed25519
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.io.encoding.Base64

/**
 * Zapstore's Cryptographic Identity Proof (kind 30509): the publisher of an app proves that the
 * key that signs its APKs and the Nostr key that publishes its releases belong to the same
 * developer. Zapstore calls the draft "NIP-C1" (unmerged; the letter pair is also used by the
 * unrelated Nostr CI draft). Spec: `zapstore/relay` `pkg/events/identity_proof.go` (what the
 * relay accepts) and `zapstore/zsp` `internal/identity/x509.go` (how it is made and verified).
 *
 * - `d` = SHA-256 of the signing certificate's DER encoding, lowercase hex: the same value a NIP-82
 *   software asset (kind 3063) carries in `apk_certificate_hash`. One proof per certificate.
 * - `signature` = base64 of the certificate key's signature over [signedMessage]:
 *   `Verifying at <created_at> until <expiry> that I control the following Nostr public key:
 *   <pubkey hex>`. RSA keys sign SHA-256 with PKCS#1 v1.5 (zsp also accepts PSS when verifying),
 *   EC keys sign SHA-256 with ECDSA (ASN.1 DER signature), Ed25519 keys sign the message itself.
 * - `expiry` = unix seconds, after `created_at` (one year by default). It is inside the signed
 *   message, as `created_at` is: the event must be published with the timestamp it was signed at.
 * - `revoked` (optional, with an optional reason) withdraws the proof.
 *
 * The Nostr signature proves the npub vouches for the certificate; the certificate signature
 * proves the certificate key vouches for the npub. Checking the second needs the certificate,
 * which the event does not carry (it comes from the APK's signing block). With it, Quartz can
 * check [certificateHashMatches] (SHA-256) and, for Ed25519 certificates only,
 * [verifyEd25519Signature]. RSA and ECDSA P-256, which every live proof uses (the 2026-10 census
 * saw 256- and 512-byte RSA signatures only), need an X.509/SPKI parser and RSA / P-256 verifiers
 * that Quartz does not have; see the platform's `java.security` on JVM/Android.
 *
 * No references (the certificate hash is a value, not an event), so no hint provider. Machine
 * data with empty content: not a SearchableEvent.
 */
@Immutable
class IdentityProofEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The certificate's SHA-256 fingerprint (`d`), when it is 64 hex chars. */
    fun certificateHash(): HexKey? = dTag().takeIf { it.length == 64 && Hex.isHex64(it) }

    /** The certificate key's signature, base64 as published. */
    fun signature() = tags.proofSignature()

    /** [signature] decoded, or null when it is missing or not valid base64. */
    fun signatureBytes(): ByteArray? =
        signature()?.let {
            try {
                Base64.decode(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    fun expiry() = tags.proofExpiry()

    fun isRevoked() = tags.isRevoked()

    fun revocationReason() = tags.revocationReason()

    /** True once [expiry] has passed, and for a proof that has none (it cannot be checked). */
    fun isExpired(now: Long = TimeUtils.now()): Boolean = expiry()?.let { now > it } ?: true

    /**
     * The checks Zapstore's relay makes before accepting the event: a 64-hex certificate hash, a
     * signature, and an expiry later than `created_at`. Says nothing about the signature itself.
     */
    fun isWellFormed(): Boolean = certificateHash() != null && signature() != null && (expiry()?.let { it > createdAt } ?: false)

    /** The exact text the certificate key signed, or null without an [expiry]. */
    fun signedMessage(): String? = expiry()?.let { signedMessage(createdAt, it, pubKey) }

    /** True when this proof's [certificateHash] is [hash] (any hex case). */
    fun isForCertificate(hash: String): Boolean = certificateHash()?.equals(hash, ignoreCase = true) ?: false

    /** True when [certificateDer] (the certificate's DER bytes) hashes to this proof's `d`. */
    fun certificateHashMatches(certificateDer: ByteArray): Boolean = isForCertificate(Hex.encode(sha256(certificateDer)))

    /**
     * Verifies [signature] over [signedMessage] with an Ed25519 certificate's 32-byte public key.
     * False for any other key type, a malformed proof, or a bad signature; never throws. This is
     * only half of a proof check: the caller must also confirm the key belongs to a certificate
     * that [certificateHashMatches].
     */
    fun verifyEd25519Signature(publicKey: ByteArray): Boolean {
        if (publicKey.size != 32) return false
        val message = signedMessage() ?: return false
        val signature = signatureBytes() ?: return false
        if (signature.size != 64) return false
        return try {
            Ed25519.verify(message.encodeToByteArray(), signature, publicKey)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when this proof is about the certificate that signed [asset] and was published by the
     * same key that published the asset — the pairing Zapstore checks before trusting a release.
     * Only a claim until the certificate signature is verified.
     */
    fun claimsSignerOf(asset: SoftwareAssetEvent): Boolean = asset.pubKey == pubKey && asset.apkCertificateHashes().any { isForCertificate(it) }

    companion object {
        const val KIND = 30509

        /** The text a certificate key signs to vouch for [pubKey] from [createdAt] to [expiry]. */
        fun signedMessage(
            createdAt: Long,
            expiry: Long,
            pubKey: HexKey,
        ) = "Verifying at $createdAt until $expiry that I control the following Nostr public key: $pubKey"

        /**
         * [createdAt] must be the timestamp the certificate signed ([signedMessage]): the template
         * keeps it, so sign the template as is.
         */
        fun build(
            certificateHash: HexKey,
            signatureBase64: String,
            expiry: Long,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<IdentityProofEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(certificateHash)
            proofSignature(signatureBase64)
            proofExpiry(expiry)
            initializer()
        }
    }
}
