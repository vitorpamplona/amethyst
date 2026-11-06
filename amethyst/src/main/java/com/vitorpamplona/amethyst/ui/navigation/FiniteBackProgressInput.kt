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
package com.vitorpamplona.amethyst.ui.navigation

import android.annotation.SuppressLint
import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput

/**
 * Hands the system back gesture to the activity's navigation dispatcher with a progress that is
 * always a number in `0..1`.
 *
 * Some OEM builds of Android 14 (seen on vivo) send a [BackEvent] whose `progress` is `NaN`.
 * androidx passes it through as is, and Navigation 3's `NavDisplay` seeks its
 * `SeekableTransitionState` to it, which requires a fraction in `0..1` and throws
 * `IllegalArgumentException: Expecting fraction between 0 and 1. Got NaN` on the main thread.
 *
 * `ComponentActivity`'s own input registers its callback at [OnBackInvokedDispatcher.PRIORITY_DEFAULT]
 * whenever a default-priority handler is enabled. This one registers under the same condition one
 * priority above, so the system sends the gesture here and never to the activity's callback beneath
 * it. Overlay handlers (dialogs, sheets) keep their own input at
 * [OnBackInvokedDispatcher.PRIORITY_OVERLAY], above both.
 */
@RequiresApi(34)
class FiniteBackProgressInput(
    private val invoker: OnBackInvokedDispatcher,
) : NavigationEventInput() {
    private var lastProgress = 0f

    private val callback =
        object : OnBackAnimationCallback {
            override fun onBackStarted(backEvent: BackEvent) {
                lastProgress = 0f
                dispatchOnBackStarted(finite(backEvent))
            }

            override fun onBackProgressed(backEvent: BackEvent) {
                dispatchOnBackProgressed(finite(backEvent))
            }

            override fun onBackInvoked() {
                dispatchOnBackCompleted()
            }

            override fun onBackCancelled() {
                dispatchOnBackCancelled()
            }
        }

    private var registered = false

    // BackEvent's swipe-edge constants are the ones NavigationEvent uses.
    @SuppressLint("WrongConstant")
    private fun finite(backEvent: BackEvent): NavigationEvent {
        val progress = finiteBackProgress(backEvent.progress, lastProgress)
        lastProgress = progress
        return NavigationEvent(
            swipeEdge = backEvent.swipeEdge,
            progress = progress,
            touchX = backEvent.touchX,
            touchY = backEvent.touchY,
            frameTimeMillis = if (Build.VERSION.SDK_INT >= 36) backEvent.frameTimeMillis else 0,
        )
    }

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) = setRegistered(hasEnabledHandlers)

    override fun onRemoved() = setRegistered(false)

    private fun setRegistered(register: Boolean) {
        if (register == registered) return
        if (register) {
            invoker.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT + 1, callback)
        } else {
            invoker.unregisterOnBackInvokedCallback(callback)
        }
        registered = register
    }
}

/**
 * [progress] clamped to `0..1`, or [last] when it is `NaN`, so a bad event holds the transition
 * where it was instead of making it jump.
 */
fun finiteBackProgress(
    progress: Float,
    last: Float,
): Float = if (progress.isNaN()) last else progress.coerceIn(0f, 1f)
