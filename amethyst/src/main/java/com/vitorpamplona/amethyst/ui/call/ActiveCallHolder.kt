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
package com.vitorpamplona.amethyst.ui.call

import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.service.call.CallController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Holds references to the active call state so that [CallActivity] can access
 * the same [CallManager] and [CallController] owned by [AccountViewModel] in
 * the main activity. Both activities run in the same process.
 */
object ActiveCallHolder {
    var callManager: CallManager? = null
        private set
    var callController: CallController? = null
        private set
    var accountViewModel: AccountViewModel? = null
        private set

    fun set(
        callManager: CallManager,
        callController: CallController?,
        accountViewModel: AccountViewModel,
    ) {
        this.callManager = callManager
        this.callController = callController
        this.accountViewModel = accountViewModel
    }

    fun clear() {
        callManager = null
        callController = null
        accountViewModel = null
    }
}
