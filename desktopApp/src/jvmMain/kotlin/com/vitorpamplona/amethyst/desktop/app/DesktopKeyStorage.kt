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
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The desktop's key storage as the shared UI sees it: [SecureKeyStorage]'s status and prompts,
 * and the two fixes Key Backup offers. [reopen] opens the keyring's vault again and
 * logs the default account in, as a cold start does.
 *
 * Both fixes run in [scope], the app's, and the caller only waits for them: the screen that asked
 * goes away as the status changes (the "couldn't open your keychain" alert leaves the moment
 * [SecureKeyStorage.resetKeyring] clears the failure), and its own scope would cancel the login
 * that was meant to follow.
 */
class DesktopKeyStorage(
    private val storage: SecureKeyStorage,
    override val prompts: KeyStoragePrompts,
    private val scope: CoroutineScope,
    private val keepDeclinedKeys: suspend () -> Unit = {},
    private val reopen: suspend (returnTo: Route?) -> Unit,
) : KeyStorageController {
    override val status: StateFlow<KeyStorageStatus> = storage.status

    override suspend fun retry(returnTo: Route?) {
        scope
            .launch {
                storage.resetKeyring()
                reopen(returnTo)
            }.join()
    }

    // Then the keys this run's "Don't save" left out (the open account's) are kept after all.
    override suspend fun saveKeys() {
        scope
            .launch {
                storage.allowKeepingKeys()
                keepDeclinedKeys()
            }.join()
    }

    override suspend fun unlock(returnTo: Route?) {
        scope
            .launch {
                storage.unlockPasswordFile()
                reopen(returnTo)
            }.join()
    }
}
