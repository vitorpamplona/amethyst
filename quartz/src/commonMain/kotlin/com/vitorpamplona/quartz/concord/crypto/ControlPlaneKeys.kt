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
package com.vitorpamplona.quartz.concord.crypto

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray

/**
 * The Control Plane's key material at one epoch (CORD-02 §5).
 *
 * The plane's stream key is split (CORD-01, Write-Restricted Streams): the
 * address-and-signer keypair derives from the staff-held `control_root`, while the
 * wraps' content is encrypted under the `community_root`-derived read key every
 * member holds. So what an account holds depends on its standing:
 *
 *  - **Member**: the [address] (`control_pk`, held — never derivable) plus the
 *    [readKey]. Enough to subscribe, verify wrap signatures, and decrypt; [signer]
 *    is null and [canWrite] false.
 *  - **Staff / owner**: additionally the [signer] (derived from the `control_root`),
 *    whose sk mints wraps that verify at the address.
 *  - **Legacy epoch** (pre-split, CORD-06 §3): the `concord/control` derivation
 *    alone was the plane — its pk the address and signer, every member holding
 *    both. [legacy] is true and [signer] == [readKey].
 */
class ControlPlaneKeys(
    /** The plane's stream address (`control_pk` on a split epoch): subscribe + verify. */
    val address: HexKey,
    /** The `community_root`-derived read key; its `conversationKey` opens the wraps. */
    val readKey: GroupKey,
    /** The keypair whose sk signs wraps at [address]. Null when this account cannot write. */
    val signer: GroupKey?,
    /** True for a pre-split epoch keyed by the legacy member-held derivation. */
    val legacy: Boolean,
) {
    /** True when this account holds the write key for the plane. */
    val canWrite: Boolean get() = signer != null

    companion object {
        /**
         * A pre-split epoch's Control Plane: the legacy `concord/control` derivation
         * is address, signer, and read key at once — every member holds all three.
         */
        fun legacy(
            communityRoot: ByteArray,
            communityId: ByteArray,
            epoch: Long,
        ): ControlPlaneKeys {
            val key = ConcordKeyDerivation.controlPlaneKey(communityRoot, communityId, epoch)
            return ControlPlaneKeys(key.publicKeyHex, key, signer = key, legacy = true)
        }

        /**
         * A split epoch as a regular member holds it: the delivered [controlPk]
         * (invite / community list / base rekey blob, CORD-02 §2) plus the derived
         * read key. Read-only — a member cannot mint a wrap at the address.
         */
        fun forMember(
            communityRoot: ByteArray,
            communityId: ByteArray,
            epoch: Long,
            controlPk: HexKey,
        ): ControlPlaneKeys =
            ControlPlaneKeys(
                address = controlPk.lowercase(),
                readKey = ConcordKeyDerivation.controlPlaneKey(communityRoot, communityId, epoch),
                signer = null,
                legacy = false,
            )

        /**
         * A split epoch as staff holds it: the signer derives from the held
         * [controlRoot], yielding the address and the write key together.
         */
        fun forStaff(
            communityRoot: ByteArray,
            communityId: ByteArray,
            epoch: Long,
            controlRoot: ByteArray,
        ): ControlPlaneKeys {
            val signer = ConcordKeyDerivation.controlSignerKey(controlRoot, communityId, epoch)
            return ControlPlaneKeys(
                address = signer.publicKeyHex,
                readKey = ConcordKeyDerivation.controlPlaneKey(communityRoot, communityId, epoch),
                signer = signer,
                legacy = false,
            )
        }

        /**
         * Dispatches on what the account holds: the `control_root` secret (staff),
         * only the `control_pk` (member), or neither — a legacy, pre-split epoch
         * (CORD-06 §3). A held [controlRoot] wins over a held [controlPk]: the pk it
         * derives is the plane by definition (a delivered secret is only ever adopted
         * after the derive-check, CORD-04 §3).
         */
        fun of(
            communityRoot: ByteArray,
            communityId: ByteArray,
            epoch: Long,
            controlPk: HexKey? = null,
            controlRoot: HexKey? = null,
        ): ControlPlaneKeys =
            when {
                controlRoot != null -> forStaff(communityRoot, communityId, epoch, controlRoot.hexToByteArray())
                controlPk != null -> forMember(communityRoot, communityId, epoch, controlPk)
                else -> legacy(communityRoot, communityId, epoch)
            }
    }
}
