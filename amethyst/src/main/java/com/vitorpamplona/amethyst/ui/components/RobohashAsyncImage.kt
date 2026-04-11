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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.ui.components.RobohashAsyncImage as CommonsRobohashAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage as CommonsRobohashFallbackAsyncImage

// Backward-compat wrappers: RobohashAsyncImage moved to commons

@Composable
fun RobohashAsyncImage(
    robot: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadRobohash: Boolean,
) = CommonsRobohashAsyncImage(robot, modifier, contentDescription, loadRobohash)

@Composable
fun RobohashFallbackAsyncImage(
    robot: String,
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
) = CommonsRobohashFallbackAsyncImage(
    robot,
    model,
    contentDescription,
    modifier,
    alignment,
    contentScale,
    alpha,
    colorFilter,
    filterQuality,
    loadProfilePicture,
    loadRobohash,
)
