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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarNestedScroll
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingBarState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberDisappearingBarState
import com.vitorpamplona.amethyst.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class DisappearingSlot { Top, Bottom, Content, Fab }

private val FabEdgePadding = 16.dp

@Composable
fun DisappearingScaffold(
    isInvertedLayout: Boolean,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingButton: (@Composable () -> Unit)? = null,
    accountViewModel: AccountViewModel,
    isActive: () -> Boolean = { true },
    allowBarHide: Boolean = true,
    mainContent: @Composable (padding: PaddingValues) -> Unit,
) {
    val state = rememberDisappearingBarState()

    // Large screens (rail / permanent drawer) pin the chrome: bars never slide away on
    // scroll, and the immersive status-bar hiding stays off.
    val canHideBars = allowBarHide && !LocalScreenLayout.current.isLargeScreen

    // Hold the latest values in state so the NSC's captured lambda stays fresh across
    // recompositions without rebuilding the NSC itself.
    val latestIsActive by rememberUpdatedState(isActive)
    val latestAllowBarHide by rememberUpdatedState(canHideBars)
    val latestAccountViewModel by rememberUpdatedState(accountViewModel)

    val connection =
        remember(state, isInvertedLayout) {
            DisappearingBarNestedScroll(
                state = state,
                canScroll = {
                    latestAllowBarHide &&
                        latestIsActive() &&
                        latestAccountViewModel.settings.isImmersiveScrollingActive()
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
    val baseModifier =
        if (canHideBars) {
            Modifier.imePadding().nestedScroll(connection)
        } else {
            Modifier.imePadding()
        }
    val rootModifier =
        baseModifier
            .let { if (topBar == null) it.statusBarsPadding() else it }
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
        // hides itself on canPop entries), reserve the system-nav-bar inset so the FAB and
        // content stay clear of the navigation bar instead of sliding under it. The
        // `bottomBar == null` branch is already handled by navigationBarsPadding on
        // rootModifier.
        val bottomHeight =
            (bottomPlaceable?.height ?: 0).let { measured ->
                if (bottomBar != null && measured == 0) navBarInsets.getBottom(this) else measured
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

/** Fraction at which the tracked in-app bar is considered fully settled into the hidden edge. */
private const val STATUS_BAR_HIDE_THRESHOLD = 0.999f

/**
 * Fraction the chrome must fall back below before the OS status bar reappears. Lower than
 * [STATUS_BAR_HIDE_THRESHOLD] on purpose: this hysteresis gap means the finger that stops a fast
 * scroll — which makes a tiny reverse reveal — does not immediately pop the status bar back. The
 * user has to deliberately scroll the chrome back by ~30% before the status bar returns.
 */
private const val STATUS_BAR_SHOW_THRESHOLD = 0.7f

/**
 * Hides the OS status bar once the tracked in-app bar has settled into the hidden edge, and shows it
 * the moment it starts coming back. The OS status bar is binary (cannot slide), so it is driven off
 * the collapse fraction with a near-1 threshold, which means it toggles on settle rather than
 * mid-drag.
 *
 * The status bar sits with the top in-app bar, so it tracks the top bar's collapse. The top and
 * bottom bars can collapse at different rates (each clamps to its own height), so tracking the top
 * fraction keeps the status bar in step with the chrome directly beneath it. On screens with no top
 * bar (topHeightLimit == 0) it falls back to the bottom bar, so the status bar still hides in sync
 * with the bottom navigation.
 *
 * Only the top status bar is touched; the bottom OS navigation bar is left alone.
 * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user swipe to peek the status bar while immersed.
 * The status bar is always restored on dispose so leaving an immersive screen can never strand a
 * hidden bar.
 */
@Composable
private fun ImmersiveStatusBarEffect(state: DisappearingBarState) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = getActivityWindow() ?: return
    val controller = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    LaunchedEffect(controller, state) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        var statusBarHidden = false
        snapshotFlow {
            if (state.topHeightLimit > 0f) state.topCollapsedFraction else state.bottomCollapsedFraction
        }.distinctUntilChanged()
            .collect { fraction ->
                // Hysteresis: hide once fully settled, but only show again after the chrome has
                // been pulled back past STATUS_BAR_SHOW_THRESHOLD. Between the two thresholds the
                // status bar keeps its current state, so a stray reverse reveal can't flip it.
                val shouldHide =
                    when {
                        fraction >= STATUS_BAR_HIDE_THRESHOLD -> true
                        fraction <= STATUS_BAR_SHOW_THRESHOLD -> false
                        else -> statusBarHidden
                    }
                if (shouldHide != statusBarHidden) {
                    statusBarHidden = shouldHide
                    if (shouldHide) {
                        controller.hide(WindowInsetsCompat.Type.statusBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.statusBars())
                    }
                }
            }
    }

    DisposableEffect(controller) {
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}
