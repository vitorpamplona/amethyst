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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
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

    val canScroll = {
        allowBarHide && isActive() && accountViewModel.settings.isImmersiveScrollingActive()
    }

    val connection =
        remember(state, isInvertedLayout) {
            DisappearingBarNestedScroll(
                state = state,
                canScroll = canScroll,
                reverseLayout = isInvertedLayout,
            )
        }

    ResetBarsOnResume(state)

    SubcomposeLayout(
        modifier =
            Modifier
                .imePadding()
                .nestedScroll(connection),
    ) { constraints ->
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
        val bottomHeight = bottomPlaceable?.height ?: 0

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
                mainContent(contentPadding)
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
                val visible = (1f - state.bottomCollapsedFraction).coerceAtLeast(0f)
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
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (state.topHeightOffset != 0f || state.bottomHeightOffset != 0f) {
                        scope.launch { state.resetToVisible() }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
