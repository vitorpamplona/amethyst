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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.authSuccessMark
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * End-to-end NIP-42 behaviour of the fetch accessories against a relay that really
 * does gate reads behind AUTH: quartz's own
 * [com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer] under
 * [com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy], driven
 * over the in-process socket by a real
 * [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient] with a real
 * [RelayAuthenticator] attached — the exact arrangement Amethyst and any mirroring
 * router run. Nothing here is scripted: the CLOSED, the challenge, the signature, the
 * OK and the post-auth re-REQ are all produced by production code. See
 * [AuthGatedRelayHarness].
 *
 * What these pin, measured on this harness with an instant signer (so the numbers
 * are the accessories' own cost, not the 300 ms the tests below now deliberately
 * add to force the refusal to happen first):
 *
 * | accessory                      | before             | after                |
 * |--------------------------------|--------------------|----------------------|
 * | `fetchAll`                     | 0 events, 18 ms    | 5 events, 17 ms      |
 * | `fetchFirst`                   | null, 13 ms        | the event, 18 ms     |
 * | `fetchAllPages`                | `CLOSED`, 20 ms    | 5 events, 23 ms      |
 * | unsatisfiable auth, 2 s window | no reason at all, 2 s | `auth-refused:…`, 1 s |
 *
 * The "before" column is a give-up, not a wait: the `auth-required:` CLOSED was
 * terminal, so every one of them returned in tens of milliseconds *while the AUTH
 * it needed was still in flight on the very same socket*. The events did arrive —
 * just at a caller that had already returned empty.
 */
class Nip42AuthGatedFetchTest {
    private val relay: NormalizedRelayUrl = AuthGatedRelayHarness.URL

    private fun filter() = listOf(Filter(kinds = listOf(1), limit = 50))

    /**
     * A responder that answers correctly, but not instantly. The delay is load-bearing:
     * it guarantees the REQ is refused with `auth-required:` before the AUTH lands, which
     * is the path every one of these tests is about. See [AuthGatedRelayHarness.signDelayMs].
     */
    private fun workingSigner() = AuthGatedRelayHarness(signer = NostrSignerInternal(KeyPair()), signDelayMs = 300)

    /** A responder that is attached but will never satisfy this relay (an ignored signer prompt). */
    private fun decliningSigner() = AuthGatedRelayHarness(signer = null, signDelayMs = 300)

    // ------------------------------------------------------------------ (a) + (b)

