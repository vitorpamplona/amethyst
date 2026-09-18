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
package com.vitorpamplona.cordn.interop

import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.mls.crypto.Ed25519
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.mls.messages.MlsKeyPackage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Reads the vendored ts-mls fixtures. See `resources/tsmls/README.md`. */
@OptIn(ExperimentalEncodingApi::class)
object TsMlsFixtures {
    private fun resource(name: String): ByteArray =
        checkNotNull(TsMlsFixtures::class.java.getResourceAsStream("/tsmls/$name")) {
            "missing ts-mls fixture '$name'"
        }.use { it.readBytes() }

    fun bytes(name: String): ByteArray = resource(name)

    fun text(name: String): String = resource(name).decodeToString().trim()

    fun b64(name: String): ByteArray = Base64.decode(text(name))

    fun hex(name: String): ByteArray = text(name).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * ts-mls's `privateKeyPackageEncoder` layout:
 *
 * ```
 * opaque init_private_key<V> || opaque hpke_private_key<V> || opaque signature_private_key<V>
 * ```
 *
 * The two implementations store the Ed25519 signing key differently and neither
 * is wrong: ts-mls (noble/WebCrypto) writes a 48-byte PKCS#8 `PrivateKeyInfo`
 * wrapping the 32-byte seed; Quartz keeps `seed || public`. The seed is the only
 * thing either really holds, so that is what converts here, and the public half
 * is re-derived rather than trusted — a pair that disagrees with itself cannot
 * be built this way.
 *
 * Layout documented by Staircase (`cordn-core/…/KeyPackageBundleCodec.kt`, MIT);
 * this is our own implementation of it.
 */
object TsMlsPrivateKeyPackage {
    /** RFC 8410 PKCS#8 PrivateKeyInfo prefix for Ed25519; the 32-byte seed follows. */
    private val PKCS8_ED25519_PREFIX =
        "302e020100300506032b657004220420".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private const val SEED_SIZE = 32

    /** Accepts a bare seed, ts-mls's PKCS#8 blob, or Quartz's `seed || public`. */
    fun seedOf(privateKey: ByteArray): ByteArray =
        when (privateKey.size) {
            SEED_SIZE -> privateKey
            48 -> {
                require(privateKey.copyOfRange(0, 16).contentEquals(PKCS8_ED25519_PREFIX)) {
                    "unexpected PKCS#8 Ed25519 header"
                }
                privateKey.copyOfRange(16, 48)
            }
            64 -> privateKey.copyOfRange(0, SEED_SIZE)
            else -> throw IllegalArgumentException("unsupported Ed25519 private key length ${privateKey.size}")
        }

    fun decode(
        keyPackageBytes: ByteArray,
        privateBytes: ByteArray,
    ): KeyPackageBundle {
        val reader = TlsReader(privateBytes)
        val initKey = reader.readOpaqueVarInt()
        val encryptionKey = reader.readOpaqueVarInt()
        val signingSeed = seedOf(reader.readOpaqueVarInt())
        require(!reader.hasRemaining) { "trailing bytes in ts-mls private key package" }

        return KeyPackageBundle(
            keyPackage = MlsKeyPackage.decodeTls(TlsReader(keyPackageBytes)),
            initPrivateKey = initKey,
            encryptionPrivateKey = encryptionKey,
            signaturePrivateKey = Ed25519.keyPairFromSeed(signingSeed).privateKey,
        )
    }

    /** The inverse, so a round trip can be asserted. */
    fun encode(bundle: KeyPackageBundle): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(bundle.initPrivateKey)
        writer.putOpaqueVarInt(bundle.encryptionPrivateKey)
        writer.putOpaqueVarInt(PKCS8_ED25519_PREFIX + seedOf(bundle.signaturePrivateKey))
        return writer.toByteArray()
    }
}
