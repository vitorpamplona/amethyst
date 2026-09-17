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
package com.vitorpamplona.amethyst.service.playback.coordinator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ownership rules a checked-out player lives by. Every case here is written to fail in one of
 * exactly two directions, because those are the two real failure modes:
 *
 *  - **released twice** — the decoder budget under-counts and the app hands out more concurrent
 *    MediaCodec instances than the device grants, which surfaces as "can't play this video";
 *  - **never released** — a decoder and its buffer leak for the life of the process.
 *
 * So the assertions are on the exact multiset of releases, never just "was it released".
 */
class VideoPlaybackCoordinatorTest {
    /** A checkout is just an identity here; the pool it stands for is irrelevant to the rules. */
    private class Checkout(
        val name: String,
    ) {
        override fun toString() = name
    }

    private val acquired = mutableListOf<VideoRequest>()
    private val released = mutableListOf<Checkout>()
    private var next = 0

    private val coordinator =
        VideoPlaybackCoordinator<Checkout>(
            acquire = {
                acquired.add(it)
                Checkout("c${++next}")
            },
            release = { released.add(it) },
        )

    private fun request(mediaId: String = "video-1") = VideoRequest(proxyPort = 0, mediaId = mediaId, repeatMode = true)

    private fun releasedNames() = released.map { it.name }

