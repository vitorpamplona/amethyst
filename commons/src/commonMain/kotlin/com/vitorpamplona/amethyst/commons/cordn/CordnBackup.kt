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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.nip49PrivKeyEnc.SCrypt
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * A passphrase-encrypted export of one account's cordn state.
 *
 * ## Why cordn needs this when Marmot does not
 *
 * A Marmot group lives on relays; a client that lost its state can at least
 * see the group exists and be re-added to it. A cordn group exists as MLS
 * state on **one device** plus an ordered stream on a coordinator that cannot
 * read a byte of it. Lose the device and the group is gone — not "gone until
 * someone re-invites you", because a fresh invitation starts at a new epoch
 * and the history before it stays unreadable forever. That asymmetry is what
 * makes an export worth the risk of having one.
 *
 * ## §5.4, answered: our own format, and portability was never available
 *
 * `amethyst/plans/2026-09-19-cordn-ui.md` §5.4 left the call open between
 * matching cordn-web's backup and doing our own, noting the trade as "better
 * for users, worse for portability". The portability half turns out to be
 * unavailable, for the reason §4.6 gives about multi-device: the valuable
 * content of any cordn backup is **MLS group state**, and that is an engine's
 * internal serialization, not a wire format. Theirs is ts-mls's; ours is
 * `MlsGroupState`. Neither can read the other whatever file format wraps it,
 * so a byte-compatible container would buy a restore that cannot restore.
 *
 * (The other half also could not be matched cleanly: their client is outside
 * the two MIT packages, and the spec prose is unlicensed.)
 *
 * ## Restoring replaces a device. It does not add one.
 *
 * An MLS state export is a **cloneable identity**. Two devices holding the
 * same state and both committing fork the ratchet tree, and MLS does not
 * recover: every message after the fork fails to decrypt for somebody, with
 * no error that says why. §5.3 keeps multi-device a non-goal for exactly this
 * reason, and a backup does not quietly become multi-device support because
 * it is technically possible to restore twice.
 *
 * So: restore onto a device that has replaced the old one. A UI offering this
 * must say that, and must not present it as sync.
 *
 * ## The file
 *
 * ```
 * magic "CORDNBAK" | version:u16 | logN:u8 | salt:16 | nonce:12 | ChaCha20-Poly1305(payload)
 * ```
 *
 * The header is passed as the AEAD's associated data. Today that is belt and
 * braces rather than the thing protecting it: every header field either feeds
 * the key derivation (`logN`, `salt`), feeds the cipher (`nonce`), or is
 * checked outright (`magic`, `version`), so editing any of them already
 * yields a wrong key or an outright refusal. The AAD earns its place if this
 * header ever gains a field that does neither — at which point forgetting it
 * would be the easy mistake. A mutation removing it survives the suite, and
 * that is expected rather than a gap.
 */
object CordnBackup {
    const val VERSION = 1

    /**
     * scrypt cost, as `log2(N)`.
     *
     * Matches NIP-49's default. A backup is decrypted once in a while by its
     * owner, never in a loop, so the cost belongs at the high end of what a
     * phone will tolerate rather than tuned for throughput.
     */
    const val DEFAULT_LOG_N = 16

    private const val R = 8
    private const val P = 1
    private const val KEY_LENGTH = 32
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private val MAGIC = "CORDNBAK".encodeToByteArray()

    /** Everything worth carrying to a replacement device. */
    data class Archive(
        val accountPubKey: HexKey,
        val coordinators: List<CoordinatorConfig>,
        val groups: List<Group>,
        val keyPackages: List<KeyPackage>,
    ) {
        data class Group(
            val coordinatorPubKey: HexKey,
            val gid: String,
            /** An `MlsGroupState` blob. Opaque here, and engine-specific. */
            val state: ByteArray,
            val cursor: GroupCursor?,
            val joinedViaRequest: Boolean,
        ) {
            override fun equals(other: Any?): Boolean =
                this === other ||
                    (
                        other is Group &&
                            coordinatorPubKey == other.coordinatorPubKey &&
                            gid == other.gid &&
                            state.contentEquals(other.state) &&
                            cursor?.fetchCursor == other.cursor?.fetchCursor &&
                            cursor?.lastCursor == other.cursor?.lastCursor &&
                            joinedViaRequest == other.joinedViaRequest
                    )

            override fun hashCode(): Int {
                var result = coordinatorPubKey.hashCode()
                result = 31 * result + gid.hashCode()
                result = 31 * result + state.contentHashCode()
                result = 31 * result + (cursor?.fetchCursor?.hashCode() ?: 0)
                result = 31 * result + joinedViaRequest.hashCode()
                return result
            }
        }

        data class KeyPackage(
            val coordinatorPubKey: HexKey,
            val keyPackageRef: String,
            /** A `KeyPackageBundle` blob — private key material. */
            val bundle: ByteArray,
        ) {
            override fun equals(other: Any?): Boolean =
                this === other ||
                    (
                        other is KeyPackage &&
                            coordinatorPubKey == other.coordinatorPubKey &&
                            keyPackageRef == other.keyPackageRef &&
                            bundle.contentEquals(other.bundle)
                    )

            override fun hashCode(): Int = 31 * (31 * coordinatorPubKey.hashCode() + keyPackageRef.hashCode()) + bundle.contentHashCode()
        }
    }

