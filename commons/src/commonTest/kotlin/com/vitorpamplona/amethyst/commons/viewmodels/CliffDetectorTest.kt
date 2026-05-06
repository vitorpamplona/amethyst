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
package com.vitorpamplona.amethyst.commons.viewmodels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * Unit tests for the [computeStalledSpeakers] predicate that drives the
 * listener-side cliff detector in [NestViewModel]. The predicate is
 * extracted as a pure function specifically so we can drive it with a
 * [TestTimeSource] — neither `runTest`'s virtual scheduler nor a real
 * monotonic clock would let us deterministically place events relative
 * to the 4 s `ROOM_AUDIO_CLIFF_TIMEOUT_MS` threshold.
 *
 * The detector defends against the moq-rs production-relay forward-
 * queue cliff documented in
 * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`:
 * the relay opens fewer (or zero) new uni streams to a slow
 * subscriber while the announce stream still says the broadcast is
 * Active and the subscribe bidi is still alive at QUIC level — no
 * other layer notices. These tests pin the recycle gate so a future
 * refactor doesn't accidentally widen or narrow when we recycle.
 */
@OptIn(ExperimentalTime::class)
class CliffDetectorTest {
    @Test
    fun returnsEmptyWhenNoSpeakersActive() {
        val ts = TestTimeSource()
        val result =
            computeStalledSpeakers(
                activeSpeakers = emptySet(),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to ts.markNow()),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSpeakerNotInAnnounced() {
        // A speaker we're subscribed to but who isn't in the relay's
        // announce-stream output is most likely either (a) already
        // off stage and the relay just hasn't propagated the Ended
        // yet, or (b) in the brief gap between an Ended and the
        // wrapper re-issuing. Recycling the whole transport here
        // would be wasteful — we only act on confirmed cliffs.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 10_000.milliseconds // way past threshold

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = emptySet(),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSpeakerWithNoFrameYet() {
        // Brand-new subscription that hasn't ramped up: lastFrameAt
        // has no entry. Don't recycle — the wrapper's per-speaker
        // re-issue + opener-throws backoff already retries on its
        // own; the cliff detector only acts once we've proven the
        // subscription was working.
        val ts = TestTimeSource()
        ts += 10_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = emptyMap(),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyJustBeforeThreshold() {
        // Boundary case: 1 ms under the 2.5 s threshold. The detector
        // should still consider this healthy. Relevant because audio
        // groups arrive at ~1 s cadence (framesPerGroup=50), so a
        // false positive at ≈ threshold would recycle on every
        // group-rollover hiccup.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 2_499.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsStalledSpeakerAtThresholdInclusive() {
        // Exactly at threshold: include. The check is `>=`, not `>`.
        // Tested explicitly because boundary semantics here are
        // user-visible (this is the "audio went silent" detection).
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 2_500.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun returnsStalledSpeakerWellPastThreshold() {
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 30_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun returnsAllStalledSpeakersAtOnceMixedWithFreshOnes() {
        // Multi-speaker stage: alice and bob have stalled, charlie's
        // subscription is fresh (just received a frame). Recycle
        // should fire for the stalled set and leave charlie alone —
        // the `recycleSession` itself takes the whole listener down,
        // but the test here is the predicate's classification.
        // Threshold = 2.5 s default. Spacings: alice/bob 4 s old (past),
        // charlie 1.5 s old (under).
        val ts = TestTimeSource()
        val aliceFrame = ts.markNow()
        val bobFrame = ts.markNow()
        ts += 2_500.milliseconds
        val charlieFrame = ts.markNow()
        ts += 1_500.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE, BOB, CHARLIE),
                announcedSpeakers = setOf(ALICE, BOB, CHARLIE),
                lastFrameAt =
                    mapOf(
                        ALICE to aliceFrame,
                        BOB to bobFrame,
                        CHARLIE to charlieFrame,
                    ),
                lastRecycleAt = null,
            )
        assertEquals(setOf(ALICE, BOB), result.toSet())
    }

    @Test
    fun cooldownSuppressesRecycleEvenWhenStalled() {
        // After a recycle, the wrapper opens a fresh QUIC transport.
        // The new session has no `lastFrameAt` entries yet for any
        // pubkey; without a cooldown we would re-trigger on the
        // very next 1 s tick because the prior tick's stalled
        // pubkeys still age past the threshold (their lastFrameAt
        // hasn't been updated by the new session yet). 30 s cooldown
        // covers the typical reconnect handshake AND gives moq-rs
        // time to drain its per-subscriber forward queue from the
        // prior subscription before the new subscribe lands.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds // past threshold
        val recycleMark = ts.markNow() // recycle just fired
        ts += 5_000.milliseconds // 5 s into cooldown — well within 30 s window

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun cooldownReleasesAfterTimeoutPasses() {
        // Once cooldown elapses, a still-stalled subscription
        // becomes eligible to recycle again. Important for the
        // case where the recycle didn't actually fix the cliff —
        // we want a second attempt rather than getting wedged.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 30_001.milliseconds // 1 ms past 30 s cooldown

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun customTimeoutsAreHonored() {
        // Defaults are wired into the production VM, but the function
        // accepts overrides so a test for tighter / looser tolerances
        // can drive it without mutating the constants.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 1_000.milliseconds

        val withDefault =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertTrue(withDefault.isEmpty(), "1 s elapsed shouldn't trip 2.5 s default")

        val withTightTimeout =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
                cliffTimeoutMs = 500L,
            )
        assertEquals(listOf(ALICE), withTightTimeout, "1 s elapsed should trip a 500 ms threshold")
    }

    @Test
    fun activeSpeakerNotInLastFrameAtIsIgnoredEvenWithStalledPeers() {
        // Mixed: alice is announced + active + stalled, bob is
        // announced + active but has no frame yet. Detector should
        // return alice only — bob hasn't proven the subscription
        // works, so it's not "stalled" yet, just slow to start.
        val ts = TestTimeSource()
        val aliceFrame = ts.markNow()
        ts += 5_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE, BOB),
                announcedSpeakers = setOf(ALICE, BOB),
                lastFrameAt = mapOf(ALICE to aliceFrame),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun nullLastRecycleNeverSuppresses() {
        // Null `lastRecycleAt` means "we've never recycled" — the
        // very first cliff event of a session must fire. The
        // cooldown check is gated on non-null.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 5_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    companion object {
        private val ALICE = "a".repeat(64)
        private val BOB = "b".repeat(64)
        private val CHARLIE = "c".repeat(64)
    }
}
