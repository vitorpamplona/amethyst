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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class MetadataStripperTest {
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var uri: Uri

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        uri = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `AVIF with no sensitive tags returns original uri marked stripped`() {
        // Arrange: AVIF content; tag reader returns null for every tag (no sensitive tags).
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        // Act: inject a pure-lambda tag reader — no ExifInterface instantiation needed.
        val result =
            MetadataStripper.stripImageMetadata(uri, context) { _ ->
                { _: String -> null }
            }

        // Assert: original URI returned, marked stripped (i.e. "verified clean").
        assertEquals(uri, result.uri)
        assertTrue(result.stripped)
    }

    @Test(expected = AvifMetadataNotVerifiableException::class)
    fun `AVIF with GPS EXIF throws AvifMetadataNotVerifiableException`() {
        // Arrange: AVIF content; tag reader reports GPS_LATITUDE present.
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        // Act: should throw before returning a StrippingResult
        MetadataStripper.stripImageMetadata(uri, context) { _ ->
            { tag: String ->
                if (tag == ExifInterface.TAG_GPS_LATITUDE) "40.7128" else null
            }
        }

        // Assert: handled by `expected` annotation; reaching here is a failure
        fail("Expected AvifMetadataNotVerifiableException, but no exception was thrown")
    }

    @Test(expected = AvifMetadataNotVerifiableException::class)
    fun `AVIF where ExifInterface fails throws AvifMetadataNotVerifiableException`() {
        // Arrange: AVIF content; tag reader factory throws on invocation (simulates parse failure).
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        MetadataStripper.stripImageMetadata(uri, context) { _ ->
            throw RuntimeException("malformed AVIF")
        }
        fail("Expected AvifMetadataNotVerifiableException")
    }
}
