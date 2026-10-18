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

import okio.Path

/** One image taken out of an emoji archive, written to a temp file the caller deletes. */
class ExtractedEmojiFile(
    /** The entry's file name, without the directories it had inside the archive. */
    val fileName: String,
    val path: Path,
)

class ExtractedEmojiArchive(
    val files: List<ExtractedEmojiFile>,
    /** Image entries left out for being larger than [EmojiArchiveLimits.MAX_ENTRY_BYTES]. */
    val tooLarge: List<String>,
)

object EmojiArchiveLimits {
    const val MAX_IMAGES = 2_000
    const val MAX_ENTRY_BYTES = 10L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
}

/**
 * Writes the images inside the zip archive [zip] to temp files, in archive order, skipping
 * folders, other files, and macOS metadata (`__MACOSX/`, `._*`). The caller deletes the files
 * through `platformFileSystem`. Null when this platform cannot read zip archives.
 *
 * Throws [IllegalArgumentException] for a corrupt archive or one past [EmojiArchiveLimits]
 * (a zip bomb), deleting whatever it already wrote.
 */
expect suspend fun extractEmojiArchive(zip: ByteArray): ExtractedEmojiArchive?
