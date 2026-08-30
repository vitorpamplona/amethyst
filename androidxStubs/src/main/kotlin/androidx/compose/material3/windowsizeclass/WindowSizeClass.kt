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

package androidx.compose.material3.windowsizeclass

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * JVM stand-in for androidx.compose.material3.windowsizeclass.
 *
 * Implemented for real rather than stubbed, because this is the one API in the
 * app that already asks the right question for a desktop: "how wide is the
 * surface I am laying out in?" A desktop window crosses all three breakpoints
 * as the user drags its edge, so the answer has to be live and it has to be
 * true — a fixed Compact would give the desktop a phone's bottom bar, and a
 * fixed Expanded would give a narrow window a permanent drawer it cannot fit.
 *
 * The breakpoints are Material's own, so a layout decision made here matches
 * the one the Android build makes at the same width.
 */
@RequiresOptIn("WindowSizeClass is experimental in Material 3.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMaterial3WindowSizeClassApi

@JvmInline
value class WindowWidthSizeClass private constructor(
    private val value: Int,
) : Comparable<WindowWidthSizeClass> {
    override fun compareTo(other: WindowWidthSizeClass) = value.compareTo(other.value)

    override fun toString() =
        when (this) {
            Compact -> "Compact"
            Medium -> "Medium"
            else -> "Expanded"
        }

    companion object {
        val Compact = WindowWidthSizeClass(0)
        val Medium = WindowWidthSizeClass(1)
        val Expanded = WindowWidthSizeClass(2)

        internal fun fromWidth(width: androidx.compose.ui.unit.Dp) =
            when {
                width < 600.dp -> Compact
                width < 840.dp -> Medium
                else -> Expanded
            }
    }
}

@JvmInline
value class WindowHeightSizeClass private constructor(
    private val value: Int,
) : Comparable<WindowHeightSizeClass> {
    override fun compareTo(other: WindowHeightSizeClass) = value.compareTo(other.value)

    override fun toString() =
        when (this) {
            Compact -> "Compact"
            Medium -> "Medium"
            else -> "Expanded"
        }

    companion object {
        val Compact = WindowHeightSizeClass(0)
        val Medium = WindowHeightSizeClass(1)
        val Expanded = WindowHeightSizeClass(2)

        internal fun fromHeight(height: androidx.compose.ui.unit.Dp) =
            when {
                height < 480.dp -> Compact
                height < 900.dp -> Medium
                else -> Expanded
            }
    }
}

class WindowSizeClass private constructor(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
) {
    override fun equals(other: Any?) =
        other is WindowSizeClass &&
            widthSizeClass == other.widthSizeClass &&
            heightSizeClass == other.heightSizeClass

    override fun hashCode() = 31 * widthSizeClass.hashCode() + heightSizeClass.hashCode()

    override fun toString() = "WindowSizeClass($widthSizeClass, $heightSizeClass)"

    companion object {
        fun calculateFromSize(size: DpSize) =
            WindowSizeClass(
                WindowWidthSizeClass.fromWidth(size.width),
                WindowHeightSizeClass.fromHeight(size.height),
            )
    }
}

/**
 * The window's current size class, from the composition's own window rather
 * than the Activity that is passed in — there is no Activity behind a desktop
 * window, and [LocalWindowInfo] is both the true source and a live one, so this
 * recomposes when the user resizes.
 */
@ExperimentalMaterial3WindowSizeClassApi
@Composable
fun calculateWindowSizeClass(activity: Activity?): WindowSizeClass {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return WindowSizeClass.calculateFromSize(
        with(density) { DpSize(size.width.toDp(), size.height.toDp()) },
    )
}
