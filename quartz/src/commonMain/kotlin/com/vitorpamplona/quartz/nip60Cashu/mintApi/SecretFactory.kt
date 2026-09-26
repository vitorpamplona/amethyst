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
 * Where a batch of deterministic secrets starts.
 *
 * [firstCounter] is null when the secrets will be random — either because the
 * factory is [RandomSecretFactory], or because a [DeterministicSecretFactory]
 * had no seed available when it reserved.
 */
data class SecretReservation(
    val keysetId: String,
    val firstCounter: Long?,
)

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
     * Reserve whatever durable state the next [count] secrets need, and return
     * where they start.
     *
     * Suspends because that is the durability boundary: a NUT-13 counter MUST
     * reach disk before any secret derived from it reaches a mint, or a crash
     * mid-mint replays the counter on the next launch and the mint answers
     * `outputs already signed`.
     *
     * Separate from [derive] so that derivation stays pure and synchronous.
     * Reserving is the only part that touches storage, and only a
     * deterministic factory does so at all.
     */
    suspend fun reserve(
        keysetId: String,
        count: Int,
    ): SecretReservation

    /**
     * Derive [count] (secret, r) pairs from an already-reserved position.
     *
     * Pure: no storage, no suspension, same output for the same reservation.
     */
    fun derive(
        reservation: SecretReservation,
        count: Int,
    ): List<DerivedSecret>

    /**
     * Reserve and derive in one step.
     *
     * Batched on purpose: the deterministic implementation reserves a
     * contiguous counter range in a single atomic write. Calling one-at-a-time
     * inside `splitAmounts(amount).map { ... }` would take the lock N times per
     * mint — wasteful for both contention and disk writes.
     */
    suspend fun nextSecrets(
        keysetId: String,
        count: Int,
    ): List<DerivedSecret> = derive(reserve(keysetId, count), count)

    /** Convenience for ops that need a single output. */
    suspend fun nextSecret(keysetId: String): DerivedSecret = nextSecrets(keysetId, 1).first()
}

/**
 * Random secret + random blinding factor. No deterministic recovery —
 * losing the kind:7375 token events is permanent funds loss. Suitable
 * as a fallback when no seed is available (e.g. a wallet that
 * pre-dates the NUT-13 wiring).
 */
object RandomSecretFactory : SecretFactory {
    /** Nothing to reserve: random secrets keep no durable state. */
    override suspend fun reserve(
        keysetId: String,
        count: Int,
    ): SecretReservation = SecretReservation(keysetId, null)

    override fun derive(
        reservation: SecretReservation,
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
     * returns the FIRST one — derivation then runs at indices
     * `[returned .. returned+count)`. Persisting in one shot avoids the
     * lock-N-times-per-mint waste of a per-secret API.
     *
     * Suspends: it must reach disk before the secrets are used.
     * `AccountSettings.reserveCashuCounters(keysetId, count)` is the
     * canonical implementation.
     */
    private val reserveCounters: suspend (keysetId: String, count: Int) -> Long,
    private val fallback: SecretFactory = RandomSecretFactory,
) : SecretFactory {
    /**
     * With no seed yet — the wallet has not decrypted its kind:17375 — this
     * reserves nothing and reports a random batch, so counters are not burned
     * for secrets that will not be derived from them.
     */
    override suspend fun reserve(
        keysetId: String,
        count: Int,
    ): SecretReservation {
        require(count > 0) { "Must request at least one secret" }
        seedProvider() ?: return SecretReservation(keysetId, null)
        return SecretReservation(keysetId, reserveCounters(keysetId, count))
    }

    override fun derive(
        reservation: SecretReservation,
        count: Int,
    ): List<DerivedSecret> {
        require(count > 0) { "Must request at least one secret" }
        val first = reservation.firstCounter ?: return fallback.derive(reservation, count)

        // Re-read rather than capturing the seed in the reservation, which
        // would carry it through a public data class. If the seed vanished
        // between reserving and deriving, this batch falls back to random and
        // the reserved counters go unused — harmless, because counters only
        // ever move forward and an unused one is never replayed.
        val seed = seedProvider() ?: return fallback.derive(SecretReservation(reservation.keysetId, null), count)

        return List(count) { offset ->
            val counter = first + offset
            // CashuDeterministic.secretBytes returns the raw 32 bytes;
            // the hex form is what BDHKE/proof storage actually use.
            val secretHex = CashuDeterministic.secretBytes(seed, reservation.keysetId, counter).toHexKey()
            val r = CashuDeterministic.blindingFactor(seed, reservation.keysetId, counter)
            DerivedSecret(secretHex, r)
        }
    }
}
