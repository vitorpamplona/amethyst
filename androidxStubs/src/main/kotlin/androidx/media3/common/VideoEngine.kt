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
package androidx.media3.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The service-provider seam between shared playback code and whatever actually
 * decodes video on this platform.
 *
 * Amethyst's Android build drives ExoPlayer directly; on the JVM there is no
 * ExoPlayer, so the desktop app installs an engine here (kdroidFilter's
 * ComposeMediaPlayer, which wraps Media Foundation on Windows, AVFoundation on
 * macOS and GStreamer on Linux) and the shared UI keeps talking to [Player].
 *
 * This lives in :androidxStubs rather than the desktop module because the
 * shared code must be able to name it; the implementation is installed at
 * startup from the other direction.
 */
interface VideoEngine {
    /** Creates a handle the shared UI can drive. */
    fun newPlayer(): Player

    /**
     * Draws [player]'s output. Returns false when this player is not the one
     * currently bound to the engine's surface, so the caller can show its
     * placeholder instead of a blank box — engines that can only decode one
     * stream at a time rely on this.
     */
    @Composable
    fun RenderSurface(
        player: Player,
        modifier: Modifier,
    ): Boolean

    companion object {
        @Volatile
        var installed: VideoEngine? = null
    }
}
