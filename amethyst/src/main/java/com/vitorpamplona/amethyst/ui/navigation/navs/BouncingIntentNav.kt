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
package com.vitorpamplona.amethyst.ui.navigation.navs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * INav for screens that live inside a free-standing Activity (no Compose
 * NavHost in scope) and need to dispatch navigation requests back to
 * [MainActivity]'s nav graph — typically a chat surface inside the
 * audio-room ([com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestActivity]).
 *
 * Each [nav] / [newStack] / [navBottomBar] call is translated into a
 * `nostr:` URI (when the route maps to a NIP-19 entity) and dispatched
 * to [MainActivity] via `Intent.ACTION_VIEW`. AppNavigation already
 * picks up `intent.data` and runs `uriToRoute` against it on resume,
 * so the destination route lands in MainActivity's NavHost without
 * any receiver-side changes.
 *
 * Routes that can't be expressed as a `nostr:` URI (settings, drafts,
 * private chatrooms, …) are silently skipped — those aren't reachable
 * from a chat-message tap anyway. Adding more cases later is one line
 * each in [routeToBouncingUri].
 *
 * Launch flags: [Intent.FLAG_ACTIVITY_NEW_TASK] +
 * [Intent.FLAG_ACTIVITY_CLEAR_TOP]. The user lands on a clean
 * MainActivity (or brings the existing one to front), with the
 * NestActivity left running in its own task — the foreground audio
 * service keeps audio playing, and a back-press from MainActivity
 * returns to the room.
 *
 * `popBack` and `popUpTo` are no-ops; the bouncing model means
 * "leaving" MainActivity is the user's job (system back). `closeDrawer`
 * / `openDrawer` operate on a local DrawerState that nothing renders;
 * implementing them is part of the [INav] contract.
 */
@Stable
class BouncingIntentNav(
    private val context: Context,
    override val navigationScope: CoroutineScope,
) : INav {
    override val drawerState = DrawerState(DrawerValue.Closed)

    override fun closeDrawer() = runBlocking { drawerState.close() }

    override fun openDrawer() = runBlocking { drawerState.open() }

    override fun nav(route: Route) {
        val uri = routeToBouncingUri(route)
        if (uri == null) {
            Log.d("BouncingIntentNav") { "no nostr: URI for $route — skipping" }
            return
        }
        bounce(uri)
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            computeRoute()?.let { nav(it) }
        }
    }

    override fun newStack(route: Route) = nav(route)

    override fun navBottomBar(route: Route) = nav(route)

    @Composable
    override fun canPop(): Boolean = false

    override fun popBack() {
        // No-op: this nav is for in-Activity chat navigation that
        // exclusively forwards to MainActivity. There's no back stack
        // to pop in our caller's scope.
    }

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        // No-op for the same reason as popBack().
    }

    private fun bounce(uri: String) {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }
            .onFailure { t -> Log.w("BouncingIntentNav") { "could not bounce to MainActivity: ${t.message}" } }
    }

    private fun routeToBouncingUri(route: Route): String? =
        when (route) {
            is Route.Profile -> {
                "nostr:" + NPub.create(route.id)
            }

            is Route.Note -> {
                "nostr:" + NEvent.create(route.id, null, null, null)
            }

            is Route.EventRedirect -> {
                "nostr:" + NEvent.create(route.id, null, null, null)
            }

            is Route.Hashtag -> {
                "nostr:hashtag?id=" + route.hashtag
            }

            is Route.LiveActivityChannel -> {
                "nostr:" + NAddress.create(route.kind, route.pubKeyHex, route.dTag, null)
            }

            is Route.Community -> {
                "nostr:" + NAddress.create(route.kind, route.pubKeyHex, route.dTag, null)
            }

            is Route.PublicChatChannel -> {
                "nostr:" + NEvent.create(route.id, null, ChannelCreateEvent.KIND, null)
            }

            else -> {
                null
            }
        }
}
