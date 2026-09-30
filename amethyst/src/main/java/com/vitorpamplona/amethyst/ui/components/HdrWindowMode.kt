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

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import coil3.BitmapImage
import coil3.Image
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import java.util.WeakHashMap

/**
 * The images on one window that currently want HDR, and how much headroom each asks for.
 *
 * An Ultra HDR photo (a JPEG with a gain map) only renders brighter than SDR white while its
 * window is in [ActivityInfo.COLOR_MODE_HDR]; otherwise the platform silently draws the SDR base
 * image. The window is shared by every card in a feed, so HDR stays on until the last image
 * asking for it leaves composition.
 */
class HdrRequests {
    private val tokens = mutableListOf<Request>()

    private class Request(
        val headroom: Float,
    )

    val wantsHdr: Boolean get() = tokens.isNotEmpty()

    /** [UNCAPPED] if any request is uncapped, else the largest cap asked for. */
    val headroom: Float
        get() = if (tokens.any { it.headroom == UNCAPPED }) UNCAPPED else tokens.maxOfOrNull { it.headroom } ?: UNCAPPED

    fun add(headroom: Float): Any = Request(headroom).also { tokens.add(it) }

    fun remove(token: Any) {
        tokens.removeAll { it === token }
    }

    companion object {
        /** `Window.setDesiredHdrHeadroom`'s "no preference": the display's full HDR range. */
        const val UNCAPPED = 0f

        /**
         * Feed cards share the screen with SDR text and chrome, which full HDR brightness makes
         * look dim and grey. Cap them; the fullscreen viewer asks for [UNCAPPED].
         */
        const val FEED_HEADROOM = 2f
    }
}

private class HdrWindowState(
    val originalColorMode: Int,
) {
    val requests = HdrRequests()
}

// Main-thread only: composition and disposal both run there.
private val windowStates = WeakHashMap<Window, HdrWindowState>()

// Each setter dispatches the window attributes to the window manager even when the value is
// unchanged, and cards scroll in and out of a feed constantly: only write what actually changed.
private fun Window.applyHdr(state: HdrWindowState) {
    val mode = if (state.requests.wantsHdr) ActivityInfo.COLOR_MODE_HDR else state.originalColorMode
    if (colorMode != mode) colorMode = mode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        val headroom = if (state.requests.wantsHdr) state.requests.headroom else HdrRequests.UNCAPPED
        if (desiredHdrHeadroom != headroom) desiredHdrHeadroom = headroom
    }
}

fun Image.hasGainmap(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (this as? BitmapImage)?.bitmap?.hasGainmap() == true

/**
 * Puts the hosting window (the dialog's own window inside a `Dialog`) into HDR mode while this
 * is in composition and [image] carries a gain map, so an Ultra HDR photo shows its highlights.
 */
@Composable
fun RequestHdrFor(
    image: Image,
    headroom: Float,
) {
    if (!remember(image) { image.hasGainmap() }) return
    val window = getDialogWindow() ?: getActivityWindow() ?: return

    DisposableEffect(window, headroom) {
        val state = windowStates.getOrPut(window) { HdrWindowState(window.colorMode) }
        val token = state.requests.add(headroom)
        window.applyHdr(state)

        onDispose {
            state.requests.remove(token)
            window.applyHdr(state)
            if (!state.requests.wantsHdr) windowStates.remove(window)
        }
    }
}
