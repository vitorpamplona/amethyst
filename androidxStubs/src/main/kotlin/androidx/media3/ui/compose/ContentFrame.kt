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
package androidx.media3.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.VideoEngine

/** Mirrors media3's surface-type constants; the JVM engine renders its own surface. */
const val SURFACE_TYPE_SURFACE_VIEW: Int = 1
const val SURFACE_TYPE_TEXTURE_VIEW: Int = 2

/**
 * JVM stand-in for media3's `ContentFrame`, the composable that shows a
 * player's video output.
 *
 * On Android this attaches the player to a SurfaceView or TextureView. On the
 * JVM the engine owns its own surface composable, so this asks the installed
 * [VideoEngine] to render the given player and falls back to the shutter — the
 * caller's placeholder — when no engine is installed or the player is not the
 * one currently bound to it. Amethyst already elects a single active video
 * through its own visibility mutex, which lines up with engines that can only
 * drive one surface at a time.
 */
@Composable
fun ContentFrame(
    player: Player?,
    modifier: Modifier = Modifier,
    surfaceType: Int = SURFACE_TYPE_SURFACE_VIEW,
    contentScale: ContentScale = ContentScale.Fit,
    shutter: @Composable () -> Unit = {},
) {
    Box(modifier) {
        val rendered = player != null && VideoEngine.installed?.RenderSurface(player, Modifier) == true
        if (!rendered) shutter()
    }
}
