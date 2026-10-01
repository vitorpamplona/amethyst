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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import kotlin.reflect.KClass

/**
 * The store that outlives every navigation destination in this window: Android's Activity. The
 * platform entry point provides it above the nav host, whose destinations each get a store of
 * their own.
 */
val LocalWindowViewModelStoreOwner: ProvidableCompositionLocal<ViewModelStoreOwner?> = staticCompositionLocalOf { null }

/**
 * A view model shared by every destination of this window, for state that has to survive
 * navigating between screens (a draft filled in over two screens, a game the lobby and the board
 * both watch). Without a window store it falls back to [rememberViewModel], scoped to the caller.
 */
@Composable
fun <VM : ViewModel> rememberWindowViewModel(
    modelClass: KClass<VM>,
    key: String,
    factory: ViewModelProvider.Factory,
): VM {
    val owner = LocalWindowViewModelStoreOwner.current ?: return rememberViewModel(modelClass, key, factory)
    return remember(owner, key) { ViewModelProvider.create(owner, factory).get(key, modelClass) }
}

@Composable
inline fun <reified VM : ViewModel> rememberWindowViewModel(
    key: String,
    factory: ViewModelProvider.Factory,
): VM = rememberWindowViewModel(VM::class, key, factory)

@Composable
inline fun <reified VM : ViewModel> rememberWindowViewModel(
    key: String,
    noinline factory: () -> VM,
): VM = rememberWindowViewModel(VM::class, key, rememberLambdaFactory(factory))
