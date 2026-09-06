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
package com.vitorpamplona.amethyst.service.images

import coil3.decode.ImageSource
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.Buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * [onSystemFileSystem] exists to satisfy an identity check inside Coil
 * (`ImageSource.toImageDecoderSourceOrNull` only reaches for the file when
 * `fileSystem === FileSystem.SYSTEM`). These tests pin the property that check reads, since
 * getting it wrong is silent: the decode still succeeds, it just copies the whole encoded
 * image into RAM first.
 */
class ImageDecoderSourcesTest {
    private lateinit var tmpRoot: File
    private lateinit var file: Path
    private lateinit var scope: CoroutineScope

    private val bytes = ByteArray(64) { it.toByte() }

    @Before
    fun setUp() {
        tmpRoot = Files.createTempDirectory("image-decoder-sources-test").toFile()
        file = tmpRoot.resolve("blob.gif").toOkioPath()
        FileSystem.SYSTEM.write(file) { write(bytes) }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
        tmpRoot.deleteRecursively()
    }

    @Test
    fun deferredDeleteWrapper_isUnwrappedToTheSystemFileSystem() {
        val source = ImageSource(file = file, fileSystem = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope))

        val rehomed = source.onSystemFileSystem()

        assertSame(FileSystem.SYSTEM, rehomed.fileSystem)
        assertSame(file, rehomed.fileOrNull())
        assertArrayEquals(bytes, rehomed.source().readByteArray())
        rehomed.close()
    }

    @Test
    fun alreadyOnTheSystemFileSystem_isReturnedUntouched() {
        val source = ImageSource(file = file, fileSystem = FileSystem.SYSTEM)

        assertSame(source, source.onSystemFileSystem())
    }

    @Test
    fun anUnknownWrapper_isLeftAlone() {
        // Only DeferredDeleteFileSystem is known to forward reads verbatim. Anything else may
        // rewrite paths or serve different bytes, so the file underneath is not ours to hand out.
        val source = ImageSource(file = file, fileSystem = object : ForwardingFileSystem(FileSystem.SYSTEM) {})

        assertSame(source, source.onSystemFileSystem())
    }

    @Test
    fun aStreamBackedSource_isNotMaterialisedIntoATempFile() {
        val source =
            ImageSource(
                source = Buffer().write(bytes),
                fileSystem = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope),
            )

        assertSame(source, source.onSystemFileSystem())
        assertNull("re-homing must not force a temp copy of a stream", source.fileOrNull())
    }
}
