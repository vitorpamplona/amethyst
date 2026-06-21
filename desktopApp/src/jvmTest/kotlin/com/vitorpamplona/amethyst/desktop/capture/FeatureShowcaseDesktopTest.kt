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
package com.vitorpamplona.amethyst.desktop.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.ui.LoginScreen
import io.mockk.mockk
import org.junit.Rule
import java.awt.image.BufferedImage
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Example showcase captures. Run headless with:
 * ```
 * xvfb-run --auto-servernum ./gradlew :desktopApp:test --tests '*FeatureShowcaseDesktopTest*'
 * ```
 * Artifacts land in `desktopApp/build/screenshots/`.
 *
 * This doubles as the executable proof that the capture harness works, and as the
 * copy-paste template a contributor/agent follows when highlighting a new feature:
 * render the real composable, drive it into the state worth showing, then
 * [saveScreenshot] a frame (PNG) or [writeAnimatedGif] a sequence of frames (clip).
 */
class FeatureShowcaseDesktopTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var tempDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("screenshot-showcase").toFile()
        File(tempDir, ".amethyst").mkdirs()
        val storage = mockk<SecureKeyStorage>(relaxed = true)
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    /** Captures a real shared screen to a PNG — the single-frame "screenshot" path. */
    @Test
    fun loginScreen_screenshot() {
        compose.setContent {
            MaterialTheme {
                LoginScreen(
                    accountManager = manager,
                    onLoginSuccess = {},
                )
            }
        }
        compose.onNodeWithText("Welcome to Amethyst").assertExists()

        val png = compose.onRoot().saveScreenshot("desktop-login-screen")
        assertTrue(png.exists() && png.length() > 0, "expected a non-empty PNG at $png")
    }

    /**
     * Captures frames across an interaction and stitches them into an animated GIF —
     * the "video clip" path. Uses a self-contained composable so the demo never breaks
     * when a real screen changes; on a real feature, render that feature instead.
     */
    @Test
    fun counter_clip() {
        compose.setContent {
            MaterialTheme {
                CounterDemo()
            }
        }

        val frames = mutableListOf<BufferedImage>()
        frames += compose.onRoot().toBufferedImage()
        repeat(3) {
            compose.onNodeWithText("Tap me").performClick()
            compose.waitForIdle()
            frames += compose.onRoot().toBufferedImage()
        }

        val gif = screenshotFile("desktop-counter-clip.gif")
        writeAnimatedGif(frames, gif, delayMs = 600)
        assertTrue(gif.exists() && gif.length() > 0, "expected a non-empty GIF at $gif")
    }
}

@androidx.compose.runtime.Composable
private fun CounterDemo() {
    var count by remember { mutableIntStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Taps: $count", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { count++ }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Tap me")
            }
        }
    }
}
