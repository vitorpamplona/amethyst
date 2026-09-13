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
 * Wraps the Messages route and gates entry behind the credential prompt.
 *
 * Branch selection happens SYNCHRONOUSLY in composition — no
 * [androidx.compose.runtime.LaunchedEffect] guard — so the chat content
 * composable never enters composition while [LockState.Locked]. Closes the
 * deep-link race (plan §Security Hardening H1).
 *
 * The gate is an overlay, NOT a wrapper that disposes content. While
 * locked, the [content] lambda is not invoked at all; on unlock, the
 * lambda is invoked fresh. This means TextField drafts that use
 * `rememberSaveable` survive a lock cycle (SavedStateRegistry-backed).
 * For plain `remember` state, drafts are cleared — accept this trade-off.
 *
 * The gate also fires
 * [com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockState.onLeaveRoute]
 * from its [DisposableEffect.onDispose] block, so navigating away locks
 * immediately.
 */
@Composable
fun MessagesLockGate(content: @Composable () -> Unit) {
    val lockState = lockStateFor(LockScope.Messages)
    val current by lockState.state.collectAsState()

    DisposableEffect(lockState) {
        onDispose { lockState.onLeaveRoute() }
    }

    when (current) {
        is LockState.Locked ->
            LockScreen(
                scope = LockScope.Messages,
                title = "Messages locked",
                subtitle = "Unlock to read or send messages.",
                unlockLabel = "Unlock",
            )
        else -> content()
    }
}
