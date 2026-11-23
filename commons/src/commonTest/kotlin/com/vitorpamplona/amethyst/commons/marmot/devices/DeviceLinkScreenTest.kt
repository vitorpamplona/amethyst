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
package com.vitorpamplona.amethyst.commons.marmot.devices

import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkEndReason
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkPrompt
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.NewDeviceLinkState
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SasSymbol
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SignIn
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager.SignInClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLinkScreenTest {
    private val code = List(7) { SasSymbol(it, "x", "X$it") }
    private val session = "a".repeat(64)

    private fun prompt(
        state: DeviceLinkPrompt.State,
        id: String = session,
        at: Long = 1_000,
    ) = DeviceLinkPrompt(id, null, at, state)

    @Test
    fun aNewDeviceStartsUnlessItIsLinkedOrFailed() {
        val none = MarmotDevicesSnapshot()
        assertEquals(DeviceLinkScreenState.Starting, DeviceLinkScreen.state(none, DeviceLinkRole.NEW_DEVICE, null))
        assertEquals(DeviceLinkScreenState.StartFailed, DeviceLinkScreen.state(none, DeviceLinkRole.NEW_DEVICE, null, startFailed = true))
        assertEquals(DeviceLinkScreenState.AlreadyLinked, DeviceLinkScreen.state(none.copy(linked = true), DeviceLinkRole.NEW_DEVICE, null))
    }

    @Test
    fun aNewDeviceFollowsItsLinkState() {
        fun of(
            s: NewDeviceLinkState,
            remaining: Long? = null,
        ) = DeviceLinkScreen.state(MarmotDevicesSnapshot(newDevice = s, remainingChats = remaining), DeviceLinkRole.NEW_DEVICE, null)
        assertEquals(DeviceLinkScreenState.WaitingForAnswer, of(NewDeviceLinkState.WaitingForAnswer))
        assertEquals(DeviceLinkScreenState.Comparing(code, true, null), of(NewDeviceLinkState.Comparing(code)))
        assertEquals(DeviceLinkScreenState.WaitingForOtherDevice, of(NewDeviceLinkState.WaitingForOtherDevice))
        assertEquals(DeviceLinkScreenState.Joining, of(NewDeviceLinkState.Joining))
        assertEquals(DeviceLinkScreenState.Linked(3), of(NewDeviceLinkState.Linked, remaining = 3))
        // Nothing left to wait for is not "0 chats on their way".
        assertEquals(DeviceLinkScreenState.Linked(null), of(NewDeviceLinkState.Linked, remaining = 0))
        assertEquals(DeviceLinkScreenState.Ended(DeviceLinkEndReason.CODES_DIFFER), of(NewDeviceLinkState.Ended(DeviceLinkEndReason.CODES_DIFFER)))
    }

    @Test
    fun aRetryThatFailsShowsTheFailureNotTheLastEnd() {
        val ended = MarmotDevicesSnapshot(newDevice = NewDeviceLinkState.Ended(DeviceLinkEndReason.TIMED_OUT))
        assertEquals(DeviceLinkScreenState.StartFailed, DeviceLinkScreen.state(ended, DeviceLinkRole.NEW_DEVICE, null, startFailed = true))
    }

    @Test
    fun anExistingDeviceShowsTheRequestItFollows() {
        fun of(s: DeviceLinkPrompt.State) = DeviceLinkScreen.state(MarmotDevicesSnapshot(prompts = listOf(prompt(s))), DeviceLinkRole.EXISTING_DEVICE, session)
        assertEquals(DeviceLinkScreenState.Preparing, of(DeviceLinkPrompt.State.Preparing))
        assertEquals(DeviceLinkScreenState.Comparing(code, false, session), of(DeviceLinkPrompt.State.Comparing(code, canLink = false)))
        assertEquals(DeviceLinkScreenState.CompetingRequests, of(DeviceLinkPrompt.State.CompetingRequests))
        assertEquals(DeviceLinkScreenState.RateLimited(5), of(DeviceLinkPrompt.State.RateLimited(5)))
        assertEquals(DeviceLinkScreenState.WaitingForOtherDevice, of(DeviceLinkPrompt.State.Linking))
        assertEquals(DeviceLinkScreenState.Linked(null), of(DeviceLinkPrompt.State.Linked))
        assertEquals(DeviceLinkScreenState.Ended(DeviceLinkEndReason.TIMED_OUT), of(DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.TIMED_OUT)))
    }

    @Test
    fun anExistingDeviceWaitsUntilARequestArrives() {
        val s = MarmotDevicesSnapshot(prompts = listOf(prompt(DeviceLinkPrompt.State.Preparing)))
        assertEquals(DeviceLinkScreenState.WaitingForRequest, DeviceLinkScreen.state(s, DeviceLinkRole.EXISTING_DEVICE, null))
        // A session that is gone from the state (another run's) is not a request either.
        assertEquals(DeviceLinkScreenState.WaitingForRequest, DeviceLinkScreen.state(s, DeviceLinkRole.EXISTING_DEVICE, "f".repeat(64)))
    }

    @Test
    fun theScreenFollowsTheNewestLiveRequestOrOneThatEndedAfterItOpened() {
        val oldEnded = prompt(DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.TIMED_OUT), id = "1".repeat(64), at = 500)
        val live = prompt(DeviceLinkPrompt.State.Comparing(code, true), id = "2".repeat(64), at = 800)
        assertEquals(live, DeviceLinkScreen.followablePrompt(listOf(oldEnded, live), openedAt = 900))
        assertNull(DeviceLinkScreen.followablePrompt(listOf(oldEnded), openedAt = 900))

        val endedSinceOpening = prompt(DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.CODES_DIFFER), id = "3".repeat(64), at = 950)
        assertEquals(endedSinceOpening, DeviceLinkScreen.followablePrompt(listOf(oldEnded, endedSinceOpening), openedAt = 900))

        // The last link's outcome is not the next link's screen.
        val oldLinked = prompt(DeviceLinkPrompt.State.Linked, id = "4".repeat(64), at = 600)
        assertNull(DeviceLinkScreen.followablePrompt(listOf(oldEnded, oldLinked), openedAt = 900))
        val linkedSinceOpening = prompt(DeviceLinkPrompt.State.Linked, id = "5".repeat(64), at = 960)
        assertEquals(linkedSinceOpening, DeviceLinkScreen.followablePrompt(listOf(oldLinked, linkedSinceOpening), openedAt = 900))
    }

    @Test
    fun onlyAnUnfinishedLinkCanBeCancelled() {
        assertTrue(DeviceLinkScreen.isCancellable(DeviceLinkScreenState.WaitingForAnswer))
        assertTrue(DeviceLinkScreen.isCancellable(DeviceLinkScreenState.Comparing(code, true, null)))
        assertFalse(DeviceLinkScreen.isCancellable(DeviceLinkScreenState.Linked(null)))
        assertFalse(DeviceLinkScreen.isCancellable(DeviceLinkScreenState.Ended(DeviceLinkEndReason.CANCELLED)))
        assertFalse(DeviceLinkScreen.isCancellable(DeviceLinkScreenState.WaitingForRequest))
    }
}

