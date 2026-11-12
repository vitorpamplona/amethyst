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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotifKind
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationClick
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationTarget
import com.vitorpamplona.amethyst.desktop.service.frontpage.DesktopFrontPageNotifier
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationClickRouterTest {
    private val me = "a".repeat(64)
    private val other = "b".repeat(64)
    private val gone = "c".repeat(64)
    private val loggedIn = setOf(me, other)
    private val note = NotificationTarget.Note("d".repeat(64))

    private fun plan(click: NotificationClick) = NotificationClickRules.plan(click, activeAccount = me) { it in loggedIn }

    @Test
    fun theActiveAccountsNotificationOpensItsTarget() {
        assertEquals(NotificationClickPlan.Open(NotificationTarget.FrontPage), plan(NotificationClick(NotificationTarget.FrontPage, me)))
        assertEquals(NotificationClickPlan.Open(note), plan(NotificationClick(note, me)))
    }

    @Test
    fun aNotificationForNoAccountOpensInTheActiveOne() {
        // Calendar reminders cover every account on the device and name none.
        val link = NotificationTarget.Link("nostr:naddr1example")
        assertEquals(NotificationClickPlan.Open(link), plan(NotificationClick(link, null)))
    }

    @Test
    fun anotherLoggedInAccountsNotificationSwitchesFirst() {
        assertEquals(NotificationClickPlan.SwitchAndOpen(other, NotificationTarget.FrontPage), plan(NotificationClick(NotificationTarget.FrontPage, other)))
        assertEquals(NotificationClickPlan.SwitchAndOpen(other, null), plan(NotificationClick(null, other)))
    }

    @Test
    fun anAccountNoLongerHereOnlyShowsTheWindow() {
        // What it points at is that account's: opening it as another would be wrong.
        assertEquals(NotificationClickPlan.ShowOnly, plan(NotificationClick(NotificationTarget.FrontPage, gone)))
    }

    @Test
    fun aClickWithNothingToOpenOnlyShowsTheWindow() {
        // A notification from before a restart: the OS reports the click, the spec is gone.
        assertEquals(NotificationClickPlan.ShowOnly, plan(NotificationClick(null, null)))
        assertEquals(NotificationClickPlan.ShowOnly, plan(NotificationClick(null, me)))
    }

    @Test
    fun theFrontPageOpensOnToday() {
        assertEquals(Route.FrontPage(), NotificationClickRules.routeFor(NotificationTarget.FrontPage, mockk<Account>(relaxed = true)))
    }

    @Test
    fun aNoteTheCacheDoesNotHoldIsLookedFor() {
        assertEquals(Route.EventRedirect(note.id), NotificationClickRules.routeFor(note, mockk<Account>(relaxed = true)))
    }

    @Test
    fun aClickWaitsForTheShellAndRaisesTheWindow() =
        runTest(UnconfinedTestDispatcher()) {
            val router = NotificationClickRouter()
            val raised = async { router.raises.first() }
            val click = NotificationClick(NotificationTarget.FrontPage, me)
            router.onClick(click)
            raised.await()
            assertEquals(click, router.pending.value)
        }

    @Test
    fun theNewestClickWins() {
        val router = NotificationClickRouter()
        router.onClick(NotificationClick(note, me))
        router.onClick(NotificationClick(NotificationTarget.FrontPage, other))
        assertEquals(NotificationClick(NotificationTarget.FrontPage, other), router.pending.value)
    }

    @Test
    fun theObserversNotificationsOpenTheFrontPageOfTheirReader() {
        val spec = DesktopFrontPageNotifier.frontPageSpec(other, "Your paper is ready", "Headline")
        assertEquals(NotificationTarget.FrontPage, spec.target)
        assertEquals(other, spec.account)
        assertEquals(NotifKind.FRONT_PAGE, spec.kind)
        // One thread per account: one reader's paper never replaces another's.
        assertEquals("front-page-${other.take(16)}", spec.threadId)
        assertNull(spec.deepLinkNoteId)
    }
}
