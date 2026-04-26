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

import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.MoqVersion
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.WebTransportException
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Walk the full join-as-listener handshake against a nests-compatible audio
 * server:
 *
 *   1. Mint a JWT — POST `<authBase>/auth` with NIP-98 + namespace body
 *      (see [NestsClient.mintToken]).
 *   2. Open a [com.vitorpamplona.nestsclient.transport.WebTransportSession]
 *      against the [room.endpoint] via [transport]. The path is
 *      `/<moqNamespace>?jwt=<token>` — moq-rs treats the URL path as
 *      `claims.root` and reads the JWT from `?jwt=`.
 *   3. Wrap the WT session in a [com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession].
 *      moq-lite Lite-03 has NO in-band SETUP message — the WT handshake
 *      itself is the handshake, version is selected by the ALPN
 *      `moq-lite-03`.
 *
 * The returned [NestsListener] is in state [NestsListenerState.Connected];
 * if any step fails, the listener is returned in
 * [NestsListenerState.Failed] with the underlying cause attached and the
 * transport torn down.
 *
 * @param room per-room config built from the NIP-53 kind 30312 event by
 *   the caller (UI / VM).
 * @param signer NIP-98 signer for the mintToken HTTP call.
 * @param scope where the moq-lite pumps live (typically the caller's
 *   ViewModel scope so they cancel when the screen leaves).
 * @param supportedMoqVersions retained for source-compatible callers but
 *   currently a no-op — moq-lite negotiates via ALPN, not an in-band
 *   SETUP message. Will be repurposed to drive ALPN preference once a
 *   second moq-lite version ships.
 */
suspend fun connectNestsListener(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
    supportedMoqVersions: List<Long> = listOf(MoqVersion.DRAFT_17),
): NestsListener {
    val state =
        MutableStateFlow<NestsListenerState>(
            NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.ResolvingRoom),
        )

    val token =
        try {
            httpClient.mintToken(room = room, publish = false, signer = signer)
        } catch (t: NestsException) {
            state.value = NestsListenerState.Failed("Auth failed: ${t.message}", t)
            return failedListener(state)
        }

    state.value = NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.OpeningTransport)

    val (authority, path) =
        try {
            buildRelayConnectTarget(room.endpoint, room.moqNamespace(), token)
        } catch (t: Throwable) {
            state.value =
                NestsListenerState.Failed(
                    "Malformed MoQ endpoint URL '${room.endpoint}': ${t.message}",
                    t,
                )
            return failedListener(state)
        }

    val webTransport =
        try {
            // moq-rs reads the JWT from the `?jwt=` query parameter and
            // ignores the Authorization header — bearer must be null here.
            transport.connect(authority = authority, path = path, bearerToken = null)
        } catch (t: WebTransportException) {
            state.value =
                NestsListenerState.Failed(
                    "WebTransport ${t.kind.name}: ${t.message}",
                    t,
                )
            return failedListener(state)
        }

    state.value = NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.MoqHandshake)

    // moq-lite Lite-03 has NO setup message — the WebTransport handshake
    // itself is the handshake, version is selected by ALPN. The
    // `supportedMoqVersions` parameter is retained for backward compat
    // with callers that may want to gate on a version mismatch in
    // future, but it is currently a no-op.
    val moq =
        try {
            com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
                .client(webTransport, scope)
        } catch (t: Throwable) {
            runCatching { webTransport.close(0, "moq-lite session init failed") }
            state.value = NestsListenerState.Failed("moq-lite session init failed: ${t.message}", t)
            return failedListener(state)
        }

    state.value = NestsListenerState.Connected(room, MOQ_LITE_03_VERSION)
    return MoqLiteNestsListener(
        session = moq,
        mutableState = state,
    )
}

