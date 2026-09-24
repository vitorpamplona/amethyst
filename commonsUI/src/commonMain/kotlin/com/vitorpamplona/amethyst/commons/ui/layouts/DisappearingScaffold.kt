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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.ui.insets.rememberSafeImeInsets
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import kotlinx.coroutines.launch

private enum class DisappearingSlot { Top, Bottom, Content, Fab }

private val FabEdgePadding = 16.dp

/**
 * A scaffold whose top and bottom bars slide away while the content scrolls and come back on the
 * reverse scroll, with a floating button that rides along with the bottom bar.
 *
 * @param immersiveScrolling read on every scroll; false keeps the bars in place (the user's
 *   immersive-scrolling setting).
 * @param allowBarHide false pins the bars and leaves the OS status bar alone (large screens,
 *   screens that must keep their chrome).
 */
@Composable
fun DisappearingScaffold(
    isInvertedLayout: Boolean,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingButton: (@Composable () -> Unit)? = null,
    immersiveScrolling: () -> Boolean = { true },
    isActive: () -> Boolean = { true },
    allowBarHide: Boolean = true,
    mainContent: @Composable (padding: PaddingValues) -> Unit,
) {
    val state = rememberDisappearingBarState()

    // Callers pin the chrome (e.g. large screens) through allowBarHide: bars never slide away on
    // scroll, and the immersive status-bar hiding stays off.
    val canHideBars = allowBarHide

    // Hold the latest values in state so the NSC's captured lambda stays fresh across
    // recompositions without rebuilding the NSC itself.
    val latestIsActive by rememberUpdatedState(isActive)
    val latestAllowBarHide by rememberUpdatedState(canHideBars)
    val latestImmersiveScrolling by rememberUpdatedState(immersiveScrolling)

    val connection =
        remember(state, isInvertedLayout) {
            DisappearingBarNestedScroll(
                state = state,
                canScroll = {
                    latestAllowBarHide &&
                        latestIsActive() &&
                        latestImmersiveScrolling()
                },
                reverseLayout = isInvertedLayout,
            )
        }

    // Only wire the lifecycle observer + system-bar control when the scaffold actually moves its bars.
    if (canHideBars) {
        ResetBarsOnResume(state)
        ImmersiveStatusBarEffect(state)
    }

    // If the bars were scrolled away when hiding got disabled (e.g. the window grew to a
    // large tier mid-scroll), nothing above can bring them back — the nested-scroll
    // connection and the resume reset are gone. Snap them visible here instead of
    // leaving the chrome stranded off-screen.
    LaunchedEffect(canHideBars, state) {
        if (!canHideBars) state.resetToVisible()
    }

    // When bars are pinned, skip attaching the nested-scroll connection entirely.
    // The outer Surface provides the Material container color + onBackground as
    // LocalContentColor, matching M3 Scaffold's behaviour (without it, default text
    // color falls back to Color.Black and is invisible on the dark theme).
    // Hoisted above the branch: imePaddingSafe() inside both arms would put the call in two
    // different composition groups, so toggling canHideBars (a window-size-class change) would
    // dispose and rebuild it. Resolved once here, and handed to ScaffoldLayout below so the value
    // this pads with is the same object the nav-bar subtraction reads.
    val imeInsets = rememberSafeImeInsets()
    val baseModifier =
        if (canHideBars) {
            Modifier.windowInsetsPadding(imeInsets).nestedScroll(connection)
        } else {
            Modifier.windowInsetsPadding(imeInsets)
        }
    val rootModifier =
        baseModifier
            // systemBars (not just statusBars) so a desktop window's caption bar is respected too.
            .let { if (topBar == null) it.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)) else it }
            .let { if (bottomBar == null) it.navigationBarsPadding() else it }

    Surface(
        modifier = rootModifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        ScaffoldLayout(
            state = state,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingButton = floatingButton,
            imeInsets = imeInsets,
            mainContent = mainContent,
        )
    }
}

