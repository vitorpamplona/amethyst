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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.appComponents.accountIdentityProof.AccountIdentityProofV2
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils.isCryptographicallyValid
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils.isValid
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsCiphersuiteTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsProposalsTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsProtocolVersionTag
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.components.ComponentsList
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Utility functions for KeyPackage lifecycle management (MIP-00).
 *
 * Covers:
 * - Selection policy: prefer non-last-resort, highest created_at, validate encoding
 * - Rotation: publish new kind:30443 under same d-tag after joining a group
 * - Migration: support both kind:443 (legacy) and kind:30443 (addressable) during transition
 */
object KeyPackageUtils {
    /**
     * 84 days plus a one-hour clock-skew margin — the maximum span a Marmot
     * KeyPackage `Lifetime` may cover (`foundation/key-packages.md`).
     */
    const val MAX_LIFETIME_SECONDS = 7_261_200L

    /** Legacy non-addressable KeyPackage kind (pre-migration) */
    const val LEGACY_KIND = 443

    /** Current addressable KeyPackage kind */
    const val CURRENT_KIND = KeyPackageEvent.KIND // 30443

    /** Maximum number of d-tag slots a user should maintain */
    const val MAX_SLOTS = 10

    /**
     * Logical name for the primary KeyPackage slot.
     * This is NOT the actual d-tag value — the actual d-tag is a randomly
     * generated 64-char hex string stored persistently in [KeyPackageRotationManager].
     */
    const val PRIMARY_SLOT = "primary"

    /**
     * Generates a cryptographically random d-tag value per MIP-00:
     * a 32-byte (64 hex char) random identifier for a KeyPackage slot.
     */
    fun generateRandomDTag(): String = MlsCryptoProvider.randomBytes(32).toHexKey()

    /**
     * Selects the best KeyPackage from a set of candidates for a given user.
     *
     * Selection policy (MIP-00):
     * 1. Filter out invalid KeyPackages (bad encoding, missing required fields)
     * 2. Prefer non-last-resort KeyPackages (those with multiple slots available)
     * 3. Among valid candidates, prefer highest created_at (most recent)
     *
     * @param candidates list of KeyPackageEvents to choose from
     * @param lastResortDTag d-tag of the user's last-resort KeyPackage (if known)
     * @return the best KeyPackage, or null if no valid candidates
     */
    fun selectBest(
        candidates: List<KeyPackageEvent>,
        lastResortDTag: String? = null,
    ): KeyPackageEvent? {
        val valid = candidates.filter { isValid(it) }
        if (valid.isEmpty()) return null

        // Prefer non-last-resort KeyPackages
        val nonLastResort = valid.filter { it.dTag() != lastResortDTag }
        val pool = nonLastResort.ifEmpty { valid }

        // Select the most recent
        return pool.maxByOrNull { it.createdAt }
    }

    /**
     * Validates a KeyPackage event has required fields and proper encoding.
     *
     * Performs the same strict tag-level MIP-00 checks used by MDK so that
     * malformed or adversarial events are rejected at parse time:
     *  - `d` tag is exactly 64 lowercase hex characters (32-byte slot ID)
     *  - `mls_protocol_version` is exactly "1.0"
     *  - `mls_ciphersuite` is exactly "0x0001"
     *  - `mls_extensions` contains both "0xf2ee" (NostrGroupData) and
     *    "0x000a" (LastResort)
     *  - `mls_proposals` contains "0x000a" (SelfRemove)
     *  - `encoding` is "base64" and content is non-empty
     *  - `i` (keyPackageRef) tag is non-empty
     *
     * For deep cryptographic checks (KeyPackageRef hash match, credential
     * identity == event.pubkey) call [isCryptographicallyValid].
     */
    fun isValid(event: KeyPackageEvent): Boolean {
        // d tag: exactly 64 hex chars per MIP-00
        val dTag = event.dTag()
        if (dTag.length != 64 || !dTag.all { it.isHexChar() }) return false

        // mls_protocol_version == "1.0"
        if (event.mlsProtocolVersion() != MlsProtocolVersionTag.CURRENT_VERSION) return false

        // mls_ciphersuite == "0x0001"
        if (event.mlsCiphersuite() != MlsCiphersuiteTag.DEFAULT_CIPHERSUITE) return false

        val extensions = event.mlsExtensions()?.map { it.lowercase() }?.toSet() ?: return false
        val proposals = event.mlsProposals()?.map { it.lowercase() }?.toSet() ?: return false

        if (event.isCurrentProfile()) {
            // Current profile: app_data_dictionary + app_data_update, and an
            // app_components tag naming 0x8009. Last resort moved from
            // extension 0x000a to a KeyPackage component, so it is NOT
            // advertised here any more.
            if (!extensions.contains(KeyPackageEvent.CURRENT_PROFILE_EXTENSION)) return false
            if (!proposals.contains(KeyPackageEvent.APP_DATA_UPDATE_PROPOSAL)) return false
            if (!proposals.contains(MlsProposalsTag.SELF_REMOVE)) return false
            // The current profile forbids the encoding tag outright: a
            // receiver must decode by the rule that defines each field, never
            // by a negotiated marker.
            if (event.encoding() != null) return false
            // ...and does not repeat relays; discovery is the author's NIP-65
            // write set.
            if (event.relays() != null) return false
        } else {
            // MIP-era shape, kept so groups already on disk stay readable.
            if (!extensions.contains("0xf2ee") || !extensions.contains("0x000a")) return false
            if (!proposals.contains("0x000a")) return false
            if (event.encoding() != EncodingTag.BASE64) return false
        }

        if (event.content.isEmpty()) return false

        // i (KeyPackageRef) tag MUST be present
        if (event.keyPackageRef().isNullOrEmpty()) return false

        return true
    }