/**
 * Synthetic version code reported on [NestsListenerState.Connected.negotiatedMoqVersion]
 * for moq-lite Lite-03 sessions. moq-lite negotiates the wire version
 * via ALPN rather than an in-band SETUP exchange, so there is no real
 * "version" the peer reports back. The constant carries the ALPN suffix
 * `-03` in the low bytes for diagnostic display.
 */
const val MOQ_LITE_03_VERSION: Long = 0x6D71_6C03L

/**
 * Build a no-op [NestsListener] in a Failed state for callers that want a
 * uniform return type even on early-failure paths.
 */
private fun failedListener(state: MutableStateFlow<NestsListenerState>): NestsListener =
    object : NestsListener {
        override val state = state

        override suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle = error("listener never connected: ${state.value}")

        override suspend fun close() {
            if (state.value !is NestsListenerState.Closed) {
                state.value = NestsListenerState.Closed
            }
        }
    }

/**
 * Speaker / host counterpart of [connectNestsListener]. Walks the same
 * three-step HTTP → WebTransport → moq-lite session sequence the
 * listener does; the difference is downstream: [NestsSpeaker.startBroadcasting]
 * claims a broadcast suffix on the moq-lite session and starts pumping
 * Opus frames out as one uni stream per group.
 *
 * @param speakerPubkeyHex this user's pubkey hex. Used as the moq-lite
 *   broadcast suffix the relay routes to subscribers
 *   (`MoqLiteSubscribe.broadcast == speakerPubkeyHex`); the JS reference
 *   mints the same value via `Path.from(identity)`
 *   (`@moq/publish/screen-B680RFft.js`).
 * @param captureFactory builds an [AudioCapture] (one per broadcast).
 *   Android passes `{ AudioRecordCapture() }`.
 * @param encoderFactory builds an [OpusEncoder] (one per broadcast).
 *   Android passes `{ MediaCodecOpusEncoder() }`.
 */
