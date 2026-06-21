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

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import java.awt.image.BufferedImage
import java.io.File

/**
 * Dependency-free screenshot / animated-GIF capture for the Compose Desktop app.
 *
 * The whole point of this harness is to let a contributor (or an automated agent)
 * render any shared composable into a PNG — or a short animated GIF "clip" — that
 * highlights a feature, without an emulator, a display, or any third-party library.
 * It rides on the existing [androidx.compose.ui.test.junit4.v2.createComposeRule]
 * test infrastructure, so it runs headless under `xvfb-run ./gradlew :desktopApp:test`
 * exactly like the smoke test does.
 *
 * Output lands in [screenshotDir] (default `desktopApp/build/screenshots/`, override
 * with `-Damethyst.screenshot.dir=...`). PNG encoding uses the JDK's bundled ImageIO
 * — no Roborazzi/Paparazzi/ffmpeg required on the desktop side.
 *
 * Usage from a test that already has a `compose` rule and rendered content:
 * ```
 * compose.onRoot().saveScreenshot("home-feed")            // single frame -> PNG
 * ```
 * For a clip, collect frames across interactions and stitch them:
 * ```
 * val frames = mutableListOf<BufferedImage>()
 * frames += compose.onRoot().toBufferedImage()
 * compose.onNodeWithText("Post").performClick(); compose.waitForIdle()
 * frames += compose.onRoot().toBufferedImage()
 * writeAnimatedGif(frames, screenshotFile("compose-post.gif"))
 * ```
 */
val screenshotDir: File by lazy {
    File(System.getProperty("amethyst.screenshot.dir") ?: "build/screenshots")
        .apply { mkdirs() }
}

/** Resolves a capture artifact path inside [screenshotDir], creating parents. */
fun screenshotFile(fileName: String): File = File(screenshotDir, fileName).apply { parentFile?.mkdirs() }

/** Captures this node (use `compose.onRoot()` for the whole window) into an AWT image. */
fun SemanticsNodeInteraction.toBufferedImage(): BufferedImage = captureToImage().toAwtImage()

/**
 * Captures this node into `<[screenshotDir]>/<[name]>.png` and returns the written file.
 * Pass `compose.onRoot()` to grab the entire rendered surface.
 */
fun SemanticsNodeInteraction.saveScreenshot(name: String): File {
    val file = screenshotFile("$name.png")
    writePng(toBufferedImage(), file)
    return file
}
