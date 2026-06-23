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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.os.Build
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols

/**
 * The persistent surface layer: a full-window overlay (mounted once in the app shell, below the
 * navigation drawer and dialogs) that renders **every** warm embedded session's [SandboxedSdkView] and
 * keeps it attached. The active session is positioned over the current tab's reserved content area; the
 * rest are parked off-screen but stay attached, so their sessions never detach/close — that's what
 * preserves their state across tab swaps.
 *
 * [barFavoriteIds] are the favorites currently configured as bottom-bar tabs; warm-keep is scoped to
 * them ([EmbeddedTabHost.retainOnly]), plus whatever is momentarily active.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabLayer(barFavoriteIds: List<String>) {
    val activeId = EmbeddedTabHost.activeId

    // Keep only bottom-row apps warm (plus the active tab, even mid-removal). A favorite removed from
    // the bar drops its warm session here.
    LaunchedEffect(barFavoriteIds, activeId) {
        EmbeddedTabHost.retainOnly(barFavoriteIds.toSet() + setOfNotNull(activeId))
    }

    val bounds = EmbeddedTabHost.contentBounds
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { layerOrigin = it.positionInWindow() },
    ) {
        EmbeddedTabHost.sessions.forEach { session ->
            key(session.id) {
                val active = session.id == activeId

                LaunchedEffect(active) {
                    if (active) session.controller.onShown() else session.controller.onHidden()
                }

                val placement =
                    if (active && bounds.width > 0f && bounds.height > 0f) {
                        with(density) {
                            Modifier
                                .absoluteOffset(
                                    (bounds.left - layerOrigin.x).toDp(),
                                    (bounds.top - layerOrigin.y).toDp(),
                                ).size(bounds.width.toDp(), bounds.height.toDp())
                        }
                    } else {
                        // Parked: tiny + far off-screen, but still attached so the session stays warm.
                        Modifier
                            .absoluteOffset(x = (-10000).dp)
                            .size(1.dp)
                    }

                AndroidView(
                    // Wrap the surface so it claims scroll gestures from host-side ancestors (the
                    // cross-process WebView can't request-disallow-intercept itself).
                    factory = { ctx ->
                        EmbeddedSurfaceTouchHolder(ctx).apply {
                            val surface = SandboxedSdkView(ctx)
                            addView(surface, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            session.controller.attachView(surface)
                        }
                    },
                    modifier = placement,
                )
            }
        }

        // Active-tab overlays, drawn over the surface (which is ordered below this window's UI): a themed
        // loading placeholder until the session opens, and the floating control puck. Positioned over the
        // active tab's reserved bounds so they track it.
        val activeSession = EmbeddedTabHost.sessions.firstOrNull { it.id == activeId }
        if (activeSession != null && bounds.width > 0f && bounds.height > 0f) {
            val overlayModifier =
                with(density) {
                    Modifier
                        .absoluteOffset(
                            (bounds.left - layerOrigin.x).toDp(),
                            (bounds.top - layerOrigin.y).toDp(),
                        ).size(bounds.width.toDp(), bounds.height.toDp())
                }
            Box(overlayModifier) {
                val ready by activeSession.controller.ready
                if (!ready) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                EmbeddedTabHost.activeChrome?.let { chrome ->
                    EmbeddedTabChromePuck(
                        chrome = chrome,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                    )
                }
            }
        }
    }
}

/** Builds the floating [AppControlPuck] for the active tab from its [EmbeddedTabChrome] description. */
@Composable
private fun EmbeddedTabChromePuck(
    chrome: EmbeddedTabChrome,
    modifier: Modifier,
) {
    val isShield = chrome.marker == EmbeddedTabChrome.Marker.SHIELD
    AppControlPuck(
        trustedIcon = if (isShield) MaterialSymbols.Security else MaterialSymbols.Public,
        trustedTint = if (isShield) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        trustedDescription = chrome.description,
        modifier = modifier,
    ) {
        chrome.torOn?.let { torOn ->
            TorToggleButton(torOn) { chrome.onToggleTor() }
        }
        IconButton(onClick = chrome.onReload) {
            Icon(MaterialSymbols.Refresh, contentDescription = stringResource(R.string.browser_reload))
        }
        chrome.onInfo?.let { info ->
            IconButton(onClick = info) {
                Icon(MaterialSymbols.Info, contentDescription = stringResource(R.string.favorite_app_access_show))
            }
        }
        IconButton(onClick = chrome.onPopOut) {
            Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = stringResource(R.string.favorite_app_open_window))
        }
    }
}
