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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStoragePrompts
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import kotlinx.coroutines.flow.StateFlow

/**
 * The desktop's key storage as the shared UI sees it: [SecureKeyStorage]'s status and prompts,
 * and the two fixes Settings › Key storage offers. [reopen] opens the keyring's vault again and
 * logs the default account in, as a cold start does.
 */
class DesktopKeyStorage(
    private val storage: SecureKeyStorage,
    override val prompts: KeyStoragePrompts,
    private val reopen: suspend () -> Unit,
) : KeyStorageController {
    override val status: StateFlow<KeyStorageStatus> = storage.status

    override suspend fun retry() {
        storage.resetKeyring()
        reopen()
    }

    override suspend fun unlock() {
        storage.unlockPasswordFile()
        reopen()
    }
}
