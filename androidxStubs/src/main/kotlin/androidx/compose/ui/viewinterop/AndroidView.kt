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
@file:Suppress("ktlint:standard:function-naming")

package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.stubs.PlatformGaps

/**
 * JVM stand-in for androidx.compose.ui.viewinterop.AndroidView.
 *
 * There is no Android View hierarchy behind a Compose Desktop window, so this
 * cannot draw. What it CAN do faithfully is run the composable's lifecycle: the
 * factory is remembered and called once, [update] runs on every recomposition,
 * and [onRelease] runs when the node leaves — exactly as on Android. Skipping
 * that would break the state the surrounding code reads back (the map's centre
 * and zoom, the listeners a factory registers), turning one missing rendering
 * into several silently wrong behaviours.
 *
 * So what is missing here is precisely the pixels, and that is reported. Every
 * caller in this app hosts something that needs its own desktop
 * implementation — an OSM tile renderer, a WebView, a WebRTC surface — and none
 * of them is a matter of porting the View.
 */
@Composable
fun <T : View> AndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = {},
    update: (T) -> Unit = {},
) {
    val context = LocalContext.current
    val view = remember(factory) { factory(context) }

    SideEffect { update(view) }

    DisposableEffect(view) {
        PlatformGaps.report(
            "AndroidView",
            "there is no Android View hierarchy to host on the desktop. The view's own lifecycle " +
                "still runs, so its state stays correct; what is missing is the drawing, and each " +
                "caller needs its own desktop rendering (an OSM tile renderer, a web view, a video " +
                "surface) rather than a port of the View.",
        )
        onDispose {
            onReset?.invoke(view)
            onRelease(view)
        }
    }

    Box(modifier)
}
