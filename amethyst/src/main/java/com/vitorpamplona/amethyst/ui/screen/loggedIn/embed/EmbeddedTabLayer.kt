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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.privacysandbox.ui.client.view.SandboxedSdkView

// How far off-screen a parked (inactive) warm tab is shifted — well past any real screen width.
private val OFFSCREEN_SHIFT = 10_000.dp

/**
 * The persistent surface layer: a full-window overlay (mounted once in the app shell, below the
 * navigation drawer and dialogs) that renders **every** warm embedded session's [SandboxedSdkView] and
 * keeps it attached. The active session is positioned over the current tab's reserved content area; the
 * rest are parked off-screen but stay attached, so their sessions never detach/close — that's what
 * preserves their state across tab swaps.
 *
 * The surface is z-ordered on top of the window (required for the SurfaceControlViewHost to receive
 * touch input), so the tab's chrome can't draw over it — each tab reserves its content area *below* a
 * control bar and the surface fills only that area. Here we just render and position the surfaces; each
 * is wrapped in an [EmbeddedSurfaceTouchHolder] so a scroll gesture isn't stolen by a host-side ancestor
 * (the cross-process WebView can't defend its own gesture).
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
                    if (bounds.width > 0f && bounds.height > 0f) {
                        with(density) {
                            // Parked tabs keep the SAME size as the active one and are only shoved
                            // off-screen, so bringing one back is a pure translation — no resize, no
                            // surface re-render, no black flash between tabs. (Parking at 1dp forced a
                            // resize + re-render on every switch, which flashed black for ~1s.)
                            val left = (bounds.left - layerOrigin.x).toDp() + (if (active) 0.dp else OFFSCREEN_SHIFT)
                            Modifier
                                .absoluteOffset(left, (bounds.top - layerOrigin.y).toDp())
                                .size(bounds.width.toDp(), bounds.height.toDp())
                        }
                    } else {
                        // No content bounds reported yet: park tiny off-screen until a tab is shown.
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

        // The active tab's top pull-down sheet, drawn AFTER the surfaces so it sits on top of the
        // (z-below) surface, anchored to the top of the active tab's reserved bounds.
        val chrome = EmbeddedTabHost.activeChrome
        if (chrome != null && bounds.width > 0f && bounds.height > 0f) {
            with(density) {
                TopControlSheet(
                    chrome = chrome,
                    modifier =
                        Modifier
                            .absoluteOffset(
                                (bounds.left - layerOrigin.x).toDp(),
                                (bounds.top - layerOrigin.y).toDp(),
                            ).width(bounds.width.toDp()),
                )
            }
        }
    }
}
