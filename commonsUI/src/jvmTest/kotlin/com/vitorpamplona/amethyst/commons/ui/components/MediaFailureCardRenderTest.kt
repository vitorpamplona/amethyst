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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.HttpException
import coil3.network.NetworkResponse
import com.vitorpamplona.amethyst.commons.service.MediaLoadFailure
import com.vitorpamplona.amethyst.commons.service.image.buildAmethystImageLoader
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import okhttp3.OkHttpClient
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders [MediaFailureCard] for every cause at every size, light and dark, to
 * `commonsUI/build/media-failure-card/`, and checks the classifier behind it.
 */
class MediaFailureCardRenderTest {
    private val url = "https://24242.io/4e910deea95eb38d4ca652d43c7c11b210b7c5b07d41898fd974da6b494d4fee.jpg"
    private val blurhash = "L4D9I?Im00-=F{?bRPM_Gws:ITR*"
    private val out = File("build/media-failure-card").apply { mkdirs() }

    @OptIn(DelicateCoilApi::class)
    @BeforeTest
    fun start() {
        // The app's loader, so the blurhash under each card draws as it does in the app.
        SingletonImageLoader.setUnsafe(
            buildAmethystImageLoader(
                context = PlatformContext.INSTANCE,
                diskCache = { null },
                memoryCache = { null },
                callFactory = { OkHttpClient() },
                blossomServerResolver = null,
                readAuth = null,
            ),
        )
    }

    @AfterTest
    fun stop() = SingletonImageLoader.reset()

    @Test
    fun theCauseFollowsTheError() {
        assertEquals(MediaLoadFailure.GONE, mediaLoadFailureOf(HttpException(NetworkResponse(code = 404))))
        assertEquals(MediaLoadFailure.GONE, mediaLoadFailureOf(HttpException(NetworkResponse(code = 410))))
        assertEquals(MediaLoadFailure.REFUSED, mediaLoadFailureOf(HttpException(NetworkResponse(code = 403))))
        assertEquals(MediaLoadFailure.PAYMENT_REQUIRED, mediaLoadFailureOf(HttpException(NetworkResponse(code = 402))))
        assertEquals(MediaLoadFailure.UNREACHABLE, mediaLoadFailureOf(HttpException(NetworkResponse(code = 503))))
        assertEquals(MediaLoadFailure.UNREACHABLE, mediaLoadFailureOf(UnknownHostException("24242.io")))
        assertEquals(MediaLoadFailure.UNREACHABLE, mediaLoadFailureOf(SocketTimeoutException()))
        assertEquals(MediaLoadFailure.NOT_MEDIA, mediaLoadFailureOf(IllegalStateException("Failed to decode")))
        assertEquals(MediaLoadFailure.NOT_MEDIA, mediaLoadFailureOf(DecodeException()))
        assertEquals(MediaLoadFailure.UNKNOWN, mediaLoadFailureOf(null))
    }

    /** Stands in for Android's ImageDecoder.DecodeException, an IOException whose bytes did arrive. */
    private class DecodeException : java.io.IOException("not an image")

    @Test
    fun rendersEveryCauseAtEverySize() {
        listOf(false, true).forEach { dark ->
            val scene =
                ImageComposeScene(width = 760, height = 1660, density = Density(1f)) {
                    AmethystPreviewTheme(dark = dark) {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MediaLoadFailure.entries.forEach { failure ->
                                    Text(failure.name, style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MediaFailureCard(url, failure, Modifier.size(300.dp, 200.dp), blurhash = blurhash, onRetry = {})
                                        MediaFailureCard(url, failure, Modifier.size(160.dp, 130.dp), animated = true, onRetry = {})
                                        MediaFailureCard(url, failure, Modifier.size(90.dp), blurhash = blurhash, onRetry = {})
                                    }
                                }
                                // No known shape: the compact row.
                                MediaFailureCard(url, MediaLoadFailure.GONE, Modifier.width(560.dp), onRetry = {})
                                MediaFailureCard(url, MediaLoadFailure.UNREACHABLE, Modifier.width(560.dp), animated = true, onRetry = {})
                            }
                        }
                    }
                }
            try {
                var image: Image? = null
                // The blurhash decodes off the composition: give it a few frames.
                repeat(20) {
                    image = scene.render(System.nanoTime())
                    Thread.sleep(50)
                }
                val png = image!!.encodeToData(EncodedImageFormat.PNG)!!.bytes
                val file = File(out, if (dark) "dark.png" else "light.png")
                file.writeBytes(png)
                assertTrue(file.length() > 10_000, "nothing rendered to $file")
            } finally {
                scene.close()
            }
        }
    }
}
