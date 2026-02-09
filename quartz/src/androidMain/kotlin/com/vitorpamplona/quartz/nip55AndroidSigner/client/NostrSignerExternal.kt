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
package com.vitorpamplona.quartz.nip55AndroidSigner.client

import android.content.ContentResolver
import android.content.Intent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip55AndroidSigner.api.DecryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.DerivationResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.EncryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.ZapEventDecryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.client.handlers.BackgroundRequestHandler
import com.vitorpamplona.quartz.nip55AndroidSigner.client.handlers.ForegroundRequestHandler
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

class NostrSignerExternal(
    pubKey: HexKey,
    packageName: String,
    contentResolver: ContentResolver,
) : NostrSigner(pubKey),
    IActivityLauncher {
    override fun isWriteable(): Boolean = true

    val backgroundQuery = BackgroundRequestHandler(pubKey, packageName, contentResolver)
    val foregroundQuery = ForegroundRequestHandler(pubKey, packageName)

    override fun registerForegroundLauncher(launcher: ((Intent) -> Unit)) {
        this.foregroundQuery.launcher.registerForegroundLauncher(launcher)
    }

    override fun unregisterForegroundLauncher(launcher: ((Intent) -> Unit)) {
        this.foregroundQuery.launcher.unregisterForegroundLauncher(launcher)
    }

    override fun newResponse(data: Intent) {
        this.foregroundQuery.launcher.newResponse(data)
    }

    override fun hasForegroundActivity() = this.foregroundQuery.launcher.hasForegroundActivity()

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        val unsignedEvent =
            Event(
                id = EventHasher.hashId(pubKey, createdAt, kind, tags, content),
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )

        val result = backgroundQuery.sign(unsignedEvent) ?: foregroundQuery.sign(unsignedEvent)

        if (result is SignerResult.RequestAddressed.Successful<SignResult>) {
            (result.result.event as? T)?.let {
                return it
            }
        }

        throw convertExceptions("Could not sign", result)
    }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        if (plaintext.isBlank()) return ""

        val result = backgroundQuery.nip04Encrypt(plaintext, toPublicKey) ?: foregroundQuery.nip04Encrypt(plaintext, toPublicKey)

        if (result is SignerResult.RequestAddressed.Successful<EncryptionResult>) {
            return result.result.ciphertext
        }

        throw convertExceptions("Could not encrypt", result)
    }

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        if (ciphertext.isBlank()) throw SignerExceptions.NothingToDecrypt()

        val result = backgroundQuery.nip04Decrypt(ciphertext, fromPublicKey) ?: foregroundQuery.nip04Decrypt(ciphertext, fromPublicKey)

        if (result is SignerResult.RequestAddressed.Successful<DecryptionResult>) {
            return result.result.plaintext
        }

        throw convertExceptions("Could not decrypt", result)
    }

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        if (plaintext.isBlank()) return ""

        val result = backgroundQuery.nip44Encrypt(plaintext, toPublicKey) ?: foregroundQuery.nip44Encrypt(plaintext, toPublicKey)

        if (result is SignerResult.RequestAddressed.Successful<EncryptionResult>) {
            return result.result.ciphertext
        }

        throw convertExceptions("Could not encrypt", result)
    }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        if (ciphertext.isBlank()) throw SignerExceptions.NothingToDecrypt()

        val result = backgroundQuery.nip44Decrypt(ciphertext, fromPublicKey) ?: foregroundQuery.nip44Decrypt(ciphertext, fromPublicKey)

        if (result is SignerResult.RequestAddressed.Successful<DecryptionResult>) {
            return result.result.plaintext
        }

        throw convertExceptions("Could not decrypt", result)
    }

    override suspend fun deriveKey(nonce: HexKey): HexKey {
        val result = backgroundQuery.deriveKey(nonce) ?: foregroundQuery.deriveKey(nonce)

        if (result is SignerResult.RequestAddressed.Successful<DerivationResult>) {
            return result.result.newPrivKey
        }

        throw convertExceptions("Could not derive key", result)
    }

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent {
        if (!event.isPrivateZap()) throw SignerExceptions.NothingToDecrypt()

        val result = backgroundQuery.decryptZapEvent(event) ?: foregroundQuery.decryptZapEvent(event)

        if (result is SignerResult.RequestAddressed.Successful<ZapEventDecryptionResult>) {
            return result.result.privateEvent
        }

        throw convertExceptions("Could not decrypt private zap", result)
    }

    // always ready
    override fun hasForegroundSupport() = hasForegroundActivity()

    fun convertExceptions(
        title: String,
        result: SignerResult.RequestAddressed<*>,
    ): Exception =
        when (result) {
            is SignerResult.RequestAddressed.Successful<*> -> IllegalStateException("$title: This should not happen. There is a bug on Quartz.")
            is SignerResult.RequestAddressed.ReceivedButCouldNotParseEventFromResult<*> -> IllegalStateException("$title: Failed to parse event: ${result.eventJson}.")
            is SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent<*> -> IllegalStateException("$title: Failed to verify event: ${result.invalidEvent.toJson()}.")
            is SignerResult.RequestAddressed.ReceivedButCouldNotPerform<*> -> SignerExceptions.CouldNotPerformException("$title: ${result.message}")
            is SignerResult.RequestAddressed.SignerNotFound<*> -> SignerExceptions.SignerNotFoundException("$title: Signer app was not found.")
            is SignerResult.RequestAddressed.AutomaticallyRejected<*> -> SignerExceptions.AutomaticallyUnauthorizedException("$title: User has rejected the request.")
            is SignerResult.RequestAddressed.ManuallyRejected<*> -> SignerExceptions.ManuallyUnauthorizedException("$title: User has rejected the request.")
            is SignerResult.RequestAddressed.TimedOut<*> -> SignerExceptions.TimedOutException("$title: User didn't accept or reject in time.")
            is SignerResult.RequestAddressed.NoActivityToLaunchFrom<*> -> SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException("$title: No activity to launch from.")
        }
}
