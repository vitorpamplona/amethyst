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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

/**
 * Provides a [androidx.lifecycle.ViewModelStoreOwner] scoped to the currently logged-in account so
 * that every ViewModel created under it (the [com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel]
 * and all of its children) lives and dies with that account.
 *
 * The owner is keyed by the account's public key via [key]. While the same account stays logged in,
 * the call site is stable, so the owner — and therefore the ViewModels — survives recompositions and
 * configuration changes (it is parented to the Activity's [LocalViewModelStoreOwner]). When the user
 * switches accounts the public key changes, the previous owner leaves the composition, and Lifecycle
 * 2.11's [rememberViewModelStoreOwner] clears its [androidx.lifecycle.ViewModelStore] immediately —
 * tearing down the old account's ViewModels instead of leaking them.
 *
 * This replaces a hand-rolled per-account ViewModelStore registry that could not clear stores around
 * configuration changes (see git history).
 */
@Composable
fun SetAccountCentricViewModelStore(
    state: AccountState.LoggedIn,
    content: @Composable () -> Unit,
) {
    key(state.account.signer.pubKey) {
        val owner = rememberViewModelStoreOwner()
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
            content = content,
        )
    }
}
