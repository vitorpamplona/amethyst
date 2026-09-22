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
package com.vitorpamplona.amethyst.commons.marmot

import java.io.File
import java.io.FileOutputStream

/**
 * An encrypted-at-rest log of UTF-8 entries that can be appended to in
 * constant time.
 *
 * The file is a sequence of independently encrypted SEGMENTS:
 * ```
 * file    := MAGIC segment*
 * segment := uint32 encLen, byte[encLen]        // whatever [encrypt] produces
 * plain   := uint32 count, (uint32 len, byte[len])*
 * ```
 *
 * Segments are the whole point. The format this replaces was a single blob
 * covering the entire history, so recording one line meant pushing every line
 * ever written back through the cipher and out to disk again — work that grew
 * with the log and, for Marmot's message log, was paid on the send path. A
 * conversation a few thousand messages long was moving hundreds of KB through
 * a hardware-backed cipher to append a couple of hundred bytes.
 *
 * Appending writes one small segment. Loose segments are folded back into one
 * every [compactAfterSegments] appends, which bounds what a read costs: without
 * that, an old conversation would need one cipher round-trip per message ever
 * sent.
 *
 * A file written by the older format has no magic prefix and is read as a
 * single legacy blob; the next append rewrites it in this format. Nothing else
 * migrates it, and nothing needs to — reading handles both.
 *
 * **Not thread-safe.** Entries are cached in memory so an append never has to
 * read the log back, and that cache assumes one owner. Callers hold their own
 * lock around every method (see `AndroidMarmotMessageStore`), and one instance
 * must own any given file.
 *
 * @param encrypt must produce a self-describing blob — it carries its own IV /
 *   nonce, since every segment is encrypted separately.
 * @param decrypt returns null for a segment it cannot open; that segment's
 *   entries are skipped and the rest of the log is still read.
 */
