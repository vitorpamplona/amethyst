/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import kotlin.math.abs

@Composable
fun DisappearingScaffold(
    isInvertedLayout: Boolean,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingButton: (@Composable () -> Unit)? = null,
    accountViewModel: AccountViewModel,
    mainContent: @Composable (padding: PaddingValues) -> Unit,
) {
    val shouldShow = remember { mutableStateOf(true) }

    val modifier =
        if (accountViewModel.settings.automaticallyHideNavigationBars == BooleanType.ALWAYS) {
            val bottomBarHeightPx = with(LocalDensity.current) { 50.dp.roundToPx().toFloat() }
            val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

            val nestedScrollConnection =
                remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            val newOffset = bottomBarOffsetHeightPx.floatValue + available.y

                            if (accountViewModel.settings.automaticallyHideNavigationBars == BooleanType.ALWAYS) {
                                val newBottomBarOffset =
                                    if (!isInvertedLayout) {
                                        newOffset.coerceIn(-bottomBarHeightPx, 0f)
                                    } else {
                                        newOffset.coerceIn(0f, bottomBarHeightPx)
                                    }

                                if (newBottomBarOffset != bottomBarOffsetHeightPx.floatValue) {
                                    bottomBarOffsetHeightPx.floatValue = newBottomBarOffset
                                }
                            } else {
                                if (abs(bottomBarOffsetHeightPx.floatValue) > 0.1) {
                                    bottomBarOffsetHeightPx.floatValue = 0f
                                }
                            }

                            val newShouldShow = abs(bottomBarOffsetHeightPx.floatValue) < bottomBarHeightPx / 2.0f

                            if (shouldShow.value != newShouldShow) {
                                shouldShow.value = newShouldShow
                            }

                            return Offset.Zero
                        }
                    }
                }

            // resets bar on resume
            val lifeCycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifeCycleOwner) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            bottomBarOffsetHeightPx.floatValue = 0f
                            shouldShow.value = true
                        }
                    }

                lifeCycleOwner.lifecycle.addObserver(observer)
                onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
            }

            Modifier
                .imePadding()
                .nestedScroll(nestedScrollConnection)
        } else {
            Modifier
                .imePadding()
        }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            bottomBar?.let {
                AnimatedContent(
                    targetState = shouldShow.value,
                    transitionSpec = AnimatedContentTransitionScope<Boolean>::bottomBarTransitionSpec,
                    label = "BottomBarAnimatedContent",
                ) { isVisible ->
                    if (isVisible) {
                        it()
                    }
                }
            }
        },
        topBar = {
            topBar?.let {
                AnimatedContent(
                    targetState = shouldShow.value,
                    transitionSpec = AnimatedContentTransitionScope<Boolean>::topBarTransitionSpec,
                    label = "TopBarAnimatedContent",
                ) { isVisible ->
                    if (isVisible) {
                        Column {
                            it()
                            HorizontalDivider(thickness = DividerThickness)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            floatingButton?.let {
                AnimatedVisibility(
                    visible = shouldShow.value,
                    enter = remember { scaleIn() },
                    exit = remember { scaleOut() },
                ) {
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = 55.dp, minHeight = 55.dp),
                    ) {
                        floatingButton()
                    }
                }
            }
        },
        content = mainContent,
    )
}

@OptIn(ExperimentalAnimationApi::class)
private fun <S> AnimatedContentTransitionScope<S>.topBarTransitionSpec(): ContentTransform = topBarAnimation

@OptIn(ExperimentalAnimationApi::class)
private fun <S> AnimatedContentTransitionScope<S>.bottomBarTransitionSpec(): ContentTransform = bottomBarAnimation

@ExperimentalAnimationApi
val topBarAnimation: ContentTransform =
    slideInVertically { height -> 0 } togetherWith slideOutVertically { height -> 0 }

val bottomBarAnimation: ContentTransform =
    slideInVertically { height -> height } togetherWith slideOutVertically { height -> height }
