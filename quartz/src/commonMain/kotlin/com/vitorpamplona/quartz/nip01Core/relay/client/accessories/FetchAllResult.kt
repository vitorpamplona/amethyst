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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Everything one [fetchAllWithHooks] call learned: the events, and — just as
 * important — what each relay *did*, so an empty [events] can be told apart from
 * an unanswered query.
 *
 * This replaces three parameters that used to smuggle the same facts back out
 * through the argument list (`deadOut`, `doneOut`, `onTimeout`). They were the
 * wrong shape for what they carried:
 *  - `deadOut` was never independent information. It is [doneReasons] run through
 *    [classifyDrainFailure], which [dead] now does on demand — so a caller can no
 *    longer receive one without the other, or read a stale half.
 *  - `onTimeout` fired with `(stalled, doneReasons, collected)`, all three of which
 *    are simply this object. A callback that only ever reports what the return
 *    value already holds is a return value taking the long way round; callers now
 *    test [stalled] after the call, at the point where they act on it.
 *  - Out-parameters force a caller to allocate a mutable map *before* the call and
 *    hope the callee filled it, which reads as optional and is not: [doneReasons]
 *    is what stops a read-merge-write from overwriting a replaceable event it
 *    failed to read.
 *
 * Nothing here is populated if the call is cancelled — the stack unwinds instead of
 * returning. See [fetchAllWithHooks] for what that costs.
 */
class FetchAllResult(
    /** Accepted `(relay, event)` pairs, in arrival order and tagged by delivering relay. */
    val events: List<Pair<NormalizedRelayUrl, Event>>,
    /**
     * Per-relay terminal reason — `"eose"`, `"closed:<msg>"`, `"cannot:<msg>"`, or
     * `"auth-refused:<msg>"` — so a caller can tell "a relay served us and had nothing"
     * from "nobody served us". An empty [events] alone cannot: both look like zero
     * events, and treating the second as the first is how a read-merge-write on a
     * replaceable event destroys the entries it failed to read.
     *
     * A relay with NO entry here is exactly one thing — nobody told us, it is in
     * [stalled] — and never "auth-gated": that case has a reason of its own.
     */
    val doneReasons: Map<NormalizedRelayUrl, String>,
    /**
     * Relays that never reached a terminal state before the idle window elapsed —
     * they have no [doneReasons] entry. Empty when every relay finished, so
     * `stalled.isNotEmpty()` is the "this fetch timed out" test that the old
     * `onTimeout` callback existed to signal.
     */
    val stalled: Set<NormalizedRelayUrl>,
) {
    /**
     * Relays whose terminal reason classifies as a failure worth acting on, derived
     * from [doneReasons] via [classifyDrainFailure]. Slow relays and 429s are absent
     * by design — they recover, and dropping them re-learns nothing.
     *
     * Test [DrainFailure.dropFromRouting] rather than comparing to `DEAD` before
     * pruning anything: an [DrainFailure.AUTH_REQUIRED] relay is alive and serves the
     * same query to a connection carrying an identity it accepts.
     */
    val dead: Map<NormalizedRelayUrl, DrainFailure> by lazy {
        buildMap {
            for ((relay, reason) in doneReasons) {
                classifyDrainFailure(reason)?.let { put(relay, it) }
            }
        }
    }

    /**
     * True when at least one relay completed normally, i.e. answered and reached EOSE.
     * An empty [events] means "nothing matched" only when this is true; otherwise it
     * means "nobody told us".
     *
     * Computed per access rather than cached: it short-circuits on the first EOSE and
     * allocates nothing, so a `lazy` holder would cost more than the scan it saves.
     * The two views that DO allocate ([dead], [authRefused]) are cached instead.
     */
    val anyRelayServed: Boolean get() = doneReasons.anyRelayServed()

    /**
     * The relays that turned us away at a NIP-42 wall — see [DONE_REASON_AUTH_REFUSED].
     * Emphatically not dead relays: they answered, and will serve the same query on a
     * connection carrying an identity they accept.
     */
    val authRefused: Set<NormalizedRelayUrl> by lazy { doneReasons.authRefusedRelays() }
}
