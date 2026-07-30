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
package com.vitorpamplona.amethyst.commons.model.chats

/**
 * Why this account may or may not post in a chat room — the **typed reason** behind what used to be a
 * bare `canPost()` boolean.
 *
 * A boolean can only hide the composer; it cannot say why, so every new gate silently degraded into
 * blank space above the keyboard. (That is exactly how a banned Concord member came to get an empty
 * slot and no explanation: the screen's only explanatory branch tested `dissolved`.) Modeling the
 * reason as a sealed type moves that from a convention to a compiler guarantee — the `when` that
 * renders [Blocked] does not compile until a newly added reason is given copy of its own.
 *
 * Each channel type derives its own gate (`ConcordChannel.postingGate()`,
 * `RelayGroupChannel.postingGate()`) and defines `canPost()` **in terms of it**, so the answer and the
 * explanation cannot drift apart.
 *
 * Only reasons a protocol can actually produce today are modeled here. A Buzz tenant ban (kind 9040)
 * or timeout (9042) is *not* among them: the relay decides at publish time and reports it in an
 * `OK false` that no send path currently surfaces, so there is no local state to derive it from. When
 * that receipt is tracked, this hierarchy gains the matching reason and the `when` will demand copy
 * for it.
 */
sealed interface PostingGate {
    /** The composer is shown: this account's write would be accepted. */
    data object Allowed : PostingGate

    /**
     * The composer is hidden and this reason takes its place. Every subtype must be renderable —
     * a [Blocked] gate with nothing to say is the bug this type exists to prevent.
     */
    sealed interface Blocked : PostingGate

    /**
     * On the community banlist (Concord CORD-04). Standing is gone, but held keys still decrypt, so
     * history stays readable — a ban is not an eviction.
     */
    data object Banned : Blocked

    /**
     * Sealed read-only by an owner-signed `DISSOLVED` tombstone (Concord CORD-02 §9). Terminal and
     * one-way, and it outranks every role: not even the owner may post after it.
     */
    data object Dissolved : Blocked

    /**
     * We cannot place this account in the community at all — no key, no role. Distinct from
     * [NotAMember] in that there is no roster to join and no relay to ask: in Concord, holding the
     * key *is* membership.
     */
    data object NoKey : Blocked

    /**
     * Not in the relay-signed roster of a membership-gated NIP-29 group (kinds 39001/39002). The
     * relay would reject the write, and the fix is the top bar's Join action.
     */
    data object NotAMember : Blocked

    /**
     * Same as [NotAMember], except the group is `closed`, so a join request would be ignored — an
     * invite is the only way in.
     */
    data object InviteOnly : Blocked
}
