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
import com.vitorpamplona.nestsclient.moq.MoqSession
import com.vitorpamplona.nestsclient.moq.MoqVersion
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.TrackNamespace
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
 *      against the [room.endpoint] via [transport], passing the JWT as the
 *      bearer token.
 *   3. Run the MoQ SETUP handshake.
 *
 * Note: step 3 runs the IETF `draft-ietf-moq-transport-17` SETUP, which is
 * NOT what nostrnests's relay (moq-lite) expects. Steps 1 and 2 are
 * already on the moq-rs wire shape (path = `/<namespace>`, JWT in
 * `?jwt=`). The MoQ framing layer needs a moq-lite codec swap before
 * end-to-end interop works — see
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md`.
 *
 * The returned [NestsListener] is in state [NestsListenerState.Connected];
 * if any step fails, the listener is returned in
 * [NestsListenerState.Failed] with the underlying cause attached and the
 * transport torn down.
 *
 * @param room per-room config built from the NIP-53 kind 30312 event by
 *   the caller (UI / VM).
 * @param signer NIP-98 signer for the mintToken HTTP call.
 * @param scope where the [MoqSession] pumps live (typically the caller's
 *   ViewModel scope so they cancel when the screen leaves).
 * @param supportedMoqVersions in preference order; defaults to draft-17.
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

    val moq =
        try {
            MoqSession.client(webTransport, scope).also { it.setup(supportedMoqVersions) }
        } catch (t: Throwable) {
            runCatching { webTransport.close(0, "moq setup failed") }
            state.value = NestsListenerState.Failed("MoQ handshake failed: ${t.message}", t)
            return failedListener(state)
        }

    val negotiatedVersion =
        moq.selectedVersion ?: run {
            runCatching { moq.close() }
            state.value = NestsListenerState.Failed("MoQ session reported no negotiated version")
            return failedListener(state)
        }

    state.value = NestsListenerState.Connected(room, negotiatedVersion)
    return DefaultNestsListener(
        session = moq,
        // moq-auth's JWT claim is `root: "<moqNamespace>"` — the simplest
        // wire-level mapping is a 1-segment TrackNamespace whose only
        // element is that exact string. Phase-3 interop test will
        // confirm; if the real relay expects a multi-segment tuple
        // (e.g. split on `/` or `:`), adjust here.
        roomNamespace = TrackNamespace.of(room.moqNamespace()),
        mutableState = state,
    )
}

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
 * HTTP → WebTransport → MoQ handshake; the difference is the post-setup
 * step is `announce(...)` (driven by [NestsSpeaker.startBroadcasting])
 * instead of `subscribe(...)`.
 *
 * @param speakerPubkeyHex this user's pubkey hex, used as the MoQ track
 *   name when we ANNOUNCE — listeners look us up by exactly that name.
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

    val moq =
        try {
            MoqSession.client(webTransport, scope).also { it.setup(supportedMoqVersions) }
        } catch (t: Throwable) {
            runCatching { webTransport.close(0, "moq setup failed") }
            state.value = NestsSpeakerState.Failed("MoQ handshake failed: ${t.message}", t)
            return failedSpeaker(state)
        }

    val negotiatedVersion =
        moq.selectedVersion ?: run {
            runCatching { moq.close() }
            state.value = NestsSpeakerState.Failed("MoQ session reported no negotiated version")
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connected(room, negotiatedVersion)
    return DefaultNestsSpeaker(
        session = moq,
        // Same single-segment shape as the listener path; see comment there.
        roomNamespace = TrackNamespace.of(room.moqNamespace()),
        speakerTrackName = speakerPubkeyHex.encodeToByteArray(),
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
