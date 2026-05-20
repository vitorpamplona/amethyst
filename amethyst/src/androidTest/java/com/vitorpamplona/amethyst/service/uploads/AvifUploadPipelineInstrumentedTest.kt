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
package com.vitorpamplona.amethyst.service.uploads

import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvifUploadPipelineInstrumentedTest {
    private val tinyAvifBase64 =
        "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAADrbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAETAAAAPAAAAChpaW5mAAAAAAABAAAAGmluZmUCAAAAAAEAAGF2MDFDb2xvcgAAAABqaXBycAAAAEtpcGNvAAAAFGlzcGUAAAAAAAAACAAAAAgAAAAQcGl4aQAAAAADCAgIAAAADGF2MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAABdpcG1hAAAAAAAAAAEAAQQBAoMEAAAARG1kYXQSAAoFGAi/YEIyMRQAAwwwxAA89itlZz4hWO2B4vIBcLfFyXWnEefrmHux78818I4WywDTVsW1Fx3+VNA="

    @Test
    fun avifUploadPipelinePreservesAvifAndComputesPreviewMetadata() =
        runBlocking {
            assumeTrue("AVIF decoding requires Android 12+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val avifFile = File.createTempFile("amethyst_tiny_", ".avif", context.cacheDir)
            avifFile.writeBytes(Base64.decode(tinyAvifBase64, Base64.DEFAULT))

            val avifUri = Uri.fromFile(avifFile)
            try {
                val compressed =
                    MediaCompressor()
                        .compress(
                            uri = avifUri,
                            contentType = "image/avif",
                            mediaQuality = CompressorQuality.MEDIUM,
                            applicationContext = context,
                        )

                assertEquals(avifUri, compressed.uri)
                assertEquals("image/avif", compressed.contentType)
                assertNull(compressed.size)

                val previewMetadata =
                    PreviewMetadataCalculator.computeFromUri(
                        context = context,
                        uri = avifUri,
                        mimeType = "image/avif",
                    )

                assertNotNull(previewMetadata)
                assertEquals(8, previewMetadata?.dim?.width)
                assertEquals(8, previewMetadata?.dim?.height)
                assertNotNull(previewMetadata?.blurhash)
                assertNotNull(previewMetadata?.thumbhash)

                val stripped =
                    MetadataStripper.strip(
                        uri = avifUri,
                        mimeType = "image/avif",
                        context = context,
                    )

                assertEquals(avifUri, stripped.uri)
                assertEquals(true, stripped.stripped)
            } finally {
                avifFile.delete()
            }
        }
}
