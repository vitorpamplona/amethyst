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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the NIP-OA [OwnerAttestation]s this device has *received* — one owner-signed
 * authorization per agent key that lets that key publish in the owner's Buzz workspace
 * without being enrolled as a relay member.
 *
 * The counterpart of issuance ([OwnerAttestation] is signed by an owner and handed to an
 * agent operator out-of-band): when the account whose pubkey equals a stored key
 * authenticates (NIP-42) to a Buzz-dialect relay, the auth coordinator attaches the
 * matching [OwnerAttestation.toTag] to the AUTH event, and the relay grants virtual
 * membership while the owner stays a member.
 *
 * Keyed by the **agent** pubkey (the key the attestation authorizes). Only a
 * [OwnerAttestation.verify]-passing attestation for that key should be stored, so the
 * store never carries a credential the relay would reject.
 *
 * Like [BuzzRelayDialect] this is a process-wide singleton, and — for now — in-memory
 * only: a held attestation is re-pasted after a process restart. Persisting it across
 * launches (per-account, encrypted) is a follow-up.
 */
object BuzzHeldAttestations {
    private val heldByAgent = MutableStateFlow<Map<HexKey, OwnerAttestation>>(emptyMap())

    /** All held attestations, keyed by agent pubkey; UI can collect this. */
    val flow: StateFlow<Map<HexKey, OwnerAttestation>> = heldByAgent

    /** The attestation held for [agentPubKey], or null. */
    fun attestationFor(agentPubKey: HexKey): OwnerAttestation? = heldByAgent.value[agentPubKey]

    /**
     * The `auth` tag to attach to [agentPubKey]'s NIP-42 AUTH event, or null when no
     * verified attestation is held for that key.
     */
    fun authTagFor(agentPubKey: HexKey): Array<String>? = attestationFor(agentPubKey)?.toTag()

    /**
     * Stores [attestation] as authorizing [agentPubKey]. The caller must have already
     * confirmed `attestation.verify(agentPubKey)`; this is a CAS-loop put so concurrent
     * writers don't clobber each other.
     */
    fun put(
        agentPubKey: HexKey,
        attestation: OwnerAttestation,
    ) {
        while (true) {
            val current = heldByAgent.value
            if (current[agentPubKey] == attestation) return
            if (heldByAgent.compareAndSet(current, current + (agentPubKey to attestation))) return
        }
    }

    /** Removes any attestation held for [agentPubKey]. */
    fun remove(agentPubKey: HexKey) {
        while (true) {
            val current = heldByAgent.value
            if (agentPubKey !in current) return
            if (heldByAgent.compareAndSet(current, current - agentPubKey)) return
        }
    }

    /**
     * Replaces the whole store with [entries] — used to restore from disk at startup. The
     * caller must have re-verified each attestation against its agent key (the same gate
     * [put] documents), so a tampered on-disk credential can't be reinstated.
     */
    fun restore(entries: Map<HexKey, OwnerAttestation>) {
        heldByAgent.value = entries
    }

    /** Test-only: clears all held attestations so unit tests don't leak state. */
    fun clearForTesting() {
        heldByAgent.value = emptyMap()
    }
}
