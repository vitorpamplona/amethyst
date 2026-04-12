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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.network.IHttpClientBuilder
import com.vitorpamplona.amethyst.commons.ui.components.toasts.IToastManager
import com.vitorpamplona.amethyst.commons.ui.screen.IUiSettingsState
import kotlinx.coroutines.CoroutineScope

/**
 * Platform-agnostic interface for AccountViewModel.
 *
 * Exposes the most commonly used members of AccountViewModel using only
 * commons-compatible types. Composables in the commons module can depend
 * on this interface instead of the concrete Android AccountViewModel,
 * enabling incremental migration to KMP.
 *
 * Builds on #2285 (viewModelScope) and #2287 (account).
 */
interface IAccountViewModel {
    /** The underlying account abstraction. */
    val account: IAccount

    /** CoroutineScope tied to the ViewModel lifecycle. */
    val accountViewModelScope: CoroutineScope

    /** UI settings (connectivity-aware display preferences). 78 usages / 38 files. */
    val settings: IUiSettingsState

    /** Toast/snackbar manager. 119 usages / 48 files. */
    val toastManager: IToastManager

    /** Role-based HTTP client builder (proxy/Tor routing). 20 usages / 6 files. */
    val httpClientBuilder: IHttpClientBuilder
}
