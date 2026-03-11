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
package com.vitorpamplona.amethyst.desktop.account

import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountManagerBunkerLoginTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var amethystDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        tempDir = createTempDirectory("acctmgr-bunker-test").toFile()
        amethystDir = File(tempDir, ".amethyst")
        amethystDir.mkdirs()
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun hasBunkerAccountReturnsFalseWhenNoFile() {
        assertFalse(manager.hasBunkerAccount())
    }

    @Test
    fun hasBunkerAccountReturnsTrueWhenFileExists() {
        File(amethystDir, "bunker_uri.txt").writeText("bunker://${"a".repeat(64)}?relay=wss://r.com")
        assertTrue(manager.hasBunkerAccount())
    }

    @Test
    fun setConnectingRelaysUpdatesState() {
        manager.setConnectingRelays()
        assertTrue(manager.accountState.value is AccountState.ConnectingRelays)
    }
}
