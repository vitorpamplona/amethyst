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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val EDGE_ZONE_WIDTH = 48.dp

fun Modifier.zonedDrawerSwipe(
    pagerState: PagerState,
    openDrawer: () -> Unit,
): Modifier =
    composed {
        val edgeZonePx = with(LocalDensity.current) { EDGE_ZONE_WIDTH.toPx() }

        var gestureStartX by remember { mutableFloatStateOf(0f) }
        var gestureStartPage by remember { mutableIntStateOf(0) }
        var drawerOpened by remember { mutableStateOf(false) }

        val connection =
            remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        if (drawerOpened) return Offset(available.x, 0f)

                        // available.x > 0 means user is swiping right
                        if (available.x > 0f) {
                            val wasOnFirstPage = gestureStartPage == 0
                            val isInEdgeZone = gestureStartX < edgeZonePx

                            if (wasOnFirstPage || !isInEdgeZone) {
                                drawerOpened = true
                                openDrawer()
                                return Offset(available.x, 0f)
                            }
                        }
                        return Offset.Zero
                    }
                }
            }

        this
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    gestureStartX = down.position.x
                    gestureStartPage = pagerState.currentPage
                    drawerOpened = false
                }
            }.nestedScroll(connection)
    }
