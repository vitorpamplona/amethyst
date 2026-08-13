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
package com.vitorpamplona.quartz.nip01Core.relay.client.auth

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How a relay's NIP-42 challenge ended, for a caller that was refused `auth-required:`. */
enum class AuthOutcome {
    /**
     * No NIP-42 responder is attached to this client, so the challenge will never be
     * answered. Waiting is pure dead time — the refusal is as terminal as it looks.
     */
    NO_RESPONDER,

    /**
     * The relay accepted our AUTH. The client has re-sent every filter on that
     * connection ([INostrClient.syncFilters], driven from the AUTH's `OK`), so the
     * refused REQ is live again and its events are on their way.
     */
    AUTHENTICATED,

    /**
     * We are not getting in — at least not within the time the caller had. Either nobody
     * took the challenge up (the responder declined this relay, the signer timed out, the
     * user dismissed the prompt), an AUTH was sent and the relay answered `OK false`, or
     * the signing was still unfinished when the caller's own window ran out.
     *
     * All three are one verdict on purpose, because they are one fact for the caller: it
     * met a NIP-42 wall and did not get over it. Distinct from silence either way — the
     * relay is alive and answering, it just will not serve *us*.
     */
    REFUSED,
}

/** True when something on this client will actually answer a relay's AUTH challenge. */
fun INostrClient.hasAuthResponder(): Boolean = authResponders().isNotEmpty()

/**
 * The number of AUTHs accepted on [relay] so far, across every responder — see
 * [RelayAuthSnapshot.successCount].
 *
 * Take this **before subscribing**, and hand it back to [awaitAuthOutcome] as `since`. It
 * is what turns "are we authenticated?" (unanswerable — the connection may have been
 * authenticated for an hour and be refusing us for an unrelated reason) into "did an AUTH
 * land since we asked?", which is exactly the condition under which our REQ was re-sent.
 */
fun INostrClient.authSuccessMark(relay: NormalizedRelayUrl): Int = authResponders().successMark(relay)

/**
 * The marks for a whole fan-out, as a map to be read with `marks[relay] ?: 0`.
 *
 * Only relays that have ALREADY authenticated get an entry: a relay whose mark is zero is
 * indistinguishable from an absent one, and at fan-out sizes where this matters (a router
 * sweeping thousands of urls per fetch) nearly every relay is at zero when the REQ goes out.
 * Building the full map instead would allocate one entry per relay on every fetch, auth-gated
 * or not, to record a value the lookup already defaults to.
 *
 * Returns an empty map when nothing answers AUTH here, so a caller pays nothing at all.
 */
fun INostrClient.authSuccessMarks(relays: Collection<NormalizedRelayUrl>): Map<NormalizedRelayUrl, Int> {
    val responders = authResponders()
    if (responders.isEmpty()) return emptyMap()
    var marks: MutableMap<NormalizedRelayUrl, Int>? = null
    for (relay in relays) {
        val mark = responders.successMark(relay)
        if (mark != 0) {
            (marks ?: HashMap<NormalizedRelayUrl, Int>().also { marks = it })[relay] = mark
        }
    }
    return marks ?: emptyMap()
}