    /** Encrypts [archive] under [passphrase]. */
    fun seal(
        archive: Archive,
        passphrase: String,
        logN: Int = DEFAULT_LOG_N,
    ): ByteArray {
        val salt = RandomInstance.bytes(SALT_LENGTH)
        val nonce = RandomInstance.bytes(NONCE_LENGTH)
        val header = header(logN, salt, nonce)
        val key = deriveKey(passphrase, salt, logN)

        return header + ChaCha20Poly1305.encrypt(encode(archive), header, nonce, key)
    }

    /**
     * Opens a sealed archive.
     *
     * Throws on a wrong passphrase, a truncated file or an edited header — all
     * of which arrive as an AEAD authentication failure, which is the right
     * answer to every one of them and deliberately does not distinguish
     * between them.
     */
    fun open(
        sealed: ByteArray,
        passphrase: String,
    ): Archive {
        require(sealed.size > HEADER_LENGTH) { "not a cordn backup: too short" }
        require(sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not a cordn backup" }

        val reader = TlsReader(sealed.copyOfRange(MAGIC.size, HEADER_LENGTH))
        val version = reader.readUint16()
        require(version == VERSION) { "unknown cordn backup version $version" }

        val logN = reader.readUint8()
        val salt = reader.readBytes(SALT_LENGTH)
        val nonce = reader.readBytes(NONCE_LENGTH)

        val key = deriveKey(passphrase, salt, logN)
        val header = sealed.copyOfRange(0, HEADER_LENGTH)
        val plaintext = ChaCha20Poly1305.decrypt(sealed.copyOfRange(HEADER_LENGTH, sealed.size), header, nonce, key)

        return decode(plaintext)
    }

    private fun header(
        logN: Int,
        salt: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val writer = TlsWriter()
        writer.putBytes(MAGIC)
        writer.putUint16(VERSION)
        writer.putUint8(logN)
        writer.putBytes(salt)
        writer.putBytes(nonce)
        return writer.toByteArray()
    }

    private fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        logN: Int,
    ): ByteArray {
        require(logN in MIN_LOG_N..MAX_LOG_N) { "unreasonable scrypt cost: 2^$logN" }
        var n = 1
        repeat(logN) { n *= 2 }
        return SCrypt.scrypt(passphrase.encodeToByteArray(), salt, n, R, P, KEY_LENGTH)
    }

    private fun encode(archive: Archive): ByteArray {
        val writer = TlsWriter()
        writer.putOpaque2(archive.accountPubKey.encodeToByteArray())
        writer.putOpaque4(CoordinatorListCodec.encode(archive.coordinators))

        writer.putUint16(archive.groups.size)
        archive.groups.forEach {
            writer.putOpaque2(it.coordinatorPubKey.encodeToByteArray())
            writer.putOpaque2(it.gid.encodeToByteArray())
            writer.putOpaque4(it.state)
            writer.putUint8(if (it.cursor != null) 1 else 0)
            it.cursor?.let { cursor ->
                writer.putUint64(cursor.fetchCursor)
                writer.putUint64(cursor.lastCursor)
            }
            writer.putUint8(if (it.joinedViaRequest) 1 else 0)
        }

        writer.putUint16(archive.keyPackages.size)
        archive.keyPackages.forEach {
            writer.putOpaque2(it.coordinatorPubKey.encodeToByteArray())
            writer.putOpaque2(it.keyPackageRef.encodeToByteArray())
            writer.putOpaque4(it.bundle)
        }
        return writer.toByteArray()
    }

    private fun decode(bytes: ByteArray): Archive {
        val reader = TlsReader(bytes)
        val accountPubKey = reader.readOpaque2().decodeToString()
        val coordinators = CoordinatorListCodec.decode(reader.readOpaque4())

        val groups =
            (0 until reader.readUint16()).map {
                val coordinator = reader.readOpaque2().decodeToString()
                val gid = reader.readOpaque2().decodeToString()
                val state = reader.readOpaque4()
                val cursor = if (reader.readUint8() == 1) GroupCursor(reader.readUint64(), reader.readUint64()) else null
                Archive.Group(coordinator, gid, state, cursor, reader.readUint8() == 1)
            }

        val keyPackages =
            (0 until reader.readUint16()).map {
                Archive.KeyPackage(
                    coordinatorPubKey = reader.readOpaque2().decodeToString(),
                    keyPackageRef = reader.readOpaque2().decodeToString(),
                    bundle = reader.readOpaque4(),
                )
            }

        return Archive(accountPubKey, coordinators, groups, keyPackages)
    }

    private const val MIN_LOG_N = 10
    private const val MAX_LOG_N = 22
    private val HEADER_LENGTH = MAGIC.size + 2 + 1 + SALT_LENGTH + NONCE_LENGTH
}
