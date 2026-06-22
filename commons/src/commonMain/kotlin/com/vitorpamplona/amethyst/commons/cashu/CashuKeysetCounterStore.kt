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
 * [reserve] atomic and durable.
 *
 * - Android backs this with `AccountSettings` / `CashuPreferences`.
 * - `amy` backs this with `~/.amy/<account>/cashu.json`.
 *
 * `CashuWalletOps` consumes the two operations as plain function references
 * ([peek] / [reserve]); this interface gives both hosts one named contract.
 */
interface CashuKeysetCounterStore {
    /** The next counter for [keysetId] without advancing it (0 if unseen). */
    fun peek(keysetId: String): Long

    /**
     * Atomically reserve [count] consecutive counters for [keysetId] and
     * return the first reserved index. Persists before returning.
     */
    fun reserve(
        keysetId: String,
        count: Int,
    ): Long
}
