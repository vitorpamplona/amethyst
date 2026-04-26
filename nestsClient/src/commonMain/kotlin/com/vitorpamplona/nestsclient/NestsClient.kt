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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * HTTP control-plane entry point for talking to a nests-compatible
 * audio-room backend. Resolves a room's MoQ endpoint + bearer token via
 * NIP-98 auth — that's the only HTTP step before the WebTransport / MoQ
 * session takes over.
 *
 * The full connect orchestration (HTTP → WebTransport → MoQ → audio) lives
 * in [NestsListener] / `connectNestsListener`; this interface stays
 * narrowly focused on the control plane so testing the audio path doesn't
 * require an HTTP fake.
 */
interface NestsClient {
    /**
     * Fetch [NestsRoomInfo] for a specific room.
     *
     * @param serviceBase value of the NIP-53 kind 30312 `service` tag
     *     (e.g. `https://nostrnests.com/api/v1/nests`)
     * @param roomId the event's `d` tag
     * @param signer signs the NIP-98 auth event that the server uses to verify
     *     the caller owns the pubkey it claims
     * @throws NestsException on transport errors, non-2xx responses, or malformed
     *     JSON
     */
    suspend fun resolveRoom(
        serviceBase: String,
        roomId: String,
        signer: NostrSigner,
    ): NestsRoomInfo
}

/**
 * Single exception type surfaced to UI code so callers don't have to know about
 * platform HTTP libraries (OkHttp on Android/JVM today).
 */
class NestsException(
    message: String,
    cause: Throwable? = null,
    val status: Int? = null,
) : RuntimeException(message, cause)
