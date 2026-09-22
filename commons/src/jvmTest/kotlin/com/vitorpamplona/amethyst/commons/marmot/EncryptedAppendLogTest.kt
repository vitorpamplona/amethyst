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
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The on-disk format behind Marmot's message log.
 *
 * This is the only copy of a decrypted Marmot message — the ratchet has moved
 * past the ciphertext it came from long before anyone reads it back — so a bug
 * here loses conversation history outright. The cases that matter are the ones
 * involving a file this code did not write itself: one left by the original
 * whole-blob format, and one whose last append never finished.
 */
class EncryptedAppendLogTest {
    // A stand-in for AES-GCM: prefixes a "nonce" so each blob is self-describing
    // and differs per call, exactly like the real one.
    private var nonce = 0

    private fun cipher(compactAfter: Int = 200) =
        EncryptedAppendLog(
            encrypt = { plain -> byteArrayOf(NONCE_MARK, (nonce++ % 251).toByte()) + plain.map { (it.toInt() xor 0x5A).toByte() } },
            decrypt = { blob ->
                if (blob.size < 2 || blob[0] != NONCE_MARK) {
                    null
                } else {
                    blob.copyOfRange(2, blob.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
                }
            },
            compactAfterSegments = compactAfter,
        )

    private fun tempFile(): File = File(Files.createTempDirectory("appendlog").toFile(), "log")

    @Test
    fun `entries survive a round trip through a fresh reader`() {
        val file = tempFile()
        val writer = cipher()
        writer.append(file, "first")
        writer.append(file, "second")
        writer.append(file, "third")

        // A second instance reads only what reached the disk.
        assertContentEquals(listOf("first", "second", "third"), cipher().readAll(file))
    }

    @Test
    fun `an empty log reads as empty rather than failing`() {
        assertContentEquals(emptyList(), cipher().readAll(tempFile()))
    }

    @Test
    fun `contains answers without reading the file back`() {
        val file = tempFile()
        val log = cipher()
        log.append(file, "hello")

        assertTrue(log.contains(file, "hello"))
        assertFalse(log.contains(file, "goodbye"))
    }

    @Test
    fun `a rewrite replaces the whole log`() {
        val file = tempFile()
        val log = cipher()
        log.append(file, "a")
        log.append(file, "b")
        log.rewrite(file, listOf("b"))

        assertContentEquals(listOf("b"), cipher().readAll(file))
        assertFalse(cipher().contains(file, "a"))
    }

    @Test
    fun `compaction keeps every entry and collapses the segments`() {
        val file = tempFile()
        val log = cipher(compactAfter = 4)
        val written = (1..20).map { "entry-$it" }
        written.forEach { log.append(file, it) }

        assertContentEquals(written, cipher().readAll(file))
        // Folding every four appends leaves ~5 segments rather than 20, which is
        // the bound on read cost that matters here.
        assertTrue(file.length() < 20 * SEGMENT_OVERHEAD_CEILING, "log should have been compacted, was ${file.length()} bytes")
    }

    @Test
    fun `a fold re-encrypts only the loose run, never the prefix`() {
        val file = tempFile()
        val log = cipher(compactAfter = 4)

        // The first append lays the header down; the next four are the loose run
        // that the fourth of them collapses.
        (1..5).forEach { log.append(file, "first-run-$it") }
        val afterFirstFold = file.readBytes()

        (1..4).forEach { log.append(file, "second-run-$it") }
        val afterSecondFold = file.readBytes()

        // The bytes the first fold produced must survive verbatim. If they were
        // re-encrypted they would differ, since the stand-in cipher — like
        // AES-GCM — uses a fresh nonce per call. This is the property that keeps
        // a send from paying for the whole conversation.
        assertContentEquals(
            afterFirstFold.toList(),
            afterSecondFold.copyOfRange(0, afterFirstFold.size).toList(),
            "folding the tail must copy the already-folded prefix as ciphertext",
        )
        assertContentEquals((1..5).map { "first-run-$it" } + (1..4).map { "second-run-$it" }, cipher().readAll(file))
    }

    @Test
    fun `a log written by the original whole-blob format is still readable`() {
        val file = tempFile()
        file.parentFile.mkdirs()
        // Exactly what the old writer produced: one encrypted blob, no magic.
        file.writeBytes(legacyBlob(listOf("old-one", "old-two")))

        assertContentEquals(listOf("old-one", "old-two"), cipher().readAll(file))
    }

    @Test
    fun `appending to a legacy log upgrades it without losing anything`() {
        val file = tempFile()
        file.parentFile.mkdirs()
        file.writeBytes(legacyBlob(listOf("old-one", "old-two")))

        val log = cipher()
        log.append(file, "new-one")

        assertContentEquals(listOf("old-one", "old-two", "new-one"), cipher().readAll(file))
        // and the upgraded file is in the new format, so the next append is cheap
        assertTrue(file.readBytes().decodeToString().startsWith("MRMTLOG2"))
    }

    @Test
    fun `a torn final append costs only the torn entry`() {
        val file = tempFile()
        val log = cipher()
        log.append(file, "kept-one")
        log.append(file, "kept-two")

        // Simulate process death partway through writing the third segment.
        val intact = file.readBytes()
        log.append(file, "lost")
        val torn = file.readBytes()
        file.writeBytes(torn.copyOfRange(0, intact.size + 6))

        assertContentEquals(listOf("kept-one", "kept-two"), cipher().readAll(file))
    }

    @Test
    fun `a segment that cannot be decrypted does not hide the rest`() {
        val file = tempFile()
        val log = cipher()
        log.append(file, "before")
        log.append(file, "after")

        // Corrupt the first segment's nonce marker so decrypt returns null for it.
        val bytes = file.readBytes()
        bytes[MAGIC_LEN + 4] = 0
        file.writeBytes(bytes)

        assertContentEquals(listOf("after"), cipher().readAll(file))
    }

    @Test
    fun `entries keep their bytes through the round trip`() {
        val file = tempFile()
        val log = cipher()
        val awkward = listOf("", "emoji 👩‍👧 here", "a\nb\tc", "\"quoted\": {\"json\": 1}")
        awkward.forEach { log.append(file, it) }

        assertEquals(awkward, cipher().readAll(file))
    }

    /** The pre-segment on-disk shape: `encrypt(uint32 count, (uint32 len, bytes)*)`. */
    private fun legacyBlob(entries: List<String>): ByteArray {
        val encoded = entries.map { it.encodeToByteArray() }
        val plain = ByteArray(4 + encoded.sumOf { 4 + it.size })
        var offset = 0

        fun putInt(value: Int) {
            plain[offset++] = (value shr 24).toByte()
            plain[offset++] = (value shr 16).toByte()
            plain[offset++] = (value shr 8).toByte()
            plain[offset++] = value.toByte()
        }
        putInt(encoded.size)
        for (entry in encoded) {
            putInt(entry.size)
            entry.copyInto(plain, offset)
            offset += entry.size
        }
        return byteArrayOf(NONCE_MARK, 0) + plain.map { (it.toInt() xor 0x5A).toByte() }
    }

    companion object {
        private const val NONCE_MARK: Byte = 0x7F
        private const val MAGIC_LEN = 8
        private const val SEGMENT_OVERHEAD_CEILING = 64
    }
}
