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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import platform.Foundation.NSUUID

/**
 * The JVM's emoji-pack unpacker over okio's zip reader: every image entry into its own temp file,
 * with the same limits (per image, image count, total, and everything inflated). okio inflates only
 * the entries it is asked to read, so the entries left out cost nothing here.
 */
actual suspend fun extractEmojiArchive(zip: ByteArray): ExtractedEmojiArchive? =
    withContext(Dispatchers.IO) {
        val fileSystem = FileSystem.SYSTEM
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        val zipPath = tempDir / "amethyst_emoji_${NSUUID().UUIDString}.zip"
        val written = mutableListOf<ExtractedEmojiFile>()
        val tooLarge = mutableListOf<String>()
        var truncated = false
        var current: Path? = null
        try {
            fileSystem.write(zipPath) { write(zip) }
            val archive =
                try {
                    fileSystem.openZip(zipPath)
                } catch (e: IOException) {
                    throw IllegalArgumentException("Not a valid zip archive: ${e.message}", e)
                }
            var inflated = 0L
            var total = 0L
            val entries = archive.listRecursively("/".toPath()).filter { archive.metadataOrNull(it)?.isRegularFile == true }.toList()
            for (entry in entries) {
                val relative = entry.toString().removePrefix("/")
                val name = EmojiImport.baseName(relative)
                val isImage = !relative.startsWith("__MACOSX/") && !name.startsWith(".") && EmojiImport.isImageFileName(name)
                if (!isImage) continue
                if (written.size >= EmojiArchiveLimits.MAX_IMAGES || total >= EmojiArchiveLimits.MAX_TOTAL_BYTES) {
                    truncated = true
                    break
                }
                val file = tempDir / "amethyst_emoji_${NSUUID().UUIDString}.${EmojiImport.extensionOf(name)}"
                current = file
                // Counted as read, not as the entry declares: a crafted archive can fake its sizes.
                var size = 0L
                var over = false
                archive.source(entry).buffer().use { source ->
                    fileSystem.write(file) {
                        val chunk = Buffer()
                        while (true) {
                            val read = source.read(chunk, CHUNK_BYTES)
                            if (read < 0) break
                            inflated += read
                            require(inflated <= EmojiArchiveLimits.MAX_INFLATED_BYTES) {
                                "The archive unpacks to more than ${EmojiArchiveLimits.MAX_INFLATED_BYTES / (1024 * 1024)} MB"
                            }
                            size += read
                            if (size > EmojiArchiveLimits.MAX_ENTRY_BYTES) {
                                over = true
                                break
                            }
                            write(chunk, read)
                        }
                    }
                }
                if (over) {
                    fileSystem.delete(file)
                    tooLarge.add(name)
                } else {
                    total += size
                    written.add(ExtractedEmojiFile(name, file))
                }
                current = null
            }
            ExtractedEmojiArchive(written, tooLarge, truncated)
        } catch (e: Throwable) {
            current?.let { fileSystem.delete(it) }
            written.forEach { fileSystem.delete(it.path) }
            if (e is CancellationException) throw e
            if (e is IOException) throw IllegalArgumentException("Not a valid zip archive: ${e.message}", e)
            throw e
        } finally {
            fileSystem.delete(zipPath)
        }
    }

private const val CHUNK_BYTES = 64L * 1024
