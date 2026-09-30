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
package com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.AttestationConditions
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The NIP-OA `auth` tag: `["auth", "<owner-pubkey-hex>", "<conditions>", "<sig-hex>"]`.
 *
 * Carries an owner's Schnorr attestation authorizing the event's own author (an agent
 * key) to publish under [AttestationConditions]. See [OwnerAttestation] for the
 * commitment format and verification.
 */
object AuthTag {
    const val TAG_NAME = "auth"

    fun match(tag: Tag) = tag.has(3) && tag[0] == TAG_NAME

    fun assemble(attestation: OwnerAttestation): Array<String> = arrayOf(TAG_NAME, attestation.ownerPubKey, attestation.conditions, attestation.sig)

    /**
     * Parses a well-formed `auth` tag into an [OwnerAttestation]. Returns null on any
     * structural problem: wrong name or not exactly 4 elements, an owner pubkey that is not
     * 64-char lowercase hex, a signature that is not 128-char lowercase hex, or non-canonical
     * conditions (mirrors `parse_auth_tag_fields` in Buzz's `buzz-sdk/src/nip_oa.rs`). This is a
     * *shape* check only — it does not verify the signature (call
     * [OwnerAttestation.verify]).
     */
    fun parse(tag: Array<String>): OwnerAttestation? {
        ensure(tag.size == 4) { return null }
        ensure(tag[0] == TAG_NAME) { return null }

        val owner = tag[1]
        val conditions = tag[2]
        val sig = tag[3]

        ensure(OwnerAttestation.isLowercaseHex(owner, 64)) { return null }
        ensure(OwnerAttestation.isLowercaseHex(sig, 128)) { return null }
        ensure(AttestationConditions.isValid(conditions)) { return null }

        return OwnerAttestation(owner, conditions, sig)
    }
}
