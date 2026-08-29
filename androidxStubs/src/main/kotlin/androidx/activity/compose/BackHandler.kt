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
package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * JVM stand-in for androidx.activity.compose's back-navigation handler.
 *
 * Desktop has no system back gesture, so there is nothing to intercept by
 * default. Rather than making this inert, handlers register with
 * [DesktopBackDispatcher] so the desktop shell can drive them from whatever it
 * decides back should be — Escape, a toolbar button, mouse button 4.
 */
@Composable
fun BackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val entry = { current() }
        DesktopBackDispatcher.push(entry)
        onDispose { DesktopBackDispatcher.remove(entry) }
    }
}

/**
 * The stack of active back handlers, innermost last — the same discipline
 * Android's OnBackPressedDispatcher uses, so handlers fire in the order their
 * authors expect.
 */
object DesktopBackDispatcher {
    private val handlers = ArrayDeque<() -> Unit>()

    val hasHandlers: Boolean get() = handlers.isNotEmpty()

    internal fun push(handler: () -> Unit) {
        handlers.addLast(handler)
    }

    internal fun remove(handler: () -> Unit) {
        handlers.remove(handler)
    }

    /** Invokes the innermost handler. Returns false when nothing handled it. */
    fun dispatch(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

/** JVM stand-in for `LocalActivity`; desktop has no Activity, so it is absent. */
val LocalActivity: ProvidableCompositionLocal<Any?> = staticCompositionLocalOf { null }

/** Present so call sites compile; edge-to-edge is an Android window concern. */
fun enableEdgeToEdge() = Unit

@Composable
internal fun ProvideNothing(content: @Composable () -> Unit) = CompositionLocalProvider(content = content)
