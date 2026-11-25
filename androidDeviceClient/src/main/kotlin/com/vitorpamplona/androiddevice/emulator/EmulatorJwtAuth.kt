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
package com.vitorpamplona.androiddevice.emulator

import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Signed-JWT access to an emulator started with `-grpc-use-jwt` (the default when Android Studio
 * starts one), as the emulator's own security code expects it (`JwtTokenAuth`,
 * `JwkDirectoryObserver` in AOSP's `android-grpc/security`):
 *
 * - the client writes its public key, as a JWK set in a `*.jwk` file, into the directory the
 *   discovery file names (`grpc.jwks`); the emulator watches it and lists the keys it accepted in
 *   `grpc.jwk_active`;
 * - each call carries `authorization: Bearer <jwt>`, an ES256 token with `iss`, an `iat` in the
 *   past, and an `aud` listing the method paths it may call.
 *
 * Which issuer may call which method is the emulator's allow list (`emulator_access.json`). This
 * client signs as [ISSUER], the issuer it gives to test tooling: the screen, touch, keys, status and
 * the rotation sensor only when the token names them in `aud`; never snapshots. It does not sign as Android Studio.
 *
 * The key lives in memory for this process; [close] takes its file back out of the directory.
 */
class EmulatorJwtAuth(
    private val jwksDir: Path,
    private val activeJwks: Path?,
    val issuer: String = ISSUER,
    private val audiences: List<String> = DEFAULT_AUDIENCES,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : AutoCloseable {
    private val keyPair: KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    /** This client's key id, in the JWK and in each token's header. */
    val keyId: String = "amethyst-" + UUID.randomUUID()

    private val keyFile: Path = jwksDir.resolve("$keyId.jwk")
    private var registered = false
    private var cached: String? = null
    private var cachedUntil = 0L

    /** The `authorization` header value, registering the key first if it is not yet. */
    @Synchronized
    fun authorization(): String {
        if (!registered) register()
        val now = nowSeconds()
        val token =
            cached?.takeIf { now < cachedUntil - RENEW_BEFORE_S } ?: token(now).also {
                cached = it
                cachedUntil = now + LIFETIME_S
            }
        return "Bearer $token"
    }

    /**
     * Writes the public key into the emulator's directory, then waits up to [waitMs] for the
     * emulator to list it as active. A key it did not take shows as UNAUTHENTICATED on the first call.
     */
    @Synchronized
    fun register(waitMs: Long = REGISTER_WAIT_MS) {
        if (registered) return
        if (!Files.isDirectory(jwksDir)) throw IOException("The emulator's key directory $jwksDir is not there")
        // Written in place, not renamed into place: the emulator's watcher acts on files created or
        // changed in the directory and never saw a rename (seen with emulator 37). It retries a file
        // it finds empty for a second, which covers the write.
        Files.writeString(keyFile, jwkSet())
        registered = true
        val active = activeJwks ?: return
        val deadline = System.currentTimeMillis() + waitMs
        while (System.currentTimeMillis() < deadline) {
            if (active.exists() && runCatching { active.readText() }.getOrDefault("").contains(keyId)) return
            Thread.sleep(POLL_MS)
        }
    }

    /** The public key as a JWK set, the format the emulator reads. */
    fun jwkSet(): String {
        val point = (keyPair.public as ECPublicKey).w
        return """{"keys":[{"kty":"EC","crv":"P-256","alg":"ES256","use":"sig","kid":"$keyId",""" +
            """"x":"${b64(unsigned32(point.affineX))}","y":"${b64(unsigned32(point.affineY))}"}]}"""
    }

    /** A signed token issued at [now] (a little before, so the emulator sees it as past). */
    fun token(now: Long): String {
        // No "typ": the emulator's validator (Tink) refuses a token that sets one it does not expect.
        val header = """{"alg":"ES256","kid":"$keyId"}"""
        val aud = audiences.joinToString(",") { "\"$it\"" }
        val claims = """{"iss":"$issuer","aud":[$aud],"iat":${now - CLOCK_SKEW_S},"exp":${now + LIFETIME_S}}"""
        val signingInput = b64(header.toByteArray()) + "." + b64(claims.toByteArray())
        // JWS wants the raw r || s, which the P1363 format is (not DER).
        val signature =
            Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initSign(keyPair.private)
                update(signingInput.toByteArray())
                sign()
            }
        return signingInput + "." + b64(signature)
    }

    override fun close() {
        try {
            Files.deleteIfExists(keyFile)
        } catch (e: IOException) {
            // The emulator's directory may be gone with the emulator.
        }
    }

    companion object {
        /** The issuer the emulator's default allow list gives to test tooling. */
        const val ISSUER = "gradle-utp-emulator-control"

        private const val METHOD_PREFIX = "/android.emulation.control.EmulatorController/"

        /** The methods the device panel calls, named in `aud` because the allow list protects them. */
        val DEFAULT_AUDIENCES: List<String> =
            listOf("getStatus", "getScreenshot", "streamScreenshot", "sendTouch", "sendKey", "setClipboard", "setPhysicalModel")
                .map { METHOD_PREFIX + it }

        private const val LIFETIME_S = 3_600L
        private const val RENEW_BEFORE_S = 60L
        private const val CLOCK_SKEW_S = 5L
        private const val REGISTER_WAIT_MS = 3_000L
        private const val POLL_MS = 100L

        private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        /** A P-256 coordinate as exactly 32 big-endian bytes (BigInteger may add a sign byte or drop zeros). */
        internal fun unsigned32(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            return when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
                else -> ByteArray(32 - bytes.size) + bytes
            }
        }
    }
}