class DeviceGroupAttentionTest {
    private val code = List(7) { SasSymbol(it, "x", "X$it") }
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    private fun attention(
        snapshot: MarmotDevicesSnapshot,
        requests: Set<String> = emptySet(),
        signIns: Set<String> = emptySet(),
        unlinkedDismissed: Boolean = false,
        linkScreenOpen: Boolean = false,
    ) = DeviceGroupAttention.of(snapshot, requests, signIns, unlinkedDismissed, linkScreenOpen)

    @Test
    fun theNewestLiveRequestIsAsked() {
        val older = DeviceLinkPrompt(a, null, 100, DeviceLinkPrompt.State.Comparing(code, true))
        val newer = DeviceLinkPrompt(b, null, 200, DeviceLinkPrompt.State.Preparing)
        val done = DeviceLinkPrompt("c".repeat(64), null, 300, DeviceLinkPrompt.State.Linked)
        val s = MarmotDevicesSnapshot(linked = true, prompts = listOf(older, newer, done))
        assertEquals(newer, attention(s).linkRequest)
        assertEquals(older, attention(s, requests = setOf(b)).linkRequest)
        assertNull(attention(s, requests = setOf(a, b)).linkRequest)
    }

    @Test
    fun noRequestPopsUpOverALinkScreenOrWhileThisDeviceLinksItself() {
        val request = DeviceLinkPrompt(a, null, 100, DeviceLinkPrompt.State.Preparing)
        val s = MarmotDevicesSnapshot(prompts = listOf(request))
        assertEquals(request, attention(s).linkRequest)
        assertNull(attention(s, linkScreenOpen = true).linkRequest)
        assertNull(attention(s.copy(newDevice = NewDeviceLinkState.WaitingForAnswer)).linkRequest)
        assertEquals(request, attention(s.copy(newDevice = NewDeviceLinkState.Ended(DeviceLinkEndReason.CANCELLED))).linkRequest)
    }