    /**
     * Deep MIP-00 validation: decodes the KeyPackage content and verifies:
     *  - `i` tag matches the computed `KeyPackageRef` (RFC 9420 §5.2)
     *  - Credential identity (BasicCredential) equals the event's `pubkey`
     *    (32-byte x-only Nostr pubkey)
     *  - KeyPackage signature over KeyPackageTBS is valid
     *
     * Returns true only if [isValid] also holds and every cryptographic check
     * passes. Requires [isValid] to be true as a precondition — it is called
     * internally.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun isCryptographicallyValid(
        event: KeyPackageEvent,
        nowSeconds: Long = TimeUtils.now(),
    ): Boolean {
        if (!isValid(event)) return false

        val iTag = event.keyPackageRef() ?: return false
        val keyPackage =
            try {
                val bytes = Base64.decode(event.content)
                MlsKeyPackage.decodeTls(TlsReader(bytes))
            } catch (_: Throwable) {
                return false
            }

        // i tag MUST equal the computed KeyPackageRef
        if (keyPackage.reference().toHexKey() != iTag.lowercase()) return false

        // Credential identity MUST equal the event's pubkey (32-byte x-only).
        // MIP-00 requires BasicCredential with the raw 32-byte Nostr pubkey.
        val credential = keyPackage.leafNode.credential
        if (credential !is Credential.Basic) return false
        if (credential.identity.size != 32) return false
        if (credential.identity.toHexKey().lowercase() != event.pubKey.lowercase()) return false

        // KeyPackage signature MUST verify against the LeafNode's signatureKey.
        if (!keyPackage.verifySignature()) return false

        if (!hasValidLifetime(keyPackage, nowSeconds)) return false

        // Current profile: the embedded LeafNode must actually advertise and
        // carry the account identity proof. The app_components TAG is only an
        // advertisement — a producer can write anything there, so the decoded
        // bytes are what decide.
        if (event.isCurrentProfile() && !hasValidAccountIdentityProof(keyPackage, credential.identity)) {
            return false
        }

        return true
    }

    /**
     * The MLS `Lifetime` extension is part of KeyPackage validity
     * (`foundation/key-packages.md`).
     *
     * A candidate MUST carry one, MUST be current at validation time, and MUST
     * span at most [MAX_LIFETIME_SECONDS]. A last-resort KeyPackage does NOT
     * relax the span limit — reuse is about the init key, not about staying
     * valid forever.
     */
    fun hasValidLifetime(
        keyPackage: MlsKeyPackage,
        nowSeconds: Long = TimeUtils.now(),
    ): Boolean {
        val lifetime = keyPackage.leafNode.lifetime ?: return false
        if (lifetime.notBefore == 0L && lifetime.notAfter == 0L) return false
        if (nowSeconds < lifetime.notBefore) return false
        if (nowSeconds > lifetime.notAfter) return false
        val span = lifetime.notAfter - lifetime.notBefore
        if (span <= 0L || span > MAX_LIFETIME_SECONDS) return false
        return true
    }

    /**
     * Validate the LeafNode's `marmot.member.account-identity-proof.v2`
     * component against that same leaf's credential and signature key.
     */
    fun hasValidAccountIdentityProof(
        keyPackage: MlsKeyPackage,
        credentialIdentity: ByteArray,
    ): Boolean {
        val dictionary = AppDataDictionary.fromExtensions(keyPackage.leafNode.extensions) ?: return false

        val supported = dictionary[ComponentsList.APP_COMPONENTS_ID]?.let { ComponentsList.decode(it) } ?: return false
        if (!supported.contains(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2)) return false

        val ciphersuite = MlsCiphersuite.fromCode(MlsCiphersuiteTag.DEFAULT_CIPHERSUITE) ?: return false
        return AccountIdentityProofV2.isValid(
            componentData = dictionary[AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2],
            credentialIdentity = credentialIdentity,
            mlsSignatureKey = keyPackage.leafNode.signatureKey,
            ciphersuite = ciphersuite,
        )
    }

    private fun Char.isHexChar(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /**
     * Builds a rotated KeyPackage for the same d-tag slot.
     *
     * After joining a group (processing a Welcome), a member MUST rotate their
     * KeyPackage to prevent reuse of the consumed init_key material.
     *
     * @param newKeyPackageBase64 the new MLS KeyPackage content
     * @param dTagSlot the d-tag slot to rotate (reuses the same slot)
     * @param newKeyPackageRef the KeyPackageRef of the new KeyPackage
     * @param relays relay URLs where this KeyPackage should be published
     * @param ciphersuite MLS ciphersuite identifier
     * @param clientName optional client name
     * @return a new KeyPackageEvent that replaces the old one at the same d-tag
     */
    fun buildRotation(
        newKeyPackageBase64: String,
        dTagSlot: String,
        newKeyPackageRef: HexKey,
        relays: List<NormalizedRelayUrl>,
        ciphersuite: String = "0x0001",
        clientName: String? = null,
    ): EventTemplate<KeyPackageEvent> =
        KeyPackageEvent.build(
            keyPackageBase64 = newKeyPackageBase64,
            dTagSlot = dTagSlot,
            keyPackageRef = newKeyPackageRef,
            relays = relays,
            ciphersuite = ciphersuite,
            clientName = clientName,
        )

    /**
     * Checks whether an event is a KeyPackage (either legacy kind:443 or current kind:30443).
     * Used during the migration period.
     */
    fun isKeyPackageKind(event: Event): Boolean = event.kind == CURRENT_KIND || event.kind == LEGACY_KIND

    /**
     * Returns the list of kinds to query during migration (both legacy and current).
     */
    fun migrationKinds(): List<Int> = listOf(CURRENT_KIND, LEGACY_KIND)
}