    @Test
    fun fetchAllReadsAnAuthGatedRelayForWhatItHolds() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)
                assertTrue(h.client.hasAuthResponder(), "the authenticator registered itself on the client")

                val events = h.client.fetchAll(relay = relay, filters = filter(), idleTimeoutMs = 4_000)

                assertEquals(5, events.size, "an auth-gated relay must not read as empty when we can authenticate")
            }
        }

    /** The same, through the wrapper that used to be the only way to get this. */
    @Test
    fun fetchAllWithHooksCollectsPostAuthEventsAndRecordsTheEose() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)

                val result =
                    h.client.fetchAllWithHooks(
                        filters = mapOf(relay to filter()),
                        idleTimeoutMs = 4_000,
                    ) { _, _ -> true }

                assertEquals(5, result.events.size)
                assertEquals(DONE_REASON_EOSE, result.doneReasons[relay], "the relay served us after AUTH")
                assertTrue(result.anyRelayServed)
                assertTrue(result.authRefused.isEmpty())
            }
        }

    @Test
    fun fetchFirstReturnsTheEventFromAnAuthGatedRelay() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)
                val event = h.client.fetchFirst(filters = mapOf(relay to filter()), idleTimeoutMs = 4_000)
                assertTrue(event != null, "an auth-gated relay's event must not read as absent")

                // The single-relay shorthand does NOT forward pendingOnAuthRequired — it just calls
                // the map form and lets its default evaluate. Pinned because it is the reason the
                // plumbing is optional: a derived default evaluates against the receiver of the
                // function that DECLARES it, so a wrapper that passes nothing still gets the right
                // answer for the client it was called on.
                assertTrue(
                    h.client.fetchFirst(relay = relay, filter = filter().first()) != null,
                    "a wrapper that forwards nothing still inherits the derived default",
                )
            }
        }

    @Test
    fun countAnswersOnAnAuthGatedRelay() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)
                val result = h.client.count(relay = relay, filter = Filter(kinds = listOf(1)), idleTimeoutMs = 4_000)
                assertEquals(5, result?.count, "a NIP-45 COUNT is auth-gated exactly like a REQ")
            }
        }

    @Test
    fun fetchAllPagesWalksAnAuthGatedRelay() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)
                var seen = 0
                val result = h.client.fetchAllPages(relay = relay, filters = filter(), idleTimeoutMs = 4_000) { seen++ }

                assertEquals(5, seen, "the walk must survive the auth challenge on page one")
                assertEquals(PagedFetchResult.End.DRAINED, result.end, "and then reach a genuinely empty, EOSEd page")
            }
        }

    /**
     * (b): the default is derived from the client, not hardcoded. With no responder
     * attached there is nothing to wait for, so the refusal stays terminal — the
     * pre-existing behaviour, and no new latency for a client that cannot authenticate.
     */
    @Test
    fun withNoResponderTheRefusalIsStillTerminalAndImmediate() =
        runBlocking {
            AuthGatedRelayHarness(attachAuthenticator = false).use { h ->
                h.preload(5)
                assertFalse(h.client.hasAuthResponder())

                val (result, elapsed) =
                    measureTimedValue {
                        h.client.fetchAllWithHooks(
                            filters = mapOf(relay to filter()),
                            idleTimeoutMs = 4_000,
                        ) { _, _ -> true }
                    }

                assertEquals(0, result.events.size)
                // Named even though we never waited: what the relay said does not depend on whether
                // anyone was there to answer it, so `authRefusedRelays()` sees a no-responder client
                // exactly as it sees a declining one.
                assertEquals(setOf(relay), result.authRefused, "was ${result.doneReasons[relay]}")
                assertTrue(elapsed.inWholeMilliseconds < 1_000, "no waiting when nobody can answer; was $elapsed")
            }
        }

    /**
     * An explicit `false` still forces the old behaviour even with a responder attached:
     * the `auth-required:` CLOSED ends the relay on the spot.
     *
     * Asserted on the terminal REASON rather than on an empty result. Ending the relay is
     * what opting out means; whether zero events come back is a race the caller does not
     * control — the client re-fires the refused REQ on the AUTH's OK regardless, and on a
     * transport this fast those events can still land in the post-loop drain before the
     * fetch returns. On a real socket they arrive milliseconds too late, which is the whole
     * defect. Pinning the reason pins the decision; pinning the count would pin the race.
     */
    @Test
    fun explicitFalseKeepsTheRefusalTerminal() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)
                val result =
                    h.client.fetchAllWithHooks(
                        filters = mapOf(relay to filter()),
                        idleTimeoutMs = 4_000,
                        pendingOnAuthRequired = false,
                    ) { _, _ -> true }

                assertEquals(
                    setOf(relay),
                    result.authRefused,
                    "opting out ends the relay on the CLOSED itself, but still names the wall; was ${result.doneReasons[relay]}",
                )
            }
        }

    // ---------------------------------------------------------------------- (c) + (d)

    /**
     * (c) and (d) together. The responder is attached but declines this relay, so the
     * AUTH can never satisfy. The fetch must end on that verdict — inside the grace, not
     * at the 8 s idle window — and must SAY so: an unsatisfied auth wall is its own
     * terminal reason, not the absence of one.
     */
    @Test
    fun anUnsatisfiedAuthEndsOnItsVerdictAndIsNamed() =
        runBlocking {
            decliningSigner().use { h ->
                h.preload(5)

                val (result, elapsed) =
                    measureTimedValue {
                        h.client.fetchAllWithHooks(
                            filters = mapOf(relay to filter()),
                            idleTimeoutMs = 8_000,
                        ) { _, _ -> true }
                    }

                assertEquals(0, result.events.size)
                assertTrue(
                    result.doneReasons[relay]?.startsWith(DONE_REASON_AUTH_REFUSED) == true,
                    "the relay must leave a terminal reason naming the auth wall; was ${result.doneReasons[relay]}",
                )
                assertEquals(setOf(relay), result.authRefused)
                assertFalse(result.anyRelayServed, "an auth wall is not a relay that served us")
                assertEquals(DrainFailure.AUTH_REQUIRED, result.dead[relay])
                assertFalse(result.dead[relay]!!.dropFromRouting, "auth-gated is fixable by a signer, not by dropping the relay")
                assertTrue(
                    elapsed.inWholeMilliseconds < 4_000,
                    "must end on the AUTH's verdict, not the 8 s idle window; was $elapsed",
                )
            }
        }

    /** (d) for the paging walk: its own `End`, never lumped in with rate limits and policy. */
    @Test
    fun anUnsatisfiedAuthEndsThePagedWalkAsAuthRequired() =
        runBlocking {
            decliningSigner().use { h ->
                h.preload(5)
                var seen = 0
                val (result, elapsed) =
                    measureTimedValue {
                        h.client.fetchAllPages(relay = relay, filters = filter(), idleTimeoutMs = 8_000) { seen++ }
                    }

                assertEquals(0, seen)
                assertEquals(
                    PagedFetchResult.End.AUTH_REQUIRED,
                    result.end,
                    "an auth wall must not read as CLOSED — they want different things from the caller",
                )
                assertFalse(result.drained, "nothing was proven about what the relay holds")
                assertTrue(elapsed.inWholeMilliseconds < 4_000, "bounded by the AUTH, not the idle window; was $elapsed")
            }
        }

    /** And a COUNT gives up on the same verdict rather than sitting out its window. */
    @Test
    fun anUnsatisfiedAuthEndsTheCountEarly() =
        runBlocking {
            decliningSigner().use { h ->
                h.preload(5)
                val (result, elapsed) =
                    measureTimedValue {
                        h.client.count(relay = relay, filter = Filter(kinds = listOf(1)), idleTimeoutMs = 8_000)
                    }

                assertNull(result)
                assertTrue(elapsed.inWholeMilliseconds < 4_000, "bounded by the AUTH, not the idle window; was $elapsed")
            }
        }

    // ------------------------------------------------------------------------- (e)

    /**
     * (e): AUTH is per-connection, so the second call on an already-authenticated socket
     * is never refused at all — no caller-side retry loop is needed, and none should be
     * written. The first call pays the challenge; the rest are ordinary fetches.
     */
    @Test
    fun aSecondFetchOnAnAuthenticatedConnectionIsNotRefusedAgain() =
        runBlocking {
            workingSigner().use { h ->
                h.preload(5)

                assertEquals(5, h.client.fetchAll(relay = relay, filters = filter(), idleTimeoutMs = 4_000).size)
                val afterFirst = h.client.authSuccessMark(relay)
                assertTrue(afterFirst >= 1, "the first fetch got in, so an AUTH must have been accepted")

                val second =
                    h.client.fetchAllWithHooks(
                        filters = mapOf(relay to filter()),
                        idleTimeoutMs = 4_000,
                    ) { _, _ -> true }

                assertEquals(5, second.events.size)
                assertEquals(
                    DONE_REASON_EOSE,
                    second.doneReasons[relay],
                    "the second REQ is served outright — no auth-required, so nothing for a caller to retry",
                )
                // The point of (e): the SECOND fetch cost no authentication at all. Asserted as
                // "the mark did not move", not "the mark is 1" — how many AUTHs the first fetch
                // took is a property of the responder, not of this contract, and pinning it here
                // makes an unrelated (pre-existing) race in RelayAuthStatus.saveAuthSubmission's
                // check-then-put fail this test under load.
                assertEquals(afterFirst, h.client.authSuccessMark(relay), "the second fetch needed no AUTH of its own")
            }
        }
}