    @Test
    fun aSlotReleasesItsCheckoutWhenItGoesAway() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())

        coordinator.detach(slot)

        assertEquals(listOf(checkout.name), releasedNames())
    }

    @Test
    fun reAttachingTheSameSlotReusesTheCheckoutInsteadOfAcquiringAgain() {
        val slot = Any()
        val first = coordinator.attach(slot, request())
        val second = coordinator.attach(slot, request())

        assertSame(first, second)
        assertEquals(1, acquired.size)

        // Still exactly one release for the one checkout.
        coordinator.detach(slot)
        assertEquals(listOf(first.name), releasedNames())
    }

    @Test
    fun detachingAnUnknownSlotIsANoOp() {
        coordinator.detach(Any())
        assertEquals(emptyList<String>(), releasedNames())
    }

    // The case the hand-off exists for: the video leaves the screen while it is playing in the
    // picture-in-picture window. Releasing here would pull the player out from under the window.
    @Test
    fun aPromotedCheckoutSurvivesItsSlotGoingAway() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())
        coordinator.promote(checkout)

        coordinator.detach(slot)

        assertEquals(emptyList<String>(), releasedNames())
        assertSame(checkout, coordinator.promoted.value)
    }

    @Test
    fun demotingAfterTheSlotIsGoneReleasesExactlyOnce() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())
        coordinator.promote(checkout)
        coordinator.detach(slot)

        coordinator.demote()

        assertEquals(listOf(checkout.name), releasedNames())
        assertNull(coordinator.promoted.value)
    }

    // Closing the window while the video is still on screen hands it straight back to the feed.
    // A release here would be a double release once the slot detaches, and would also throw away a
    // buffer the feed is about to draw from.
    @Test
    fun demotingWhileTheSlotIsStillOnScreenDoesNotRelease() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())
        coordinator.promote(checkout)

        coordinator.demote()

        assertEquals(emptyList<String>(), releasedNames())
        assertFalse(coordinator.isPromoted(checkout))

        coordinator.detach(slot)
        assertEquals(listOf(checkout.name), releasedNames())
    }

    @Test
    fun aDemotedCheckoutIsStillTheOneTheSlotHolds() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())
        coordinator.promote(checkout)
        coordinator.demote()

        // No re-acquire: the slot never stopped holding it, so the video drops back inline with its
        // buffer and position intact.
        assertSame(checkout, coordinator.attach(slot, request()))
        assertEquals(1, acquired.size)
    }

    // The bug this whole type was extracted for. Promoting a second playback used to orphan the
    // first (leaked decoder) in one draft and double-release it in the next.
    @Test
    fun promotingAReplacementReleasesTheDisplacedCheckoutOnceWhenNoSlotHoldsIt() {
        val slotA = Any()
        val slotB = Any()
        val first = coordinator.attach(slotA, request("a"))
        val second = coordinator.attach(slotB, request("b"))

        coordinator.promote(first)
        coordinator.detach(slotA)
        coordinator.promote(second)

        assertEquals(listOf(first.name), releasedNames())
        assertSame(second, coordinator.promoted.value)
    }

    @Test
    fun promotingAReplacementLeavesTheDisplacedCheckoutToItsSlot() {
        val slotA = Any()
        val slotB = Any()
        val first = coordinator.attach(slotA, request("a"))
        val second = coordinator.attach(slotB, request("b"))

        coordinator.promote(first)
        coordinator.promote(second)

        // slotA is still showing `first`, so the promotion change must not release it.
        assertEquals(emptyList<String>(), releasedNames())

        coordinator.detach(slotA)
        assertEquals(listOf(first.name), releasedNames())
    }

    @Test
    fun promotingTheAlreadyPromotedCheckoutChangesNothing() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())
        coordinator.promote(checkout)
        coordinator.promote(checkout)

        assertEquals(emptyList<String>(), releasedNames())
        assertSame(checkout, coordinator.promoted.value)
    }

    @Test
    fun aDetachedPromotionIsAcquiredAndReleasedByThePromotionAlone() {
        val checkout = coordinator.promoteDetached(request())

        assertEquals(1, acquired.size)
        assertSame(checkout, coordinator.promoted.value)

        coordinator.demote()
        assertEquals(listOf(checkout.name), releasedNames())
    }

    @Test
    fun aDetachedPromotionIsReleasedWhenAnotherPlaybackDisplacesIt() {
        val orphan = coordinator.promoteDetached(request("a"))
        val slot = Any()
        val onScreen = coordinator.attach(slot, request("b"))

        coordinator.promote(onScreen)

        assertEquals(listOf(orphan.name), releasedNames())
    }

    @Test
    fun demoteIfOnlyActsOnAMatchingPromotion() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())
        coordinator.promote(checkout)
        coordinator.detach(slot)

        coordinator.demoteIf { it.name == "not-this-one" }
        assertEquals(emptyList<String>(), releasedNames())
        assertSame(checkout, coordinator.promoted.value)

        coordinator.demoteIf { it === checkout }
        assertEquals(listOf(checkout.name), releasedNames())
    }

    @Test
    fun demotingWithNothingPromotedIsANoOp() {
        coordinator.demote()
        coordinator.demoteIf { true }
        assertEquals(emptyList<String>(), releasedNames())
    }

    @Test
    fun isPromotedIsIdentityBasedAndNullSafe() {
        val slot = Any()
        val checkout = coordinator.attach(slot, request())

        assertFalse(coordinator.isPromoted(checkout))
        assertFalse(coordinator.isPromoted(null))

        coordinator.promote(checkout)
        assertTrue(coordinator.isPromoted(checkout))

        // An equal-looking checkout is not the same checkout.
        assertFalse(coordinator.isPromoted(Checkout(checkout.name)))
    }

    @Test
    fun teardownReleasesEveryCheckoutExactlyOnceIncludingTheDoublyHeldOne() {
        val slotA = Any()
        val slotB = Any()
        val promotedAndOnScreen = coordinator.attach(slotA, request("a"))
        val onScreenOnly = coordinator.attach(slotB, request("b"))
        coordinator.promote(promotedAndOnScreen)

        coordinator.releaseAll()

        assertEquals(setOf(promotedAndOnScreen.name, onScreenOnly.name), releasedNames().toSet())
        assertEquals(2, released.size)
        assertNull(coordinator.promoted.value)

        // And nothing lingers to be released a second time.
        coordinator.detach(slotA)
        coordinator.detach(slotB)
        coordinator.demote()
        assertEquals(2, released.size)
    }

    @Test
    fun teardownWithADetachedPromotionReleasesItToo() {
        val orphan = coordinator.promoteDetached(request())

        coordinator.releaseAll()

        assertEquals(listOf(orphan.name), releasedNames())
    }

    /**
     * The invariant the two failure modes above reduce to, exercised over a full life cycle that
     * crosses every transition: attach, promote, detach, promote a replacement, demote, re-attach.
     */
    @Test
    fun everyCheckoutIsReleasedExactlyOnceAcrossAFullLifeCycle() {
        val slotA = Any()
        val slotB = Any()

        val a = coordinator.attach(slotA, request("a"))
        coordinator.promote(a)
        coordinator.detach(slotA)

        val b = coordinator.attach(slotB, request("b"))
        coordinator.promote(b)
        coordinator.demote()
        coordinator.detach(slotB)

        assertEquals(listOf(a.name, b.name), releasedNames())
        assertEquals(acquired.size, released.size)
    }
}
