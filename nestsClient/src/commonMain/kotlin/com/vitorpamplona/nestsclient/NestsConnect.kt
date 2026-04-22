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
 *   1. Resolve the room — POST/GET `<serviceBase>/<roomId>` with NIP-98 auth,
 *      returning [NestsRoomInfo] (the MoQ endpoint + bearer token).
 *   2. Open a [com.vitorpamplona.nestsclient.transport.WebTransportSession]
 *      against the endpoint via [transport].
 *   3. Run the MoQ SETUP handshake.
 *
 * The returned [NestsListener] is in state [NestsListenerState.Connected];
 * if any step fails, the listener is returned in
 * [NestsListenerState.Failed] with the underlying cause attached and the
 * transport torn down.
 *
 * @param signer NIP-98 signer for the resolveRoom HTTP call.
 * @param scope where the [MoqSession] pumps live (typically the caller's
 *   ViewModel scope so they cancel when the screen leaves).
 * @param supportedMoqVersions in preference order; defaults to draft-17.
 */
suspend fun connectNestsListener(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    serviceBase: String,
    roomId: String,
    signer: NostrSigner,
    supportedMoqVersions: List<Long> = listOf(MoqVersion.DRAFT_17),
): NestsListener {
    val state =
        MutableStateFlow<NestsListenerState>(
            NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.ResolvingRoom),
        )

    val roomInfo =
        try {
            httpClient.resolveRoom(serviceBase = serviceBase, roomId = roomId, signer = signer)
        } catch (t: NestsException) {
            state.value = NestsListenerState.Failed("Room resolution failed: ${t.message}", t)
            return failedListener(state)
        }

    state.value = NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.OpeningTransport)

    val (authority, path) =
        try {
            parseEndpoint(roomInfo.endpoint)
        } catch (t: Throwable) {
            state.value =
                NestsListenerState.Failed(
                    "Malformed MoQ endpoint URL '${roomInfo.endpoint}': ${t.message}",
                    t,
                )
            return failedListener(state)
        }

    val webTransport =
        try {
            transport.connect(authority = authority, path = path, bearerToken = roomInfo.token)
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

    state.value = NestsListenerState.Connected(roomInfo, negotiatedVersion)
    return DefaultNestsListener(
        session = moq,
        roomNamespace = TrackNamespace.of("nests", roomId),
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
