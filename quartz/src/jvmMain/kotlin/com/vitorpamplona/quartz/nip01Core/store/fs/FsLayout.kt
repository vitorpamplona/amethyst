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
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.path.exists

/**
 * Pure path helpers for the file-backed event store. No I/O except the
 * seed-file reader, which lives here because the seed is a layout concern
 * (it salts every tag / owner hash).
 */
internal class FsLayout(
    val root: Path,
) {
    val events: Path = root.resolve(EVENTS_DIR)
    val staging: Path = root.resolve(STAGING_DIR)
    val idx: Path = root.resolve(IDX_DIR)
    val idxKind: Path = idx.resolve(IDX_KIND)
    val idxAuthor: Path = idx.resolve(IDX_AUTHOR)
    val idxOwner: Path = idx.resolve(IDX_OWNER)
    val idxTag: Path = idx.resolve(IDX_TAG)
    val idxExpiresAt: Path = idx.resolve(IDX_EXPIRES_AT)
    val replaceable: Path = root.resolve(REPLACEABLE_DIR)
    val addressable: Path = root.resolve(ADDRESSABLE_DIR)
    val tombstones: Path = root.resolve(TOMBSTONES_DIR)
    val tombstonesId: Path = tombstones.resolve(TOMB_ID)
    val tombstonesAddr: Path = tombstones.resolve(TOMB_ADDR)

    fun canonical(id: HexKey): Path {
        require(id.length >= 4) { "event id must be at least 4 hex chars, got '$id'" }
        return events.resolve(id.substring(0, 2)).resolve(id.substring(2, 4)).resolve("$id$JSON_EXT")
    }

    fun kindEntry(
        kind: Kind,
        ts: Long,
        id: HexKey,
    ): Path = idxKind.resolve(kind.toString()).resolve(entryName(ts, id))

    fun authorEntry(
        pubkey: HexKey,
        ts: Long,
        id: HexKey,
    ): Path = idxAuthor.resolve(pubkey).resolve(entryName(ts, id))

    fun ownerEntry(
        ownerHash: Long,
        ts: Long,
        id: HexKey,
    ): Path = idxOwner.resolve(hashHex(ownerHash)).resolve(entryName(ts, id))

    fun tagEntry(
        name: String,
        valueHash: Long,
        ts: Long,
        id: HexKey,
    ): Path = idxTag.resolve(name).resolve(hashHex(valueHash)).resolve(entryName(ts, id))

    /** NIP-40 expiration index entry. `exp` is unix seconds, padded for sort order. */
    fun expirationEntry(
        exp: Long,
        id: HexKey,
    ): Path = idxExpiresAt.resolve(entryName(exp, id))

    /** Directory that holds every indexed value for a tag name. */
    fun tagDir(name: String): Path = idxTag.resolve(name)

    /** Directory that holds every entry for a specific (tag name, value hash). */
    fun tagValueDir(
        name: String,
        valueHash: Long,
    ): Path = idxTag.resolve(name).resolve(hashHex(valueHash))

    fun kindDir(kind: Kind): Path = idxKind.resolve(kind.toString())

    fun authorDir(pubkey: HexKey): Path = idxAuthor.resolve(pubkey)

    /** Slot path for a replaceable event (kinds 0, 3, 10000-19999). */
    fun replaceableSlot(
        kind: Kind,
        pubkey: HexKey,
    ): Path = replaceable.resolve(kind.toString()).resolve("$pubkey$JSON_EXT")

    /** Slot path for an addressable event (kinds 30000-39999). */
    fun addressableSlot(
        kind: Kind,
        pubkey: HexKey,
        dTag: String,
    ): Path = addressable.resolve(kind.toString()).resolve(pubkey).resolve("${sha256Hex(dTag)}$JSON_EXT")

    /** NIP-09 tombstone path keyed by target event id. */
    fun idTombstonePath(id: HexKey): Path = tombstonesId.resolve("$id$JSON_EXT")

    /**
     * NIP-09 tombstone path keyed by address. `dTag` may be empty — used
     * for replaceable-kind tombstones that have no d-tag.
     */
    fun addrTombstonePath(
        kind: Kind,
        pubkey: HexKey,
        dTag: String,
    ): Path = tombstonesAddr.resolve(kind.toString()).resolve(pubkey).resolve("${sha256Hex(dTag)}$JSON_EXT")

    fun ensureSkeleton() {
        Files.createDirectories(events)
        Files.createDirectories(staging)
        Files.createDirectories(idxKind)
        Files.createDirectories(idxAuthor)
        Files.createDirectories(idxOwner)
        Files.createDirectories(idxTag)
        Files.createDirectories(idxExpiresAt)
        Files.createDirectories(replaceable)
        Files.createDirectories(addressable)
        Files.createDirectories(tombstonesId)
        Files.createDirectories(tombstonesAddr)
    }

    /**
     * Read the seed salt (creates it on first call). The seed is
     * write-once — reopening an existing store must return the same Long
     * or every previously-written index entry becomes unreachable.
     */
    fun readOrCreateSeed(): Long {
        val f = root.resolve(SEED_FILE)
        if (!f.exists()) {
            val bytes = ByteArray(8)
            SecureRandom().nextBytes(bytes)
            val tmp = Files.createTempFile(root, ".seed-", ".tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE)
        }
        val bytes = Files.readAllBytes(f)
        require(bytes.size == 8) { "$SEED_FILE must be exactly 8 bytes, got ${bytes.size}" }
        return ByteBuffer.wrap(bytes).long
    }

    companion object {
        const val EVENTS_DIR = "events"
        const val STAGING_DIR = ".staging"
        const val IDX_DIR = "idx"
        const val IDX_KIND = "kind"
        const val IDX_AUTHOR = "author"
        const val IDX_OWNER = "owner"
        const val IDX_TAG = "tag"
        const val IDX_EXPIRES_AT = "expires_at"
        const val REPLACEABLE_DIR = "replaceable"
        const val ADDRESSABLE_DIR = "addressable"
        const val TOMBSTONES_DIR = "tombstones"
        const val TOMB_ID = "id"
        const val TOMB_ADDR = "addr"
        const val SEED_FILE = ".seed"
        const val JSON_EXT = ".json"

        /** Lowercase hex SHA-256 of the given UTF-8 string. Used for d-tag slots. */
        fun sha256Hex(s: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(s.encodeToByteArray())
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xff
                sb.append(HEX[v ushr 4])
                sb.append(HEX[v and 0x0f])
            }
            return sb.toString()
        }

        private val HEX = "0123456789abcdef".toCharArray()

        /** zero-padded to 10 digits so lex order == chronological order through year 2286. */
        fun tsPad(ts: Long): String = ts.toString().padStart(TS_PAD_WIDTH, '0')

        /** 16-hex of a Long, zero-padded. */
        fun hashHex(h: Long): String =
            java.lang.Long
                .toHexString(h)
                .padStart(16, '0')

        fun entryName(
            ts: Long,
            id: HexKey,
        ): String = "${tsPad(ts)}-$id"

        /**
         * Recover (createdAt, id) from an index filename. Returns null if the
         * filename does not match — lets scrubs tolerate garbage.
         */
        fun parseEntry(name: String): Pair<Long, HexKey>? {
            if (name.length < TS_PAD_WIDTH + 1 + 1) return null
            if (name[TS_PAD_WIDTH] != '-') return null
            val ts = name.substring(0, TS_PAD_WIDTH).toLongOrNull() ?: return null
            val id = name.substring(TS_PAD_WIDTH + 1)
            return ts to id
        }

        private const val TS_PAD_WIDTH = 10
    }
}
