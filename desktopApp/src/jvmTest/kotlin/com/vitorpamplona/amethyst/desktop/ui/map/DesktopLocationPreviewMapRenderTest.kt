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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GeocacheMapMark
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.geocacheMapMarks
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.geocachePin
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import okhttp3.OkHttpClient
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import kotlin.math.abs
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
                // Coil keeps a shared disk cache by default: a tile cached by another run would be served instead.
                .diskCache(null)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
                .build()
        }
    }

    /**
     * OSM serves its "Access blocked" tile to a client that doesn't say who it is (OkHttp's default
     * User-Agent included), so the live renders identify themselves, as the app's image client does.
     */
    private fun useIdentifiedLoader() {
        // The setup's loader may already be built; a new factory alone would not replace it.
        SingletonImageLoader.reset()
        SingletonImageLoader.setUnsafe { ctx: PlatformContext ->
            ImageLoader
                .Builder(ctx)
                // Coil keeps a shared disk cache by default: a tile cached by another run would be served instead.
                .diskCache(null)
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
        assertEquals(41.0739, latitudeOf(mercatorY(41.0739)), 1e-9)
        assertEquals(-85.219, longitudeOf(mercatorX(-85.219)), 1e-9)
        // A longitude past the antimeridian is the same place one world over.
        assertEquals(0.0, mercatorX(180.0))
        assertEquals(mercatorX(-170.0), mercatorX(190.0), 1e-12)
    }

    /** A notch zooms a level; a trackpad's run of small deltas zooms one level per beat, not one per delta. */
    @Test
    fun theWheelStepsOneLevelAtATime() {
        val wheel = WheelSteps()
        assertEquals(1, wheel.step(-1f, 5_000))
        assertEquals(null, wheel.step(-1f, 5_050), "too soon after the last step")
        assertEquals(-1, wheel.step(2f, 5_200))
        val trackpad = WheelSteps()
        val steps = (0 until 40).mapNotNull { trackpad.step(-0.3f, 10_000L + it * 8) }
        assertEquals(listOf(1, 1, 1), steps)
    }

    /** Panning stops where the world does: a pole never reaches the middle of the view. */
    @Test
    fun panningStopsAtThePoles() {
        val state = DesktopMapState(0.0, 0.0, 3)
        val world = 8 * 256.0
        state.panBy(0.0, 1e9, world, 400f)
        assertEquals(200 / world, state.centerY, 1e-12)
        state.panBy(0.0, -1e9, world, 400f)
        assertEquals(1 - 200 / world, state.centerY, 1e-12)
        assertTrue(state.positioned, "a pan places the map, so a first pin doesn't pull it back")
    }

    private fun renderScene(
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ): Image {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) { AmethystPreviewTheme(dark = false) { content() } }
        try {
            var image = scene.render(0)
            repeat(10) {
                Thread.sleep(40)
                image = scene.render((it + 1) * 40_000_000L)
            }
            return image
        } finally {
            scene.close()
        }
    }

    private fun Image.countPixels(color: Color): Int {
        val pixels = peekPixels()!!
        var n = 0
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val c = Color(pixels.getColor(x, y))
                if (abs(c.red - color.red) < 0.05f && abs(c.green - color.green) < 0.05f && abs(c.blue - color.blue) < 0.05f) n++
            }
        }
        return n
    }

    /** At zoom 3 the world is under two views wide: a pin across the antimeridian from the centre still shows. */
    @Test
    fun aPinAcrossTheAntimeridianShows() {
        val red = Color(0xFFE53935)
        val image =
            renderScene(1200, 400) {
                val state = remember { DesktopMapState(0.0, 144.0, 3) }
                DesktopTileMap(state, listOf(MapMarker("p", 0.0, -137.0, red)), Modifier.fillMaxSize(), tileUrl = { z, x, y -> "http://127.0.0.1:${server.address.port}/$z/$x/$y.png" })
            }
        assertTrue(image.countPixels(red) > 50, "the pin east of the antimeridian is missing")
    }

    /** Coordinates that aren't numbers ("NaN" parses as one) draw no pin and break nothing. */
    @Test
    fun coordinatesThatAreNoNumbersDrawNoPin() {
        val red = Color(0xFFE53935)
        val image =
            renderScene(600, 300) {
                DesktopLocationPreviewMap(
                    latitude = Double.NaN,
                    longitude = Double.POSITIVE_INFINITY,
                    modifier = Modifier,
                    zoom = 16.0,
                    aspectRatio = 2f,
                    pinColor = red,
                    pinEmoji = null,
                    pinAlpha = 1f,
                    tileUrl = { z, x, y -> "http://127.0.0.1:${server.address.port}/$z/$x/$y.png" },
                )
            }
        assertEquals(0, image.countPixels(red))
    }

    /** The real OSM tiles, for a human: AMETHYST_MAP_LIVE=1 ./gradlew :desktopApp:test --tests '*DesktopLocationPreviewMapRenderTest*'. */
    @Test
    fun liveOpenStreetMap() {
        if (System.getenv("AMETHYST_MAP_LIVE") == null) return
        useIdentifiedLoader()
        val start = System.currentTimeMillis()
        render("live-light", dark = false, tileUrl = ::osmTileUrl) { System.currentTimeMillis() - start > 6000 }
        val start2 = System.currentTimeMillis()
        render("live-dark", dark = true, tileUrl = ::osmTileUrl) { System.currentTimeMillis() - start2 > 6000 }
    }

    private fun cache(
        n: Int,
        lat: Double,
        lon: Double,
    ) = GeocacheListingEvent(
        id = n.toString().padStart(64, '0'),
        pubKey = "a".repeat(64),
        createdAt = 1L,
        tags = arrayOf(arrayOf("d", "cache-$n"), arrayOf("g", GeoHash.encode(lat, lon, 9).toString())),
        content = "",
        sig = "b".repeat(128),
    )

    /**
     * The geocache tab's pins (shared marks and colours, `geocacheMapMarks` and `geocachePin`)
     * on the desktop map: a busy cluster drawn as a count, and the caches around it as their own pins.
     */
    @Test
    fun geocachePinsAndClusters() {
        val city = (0 until 60).map { cache(it, 41.07 + (it % 6) * 0.0008, -85.21 + (it / 6) * 0.0008) }
        val loners = listOf(cache(100, 41.10, -85.30), cache(101, 41.04, -85.12))
        val listings = city + loners
        var live = Color.Unspecified
        val liveTiles = System.getenv("AMETHYST_MAP_LIVE") != null
        if (liveTiles) useIdentifiedLoader()
        val tileUrl = { z: Int, x: Int, y: Int -> if (liveTiles) osmTileUrl(z, x, y) else "http://127.0.0.1:${server.address.port}/$z/$x/$y.png" }

        val density = 2f
        val scene =
            ImageComposeScene(width = (600 * density).toInt(), height = (400 * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = false) {
                    val palette = rememberGeocachePalette()
                    live = palette.live
                    val state = remember { DesktopMapState(41.07, -85.21, 12) }
                    val markers =
                        geocacheMapMarks(listings, state.zoom.toDouble()).map { mark ->
                            when (mark) {
                                is GeocacheMapMark.Cache -> {
                                    val pin = geocachePin(mark.listing, emptySet(), palette)
                                    MapMarker(mark.listing.address(), mark.lat, mark.lon, pin.color, pin.emoji, pin.alpha)
                                }
                                is GeocacheMapMark.Cluster -> MapMarker("c", mark.lat, mark.lon, palette.live, mark.size.toString(), centered = true)
                            }
                        }
                    DesktopTileMap(state, markers, Modifier.fillMaxSize(), wheelZoom = true, controlsAlignment = Alignment.TopEnd, tileUrl = tileUrl)
                }
            }
        try {
            var image = scene.render(0)
            repeat(if (liveTiles) 80 else 25) {
                Thread.sleep(80)
                image = scene.render((it + 1) * 80_000_000L)
            }
            File(outDir, "geocache-pins.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)

            // The cluster sits on the view's centre: its head is the palette's amber.
            val pixels = image.peekPixels()!!
            var amber = 0
            val cx = image.width / 2
            val cy = image.height / 2
            for (y in cy - 40 until cy + 40 step 2) {
                for (x in cx - 40 until cx + 40 step 2) {
                    val c = Color(pixels.getColor(x, y))
                    if (abs(c.red - live.red) < 0.05f && abs(c.green - live.green) < 0.05f && abs(c.blue - live.blue) < 0.05f) amber++
                }
            }
            assertTrue(amber > 50, "no amber cluster pin at the centre ($amber pixels)")
        } finally {
            scene.close()
        }
    }
}
