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
package com.vitorpamplona.amethyst.desktop.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import com.vitorpamplona.amethyst.commons.privacylock.lockStateFor

/**
 * Desktop equivalent of `MessagesLockGate`. Uses synchronous password
 * verification (no async CredentialPrompter round-trip needed) via the
 * shared [DesktopLockScreen] surface.
 *
 * Branch selection is SYNCHRONOUS in composition — no LaunchedEffect
 * guard — closing the deep-link race (plan §Security Hardening H1).
 *
 * @param onOpenSettings optional deep-link into the Settings screen used
 *   when the user cleared the master password while the Messages lock was
 *   still toggled on. When null, the fallback "Disable lock" button is
 *   offered instead.
 */
@Composable
fun DesktopMessagesLockGate(
    onOpenSettings: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val lockState = lockStateFor(LockScope.Messages)
    val current by lockState.state.collectAsState()

    DisposableEffect(lockState) {
        onDispose { lockState.onLeaveRoute() }
    }

    when (current) {
        is LockState.Locked ->
            DesktopLockScreen(
                scope = LockScope.Messages,
                title = "Messages locked",
                subtitle = "Enter your privacy-lock password to view messages.",
                onNoPasswordAction = onOpenSettings,
            )
        else -> content()
    }
}
