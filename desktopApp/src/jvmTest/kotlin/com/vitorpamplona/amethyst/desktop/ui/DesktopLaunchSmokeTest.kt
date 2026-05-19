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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import io.mockk.mockk
import org.junit.Rule
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Smoke test that verifies the desktop app's login screen renders.
 *
 * This exercises the full dependency graph that a fresh launch hits:
 * Compose + Skiko rendering, string resources, Material3 theming,
 * and AccountManager construction (Jackson, filesystem access).
 *
 * On Linux CI this requires xvfb (Skiko needs a display server).
 * On macOS/Windows it runs natively.
 */
class DesktopLaunchSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var tempDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("smoke-test").toFile()
        File(tempDir, ".amethyst").mkdirs()
        val storage = mockk<SecureKeyStorage>(relaxed = true)
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun loginScreenRenders() {
        compose.setContent {
            MaterialTheme {
                LoginScreen(
                    accountManager = manager,
                    onLoginSuccess = {},
                )
            }
        }

        compose.onNodeWithText("Welcome to Amethyst").assertExists()
        compose.onNodeWithText("A Nostr client for desktop").assertExists()
    }
}
