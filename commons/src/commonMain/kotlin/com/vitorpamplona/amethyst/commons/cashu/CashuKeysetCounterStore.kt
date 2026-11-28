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
package com.vitorpamplona.amethyst.commons.cashu

/**
 * Durable NUT-13 deterministic-secret counter store, keyed by mint keyset id.
 *
 * Cashu NUT-13 derives blinded secrets from the wallet seed plus a per-keyset
 * monotonically increasing counter. Reusing a counter makes the mint reply
 * `outputs already signed`, so every reservation MUST be persisted **before**
 * the blinded outputs hit the mint. Implementations therefore make
 * [reserve] atomic and durable. They suspend because durable storage on
 * every target this runs on is a suspending API.
 *
 * - Android backs this with `AccountSettings` / `CashuPreferences`.
 * - `amy` backs this with `~/.amy/<account>/cashu.json`.
 *
 * `CashuWalletOps` consumes the two operations as plain function references
 * ([peek] / [reserve]); this interface gives both hosts one named contract.
 */
interface CashuKeysetCounterStore {
    /** The next counter for [keysetId] without advancing it (0 if unseen). */
    suspend fun peek(keysetId: String): Long

    /**
     * Atomically reserve [count] consecutive counters for [keysetId] and
     * return the first reserved index. Persists before returning.
     */
    suspend fun reserve(
        keysetId: String,
        count: Int,
    ): Long

    /**
     * Carry a counter forward from a host's pre-existing storage, never backwards.
     *
     * The default advances through [reserve] so the durability guarantee above is
     * kept whatever the backing store; a host whose store can write the value in
     * one atomic op should override it.
     */
    suspend fun seedIfMissing(
        keysetId: String,
        legacyValue: Long,
    ) {
        if (legacyValue <= 0L) return
        val current = peek(keysetId)
        if (current >= legacyValue) return
        reserve(keysetId, (legacyValue - current).toInt())
    }

    /**
     * Whether this install has already aligned its counter for [keysetId] with the mint, or
     * found that the mint gives it no way to. False until [markSettled].
     *
     * The NUT-13 seed belongs to the wallet; the counter belongs to the install. A reinstall or
     * a second device starts the counter at 0 and derives the very outputs the mint signed for
     * the first, which it refuses. The wallet settles that once per keyset
     * (`CashuWalletOps.settleCounter`) and remembers it here, so later starts don't ask the mint
     * again. Per keyset, because one the mint rotates in starts a fresh counter that no install
     * has aligned yet.
     */
    suspend fun isSettled(keysetId: String): Boolean

    /** Records that [isSettled] answers true for [keysetId] from now on. Persists before returning. */
    suspend fun markSettled(keysetId: String)
}

/**
 * The stand-in for a host that has not wired a durable store.
 *
 * It throws rather than answering 0: a zeroed counter is a *reused* counter, and
 * the mint answers `outputs already signed` and strands the wallet. Failing at the
 * first reservation is recoverable; silently handing out slot 0 again is not.
 */
object UnavailableCashuKeysetCounterStore : CashuKeysetCounterStore {
    private fun fail(): Nothing = error("No durable NUT-13 counter store is wired for this account; refusing to reuse counters.")

    override suspend fun peek(keysetId: String): Long = fail()

    override suspend fun reserve(
        keysetId: String,
        count: Int,
    ): Long = fail()

    /** Nothing is ever reserved here, so no slot can be reused: there is nothing to settle. */
    override suspend fun isSettled(keysetId: String): Boolean = true

    override suspend fun markSettled(keysetId: String) = Unit
}
