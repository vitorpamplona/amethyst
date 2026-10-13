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

import com.vitorpamplona.amethyst.commons.keystorage.KeyStoragePrompts
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test

class DesktopKeyStorageTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun tryAgainLogsInEvenWhenTheScreenThatAskedGoesAway() =
        runBlocking<Unit> {
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val reopenStarted = CompletableDeferred<Unit>()
            val keyringAnswered = CompletableDeferred<Unit>()
            val loggedIn = CompletableDeferred<Unit>()
            val controller =
                DesktopKeyStorage(SecureKeyStorage.create(folder.root), KeyStoragePrompts(), appScope) {
                    reopenStarted.complete(Unit)
                    keyringAnswered.await()
                    loggedIn.complete(Unit)
                }

            // The "couldn't open your keychain" alert launches Try again in its own scope, and
            // leaves the composition as soon as the failure clears, cancelling that scope.
            val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            alertScope.launch { controller.retry() }
            withTimeout(5_000) { reopenStarted.await() }
            alertScope.cancel()

            keyringAnswered.complete(Unit)
            withTimeout(5_000) { loggedIn.await() }
            appScope.cancel()
        }
}
