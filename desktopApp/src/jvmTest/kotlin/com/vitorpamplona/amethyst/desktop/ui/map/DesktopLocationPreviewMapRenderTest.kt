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
package com.vitorpamplona.amethyst.desktop.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import okhttp3.OkHttpClient
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.awt.Color as AwtColor

/**
 * The desktop map draws OpenStreetMap-style tiles around a point with a pin on it. Tiles come from
 * a local server here, one solid colour, so the test checks which tiles were asked for and that
 * they cover the view. Set AMETHYST_MAP_LIVE=1 to also render against the real OSM tiles into
 * `desktopApp/build/desktop-map/` for a human to look at.
 */
@OptIn(DelicateCoilApi::class)
class DesktopLocationPreviewMapRenderTest {
    // Steak 'n Shake, Fort Wayne: the food and drink place the list item renderers were checked on.
    private val lat = 41.07390385
    private val lon = -85.2190326
    private val zoom = 16

    private lateinit var server: HttpServer
    private val requested = ConcurrentLinkedQueue<String>()
    private val outDir = File("build/desktop-map").apply { mkdirs() }

    private val tile: ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                g.color = AwtColor(0x2E, 0x7D, 0x32)
                g.fillRect(0, 0, 256, 256)
                g.dispose()
                ImageIO.write(image, "png", out)
            }.toByteArray()

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requested += exchange.requestURI.path
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, tile.size.toLong())
            exchange.responseBody.use { it.write(tile) }
        }
        server.start()
        SingletonImageLoader.setUnsafe { ctx: PlatformContext ->
            ImageLoader
                .Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
                .build()
        }
    }

    @AfterTest
    fun stop() {
        SingletonImageLoader.reset()
        server.stop(0)
    }

    private fun render(
        name: String,
        dark: Boolean,
        tileUrl: (Int, Int, Int) -> String,
        settle: () -> Boolean,
    ): Image {
        val density = 2f
        val scene =
            ImageComposeScene(width = (600 * density).toInt(), height = (324 * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = dark) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) {
                        DesktopLocationPreviewMap(
                            latitude = lat,
                            longitude = lon,
                            modifier = Modifier,
                            zoom = zoom.toDouble(),
                            aspectRatio = 2f,
                            pinColor = null,
                            pinEmoji = null,
                            pinAlpha = 1f,
                            tileUrl = tileUrl,
                        )
                    }
                }
            }
        try {
            var image = scene.render(0)
            for (frame in 1..100) {
                Thread.sleep(80)
                image = scene.render(frame * 80_000_000L)
                if (settle()) break
            }
            // A few more frames so the last tiles finish decoding.
            repeat(5) { image = scene.render((101 + it) * 80_000_000L) }
            File(outDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            return image
        } finally {
            scene.close()
        }
    }

    @Test
    fun asksForTheTilesAroundThePointAndCoversTheView() {
        val tiles = 1 shl zoom
        val centerX = floor(mercatorX(lon) * tiles).toInt()
        val centerY = floor(mercatorY(lat) * tiles).toInt()

        val image = render("local-tiles", dark = false, tileUrl = { z, x, y -> "http://127.0.0.1:${server.address.port}/$z/$x/$y.png" }) { requested.size >= 6 }

        assertTrue("/$zoom/$centerX/$centerY.png" in requested, "the tile under the point was never asked for: $requested")
        assertTrue(requested.all { it.startsWith("/$zoom/") }, "a tile from another zoom: $requested")
        // A 576x288 dp view over 256 dp tiles needs a 3x2 block at least.
        assertTrue(requested.toSet().size >= 6, "too few tiles for the view: $requested")

        // The tiles' green (muted by the light-mode filter, so green-dominant rather than exact)
        // fills most of the map.
        val pixels = image.peekPixels()!!
        var green = 0
        var total = 0
        for (y in 40 until image.height - 40 step 9) {
            for (x in 40 until image.width - 40 step 9) {
                val c = Color(pixels.getColor(x, y))
                total++
                if (c.green > c.red + 0.1f && c.green > c.blue + 0.1f) green++
            }
        }
        assertTrue(green > total / 2, "tiles cover only $green of $total sampled pixels")
    }

    @Test
    fun theWorldWrapsEastAndWest() {
        assertEquals(0.0, mercatorX(-180.0))
        assertEquals(0.5, mercatorX(0.0))
        assertEquals(0.5, mercatorY(0.0), 1e-12)
        assertTrue(mercatorY(85.1) == mercatorY(89.0), "the poles are clamped to Web Mercator's edge")
    }

    /** The real OSM tiles, for a human: AMETHYST_MAP_LIVE=1 ./gradlew :desktopApp:test --tests '*DesktopLocationPreviewMapRenderTest*'. */
    @Test
    fun liveOpenStreetMap() {
        if (System.getenv("AMETHYST_MAP_LIVE") == null) return
        SingletonImageLoader.setUnsafe { ctx: PlatformContext ->
            ImageLoader
                .Builder(ctx)
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = {
                                OkHttpClient
                                    .Builder()
                                    .addInterceptor {
                                        it.proceed(
                                            it
                                                .request()
                                                .newBuilder()
                                                .header("User-Agent", "Amethyst-Desktop/test")
                                                .build(),
                                        )
                                    }.build()
                            },
                        ),
                    )
                }.build()
        }
        val start = System.currentTimeMillis()
        render("live-light", dark = false, tileUrl = ::osmTileUrl) { System.currentTimeMillis() - start > 6000 }
        val start2 = System.currentTimeMillis()
        render("live-dark", dark = true, tileUrl = ::osmTileUrl) { System.currentTimeMillis() - start2 > 6000 }
    }
}
