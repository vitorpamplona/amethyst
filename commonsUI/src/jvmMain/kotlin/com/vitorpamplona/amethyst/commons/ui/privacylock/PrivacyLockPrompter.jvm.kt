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
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.PasswordHasher
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.privacylock.enabledFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The desktop unlocks with an app password: there is no device-credential prompt to borrow. The
 * password is hashed with PBKDF2 ([PasswordHasher]) and only the hash is stored.
 */
@Composable
actual fun rememberPrivacyLockPrompter(settings: PrivacyLockSettings): CredentialPrompter = remember(settings) { PasswordPrompter(settings) }

private class PasswordPrompter(
    private val settings: PrivacyLockSettings,
) : CredentialPrompter {
    override val available: Boolean get() = settings.passwordHashed.value != null

    override val usesPassword: Boolean = true

    override suspend fun prompt(): PromptResult = PromptResult.Unavailable

    override suspend fun verifyPassword(password: CharArray): PromptResult =
        withContext(Dispatchers.Default) {
            val stored = settings.passwordHashed.value ?: return@withContext PromptResult.Unavailable
            if (PasswordHasher.verify(password, stored)) {
                // Brings a hash from before the 600k-iteration format up to date.
                if (PasswordHasher.isLegacyFormat(stored)) settings.setPasswordHashed(PasswordHasher.hash(password))
                PromptResult.Success
            } else {
                PromptResult.Failed
            }
        }

    override suspend fun changePassword(
        current: CharArray?,
        new: CharArray?,
    ): PromptResult =
        withContext(Dispatchers.Default) {
            val stored = settings.passwordHashed.value
            if (stored != null && (current == null || !PasswordHasher.verify(current, stored))) {
                return@withContext PromptResult.Failed
            }
            if (new == null) {
                // Nothing could unlock them any more: every lock goes off with the password.
                LockScope.entries.forEach { if (settings.enabledFor(it).value) settings.setScopeLocked(it, false) }
                settings.setPasswordHashed(null)
            } else {
                settings.setPasswordHashed(PasswordHasher.hash(new))
            }
            PromptResult.Success
        }
}
