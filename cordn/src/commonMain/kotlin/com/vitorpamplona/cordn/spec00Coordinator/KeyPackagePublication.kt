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
package com.vitorpamplona.cordn.spec00Coordinator

import com.vitorpamplona.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Verifying that a coordinator-served KeyPackage really belongs to the account
 * it claims (`spec/00.md` §7 and §9).
 *
 * ## The payload is the request event
 *
 * cordn has no KeyPackage event kind. §7 requires a "signed publication
 * payload" binding publisher, bytes and lookup fields, and the reference
 * implementation satisfies it with the **`kp_publish` ContextVM request event
 * itself**: the coordinator reaches back into the transport for the signed
 * kind-25910 event, stores it verbatim, and returns it from `kp_take`
 * (`packages/server/src/coordinatorServer.ts` →
 * `transport.getNostrRequestEvent`).
 *
 * So the KeyPackage is recovered by parsing JSON-RPC out of an event's
 * `content`, and the identity binding is "the credential inside the KeyPackage
 * equals the event's `pubkey`". That means the invariant rides the shape of a
 * JSON-RPC envelope rather than a stable event schema — a rename in the
 * arguments object changes the wire format, and the reference client already
 * carries a fallback from exactly that happening once
 * (`kp_64 ?? keyPackageBase64`). We accept both for the same reason.
 *
 * Worth proposing upstream: give publication its own signed payload or its own
 * kind. It would also let a cordn KeyPackage be published to relays and
 * consumed with no coordinator at all.
 *
 * ## Why the client checks at all
 *
 * §8 makes the coordinator validate the same things. §9 makes us do it anyway,
 * and §10 says why: coordinator checks are admission control, not a trust
 * anchor. A coordinator that skipped them — or was modified to — could
 * otherwise hand us any account's name over any account's key material, and we
 * would invite the wrong person into a group.
 */
object KeyPackagePublication {
    private const val LEGACY_KP_FIELD = "keyPackageBase64"

    /** The arguments a `kp_publish` call carries. */
    fun arguments(
        keyPackageRef: String,
        keyPackageBase64: String,
    ): JsonObject =
        buildJsonObject {
            put(CoordinatorFields.KP_REF, keyPackageRef)
            put(CoordinatorFields.KP_64, keyPackageBase64)
        }

    /**
     * The base64 KeyPackage inside a publication event, or null when the event
     * is not a `kp_publish` call in a shape we recognise.
     */
    fun keyPackageBase64Of(publicationEvent: Event): String? {
        val params =
            try {
                Json.parseToJsonElement(publicationEvent.content).jsonObject["params"]?.jsonObject
            } catch (e: IllegalArgumentException) {
                return null
            } ?: return null
        val arguments = params["arguments"]?.jsonObject ?: return null
        return (arguments[CoordinatorFields.KP_64] ?: arguments[LEGACY_KP_FIELD])?.jsonPrimitive?.content
    }

    /**
     * Runs every §9 check and returns the decoded KeyPackage.
     *
     * @throws CoordinatorException naming the check that failed. Each one is a
     *   different lie: a bad signature means the payload was not written by its
     *   claimed author, a missing KeyPackage means the coordinator substituted
     *   its own representation (§7 forbids it), and a credential mismatch means
     *   someone published another account's key under their own name.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun verify(publicationEvent: Event): VerifiedKeyPackage {
        if (!publicationEvent.verify()) {
            throw CoordinatorException("KeyPackage publication payload signature is invalid")
        }

        val base64 =
            keyPackageBase64Of(publicationEvent)
                ?: throw CoordinatorException("KeyPackage publication payload carries no KeyPackage")

        val bytes =
            try {
                Base64.decode(base64)
            } catch (e: IllegalArgumentException) {
                throw CoordinatorException("KeyPackage publication payload KeyPackage is not valid base64", e)
            }

        val keyPackage =
            try {
                MlsKeyPackage.decodeTls(TlsReader(bytes))
            } catch (e: Exception) {
                throw CoordinatorException("KeyPackage publication payload KeyPackage does not decode", e)
            }

        val claimed = CordnCredential.identityOrNull(keyPackage.leafNode)
        if (claimed == null) {
            throw CoordinatorException(
                "KeyPackage credential is not a cordn identity: expected 64 hex ASCII bytes in a BasicCredential",
            )
        }
        if (claimed != publicationEvent.pubKey) {
            throw CoordinatorException(
                "KeyPackage credential identity $claimed does not match its publisher ${publicationEvent.pubKey}",
            )
        }

        return VerifiedKeyPackage(publicationEvent.pubKey, keyPackage, bytes)
    }

    /** Verifies, or returns null. For walking a list where one bad entry is not fatal. */
    fun verifyOrNull(publicationEvent: Event): VerifiedKeyPackage? =
        try {
            verify(publicationEvent)
        } catch (e: CoordinatorException) {
            null
        }
}

/** A KeyPackage whose identity binding has been checked against its signed payload. */
data class VerifiedKeyPackage(
    /** The account that signed the publication, and whose credential this carries. */
    val pubKey: HexKey,
    val keyPackage: MlsKeyPackage,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is VerifiedKeyPackage && pubKey == other.pubKey && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = pubKey.hashCode() * 31 + bytes.contentHashCode()
}
