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
package androidx.core.view

import android.view.View
import android.view.Window
import com.vitorpamplona.amethyst.stubs.PlatformGaps

/**
 * JVM stand-in for androidx.core.view.WindowInsetsControllerCompat.
 *
 * There are no system bars on the desktop to hide or show: a window has a
 * title bar owned by the window manager, and "immersive fullscreen" is the
 * shell's fullscreen mode, reached through the desktop window rather than
 * through per-view insets. So this is declared unavailable rather than made to
 * look applied — a fullscreen video that quietly kept the chrome would be a
 * visible bug with no error behind it.
 *
 * The light-appearance flags are a different matter: they tell the system bars
 * to draw dark icons over a light background, and with no system bars there is
 * nothing for them to be wrong about. Those are recorded silently.
 */
class WindowInsetsControllerCompat(
    private val window: Window?,
    private val view: View?,
) {
    companion object {
        const val BEHAVIOR_DEFAULT = 1
        const val BEHAVIOR_SHOW_BARS_BY_TOUCH = 0
        const val BEHAVIOR_SHOW_BARS_BY_SWIPE = 1
        const val BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2
    }

    var systemBarsBehavior: Int = BEHAVIOR_DEFAULT

    var isAppearanceLightStatusBars: Boolean = false

    var isAppearanceLightNavigationBars: Boolean = false

    fun hide(types: Int) = declareUnavailable("hide")

    fun show(types: Int) = declareUnavailable("show")

    private fun declareUnavailable(action: String) =
        PlatformGaps.unavailable(
            "WindowInsetsControllerCompat.$action",
            "there are no system bars on the desktop; going fullscreen is the window manager's job, " +
                "so the desktop shell has to drive it through the window rather than through view insets",
        )
}
