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
package com.vitorpamplona.quartz

import com.vitorpamplona.quartz.utils.GZip
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv

/**
 * Linux/Native actual for [TestResourceLoader].
 *
 * Until this existed it was `TODO()`, which failed 69 tests on this target — every
 * suite driven by a vector file: the whole MLS interop set, NIP-44, the NIP-01 hint
 * indexer, the SQLite store's large-DB tests and the Bolt12 payer proofs. None of them
 * were failing because of missing production code; they could not read their input.
 *
 * Resolves paths against `TEST_RESOURCES_ROOT`, the same environment variable the Apple
 * actual uses, exported onto every `KotlinNativeTest` task by `quartz/build.gradle.kts`.
 * Reads through `platform.posix` rather than Foundation, which linuxX64 does not have.
 *
 * The read is a single `stat`-sized allocation filled by `fread`, so a vector file
 * costs exactly one `ByteArray` — less than the JVM actual's `bufferedReader().readText()`,
 * which grows a `StringBuilder` as it goes.
 */
@OptIn(ExperimentalForeignApi::class)
actual class TestResourceLoader actual constructor() {
    actual fun loadDecompressString(file: String): String = GZip.decompress(readBytes(file))

    actual fun loadString(file: String): String = readBytes(file).decodeToString()

    private fun readBytes(file: String): ByteArray {
        val root =
            getenv("TEST_RESOURCES_ROOT")?.toKString()
                ?: throw IllegalStateException(
                    "TEST_RESOURCES_ROOT is not set. quartz/build.gradle.kts exports it onto every " +
                        "KotlinNativeTest task; running the test binary directly has to set it too.",
                )

        val path = "$root/$file"
        val handle = fopen(path, "rb") ?: throw IllegalArgumentException("Resource not found: $path")

        try {
            if (fseek(handle, 0, SEEK_END) != 0) throw IllegalArgumentException("Resource is not seekable: $path")
            val size = ftell(handle)
            if (size < 0L) throw IllegalArgumentException("Cannot determine the size of: $path")
            if (size == 0L) return ByteArray(0)
            if (fseek(handle, 0, SEEK_SET) != 0) throw IllegalArgumentException("Cannot rewind: $path")

            val bytes = ByteArray(size.toInt())
            bytes.usePinned { pinned ->
                var read = 0
                while (read < bytes.size) {
                    val count =
                        fread(
                            pinned.addressOf(read),
                            1.convert(),
                            (bytes.size - read).convert(),
                            handle,
                        ).toInt()
                    if (count <= 0) break
                    read += count
                }
                if (read != bytes.size) {
                    throw IllegalArgumentException("Short read on $path: got $read of ${bytes.size} bytes")
                }
            }
            return bytes
        } finally {
            fclose(handle)
        }
    }
}
