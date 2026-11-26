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
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [uriByteSize] answers for the app's own `file://` temps (which no provider knows), takes a
 * provider's SIZE when it has one, falls back to the descriptor's stat, and never throws.
 */
class UriByteSizeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver

    @Before
    fun setUp() {
        resolver = mockk(relaxed = true)
        context = mockk { every { contentResolver } returns resolver }
        // No provider row and no descriptor unless a test sets one up.
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        every { resolver.openFileDescriptor(any(), any()) } returns null
    }

    private fun uri(
        scheme: String,
        path: String? = null,
    ): Uri =
        mockk {
            every { this@mockk.scheme } returns scheme
            every { this@mockk.path } returns path
        }

    private fun sizeCursor(size: Long?): Cursor =
        mockk(relaxed = true) {
            every { getColumnIndex(OpenableColumns.SIZE) } returns 0
            every { moveToFirst() } returns true
            every { isNull(0) } returns (size == null)
            every { getLong(0) } returns (size ?: 0L)
        }

    private fun descriptor(statSize: Long): ParcelFileDescriptor =
        mockk(relaxed = true) {
            every { this@mockk.statSize } returns statSize
        }

    @Test
    fun `a file uri is the file's length`() {
        val file = tempFolder.newFile("trim_1.mp4").apply { writeBytes(ByteArray(54_321)) }

        assertEquals(54_321L, context.uriByteSize(uri("file", file.absolutePath)))
        verify(exactly = 0) { resolver.query(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a file uri to a missing file is unknown`() {
        val missing = File(tempFolder.root, "gone.mp4")

        assertNull(context.uriByteSize(uri("file", missing.absolutePath)))
        verify(exactly = 0) { resolver.query(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a file uri to a directory or with no path is unknown`() {
        assertNull(context.uriByteSize(uri("file", tempFolder.newFolder("dir").absolutePath)))
        assertNull(context.uriByteSize(uri("file", null)))
    }

    @Test
    fun `a content uri is the provider's size`() {
        val content = uri("content")
        val cursor = sizeCursor(1_234L)
        every { resolver.query(content, any(), any(), any(), any()) } returns cursor

        assertEquals(1_234L, context.uriByteSize(content))
        verify { cursor.close() }
        verify(exactly = 0) { resolver.openFileDescriptor(any(), any()) }
    }

    @Test
    fun `a provider without a SIZE column falls through to the descriptor`() {
        val content = uri("content")
        val cursor: Cursor = mockk(relaxed = true) { every { getColumnIndex(OpenableColumns.SIZE) } returns -1 }
        every { resolver.query(content, any(), any(), any(), any()) } returns cursor
        val fd = descriptor(4_321L)
        every { resolver.openFileDescriptor(content, "r") } returns fd

        assertEquals(4_321L, context.uriByteSize(content))
        verify { fd.close() }
    }

    @Test
    fun `a NULL size falls through to the descriptor`() {
        val content = uri("content")
        every { resolver.query(content, any(), any(), any(), any()) } returns sizeCursor(null)
        every { resolver.openFileDescriptor(content, "r") } returns descriptor(9_876L)

        assertEquals(9_876L, context.uriByteSize(content))
    }

    @Test
    fun `no row and a pipe descriptor is unknown`() {
        val content = uri("content")
        every { resolver.openFileDescriptor(content, "r") } returns descriptor(-1L)

        assertNull(context.uriByteSize(content))
    }

    @Test
    fun `a resolver that throws is unknown`() {
        val content = uri("content")
        every { resolver.query(content, any(), any(), any(), any()) } throws SecurityException("no access")
        every { resolver.openFileDescriptor(content, "r") } throws SecurityException("no access")

        assertNull(context.uriByteSize(content))
    }
}