class EncryptedAppendLog(
    private val encrypt: (ByteArray) -> ByteArray,
    private val decrypt: (ByteArray) -> ByteArray?,
    private val compactAfterSegments: Int = COMPACT_AFTER_SEGMENTS,
) {
    /** Entries of one file, plus what it takes to append without re-reading it. */
    private class LogState(
        val entries: MutableList<String>,
        val seen: MutableSet<String>,
        var segments: Int,
    )

    private val logs = mutableMapOf<String, LogState>()

    private fun stateFor(file: File): LogState =
        logs.getOrPut(file.absolutePath) {
            val (entries, segments) = decodeFile(file)
            LogState(entries.toMutableList(), entries.toMutableSet(), segments)
        }

    /** Every entry in [file], oldest first. */
    fun readAll(file: File): List<String> = stateFor(file).entries.toList()

    /** Whether [entry] is already in [file], without reading it back from disk. */
    fun contains(
        file: File,
        entry: String,
    ): Boolean = entry in stateFor(file).seen

    /** Append one entry. Constant time, apart from a periodic compaction. */
    fun append(
        file: File,
        entry: String,
    ) {
        val state = stateFor(file)
        state.entries.add(entry)
        state.seen.add(entry)

        // Either there is no header to append after (a file that does not
        // exist yet, or one still in the old format), or too many loose
        // segments have piled up to keep reads cheap. A rewrite fixes both,
        // and is what lays the header down.
        if (state.segments == UNSEGMENTED || state.segments >= compactAfterSegments) {
            rewrite(file, state.entries.toList())
            return
        }

        file.parentFile?.mkdirs()
        val segment = encrypt(encodeEntries(listOf(entry)))
        FileOutputStream(file, true).use { out ->
            out.write(lengthPrefix(segment.size))
            out.write(segment)
            // An append that survives only in the page cache would lose a
            // message the UI has already shown as sent.
            out.fd.sync()
        }
        state.segments += 1
    }

    /** Replace the whole log with [entries], as a single segment. */
    fun rewrite(
        file: File,
        entries: List<String>,
    ) {
        file.parentFile?.mkdirs()

        val segment = encrypt(encodeEntries(entries))
        val out = ByteArray(MAGIC.size + 4 + segment.size)
        MAGIC.copyInto(out, 0)
        lengthPrefix(segment.size).copyInto(out, MAGIC.size)
        segment.copyInto(out, MAGIC.size + 4)
        atomicWrite(file, out)

        val state = logs.getOrPut(file.absolutePath) { LogState(mutableListOf(), mutableSetOf(), 1) }
        state.entries.clear()
        state.entries.addAll(entries)
        state.seen.clear()
        state.seen.addAll(entries)
        state.segments = 1
    }

    /** Drop the in-memory cache for [file]; call when the file is deleted. */
    fun forget(file: File) {
        logs.remove(file.absolutePath)
    }

    /** Every entry in [file], and how many segments they came from. */
    private fun decodeFile(file: File): Pair<List<String>, Int> {
        // A file that does not exist yet has no header, so the first append has
        // to write one rather than tack a bare segment onto nothing.
        if (!file.exists()) return emptyList<String>() to UNSEGMENTED
        val bytes = file.readBytes()

        if (!bytes.startsWithMagic()) {
            // The older format: the file is one encrypted blob and nothing else.
            val plain = decrypt(bytes) ?: return emptyList<String>() to UNSEGMENTED
            return decodeEntries(plain) to UNSEGMENTED
        }

        val result = ArrayList<String>()
        var offset = MAGIC.size
        var segments = 0
        while (offset + 4 <= bytes.size) {
            val encLen = readInt(bytes, offset)
            offset += 4
            // A truncated tail is a half-finished append (process death between
            // the write and the sync). Everything before it is intact and is
            // what we keep; the torn record is dropped rather than failing the
            // whole log.
            if (encLen <= 0 || offset + encLen > bytes.size) break
            val plain = decrypt(bytes.copyOfRange(offset, offset + encLen))
            offset += encLen
            segments += 1
            if (plain != null) result.addAll(decodeEntries(plain))
        }
        return result to segments.coerceAtLeast(1)
    }

    private fun encodeEntries(entries: List<String>): ByteArray {
        val encoded = entries.map { it.encodeToByteArray() }
        val buffer = ByteArray(4 + encoded.sumOf { 4 + it.size })
        var offset = 0

        writeInt(buffer, offset, encoded.size)
        offset += 4

        for (entry in encoded) {
            writeInt(buffer, offset, entry.size)
            offset += 4
            entry.copyInto(buffer, offset)
            offset += entry.size
        }
        return buffer
    }

    private fun decodeEntries(plain: ByteArray): List<String> {
        if (plain.size < 4) return emptyList()
        var offset = 0
        val count = readInt(plain, offset)
        offset += 4

        val result = ArrayList<String>(count.coerceIn(0, MAX_ENTRIES))
        for (i in 0 until count) {
            if (offset + 4 > plain.size) break
            val len = readInt(plain, offset)
            offset += 4
            if (len < 0 || offset + len > plain.size) break
            result.add(plain.copyOfRange(offset, offset + len).decodeToString())
            offset += len
        }
        return result
    }

    /** Write via a temp file and rename, so a crash can't leave a half-written log. */
    private fun atomicWrite(
        target: File,
        data: ByteArray,
    ) {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.writeBytes(data)
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
    }

    private fun ByteArray.startsWithMagic(): Boolean {
        if (size < MAGIC.size) return false
        for (i in MAGIC.indices) if (this[i] != MAGIC[i]) return false
        return true
    }

    private fun lengthPrefix(value: Int): ByteArray = ByteArray(4).also { writeInt(it, 0, value) }

    private fun writeInt(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value shr 24).toByte()
        target[offset + 1] = (value shr 16).toByte()
        target[offset + 2] = (value shr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)

    companion object {
        /** Marks the segmented format; a file without it predates it. */
        private val MAGIC = "MRMTLOG2".encodeToByteArray()

        /**
         * Segment count standing for "this file has no header yet" — either it
         * does not exist, or it was written by the original whole-blob format.
         * The next append rewrites it rather than appending into nothing.
         */
        private const val UNSEGMENTED = -1

        private const val COMPACT_AFTER_SEGMENTS = 200

        private const val MAX_ENTRIES = 1_000_000
    }
}
