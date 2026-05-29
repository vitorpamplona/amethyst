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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.seed.CashuDeterministic

/**
 * A pair of secret + blinding factor for one BDHKE blind message.
 *  - [secretHex] is the lowercase hex of the 32-byte secret. NUT-00
 *    specifies the secret is the UTF-8 encoding of this hex string, so
 *    consumers always call `secretHex.encodeToByteArray()` before
 *    passing to `Bdhke.blind` / `hashToCurve`.
 *  - [blindingFactor] is 32 raw bytes, a valid secp256k1 scalar.
 */
data class DerivedSecret(
    val secretHex: String,
    val blindingFactor: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is DerivedSecret &&
            other.secretHex == secretHex &&
            other.blindingFactor.contentEquals(blindingFactor)

    override fun hashCode(): Int = 31 * secretHex.hashCode() + blindingFactor.contentHashCode()
}

/**
 * Strategy for producing the (secret, r) pairs that go into BDHKE blind
 * messages. Two impls today:
 *  - [RandomSecretFactory] — fresh randomness for each call. Default.
 *    Forwards-compatible with NUT-09 restore in the sense that the mint
 *    will still hand back the proofs if we have the secrets — but we
 *    won't have them after a wallet-event loss.
 *  - [DeterministicSecretFactory] — NUT-13 derivation from a seed plus
 *    a per-keyset counter. The wallet can re-derive past secrets after a
 *    catastrophic kind:7375 loss and recover via NUT-09 /v1/restore.
 *
 * The factory is keyset-aware because NUT-13 derivation depends on the
 * keyset id (different keysets yield different secrets from the same
 * seed+counter), and the counter is per-keyset.
 */
interface SecretFactory {
    /**
     * Mint [count] (secret, r) pairs for use on the specified keyset.
     *
     * Batched on purpose: the deterministic implementation reserves a
     * contiguous counter range via a single atomic critical section on
     * `AccountSettings.reserveCashuCounters`. Calling one-at-a-time
     * inside `splitAmounts(amount).map { ... }` would take the lock N
     * times per mint — wasteful for both contention and disk writes.
     */
    fun nextSecrets(
        keysetId: String,
        count: Int,
    ): List<DerivedSecret>

    /** Convenience for ops that need a single output. */
    fun nextSecret(keysetId: String): DerivedSecret = nextSecrets(keysetId, 1).first()
}

/**
 * Random secret + random blinding factor. No deterministic recovery —
 * losing the kind:7375 token events is permanent funds loss. Suitable
 * as a fallback when no seed is available (e.g. a wallet that
 * pre-dates the NUT-13 wiring).
 */
object RandomSecretFactory : SecretFactory {
    override fun nextSecrets(
        keysetId: String,
        count: Int,
    ): List<DerivedSecret> {
        require(count > 0) { "Must request at least one secret" }
        return List(count) {
            val secret = Bdhke.randomSecret()
            val r = Bdhke.randomScalar()
            DerivedSecret(secret.toHexKey(), r)
        }
    }
}

/**
 * NUT-13 deterministic secret factory.
 *
 * [seedProvider] is a thunk that returns the wallet's seed when
 * available, null when the wallet hasn't decrypted its kind:17375 yet.
 * Lazy-resolving instead of taking the seed up-front lets the factory
 * be constructed at wallet-state init time (before any signer round-
 * trip) — when the seed isn't ready, the factory transparently falls
 * back to [fallback], preserving the no-NUT-13 invariant.
 *
 * [reserveCounter] is a thunk that atomically increments and returns the
 * previous value for a given keyset id — that's the wallet's persistent
 * counter state, NOT a fresh random index.
 *
 * Two reserveCounter contracts the caller MUST honour:
 *  1. Returned counters are STRICTLY MONOTONIC per keyset id. Reusing one
 *     under the same (seed, keysetId) reuses the same (secret, r) pair —
 *     reveals the seed-derivation relationship and could let an observer
 *     correlate proofs across mints.
 *  2. The increment is PERSISTED before the secret is actually used in a
 *     mint request. Otherwise a crash mid-mint reuses on next launch.
 *
 * `AccountSettings.reserveCashuCounters` satisfies both.
 */
class DeterministicSecretFactory(
    private val seedProvider: () -> ByteArray?,
    /**
     * Atomically reserves [count] consecutive counters for a keyset and
     * returns the FIRST one — the factory then derives at indices
     * `[returned .. returned+count)`. Persisting in one shot avoids the
     * lock-N-times-per-mint waste of the old per-secret API.
     *
     * `AccountSettings.reserveCashuCounters(keysetId, count)` is the
     * canonical implementation.
     */
    private val reserveCounters: (keysetId: String, count: Int) -> Long,
    private val fallback: SecretFactory = RandomSecretFactory,
) : SecretFactory {
    override fun nextSecrets(
        keysetId: String,
        count: Int,
    ): List<DerivedSecret> {
        require(count > 0) { "Must request at least one secret" }
        val seed = seedProvider() ?: return fallback.nextSecrets(keysetId, count)
        val first = reserveCounters(keysetId, count)
        return List(count) { offset ->
            val counter = first + offset
            // CashuDeterministic.secretBytes returns the raw 32 bytes;
            // the hex form is what BDHKE/proof storage actually use.
            val secretHex = CashuDeterministic.secretBytes(seed, keysetId, counter).toHexKey()
            val r = CashuDeterministic.blindingFactor(seed, keysetId, counter)
            DerivedSecret(secretHex, r)
        }
    }
}
