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
package com.vitorpamplona.amethyst.commons.ui.privacylock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import com.vitorpamplona.amethyst.commons.privacylock.lockStateFor

/**
 * Wraps the Wallet route and gates entry behind the credential prompt.
 *
 * Behaviour mirrors [MessagesLockGate] — see that composable's KDoc for the
 * deep-link race, draft persistence, and leave-route semantics. Only the
 * [LockScope] and the lock-screen copy differ.
 *
 * Desktop apps use the platform-specific `DesktopWalletLockGate` (password
 * input inline, no async CredentialPrompter round-trip); Android + iOS
 * front ends use this composable directly.
 */
@Composable
fun WalletLockGate(content: @Composable () -> Unit) {
    val lockState = lockStateFor(LockScope.Wallet)
    val current by lockState.state.collectAsState()

    DisposableEffect(lockState) {
        onDispose { lockState.onLeaveRoute() }
    }

    when (current) {
        is LockState.Locked ->
            LockScreen(
                scope = LockScope.Wallet,
                title = "Wallet locked",
                subtitle = "Unlock to see your balance and send or receive sats.",
                unlockLabel = "Unlock",
            )
        else -> content()
    }
}
