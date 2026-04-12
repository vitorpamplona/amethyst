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

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

inline fun <reified T : Any> NavGraphBuilder.composableFromEnd(noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = { slideInHorizontallyFromEnd },
        exitTransition = { scaleOut },
        popEnterTransition = { scaleIn },
        popExitTransition = { slideOutHorizontallyToEnd },
        content = content,
    )
}

inline fun <reified T : Any> NavGraphBuilder.composableFromEndArgs(noinline content: @Composable AnimatedContentScope.(T) -> Unit) {
    composableFromEnd<T> {
        content(it.toRoute<T>())
    }
}

inline fun <reified T : Any> NavGraphBuilder.composableFromBottom(noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = { slideInVerticallyFromBottom },
        exitTransition = { scaleOut },
        popEnterTransition = { scaleIn },
        popExitTransition = { slideOutVerticallyToBottom },
        content = content,
    )
}

inline fun <reified T : Any> NavGraphBuilder.composableFromBottomArgs(noinline content: @Composable AnimatedContentScope.(T) -> Unit) {
    composableFromBottom<T> {
        content(it.toRoute())
    }
}

inline fun <reified T : Any> NavGraphBuilder.composableArgs(noinline content: @Composable AnimatedContentScope.(T) -> Unit) {
    composable<T> {
        content(it.toRoute())
    }
}

val slideInVerticallyFromBottom = slideInVertically(animationSpec = tween(), initialOffsetY = { it })
val slideOutVerticallyToBottom = slideOutVertically(animationSpec = tween(), targetOffsetY = { it })

val slideInHorizontallyFromEnd = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
val slideOutHorizontallyToEnd = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })

val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.9f)
val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.9f)
