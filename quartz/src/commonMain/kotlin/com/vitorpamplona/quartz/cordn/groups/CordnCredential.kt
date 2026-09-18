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
package com.vitorpamplona.quartz.cordn.groups

import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.tree.Credential
import com.vitorpamplona.quartz.mls.tree.LeafNode
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * How cordn writes a Nostr pubkey into an MLS BasicCredential.
 *
 * **64 ASCII bytes of lowercase hex, not the 32 raw bytes.** `spec/00.md` §6
 * requires "the canonical encoded Nostr public key" and then explicitly
 * declines to say which encoding, leaving it "an application-wide convention
 * to be fixed uniformly by implementations". The reference implementation
 * fixed it as UTF-8 of the hex string
 * (`packages/cli/src/utils/mlsIdentity.ts:createCredential`), and the
 * coordinator reads it back with a `TextDecoder` and string-compares it to the
 * calling event's pubkey (`packages/server/src/coordinatorMethods.ts:128`).
 *
 * Marmot picked the other one — raw 32 bytes — so a Marmot KeyPackage and a
 * cordn KeyPackage are not interchangeable, and this is the single line where
 * that divergence lives. It is a one-line change on either side today and
 * unfixable once either has users at scale; see §4.1 of
 * `quartz/plans/2026-09-17-cordn-interop.md`, which is still open upstream.
 *
 * A credential is a CLAIM, never proof. `spec/00.md` §6 is explicit that
 * BasicCredential alone establishes nothing; the binding comes from the signed
 * publication payload, which [com.vitorpamplona.quartz.cordn.spec00Coordinator]
 * verifies.
 */
object CordnCredential {
    /** The BasicCredential a cordn leaf carries for [pubKeyHex]. */
    fun of(pubKeyHex: HexKey): Credential.Basic {
        require(isCanonical(pubKeyHex)) {
            "cordn credential identity must be 64 lowercase hex chars, was '$pubKeyHex'"
        }
        return Credential.Basic(pubKeyHex.encodeToByteArray())
    }

    /**
     * The account hex this credential claims, or null if it is not a cordn
     * one.
     *
     * Null rather than an exception: a leaf in a mixed or malformed tree is an
     * ordinary thing to walk past, and the callers that care about the
     * difference check it explicitly.
     */
    fun identityOrNull(credential: Credential?): HexKey? {
        val identity = (credential as? Credential.Basic)?.identity ?: return null
        if (identity.size != HEX_LENGTH) return null
        val hex =
            try {
                identity.decodeToString(throwOnInvalidSequence = true)
            } catch (e: CharacterCodingException) {
                return null
            }
        return if (isCanonical(hex)) hex else null
    }

    /** The account hex claimed by [leaf], or null. */
    fun identityOrNull(leaf: LeafNode?): HexKey? = identityOrNull(leaf?.credential)

    /**
     * Every member's account hex, by leaf index.
     *
     * Use this rather than `MlsGroup.memberIdentityHex`, which hex-encodes the
     * credential bytes — correct for a binding that stores a raw key, and for
     * cordn it returns 128 characters of hex-of-hex. Leaves whose credential is
     * not a cordn identity are skipped rather than reported as garbage.
     */
    fun membersOf(group: MlsGroup): Map<Int, HexKey> = group.members().mapNotNull { (index, leaf) -> identityOrNull(leaf)?.let { index to it } }.toMap()

    /** The set of accounts holding at least one leaf. One account may hold several. */
    fun memberIdentities(group: MlsGroup): Set<HexKey> = membersOf(group).values.toSet()

    private fun isCanonical(hex: String) = hex.length == HEX_LENGTH && hex.all { it in HEX_ALPHABET }

    private const val HEX_LENGTH = 64
    private const val HEX_ALPHABET = "0123456789abcdef"
}
