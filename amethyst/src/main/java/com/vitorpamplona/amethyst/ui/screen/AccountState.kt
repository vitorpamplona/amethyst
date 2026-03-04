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
package com.vitorpamplona.amethyst.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.components.getActivity

/**
 * Creates a new scope for the given ViewModel type.
 */
@Composable
fun SetAccountCentricViewModelStore(
    state: AccountState.LoggedIn,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current.getActivity()
    val vmStore: StoreOwnerRegistry = viewModel(viewModelStoreOwner = activity)
    vmStore.checkAttached(activity)

    val owner = vmStore.getOwner(state)

    val observer = remember { CompositionObserver(vmStore, state) }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        content = content,
    )
}

/**
 * This class is responsible for notifying the [StoreOwnerRegistry] when a composable is detached so
 * that the viewmodel can be cleared.
 */
class CompositionObserver(
    private val vmStore: StoreOwnerRegistry,
    private val key: Any,
) : RememberObserver {
    override fun onRemembered() {}

    override fun onForgotten() = vmStore.composableDetached(key)

    override fun onAbandoned() = vmStore.composableDetached(key)
}

/**
 * Registry for [ViewModelStoreOwner]s that are scoped to a particular composition.
 * This ViewModel is registered with the Activity's lifecycle and will clear the viewmodels.
 */
class StoreOwnerRegistry : ViewModel() {
    private var isActivityRegistered: Boolean = false
    private var isChangingConfigurations: Boolean = false
    private val map = mutableMapOf<Any, ViewModelStoreOwner>()

    override fun onCleared() {
        map.values.forEach { it.viewModelStore.clear() }
        super.onCleared()
    }

    fun getOwner(key: Any): ViewModelStoreOwner = map[key] ?: ScopedViewModelStoreOwner().also { map[key] = it }

    fun composableDetached(key: Any) {
        // TODO: This prevents the viewmodel from being cleared when the Composable is detached due
        //  to a configuration change. We need to make sure that the viewmodel is cleared when the
        //  Composition is recreated without the Composable. E.g. by observing the Composition
        if (isChangingConfigurations) return
        map.remove(key)?.also { owner -> owner.viewModelStore.clear() }
    }

    fun checkAttached(activity: ComponentActivity) {
        if (!isActivityRegistered) {
            isActivityRegistered = true
            activity.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        isChangingConfigurations = false
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        if (activity.isChangingConfigurations) {
                            isChangingConfigurations = true
                        }
                    }

                    override fun onDestroy(owner: LifecycleOwner) {
                        isActivityRegistered = false
                        owner.lifecycle.removeObserver(this)
                    }
                },
            )
        }
    }
}

/**
 * Simple ViewModelStoreOwner that can be used to create a new scope.
 */
class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
