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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate

/**
 * Utility functions for KeyPackage lifecycle management (MIP-00).
 *
 * Covers:
 * - Selection policy: prefer non-last-resort, highest created_at, validate encoding
 * - Rotation: publish new kind:30443 under same d-tag after joining a group
 * - Migration: support both kind:443 (legacy) and kind:30443 (addressable) during transition
 */
object KeyPackageUtils {
    /** Legacy non-addressable KeyPackage kind (pre-migration) */
    const val LEGACY_KIND = 443

    /** Current addressable KeyPackage kind */
    const val CURRENT_KIND = KeyPackageEvent.KIND // 30443

    /** Default d-tag slot for primary KeyPackage */
    const val PRIMARY_SLOT = "0"

    /** Maximum number of d-tag slots a user should maintain */
    const val MAX_SLOTS = 10

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
     */
    fun isValid(event: KeyPackageEvent): Boolean =
        event.encoding() == EncodingTag.BASE64 &&
            event.content.isNotEmpty() &&
            !event.keyPackageRef().isNullOrEmpty() &&
            !event.mlsCiphersuite().isNullOrEmpty()

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
