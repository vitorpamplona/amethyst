/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.model.AccountSettings

sealed class AccountState {
    object Loading : AccountState()

    object LoggedOff : AccountState()

    @Stable
    class LoggedIn(
        val accountSettings: AccountSettings,
        var route: String? = null,
    ) : AccountState() {
        val currentViewModelStore = AccountCentricViewModelStore()
    }
}

@Composable
fun SetAccountCentricViewModelStore(
    state: AccountState.LoggedIn,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides state.currentViewModelStore,
    ) {
        content()
    }

    DisposableEffect(key1 = state) {
        onDispose {
            state.currentViewModelStore.viewModelStore.clear()
        }
    }
}

class AccountCentricViewModelStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
