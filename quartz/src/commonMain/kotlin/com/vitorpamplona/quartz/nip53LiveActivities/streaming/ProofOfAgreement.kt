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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * NIP-53 Proof of Agreement to Participate.
 *
 * The proof is a schnorr signature by the participant's private key over the
 * SHA256 of the event's addressable `a` tag value (`kind:pubkey:dTag`), encoded as hex.
 *
 * See: https://github.com/nostr-protocol/nips/blob/master/53.md#proof-of-agreement-to-participate
 */
object ProofOfAgreement {
    /**
     * Computes the SHA256 digest over the canonical `kind:pubkey:dTag` string.
     * This is the message that the participant's private key signs.
     */
    fun digest(
        eventKind: Int,
        eventAuthor: HexKey,
        eventDTag: String,
    ): ByteArray = sha256(Address.assemble(eventKind, eventAuthor, eventDTag).encodeToByteArray())

    fun digest(address: Address): ByteArray = digest(address.kind, address.pubKeyHex, address.dTag)

    /**
     * Signs the proof using the participant's private key (schnorr over the SHA256
     * digest of the event's `a` tag value). Returns the hex-encoded signature.
     *
     * Only usable by the participant — requires access to their raw private key
     * (e.g. from a local `NostrSignerInternal`). External signers (NIP-46, NIP-55)
     * do not expose a raw-bytes sign API so they cannot produce this proof.
     */
    fun sign(
        participantPrivKey: ByteArray,
        eventKind: Int,
        eventAuthor: HexKey,
        eventDTag: String,
    ): HexKey = Nip01Crypto.sign(digest(eventKind, eventAuthor, eventDTag), participantPrivKey).toHexKey()

    /**
     * Verifies that [proof] is a valid schnorr signature by [participantPubKey] over
     * SHA256(`kind:pubkey:dTag`). Returns `false` if the proof is malformed, the
     * pubkey is invalid, or the signature doesn't match.
     */
    fun verify(
        proof: HexKey,
        participantPubKey: HexKey,
        eventKind: Int,
        eventAuthor: HexKey,
        eventDTag: String,
    ): Boolean {
        val signature = proof.hexToByteArrayOrNull() ?: return false
        if (signature.size != 64) return false
        val pubKey = participantPubKey.hexToByteArrayOrNull() ?: return false
        if (pubKey.size != 32) return false

        return runCatching {
            Nip01Crypto.verify(signature, digest(eventKind, eventAuthor, eventDTag), pubKey)
        }.getOrDefault(false)
    }

    fun verify(
        proof: HexKey,
        participantPubKey: HexKey,
        address: Address,
    ): Boolean = verify(proof, participantPubKey, address.kind, address.pubKeyHex, address.dTag)

    /**
     * Verifies a participant tag's proof against its owning [LiveActivitiesEvent].
     * Returns `false` if the tag has no proof or the proof is invalid.
     */
    fun verify(
        tag: ParticipantTag,
        event: LiveActivitiesEvent,
    ): Boolean {
        val proof = tag.proof ?: return false
        return verify(proof, tag.pubKey, event.address())
    }
}

/**
 * Convenience: check whether a participant's proof of agreement is valid for this event.
 * Useful for UI that wants to distinguish "verified" participants from "invited" ones
 * (per NIP-53: "Clients MAY only display participants if the proof is available or
 * MAY display participants as 'invited' if the proof is not available").
 */
fun LiveActivitiesEvent.hasValidProof(participant: ParticipantTag): Boolean = ProofOfAgreement.verify(participant, this)
