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
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationClick
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationTarget
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where a click on one of the app's OS notifications goes. The notifier reports it from the OS's
 * own thread ([onClick]); the window comes forward at once ([raises], collected by the window in
 * `main`), and the logged-in shell opens what the notification was about ([pending], collected in
 * `DesktopAppRoot.NavigationEffects`), switching accounts first when it was another's.
 */
class NotificationClickRouter {
    /** The newest click waiting for a logged-in shell; the shell clears it as it opens it. */
    val pending = MutableStateFlow<NotificationClick?>(null)

    private val raiseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One per click: show the main window (hidden, minimized or behind others) and bring it forward. */
    val raises: SharedFlow<Unit> = raiseRequests.asSharedFlow()

    fun onClick(click: NotificationClick) {
        Log.d(TAG) { "Notification clicked: ${click.target?.let { it::class.simpleName }} for ${click.account?.take(8)}" }
        pending.value = click
        raiseRequests.tryEmit(Unit)
    }

    private companion object {
        const val TAG = "NotificationClicks"
    }
}

/** What the logged-in shell does with a notification click. */
sealed interface NotificationClickPlan {
    /** Opens [target] in the account in use. */
    data class Open(
        val target: NotificationTarget,
    ) : NotificationClickPlan

    /** The notification is about [account], logged in on this device but not the one in use: switch, then open [target] there. */
    data class SwitchAndOpen(
        val account: HexKey,
        val target: NotificationTarget?,
    ) : NotificationClickPlan

    /** Only the window comes forward: nothing to open, or the account it was for is not logged in here. */
    data object ShowOnly : NotificationClickPlan
}

object NotificationClickRules {
    /**
     * [click] while [activeAccount] is in use. A notification of another account opens in that
     * account when [isLoggedInHere] says it is still on this device; one of an account that has
     * logged out since opens nothing, because what it points at is that account's.
     */
    fun plan(
        click: NotificationClick,
        activeAccount: HexKey,
        isLoggedInHere: (HexKey) -> Boolean,
    ): NotificationClickPlan {
        val account = click.account
        if (account != null && account != activeAccount) {
            return if (isLoggedInHere(account)) NotificationClickPlan.SwitchAndOpen(account, click.target) else NotificationClickPlan.ShowOnly
        }
        return click.target?.let { NotificationClickPlan.Open(it) } ?: NotificationClickPlan.ShowOnly
    }

    /**
     * Whether [target] is about today's paper: an Observer screen showing a past edition goes back
     * to today ([com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinter.showToday]), since navigating to the screen on top does nothing.
     */
    fun showsToday(target: NotificationTarget): Boolean = target == NotificationTarget.FrontPage

    /**
     * The screen [target] opens for [account]: The Observer on today's paper, a link as the OS
     * would route it, or a note where a tap on it in the app goes (its thread, its chat...), or the
     * screen that finds it while the cache does not hold it.
     */
    fun routeFor(
        target: NotificationTarget,
        account: Account,
    ): Route? =
        when (target) {
            NotificationTarget.FrontPage -> Route.FrontPage()
            is NotificationTarget.Link -> uriToRoute(target.uri, account)
            is NotificationTarget.Note -> LocalCache.getNoteIfExists(target.id)?.let { routeFor(it, account) } ?: Route.EventRedirect(target.id)
        }
}
