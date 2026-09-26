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
package com.vitorpamplona.quartz.cordn.spec01GroupMetadata

import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * The `cordn_group_metadata` GroupContext extension (`0xC04D`), from
 * `spec/01.md`.
 *
 * Shared presentation metadata that rides inside MLS group state, so it is
 * authenticated by ordinary Proposal/Commit processing rather than by
 * coordinator-local storage. It is optional: a group may carry none and remain
 * valid.
 *
 * ## The admin model is not Marmot's
 *
 * `spec/01.md` §5.3: an EMPTY [adminPubkeys] means **egalitarian** — every
 * member has the same administrative level — and a non-empty one means only
 * those accounts are admins. Marmot's MIP-03 reads an empty admin set as
 * "bootstrap, gate open", which looks the same and means something else: there
 * it is a transient state before an admin is named, here it is a permanent
 * choice. Do not reuse either side's authorization code for the other.
 */
data class CordnGroupMetadata(
    /** UTF-8 display name. May be empty. */
    val name: String,
    /** UTF-8 description. Empty means absent. */
    val description: String = "",
    /**
     * Admin accounts as lowercase 64-char hex, encoded on the wire as
     * concatenated raw 32-byte keys. Empty means egalitarian mode.
     */
    val adminPubkeys: List<HexKey> = emptyList(),
    /** A short glyph or emoji standing in for an image. Empty means absent. */
    val icon: String = "",
    /** Advisory image URL, possibly a `data:` URL. Empty means absent. */
    val imageUrl: String = "",
) {
    init {
        adminPubkeys.forEach {
            require(it.length == PUBKEY_HEX_LENGTH && it.all { c -> c in HEX_ALPHABET }) {
                "cordn group metadata admin pubkey must be 64 lowercase hex chars, was '$it'"
            }
        }
        require(adminPubkeys.size == adminPubkeys.toSet().size) {
            "cordn group metadata admin pubkeys must not contain duplicates"
        }
    }

    /** True when the group names no admins, i.e. every member is equally privileged. */
    val isEgalitarian: Boolean get() = adminPubkeys.isEmpty()

    fun encode(): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(VERSION)
        writer.putOpaque2(name.encodeToByteArray())
        writer.putOpaque2(description.encodeToByteArray())
        writer.putOpaque2(adminPubkeys.fold(ByteArray(0)) { acc, key -> acc + key.hexToByteArray() })
        writer.putOpaque2(icon.encodeToByteArray())
        writer.putOpaque2(imageUrl.encodeToByteArray())
        return writer.toByteArray()
    }

    fun toExtension(): Extension = Extension(EXTENSION_TYPE, encode())

    companion object {
        /** `cordn_group_metadata`, in the MLS private-use extension range. */
        const val EXTENSION_TYPE = 0xC04D

        /** Version 0 is reserved and MUST be rejected (`spec/01.md` §4). */
        const val VERSION = 1

        private const val PUBKEY_HEX_LENGTH = 64
        private const val HEX_ALPHABET = "0123456789abcdef"

        /**
         * Decodes the extension payload.
         *
         * Each field is a plain uint16 length prefix, NOT an MLS varint vector.
         * `spec/01.md` §3 says "TLS presentation language with MLS
         * variable-length vector encoding conventions" and then writes
         * `opaque Name<0..2^16-1>`, which are two different encodings; the
         * reference implementation
         * (`packages/cli/src/groupMetadata.ts:encodeField`) emits uint16, so
         * that is what interoperates. Worth raising upstream — the prose and
         * the code disagree and only one of them is on the wire.
         */
        fun decode(data: ByteArray): CordnGroupMetadata {
            val reader = TlsReader(data)
            val version = reader.readUint16()
            require(version == VERSION) { "unsupported cordn group metadata version: $version" }

            val name = reader.readOpaque2().decodeToStringStrict("name")
            val description = reader.readOpaque2().decodeToStringStrict("description")
            val adminBytes = reader.readOpaque2()
            val icon = reader.readOpaque2().decodeToStringStrict("icon")
            val imageUrl = reader.readOpaque2().decodeToStringStrict("image_url")

            require(!reader.hasRemaining) { "unexpected trailing bytes in cordn group metadata" }
            require(adminBytes.size % KEY_SIZE == 0) {
                "cordn group metadata admin_pubkeys length must be a multiple of $KEY_SIZE, was ${adminBytes.size}"
            }

            val admins =
                (0 until adminBytes.size / KEY_SIZE).map {
                    adminBytes.copyOfRange(it * KEY_SIZE, (it + 1) * KEY_SIZE).toHexKey()
                }
            return CordnGroupMetadata(name, description, admins, icon, imageUrl)
        }

        /** The group's metadata, or null when it carries none. */
        fun fromExtensions(extensions: List<Extension>): CordnGroupMetadata? = extensions.firstOrNull { it.extensionType == EXTENSION_TYPE }?.let { decode(it.extensionData) }

        private const val KEY_SIZE = 32

        /**
         * `spec/01.md` §9 requires rejecting invalid UTF-8 rather than
         * substituting replacement characters, which is what
         * `decodeToString()` does by default.
         */
        private fun ByteArray.decodeToStringStrict(field: String): String =
            try {
                decodeToString(throwOnInvalidSequence = true)
            } catch (e: CharacterCodingException) {
                throw IllegalArgumentException("cordn group metadata $field is not valid UTF-8", e)
            }
    }
}
