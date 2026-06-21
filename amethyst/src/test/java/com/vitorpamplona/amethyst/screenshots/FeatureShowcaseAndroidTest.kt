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
package com.vitorpamplona.amethyst.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboGif
import com.github.takahirom.roborazzi.captureRoboImage
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android-flavored feature captures via Robolectric + Roborazzi — no emulator, no device.
 * Renders composables through the real [AmethystTheme] and writes PNGs / animated GIFs.
 *
 * Run with:
 * ```
 * ./gradlew :amethyst:recordRoborazziPlayDebug --tests '*FeatureShowcaseAndroidTest*'
 * ```
 * Artifacts land in `amethyst/build/screenshots/`.
 *
 * compileSdk is 37, but Robolectric only ships framework jars up to API 36, so the
 * tests are pinned with `@Config(sdk = [36])`. NATIVE graphics mode is required for
 * Roborazzi to rasterize real pixels.
 *
 * This is both the executable proof of the harness and the copy-paste template for
 * highlighting a new Android feature: render it inside [AmethystTheme], then
 * `captureRoboImage` a frame or `captureRoboGif` a sequence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Stock Application stub — skip the real Amethyst.onCreate (WorkManager, Tor, Coil, …)
// which a headless screenshot render neither needs nor can boot.
@Config(sdk = [36], application = Application::class)
class FeatureShowcaseAndroidTest {
    @get:Rule
    val compose = createComposeRule()

    // captureRoboImage creates parent dirs itself, but the GIF writer does not — ensure it exists.
    @Before
    fun ensureOutputDir() {
        java.io.File("build/screenshots").mkdirs()
    }

    /** Single-frame screenshot of a themed composable. */
    @Test
    fun demo_screenshot() {
        compose.setContent {
            AmethystTheme(ThemeType.LIGHT) {
                CounterDemo()
            }
        }
        compose.onNodeWithText("Tap me").assertExists()

        compose.onRoot().captureRoboImage("build/screenshots/android-demo.png")
    }

    /** Animated-GIF clip: Roborazzi records a frame after each interaction in the block. */
    @Test
    fun demo_clip() {
        compose.setContent {
            AmethystTheme(ThemeType.LIGHT) {
                CounterDemo()
            }
        }

        compose.onRoot().captureRoboGif(compose, "build/screenshots/android-demo-clip.gif") {
            repeat(3) {
                compose.onNodeWithText("Tap me").performClick()
            }
        }
    }
}

@Composable
private fun CounterDemo() {
    var count by remember { mutableIntStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Taps: $count")
            Button(onClick = { count++ }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Tap me")
            }
        }
    }
}