@Composable
private fun ScaffoldLayout(
    state: DisappearingBarState,
    topBar: (@Composable () -> Unit)?,
    bottomBar: (@Composable () -> Unit)?,
    floatingButton: (@Composable () -> Unit)?,
    imeInsets: WindowInsets,
    mainContent: @Composable (padding: PaddingValues) -> Unit,
) {
    val navBarInsets = WindowInsets.navigationBars
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val topPlaceable =
            topBar?.let { bar ->
                subcompose(DisappearingSlot.Top) {
                    Column(
                        modifier =
                            Modifier.graphicsLayer {
                                translationY = state.topHeightOffset
                            },
                    ) {
                        bar()
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }.firstOrNull()?.measure(looseConstraints)
            }
        val topHeight = topPlaceable?.height ?: 0

        val bottomPlaceable =
            bottomBar?.let { bar ->
                subcompose(DisappearingSlot.Bottom) {
                    Column(
                        modifier =
                            Modifier.graphicsLayer {
                                translationY = -state.bottomHeightOffset
                            },
                    ) {
                        bar()
                    }
                }.firstOrNull()?.measure(looseConstraints)
            }
        // When the bar lambda is provided but its content emits nothing (e.g. AppBottomBar
        // hides itself on canPop entries, or while the keyboard is up), reserve the
        // system-nav-bar inset so the FAB and content stay clear of the navigation bar instead
        // of sliding under it. Subtract the IME inset: the root imePadding has already lifted the
        // whole scaffold above the keyboard, and the IME inset spans the nav-bar band, so
        // reserving the full nav bar on top of that would double-count and strand a gap above the
        // keyboard (the `bottomBar == null` branch gets this for free via navigationBarsPadding,
        // which excludes the consumed IME inset). The `bottomBar == null` case is on rootModifier.
        val bottomHeight =
            (bottomPlaceable?.height ?: 0).let { measured ->
                if (bottomBar != null && measured == 0) {
                    (navBarInsets.getBottom(this) - imeInsets.getBottom(this)).coerceAtLeast(0)
                } else {
                    measured
                }
            }

        // Publish the measured limits so the nested-scroll connection can clamp correctly.
        state.topHeightLimit = topHeight.toFloat()
        state.bottomHeightLimit = bottomHeight.toFloat()

        val contentPadding =
            PaddingValues(
                top = topHeight.toDp(),
                bottom = bottomHeight.toDp(),
            )

        val contentPlaceable =
            subcompose(DisappearingSlot.Content) {
                CompositionLocalProvider(
                    LocalDisappearingScaffoldPadding provides contentPadding,
                    LocalDisappearingBarState provides state,
                ) {
                    mainContent(contentPadding)
                }
            }.firstOrNull()?.measure(
                Constraints.fixed(layoutWidth, layoutHeight),
            )

        val fabPlaceable =
            floatingButton?.let { fab ->
                subcompose(DisappearingSlot.Fab) {
                    FloatingButtonHolder(state = state) { fab() }
                }.firstOrNull()?.measure(looseConstraints)
            }

        val fabEdgePx = FabEdgePadding.roundToPx()

        layout(layoutWidth, layoutHeight) {
            contentPlaceable?.place(0, 0)
            topPlaceable?.place(0, 0)
            bottomPlaceable?.place(0, layoutHeight - bottomHeight)
            if (fabPlaceable != null) {
                val x = layoutWidth - fabPlaceable.width - fabEdgePx
                val yBase = layoutHeight - bottomHeight - fabPlaceable.height - fabEdgePx
                fabPlaceable.place(x, yBase)
            }
        }
    }
}

/**
 * Holds the floating button. Scales / fades it with the bottom-bar collapse fraction and
 * rides along with the bar via graphicsLayer, so the layout pass is untouched while
 * the bar animates.
 */
@Composable
private fun FloatingButtonHolder(
    state: DisappearingBarState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier.graphicsLayer {
                val visible = (1f - state.bottomCollapsedFraction).coerceAtLeast(0.001f)
                scaleX = visible
                scaleY = visible
                alpha = visible
                translationY = -state.bottomHeightOffset
            },
    ) {
        content()
    }
}

@Composable
private fun ResetBarsOnResume(state: DisappearingBarState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, state) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME &&
                    (state.topHeightOffset != 0f || state.bottomHeightOffset != 0f)
                ) {
                    scope.launch { state.resetToVisible() }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Hides the OS status bar while the in-app chrome is scrolled away, and restores it when the chrome
 * returns or this leaves composition. Only Android has a status bar to hide; elsewhere a no-op.
 */
@Composable
internal expect fun ImmersiveStatusBarEffect(state: DisappearingBarState)
