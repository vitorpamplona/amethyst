/**
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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class KeyboardState {
    Opened,
    Closed,
}

@Composable
fun keyboardAsState(): State<KeyboardState> {
    val view = LocalView.current

    val keyboardState = remember(view) { mutableStateOf(isKeyboardOpen(view)) }

    DisposableEffect(view) {
        val onGlobalListener =
            ViewTreeObserver.OnGlobalLayoutListener {
                val newKeyboardValue = isKeyboardOpen(view)

                if (newKeyboardValue != keyboardState.value) {
                    keyboardState.value = newKeyboardValue
                }
            }
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalListener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalListener) }
    }

    return keyboardState
}

fun isKeyboardOpen(view: View): KeyboardState {
    val rect = Rect()
    view.getWindowVisibleDisplayFrame(rect)
    val screenHeight = view.rootView.height
    val keypadHeight = screenHeight - rect.bottom

    return if (keypadHeight > screenHeight * 0.15) {
        KeyboardState.Opened
    } else {
        KeyboardState.Closed
    }
}
