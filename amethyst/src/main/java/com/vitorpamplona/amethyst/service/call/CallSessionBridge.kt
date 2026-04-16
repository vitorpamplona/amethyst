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
package com.vitorpamplona.amethyst.service.call

import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Process-level singleton that bridges the active [CallManager] and
 * [AccountViewModel] between the main activity and
 * [com.vitorpamplona.amethyst.ui.call.CallActivity] (which runs in its
 * own window but in the same process).
 *
 * No call controller / session is held here — each [CallActivity]
 * creates and owns its own [com.vitorpamplona.amethyst.ui.call.session.CallSession]
 * whose lifetime is tied to the Activity's lifecycle.
 */
object CallSessionBridge {
    var callManager: CallManager? = null
        private set
    var accountViewModel: AccountViewModel? = null
        private set

    fun set(
        callManager: CallManager,
        accountViewModel: AccountViewModel,
    ) {
        this.callManager = callManager
        this.accountViewModel = accountViewModel
    }

    fun clear() {
        callManager = null
        accountViewModel = null
    }
}
