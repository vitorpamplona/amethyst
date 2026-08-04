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
package com.vitorpamplona.quartz.nip01Core.store

/**
 * The machine-readable insert-rejection vocabulary, shared by every
 * [IEventStore] implementation so the same condition always rejects with the
 * same words — a caller tallying [IEventStore.InsertOutcome.Rejected] reasons,
 * or a relay building `OK false` frames, must never see two stores spell
 * "duplicate" differently.
 *
 * NIP-01: an `OK false` message SHOULD begin with a single-word
 * machine-readable prefix followed by `:`. The full-sentence constants here
 * are the standard reasons the built-in stores emit. `replaced:` is not in
 * NIP-01 but is the de-facto prefix (strfry and others) for a replaceable
 * event that lost to a stored newer version — distinct from `duplicate:`
 * (the exact event is already held).
 */
object RejectionReason {
    /**
     * The NIP-01 prefix vocabulary itself lives in
     * [com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix]
     * — use its `format`/`parse` instead of hand-writing prefixes. The one
     * prefix that enum lacks is the de-facto (not in NIP-01) word for a stale
     * version of a replaceable/addressable event:
     */
    const val PREFIX_REPLACED = "replaced:"

    // The standard store reasons.
    const val DUPLICATE = "duplicate: already have this event"
    const val EXPIRED = "blocked: Cannot insert an expired event"
    const val DELETED = "blocked: a deletion event exists"
    const val VANISHED = "blocked: a request to vanish event exists"
    const val REPLACED = "replaced: a newer version exists"
    const val INSERT_FAILED = "error: insert failed"
}