suspend fun connectNestsSpeaker(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
    speakerPubkeyHex: String,
    captureFactory: () -> AudioCapture,
    encoderFactory: () -> OpusEncoder,
    supportedMoqVersions: List<Long> = listOf(MoqVersion.DRAFT_17),
): NestsSpeaker {
    val state =
        MutableStateFlow<NestsSpeakerState>(
            NestsSpeakerState.Connecting(NestsSpeakerState.Connecting.ConnectStep.ResolvingRoom),
        )

    val token =
        try {
            httpClient.mintToken(room = room, publish = true, signer = signer)
        } catch (t: NestsException) {
            state.value = NestsSpeakerState.Failed("Auth failed: ${t.message}", t)
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connecting(NestsSpeakerState.Connecting.ConnectStep.OpeningTransport)

    val (authority, path) =
        try {
            buildRelayConnectTarget(room.endpoint, room.moqNamespace(), token)
        } catch (t: Throwable) {
            state.value =
                NestsSpeakerState.Failed(
                    "Malformed MoQ endpoint URL '${room.endpoint}': ${t.message}",
                    t,
                )
            return failedSpeaker(state)
        }

    val webTransport =
        try {
            // moq-rs reads the JWT from the `?jwt=` query parameter and
            // ignores the Authorization header — bearer must be null here.
            transport.connect(authority = authority, path = path, bearerToken = null)
        } catch (t: WebTransportException) {
            state.value =
                NestsSpeakerState.Failed(
                    "WebTransport ${t.kind.name}: ${t.message}",
                    t,
                )
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connecting(NestsSpeakerState.Connecting.ConnectStep.MoqHandshake)

    // moq-lite Lite-03 has NO setup message. Same logic as the listener
    // path — `supportedMoqVersions` retained for backward compat but
    // currently a no-op because version is selected by ALPN.
    val moq =
        try {
            com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
                .client(webTransport, scope)
        } catch (t: Throwable) {
            runCatching { webTransport.close(0, "moq-lite session init failed") }
            state.value = NestsSpeakerState.Failed("moq-lite session init failed: ${t.message}", t)
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connected(room, MOQ_LITE_03_VERSION)
    return MoqLiteNestsSpeaker(
        session = moq,
        speakerPubkeyHex = speakerPubkeyHex,
        captureFactory = captureFactory,
        encoderFactory = encoderFactory,
        scope = scope,
        mutableState = state,
    )
}

/** Mirror of [failedListener] for the speaker path. */
private fun failedSpeaker(state: MutableStateFlow<NestsSpeakerState>): NestsSpeaker =
    object : NestsSpeaker {
        override val state = state

        override suspend fun startBroadcasting(): BroadcastHandle = error("speaker never connected: ${state.value}")

        override suspend fun close() {
            if (state.value !is NestsSpeakerState.Closed) {
                state.value = NestsSpeakerState.Closed
            }
        }
    }

/**
 * Build the WebTransport connect target for a nests room.
 *
 *   - **Authority** comes from the kind-30312 `endpoint` URL (host + port).
 *   - **Path** is `/<moqNamespace>?jwt=<token>`. The JS reference client
 *     overwrites `relayUrl.pathname` with the namespace literal — moq-rs
 *     compares this path to `claims.root` for ANNOUNCE / SUBSCRIBE
 *     authorisation, so any other path on the relay returns
 *     `401 IncorrectRoot`.
 *   - **Token** is delivered as the `?jwt=` query parameter (NOT an
 *     `Authorization` header — moq-rs only reads the query param). Per the
 *     ES256 JWT alphabet (base64url `[A-Za-z0-9_-]` + `.`) the token never
 *     contains characters reserved in a query string, so no encoding is
 *     applied. The path itself contains `:` and `/`, both legal in
 *     `pchar` per RFC 3986.
 */
internal fun buildRelayConnectTarget(
    endpoint: String,
    namespace: String,
    token: String,
): Pair<String, String> {
    val (authority, _) = parseEndpoint(endpoint)
    val path = "/" + namespace + "?jwt=" + token
    return authority to path
}

/**
 * Split a typical nests endpoint URL such as `https://relay.example.com/moq`
 * or `https://relay.example.com:4443/api/v1/moq?room=abc` into the
 * WebTransport `(authority, path)` pair. WebTransport authority is
 * `host[:port]` (port omitted when it's the protocol default); path is
 * everything after the authority including any query string. Defaults to
 * `/` when the URL has no path.
 *
 * Hand-rolled rather than pulling in a URL library so this stays in
 * commonMain with no extra dependency.
 */
internal fun parseEndpoint(endpoint: String): Pair<String, String> {
    val schemeEnd = endpoint.indexOf("://")
    require(schemeEnd > 0) { "endpoint must include a scheme (got '$endpoint')" }
    val scheme = endpoint.substring(0, schemeEnd).lowercase()
    val rest = endpoint.substring(schemeEnd + 3)

    val pathSep = rest.indexOf('/')
    val (authorityRaw, pathRaw) =
        if (pathSep < 0) {
            rest to "/"
        } else {
            rest.substring(0, pathSep) to rest.substring(pathSep)
        }

    require(authorityRaw.isNotEmpty()) { "endpoint must include an authority (got '$endpoint')" }

    val portSep = authorityRaw.lastIndexOf(':')
    val hasUserInfo = authorityRaw.contains('@')
    require(!hasUserInfo) { "endpoint must not include userinfo (got '$endpoint')" }

    // Strip the port if it's the scheme default so the on-the-wire authority is
    // canonical.
    val authority =
        if (portSep >= 0) {
            val host = authorityRaw.substring(0, portSep)
            val portStr = authorityRaw.substring(portSep + 1)
            val port = portStr.toIntOrNull() ?: error("malformed port '$portStr' in '$endpoint'")
            val defaultPort =
                when (scheme) {
                    "https", "wss" -> 443
                    "http", "ws" -> 80
                    else -> -1
                }
            if (port == defaultPort) host else "$host:$port"
        } else {
            authorityRaw
        }

    return authority to pathRaw
}