/**
 * Suspends until this relay's NIP-42 challenge resolves one way or the other, for a
 * caller whose REQ (or COUNT) just came back `CLOSED auth-required:`.
 *
 * This is what lets an auth-gated fetch end on the AUTH's *verdict* rather than on a
 * timeout. Without it the only exit is the full idle window, so a relay that will never
 * let us in costs exactly as much as one that is merely slow — and the caller cannot
 * tell which it met, because both produce the same silence.
 *
 * The wait is in two stages, because "no AUTH is in flight" means two opposite things
 * depending on when you look:
 *
 *  1. **Pick-up ([graceMs], bounded).** Wait for a responder to actually start work —
 *     [RelayAuthSnapshot.inFlight], i.e. signing or awaiting the relay's `OK`. A short
 *     bound is needed because the CLOSED reaches subscription listeners *before*
 *     connection listeners (see [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient.onIncomingMessage]),
 *     so a re-auth about to be signed can be a scheduling hop behind us. Nothing in
 *     flight once the grace expires is a real answer, not impatience: it also covers the
 *     connection that is *already* AUTHENTICATED and still refused — a relay restricting
 *     this particular query — where no further AUTH is coming and waiting out the idle
 *     window would buy nothing.
 *  2. **Settle ([settleMs]).** Once someone is working, wait for them to finish. Far more
 *     generous than the grace, because this is where a NIP-55 or NIP-46 signer holds a
 *     prompt in front of a human: cutting that short would fail an AUTH the user is in the
 *     middle of approving. Callers pass their own idle window here, which yields the
 *     guarantee that keeps this safe to turn on by default — **an auth-gated relay costs
 *     at most what a silent one already cost**, never a multiple of it spent waiting on
 *     a prompt nobody answers.
 *
 * Responders are polled as a set: the relay is still working while *any* of them is in
 * flight, and authenticated as soon as *any* of them succeeds — one client can hold an
 * account signer and a derived stream-key signer, and either one getting in is enough.
 *
 * An [IAuthStatus] that answers challenges but does not publish [IAuthStatus.authStateFlow]
 * never reports in-flight, so it resolves [REFUSED] at the end of the grace. That is still
 * strictly better than the pre-existing behaviour (give up the instant the CLOSED lands),
 * and exact for [RelayAuthenticator], which publishes every transition.
 */
suspend fun INostrClient.awaitAuthOutcome(
    relay: NormalizedRelayUrl,
    since: Int,
    graceMs: Long = DEFAULT_AUTH_GRACE_MS,
    settleMs: Long = Long.MAX_VALUE,
): AuthOutcome {
    val responders = authResponders()
    if (responders.isEmpty()) return AuthOutcome.NO_RESPONDER

    // The AUTH may already have landed while the refusal was still in flight towards us —
    // the two cross on the wire constantly, since the challenge is answered on connect and
    // our REQ was refused before that answer arrived. Nothing to wait for: the success has
    // already re-sent our subscription.
    if (responders.successMark(relay) > since) return AuthOutcome.AUTHENTICATED

    val pickedUp =
        withTimeoutOrNull(graceMs) {
            responders.awaitCombined(relay) { it.inFlight || it.successes > since }
        }
    if (pickedUp == null) return AuthOutcome.REFUSED

    withTimeoutOrNull(settleMs) {
        responders.awaitCombined(relay) { !it.inFlight }
    }
    // Read the mark rather than the timeout's own result: a settle that ran out still
    // counts as AUTHENTICATED if the success landed on its way out, and a caller that
    // stopped waiting on an unfinished prompt is REFUSED — see [AuthOutcome.REFUSED].
    return if (responders.successMark(relay) > since) AuthOutcome.AUTHENTICATED else AuthOutcome.REFUSED
}

/** Default stage-one grace — a scheduling hop's worth, not a user-facing wait. */
const val DEFAULT_AUTH_GRACE_MS = 1_000L

/**
 * Several responders' views of one relay, folded into the two facts a waiting caller needs.
 * Folded rather than ranked: one responder having failed says nothing about another still
 * signing, and any single identity getting in is enough to re-open the subscription.
 */
private data class CombinedAuth(
    val inFlight: Boolean,
    val successes: Int,
)

private fun Set<IAuthStatus>.snapshots(relay: NormalizedRelayUrl) = map { it.authSnapshot(relay) }

private fun Set<IAuthStatus>.successMark(relay: NormalizedRelayUrl) = snapshots(relay).sumOf { it.successCount }

/**
 * Suspends until [relay]'s combined state satisfies [predicate]. Returns immediately when it
 * already does — every [IAuthStatus.authStateFlow] is a `StateFlow`, so the combine replays
 * the current value before any change.
 */
private suspend fun Set<IAuthStatus>.awaitCombined(
    relay: NormalizedRelayUrl,
    predicate: (CombinedAuth) -> Boolean,
): CombinedAuth =
    combine(map { it.authStateFlow }) { states ->
        val snaps = states.map { it[relay] ?: RelayAuthSnapshot.IDLE }
        CombinedAuth(
            inFlight = snaps.any { it.inFlight },
            successes = snaps.sumOf { it.successCount },
        )
    }.first(predicate)
