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
package com.vitorpamplona.amethyst.commons.ui.components.pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import okio.Path.Companion.toOkioPath
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shared PDF card and reader draw a real PDF on the desktop: fetched over HTTP into Coil's
 * disk cache, opened by PDFBox, and drawn through Compose, offscreen. The page is painted solid
 * red, so the pixels say whether it arrived.
 */
class PdfRenderTest {
    private lateinit var server: HttpServer
    private val cacheDir = Files.createTempDirectory("pdf-render-cache").toFile()
    private lateinit var url: String

    @BeforeTest
    fun startServer() {
        val pdf =
            ByteArrayOutputStream()
                .also { out ->
                    PDDocument().use { doc ->
                        repeat(3) {
                            val page = PDPage(PDRectangle.LETTER)
                            doc.addPage(page)
                            PDPageContentStream(doc, page).use { stream ->
                                stream.setNonStrokingColor(1f, 0f, 0f)
                                stream.addRect(0f, 0f, 612f, 792f)
                                stream.fill()
                            }
                        }
                        doc.save(out)
                    }
                }.toByteArray()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/doc.pdf") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.sendResponseHeaders(200, pdf.size.toLong())
            exchange.responseBody.use { it.write(pdf) }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}/doc.pdf"
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        cacheDir.deleteRecursively()
    }

    private fun render(content: @Composable () -> Unit): Int {
        val width = 600
        val height = 900
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        try {
            scene.setContent {
                setSingletonImageLoaderFactory { ctx: PlatformContext ->
                    ImageLoader
                        .Builder(ctx)
                        .diskCache { DiskCache.Builder().directory(cacheDir.toOkioPath()).build() }
                        .build()
                }
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxSize()) { content() }
                    }
                }
            }

            // The fetch and the render run off the composition, so poll the frames.
            var redPixels = 0
            for (poll in 0 until 100) {
                redPixels = scene.render().countRed()
                if (redPixels > 10_000) break
                Thread.sleep(100)
            }
            return redPixels
        } finally {
            scene.close()
        }
    }

    @Test
    fun theCardDrawsTheFirstPage() {
        val red =
            render {
                PdfPreviewCard(content = MediaUrlPdf(url), accountViewModel = mockAccountViewModel(), onOpen = {})
            }
        assertTrue(red > 10_000, "the first page should have been drawn in the card; found $red red pixels")
    }

    @Test
    fun theReaderDrawsAPage() {
        val red =
            render {
                PdfViewerContent(content = MediaUrlPdf(url), accountViewModel = mockAccountViewModel(), onDismiss = {})
            }
        assertTrue(red > 10_000, "the reader should have drawn a page; found $red red pixels")
    }

    private fun Image.countRed(): Int {
        val bitmap = Bitmap.makeFromImage(this)
        var count = 0
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val argb = bitmap.getColor(x, y)
                val color = Color(argb)
                if (color.red > 0.9f && color.green < 0.1f && color.blue < 0.1f) count++
            }
        }
        return count * 16
    }
}
