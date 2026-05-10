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
package com.vitorpamplona.amethyst.service.cast.chromecast

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import com.vitorpamplona.amethyst.service.cast.CastDevice
import com.vitorpamplona.amethyst.service.cast.CastDeviceKind
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.amethyst.service.cast.VideoCaster
import com.vitorpamplona.amethyst.service.cast.effectiveMimeType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ChromecastCaster"
private const val SESSION_START_TIMEOUT_MS = 30_000L

/**
 * Google Cast (Chromecast) implementation of [VideoCaster].
 *
 * Only present in the play flavor — the F-Droid flavor ships a no-op stub
 * with the same FQN. The class still works at runtime when Google Play
 * services are unavailable: discovery returns an empty list and casts fail
 * cleanly.
 */
class ChromecastCaster(
    private val appContext: Context,
) : VideoCaster {
    override val id: String = "chromecast"

    private val devicesFlow = MutableStateFlow<List<CastDevice>>(emptyList())
    override val devices: StateFlow<List<CastDevice>> = devicesFlow.asStateFlow()

    private val sessionFlow = MutableStateFlow<CastSessionState>(CastSessionState.Idle)
    override val sessionState: StateFlow<CastSessionState> = sessionFlow.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null

    @Volatile
    private var castContext: CastContext? = null
    private var registered = false

    private val routes = mutableMapOf<String, MediaRouter.RouteInfo>()

    private val routerCallback =
        object : MediaRouter.Callback() {
            override fun onRouteAdded(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                Log.d(TAG) { "onRouteAdded id=${route.id} name=${route.name}" }
                updateRoutes(router)
            }

            override fun onRouteRemoved(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                Log.d(TAG) { "onRouteRemoved id=${route.id} name=${route.name}" }
                updateRoutes(router)
            }

            override fun onRouteChanged(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                Log.d(TAG) { "onRouteChanged id=${route.id} name=${route.name} selected=${route.isSelected}" }
                updateRoutes(router)
            }
        }

    private val sessionListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                Log.d(TAG) { "session.onStarting" }
            }

            override fun onSessionStarted(
                session: CastSession,
                sessionId: String,
            ) {
                Log.d(TAG) { "session.onStarted id=$sessionId connected=${session.isConnected} hasClient=${session.remoteMediaClient != null}" }
                pendingSessionStart?.complete(true)
                pendingSessionStart = null
            }

            override fun onSessionStartFailed(
                session: CastSession,
                error: Int,
            ) {
                Log.w(TAG, "session.onStartFailed error=$error")
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
                sessionFlow.value = CastSessionState.Error(currentDevice(), "Cast session failed (code $error)")
            }

            override fun onSessionEnding(session: CastSession) {
                Log.d(TAG) { "session.onEnding" }
            }

            override fun onSessionEnded(
                session: CastSession,
                error: Int,
            ) {
                Log.d(TAG) { "session.onEnded error=$error" }
                // If a cast() was awaiting a session start, this is also a terminal
                // outcome — the session never reached a usable state. Without
                // completing here the cast coroutine hangs and the discovery
                // ref-count leaks +1 for every failed attempt.
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
                sessionFlow.value = CastSessionState.Idle
            }

            override fun onSessionResuming(
                session: CastSession,
                sessionId: String,
            ) {
                Log.d(TAG) { "session.onResuming id=$sessionId" }
            }

            override fun onSessionResumed(
                session: CastSession,
                wasSuspended: Boolean,
            ) {
                Log.d(TAG) { "session.onResumed wasSuspended=$wasSuspended" }
                // Resume counts as the session being usable — let cast() proceed.
                pendingSessionStart?.complete(true)
                pendingSessionStart = null
            }

            override fun onSessionResumeFailed(
                session: CastSession,
                error: Int,
            ) {
                Log.w(TAG, "session.onResumeFailed error=$error")
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
            }

            override fun onSessionSuspended(
                session: CastSession,
                reason: Int,
            ) {
                Log.d(TAG) { "session.onSuspended reason=$reason" }
                // Suspended sessions can't accept loads — fail any pending start so
                // the caller doesn't try to call remoteMediaClient.load() against
                // a broken session.
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
            }
        }

    @Volatile
    private var pendingSessionStart: CompletableDeferred<Boolean>? = null

    private fun currentDevice(): CastDevice? =
        when (val s = sessionFlow.value) {
            is CastSessionState.Connecting -> s.device
            is CastSessionState.Casting -> s.device
            else -> null
        }

    private fun ensureCastContext(): CastContext? {
        castContext?.let { return it }
        val gms = GoogleApiAvailability.getInstance()
        val status = gms.isGooglePlayServicesAvailable(appContext)
        if (status != ConnectionResult.SUCCESS) {
            Log.d(TAG) { "Google Play services unavailable (status=$status); Chromecast disabled." }
            return null
        }
        return try {
            // The blocking overload is the right choice here: we only call it on
            // the main thread, OptionsProvider is declared in the play manifest,
            // and the SDK caches the singleton after the first call.
            @Suppress("DEPRECATION")
            CastContext.getSharedInstance(appContext).also { castContext = it }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize CastContext: ${t.message}", t)
            null
        }
    }

    override fun startDiscovery() {
        Log.d(TAG) { "startDiscovery (already registered? $registered)" }
        main.post {
            if (registered) {
                Log.d(TAG) { "startDiscovery: already registered, no-op" }
                return@post
            }
            val ctx = ensureCastContext()
            if (ctx == null) {
                Log.d(TAG) { "startDiscovery: CastContext unavailable, aborting" }
                return@post
            }
            val router = MediaRouter.getInstance(appContext)
            val selector =
                MediaRouteSelector
                    .Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                    .build()
            router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            mediaRouter = router
            routeSelector = selector
            registered = true
            Log.d(TAG) { "startDiscovery: registered router callback + session listener" }
            updateRoutes(router)
        }
    }

    override fun stopDiscovery() {
        Log.d(TAG) { "stopDiscovery (registered=$registered)" }
        main.post {
            if (!registered) return@post
            mediaRouter?.removeCallback(routerCallback)
            castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
            registered = false
            routes.clear()
            devicesFlow.value = emptyList()
            Log.d(TAG) { "stopDiscovery: unregistered" }
        }
    }

    private fun updateRoutes(router: MediaRouter) {
        val selector = routeSelector ?: return
        routes.clear()
        for (route in router.routes) {
            if (route.isDefault || route.isBluetooth) continue
            if (!route.matchesSelector(selector)) continue
            routes[route.id] = route
        }
        val list =
            routes.values.map { route ->
                CastDevice(
                    id = route.id,
                    name = route.name,
                    kind = CastDeviceKind.Chromecast,
                    casterId = id,
                )
            }
        Log.d(TAG) { "updateRoutes: count=${list.size} -> [${list.joinToString { it.name }}]" }
        devicesFlow.value = list
    }

    override suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    ) {
        Log.d(TAG) { "cast device=${device.name} url=${request.url}" }
        val ctx = withContext(Dispatchers.Main) { ensureCastContext() }
        if (ctx == null) {
            Log.w(TAG, "cast: CastContext unavailable")
            sessionFlow.value = CastSessionState.Error(device, "Google Play services unavailable")
            return
        }
        val route =
            withContext(Dispatchers.Main) {
                routes[device.id] ?: mediaRouter?.routes?.firstOrNull { it.id == device.id }
            }
        if (route == null) {
            Log.w(TAG, "cast: route ${device.id} not in current set; offline?")
            sessionFlow.value = CastSessionState.Error(device, "Device went offline")
            return
        }

        sessionFlow.value = CastSessionState.Connecting(device)

        val started =
            withContext(Dispatchers.Main) {
                val existing = ctx.sessionManager.currentCastSession
                Log.d(TAG) {
                    "cast: existing session connected=${existing?.isConnected} hasClient=${existing?.remoteMediaClient != null}"
                }
                val pending = CompletableDeferred<Boolean>()
                pendingSessionStart = pending
                try {
                    Log.d(TAG) { "cast: selectRoute id=${route.id}" }
                    mediaRouter?.selectRoute(route)
                    if (existing?.isConnected == true) {
                        Log.d(TAG) { "cast: reusing already-connected session, completing immediately" }
                        pending.complete(true)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "cast: selectRoute threw: ${t.message}", t)
                    pending.complete(false)
                }
                // Defence in depth: if a callback is somehow missed (SDK bug,
                // process killed mid-flight, …) don't hang the coroutine forever.
                // The session listener already covers every documented terminal
                // state, so reaching the timeout means something genuinely went
                // wrong on the SDK side.
                val outcome = withTimeoutOrNull(SESSION_START_TIMEOUT_MS) { pending.await() }
                if (outcome == null) {
                    Log.w(TAG, "cast: session start timed out after ${SESSION_START_TIMEOUT_MS}ms")
                    pendingSessionStart = null
                }
                outcome ?: false
            }

        if (!started) {
            Log.w(TAG, "cast: session start refused")
            sessionFlow.value = CastSessionState.Error(device, "Cast session refused")
            return
        }

        val ok =
            withContext(Dispatchers.Main) {
                val session = ctx.sessionManager.currentCastSession
                val client = session?.remoteMediaClient
                Log.d(TAG) {
                    "cast: post-start session=${session != null} connected=${session?.isConnected} client=${client != null}"
                }
                if (client == null) {
                    Log.w(TAG, "cast: remoteMediaClient is null (session not fully ready); load skipped")
                    false
                } else {
                    try {
                        client.load(buildLoadRequest(request))
                        Log.d(TAG) { "cast: load() submitted (status=${client.playerState})" }
                        true
                    } catch (t: Throwable) {
                        Log.w(TAG, "cast: remoteMediaClient.load failed: ${t.message}", t)
                        false
                    }
                }
            }

        sessionFlow.value =
            if (ok) {
                CastSessionState.Casting(device, request)
            } else {
                CastSessionState.Error(device, "Could not load media on receiver")
            }
    }

    private fun buildLoadRequest(request: CastRequest): MediaLoadRequestData {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        request.title?.let { metadata.putString(MediaMetadata.KEY_TITLE, it) }
        request.artworkUri?.let {
            runCatching { metadata.addImage(WebImage(it.toUri())) }
        }
        val info =
            MediaInfo
                .Builder(request.url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(request.effectiveMimeType())
                .setMetadata(metadata)
                .build()
        return MediaLoadRequestData
            .Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .build()
    }

    override suspend fun stopCasting() {
        Log.d(TAG) { "stopCasting" }
        withContext(Dispatchers.Main) {
            try {
                castContext?.sessionManager?.endCurrentSession(true)
            } catch (t: Throwable) {
                Log.w(TAG, "endCurrentSession failed: ${t.message}", t)
            }
            mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
        }
        sessionFlow.value = CastSessionState.Idle
    }
}
