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
package com.vitorpamplona.amethyst.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.debugState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.notifications.NotificationRelayService
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.ui.navigation.findParameterValue
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.elements.NowProvider
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip29RelayGroups.GroupInviteLink
import com.vitorpamplona.quartz.nip29RelayGroups.GroupNAddrInvite
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.UriParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {
    companion object {
        // True only while MainActivity is resumed. Used by the notification
        // pipeline to suppress in-app notifications — PiP/Call activities
        // have their own lifecycle, so MainActivity is paused while they're up.
        @Volatile
        var isResumed: Boolean = false
            private set
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Log.d("ActivityLifecycle") { "MainActivity.onCreate $this" }

        setContent {
            StringResSetup()
            AmethystTheme {
                NowProvider {
                    AccountScreen(Amethyst.instance.sessionManager)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()
        isResumed = true

        Log.d("ActivityLifecycle") { "MainActivity.onResume $this" }

        // starts muted every time
        DEFAULT_MUTED_SETTING.value = true

        // If always-on notifications are enabled but the foreground service couldn't be
        // started from the background during cold-start (Android 12+ restriction), retry
        // now that the activity is in the foreground.
        if (NotificationRelayService.isEnabled(this)) {
            NotificationRelayService.start(this)
        }
    }

    override fun onPause() {
        isResumed = false
        Log.d("ActivityLifecycle") { "MainActivity.onPause $this" }

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            LanguageTranslatorService.clear()
        }

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            debugState(applicationContext)
            Amethyst.instance.relayReqStats?.printStats()
        }

        super.onPause()
    }

    override fun onStop() {
        super.onStop()

        // Graph doesn't completely clear.
        // @OptIn(DelicateCoroutinesApi::class)
        // GlobalScope.launch(Dispatchers.IO) {
        //    serviceManager.trimMemory()
        // }

        Log.d("ActivityLifecycle") { "MainActivity.onStop $this" }
    }

    override fun onDestroy() {
        Log.d("ActivityLifecycle") { "MainActivity.onDestroy $this" }

        BackgroundMedia.removeBackgroundControllerAndReleaseIt()

        super.onDestroy()
    }
}

private const val NOSTR_URI_PREFIX = "nostr:"

fun isNotificationRoute(uri: String) = uri.startsWith("notifications", true) || uri.startsWith("nostr:notifications", true)

fun isHashtagRoute(uri: String) = uri.startsWith("hashtag?id=") || uri.startsWith("nostr:hashtag?id=")

/**
 * A markdown link target that is a bare hashtag fragment, e.g. the `#NostrMultiplayerGames` in
 * `[Games](#NostrMultiplayerGames)`. Returns the tag without the leading `#`, or null when the
 * uri is anything else (full URLs with anchors don't start with `#`). Reuses the same character
 * class that linkifies #hashtags in plain text, so validity matches how tags parse everywhere else.
 */
fun fragmentHashtagOrNull(uri: String): String? {
    val match = RichTextParser.hashTagsPattern.matchEntire(uri) ?: return null
    if (!match.groups[2]?.value.isNullOrEmpty()) return null
    return match.groups[1]?.value
}

fun isUrlRoute(uri: String) = uri.startsWith("url?id=") || uri.startsWith("nostr:url?id=")

fun isConnectedAppRoute(uri: String) = uri.startsWith("connectedapp?coordinate=") || uri.startsWith("nostr:connectedapp?coordinate=")

/**
 * The Connected Apps permission-detail route for an app coordinate (`pubkey:dtag` for a napplet/nsite,
 * `browser:<origin>` for a web client). Fired by the sandbox host so a running full-screen surface can
 * jump straight to its editable permissions; the coordinate is URL-encoded into the [coordinate] param.
 */
fun connectedAppRoute(uri: String): Route.ConnectedAppDetail? {
    val coordinate =
        runCatching {
            val raw = java.net.URI(uri.removePrefix(NOSTR_URI_PREFIX)).findParameterValue("coordinate") ?: return null
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

    return Route.ConnectedAppDetail(coordinate)
}

fun urlRoute(uri: String): Route.Url? {
    val url =
        runCatching {
            val rawUrl = java.net.URI(uri.removePrefix(NOSTR_URI_PREFIX)).findParameterValue("id") ?: return null
            URLDecoder.decode(rawUrl, Charsets.UTF_8.name())
        }.getOrNull() ?: return null

    return UrlId.toScopeOrNull(url)?.let { Route.Url(it) }
}

fun isWalletConnectRoute(uri: String) = uri.startsWith("dlnwc?value=") || uri.startsWith("amethyst+walletconnect:dlnwc?value=") || uri.startsWith("amethyst+walletconnect://dlnwc?value=")

fun isMarmotGroupRoute(uri: String) = uri.startsWith("marmot:")

private val MARMOT_HEX = Regex("^[0-9a-fA-F]+$")

fun uriToRoute(
    uri: String,
    account: Account,
): Route? {
    if (isNotificationRoute(uri)) {
        val scrollTo = runCatching { java.net.URI(uri.removePrefix(NOSTR_URI_PREFIX)).findParameterValue("scrollTo") }.getOrNull()
        return Route.Notification(scrollToEventId = scrollTo)
    }
    if (isHashtagRoute(uri)) {
        return Route.Hashtag(uri.removePrefix(NOSTR_URI_PREFIX).removePrefix("hashtag?id=").lowercase())
    }
    fragmentHashtagOrNull(uri)?.let {
        return Route.Hashtag(it.lowercase())
    }
    if (isUrlRoute(uri)) {
        return urlRoute(uri)
    }
    if (isConnectedAppRoute(uri)) {
        return connectedAppRoute(uri)
    }

    // A scanned/opened `nostrconnect://` offer is an app asking to connect to our signer: open the
    // NIP-46 signer screen and let it run the pairing (it enables the signer as part of connecting).
    if (uri.startsWith(NostrConnectURI.NOSTRCONNECT_SCHEME)) {
        return Route.Nip46Signer(connectUri = uri)
    }

    relayGroupInviteRoute(uri)?.let { return it }
    concordInviteRoute(uri)?.let { return it }
    buzzInviteRoute(uri)?.let { return it }

    val parsedNip19 = Nip19Parser.uriToRoute(uri)
    val nip19 = parsedNip19?.entity
    if (nip19 != null) {
        LocalCache.consume(nip19)

        val route =
            when (nip19) {
                is NPub -> {
                    Route.Profile(nip19.hex)
                }

                is NProfile -> {
                    Route.Profile(nip19.hex)
                }

                is NNote -> {
                    Route.Note(nip19.hex)
                }

                is NEvent -> {
                    routeFor(
                        note = LocalCache.getOrCreateNote(nip19.hex),
                        loggedIn = account,
                    ) ?: Route.EventRedirect(nip19.hex)
                }

                is NAddress -> {
                    relayGroupDirectRoute(nip19, parsedNip19.additionalChars)
                        ?: routeFor(
                            note = LocalCache.getOrCreateAddressableNote(nip19.address()),
                            loggedIn = account,
                        ) ?: calendarDirectRoute(nip19) ?: Route.EventRedirect(nip19.aTag())
                }

                is NEmbed -> {
                    val noteEvent = nip19.event
                    if (noteEvent is AddressableEvent) {
                        routeFor(
                            note = LocalCache.getOrCreateAddressableNote(noteEvent.address()),
                            loggedIn = account,
                        ) ?: Route.EventRedirect(noteEvent.addressTag())
                    } else {
                        routeFor(
                            note = LocalCache.getOrCreateNote(nip19.event.id),
                            loggedIn = account,
                        ) ?: Route.EventRedirect(nip19.event.id)
                    }
                }

                else -> {
                    null
                }
            }

        if (route != null) {
            return route
        }
    }

    if (isMarmotGroupRoute(uri)) {
        // marmot:<groupHex>?account=<npub>
        val groupHex =
            uri
                .removePrefix("marmot:")
                .substringBefore("?")
                .substringBefore("&")
        if (groupHex.matches(MARMOT_HEX)) {
            return Route.MarmotGroupChat(groupHex)
        }
    }

    if (isWalletConnectRoute(uri)) {
        try {
            val url = UriParser(uri)
            val nip47Uri = url.getQueryParameter("value")?.firstOrNull()
            if (nip47Uri != null) {
                Nip47WalletConnect.parse(nip47Uri)
                return Route.WalletAddNwc(nip47Uri)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    try {
        Nip47WalletConnect.parse(uri)
        return Route.WalletAddNwc(uri)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
    }

    return null
}

/**
 * Direct route for an `naddr` whose event hasn't arrived in [LocalCache] yet. When a notification
 * is tapped (or a `nostr:naddr…` deep link arrives) for a calendar appointment we know is kind
 * 31922/31923, route straight to the dedicated detail screen instead of bouncing through
 * [Route.EventRedirect]. The detail screen issues its own per-event subscription, so the user
 * sees the calendar-specific loading placeholder while the event arrives, not the generic
 * redirect screen.
 */
private fun calendarDirectRoute(nip19: NAddress): Route? =
    when (nip19.kind) {
        CalendarTimeSlotEvent.KIND,
        CalendarDateSlotEvent.KIND,
        -> Route.CalendarEventDetail(nip19.kind, nip19.author, nip19.dTag)
        else -> null
    }

/**
 * Direct route for a NIP-29 group `naddr` (kind 39000) shared as a link. The group
 * only exists on its host relay, so the naddr's relay hint is mandatory — the group
 * id alone can't be resolved. With a hint we open the group chat straight away
 * (cold start included); without one there's nowhere to look, so fall through.
 */
private fun relayGroupDirectRoute(
    nip19: NAddress,
    additionalChars: String?,
): Route? {
    if (nip19.kind != GroupMetadataEvent.KIND) return null
    val relay = nip19.relay.firstOrNull() ?: return null
    // The spec allows an invite code appended as `naddr1…?invite=<code>`; it arrives
    // in the trailing chars after the bech32 body. Carry it so the group auto-joins.
    val inviteCode = GroupNAddrInvite.parse(additionalChars)
    return Route.RelayGroup(nip19.dTag, relay.url, inviteCode = inviteCode)
}

/**
 * Direct route for a NIP-29 group invite link in the de-facto `<relay>'<groupId>[?code=<code>]`
 * form shared by Wisp and 0xchat (e.g. `wss://groups.0xchat.com'abc123`). The link carries its
 * own host relay, so we can open the group straight away. Returns null for anything that isn't
 * this exact shape, so it's safe to try before the nostr-entity parser.
 */
private fun relayGroupInviteRoute(uri: String): Route? {
    val link = GroupInviteLink.parse(uri.removePrefix(NOSTR_URI_PREFIX)) ?: return null
    return Route.RelayGroup(link.groupId, link.relayUrl.url, inviteCode = link.code)
}

/**
 * A shared Concord invite URL (`…/invite/<naddr>#<fragment>`). Cheap substring gates
 * keep the parse off the hot path; the whole URL (fragment included) is carried into
 * the route so the redeem flow still has the unlock token.
 */
private fun concordInviteRoute(uri: String): Route? =
    if (uri.contains("/invite/") && uri.contains('#') && ConcordActions.parseInviteLink(uri) != null) {
        Route.ConcordInvite(uri)
    } else {
        null
    }

/**
 * A Buzz workspace invite (`https://<host>/invite/<token>`) — a plain `/invite/` https URL with
 * no fragment, so disjoint from the Concord shape above. Opens the in-app join flow
 * ([Route.BuzzInvite]) instead of the external browser so the claim signs with the user's key.
 */
private fun buzzInviteRoute(uri: String): Route? =
    if (uri.contains("/invite/") && BuzzInviteLink.parse(uri) != null) {
        Route.BuzzInvite(uri)
    } else {
        null
    }