    @Test
    fun onlyNewSignInsAsk() {
        val new = SignIn(a, 200, SignInClass.NEW_SIGN_IN)
        val earlier = SignIn(b, 300, SignInClass.ALREADY_SIGNED_IN)
        val rejected = SignIn("c".repeat(64), 400, SignInClass.REJECTED)
        val s = MarmotDevicesSnapshot(signIns = listOf(new, earlier, rejected))
        assertEquals(new, attention(s).signIn)
        assertNull(attention(s, signIns = setOf(a)).signIn)
        // "That was me" earlier: never asked again.
        assertNull(attention(s.copy(signIns = listOf(new.copy(confirmed = true)))).signIn)
    }

    @Test
    fun aDeviceAskingToLinkIsNotAlsoASignInToAskAbout() {
        val new = SignIn(a, 200, SignInClass.NEW_SIGN_IN)
        val linking = MarmotDevicesSnapshot(prompts = listOf(DeviceLinkPrompt("1".repeat(32), a, 100, DeviceLinkPrompt.State.Linking)), signIns = listOf(new))
        assertNull(attention(linking).signIn)
        assertNull(attention(MarmotDevicesSnapshot(signIns = listOf(new)), linkScreenOpen = true).signIn)
        // Once that request ended, the sign-in is asked about after all.
        val ended = linking.copy(prompts = listOf(DeviceLinkPrompt("1".repeat(32), a, 100, DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.CANCELLED))))
        assertEquals(new, attention(ended).signIn)
    }

    @Test
    fun anUnlinkedDeviceOnlySaysSo() {
        val s =
            MarmotDevicesSnapshot(
                unlinked = true,
                prompts = listOf(DeviceLinkPrompt(a, null, 100, DeviceLinkPrompt.State.Preparing)),
                signIns = listOf(SignIn(b, 200, SignInClass.NEW_SIGN_IN)),
            )
        assertEquals(DeviceGroupAttention(unlinked = true), attention(s))
        assertEquals(DeviceGroupAttention.NONE, attention(s, unlinkedDismissed = true))
    }

    @Test
    fun theStateHolderRemembersWhatWasDismissed() =
        runTest {
            val holder = DeviceGroupAttentionState()
            val s = MarmotDevicesSnapshot(prompts = listOf(DeviceLinkPrompt(a, null, 100, DeviceLinkPrompt.State.Preparing)))
            val attention = holder.attention(flowOf(s))
            assertEquals(a, attention.first().linkRequest?.sessionId)
            holder.linkScreenOpened()
            assertNull(attention.first().linkRequest)
            holder.linkScreenClosed()
            holder.linkScreenClosed()
            assertEquals(0, holder.openLinkScreens.value)
            holder.dismissRequest(a)
            assertNull(attention.first().linkRequest)
        }
}
