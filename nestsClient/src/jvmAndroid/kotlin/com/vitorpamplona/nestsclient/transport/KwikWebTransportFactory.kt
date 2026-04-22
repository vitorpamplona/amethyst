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
package com.vitorpamplona.nestsclient.transport

/**
 * JVM + Android [WebTransportFactory] that **will** wrap the Kwik QUIC library
 * (plus Flupke for HTTP/3) once the Extended CONNECT handshake lands. Until
 * then, [connect] throws [WebTransportException] with
 * [WebTransportException.Kind.NotImplemented].
 *
 * The Phase 3c MoQ framing layer + Phase 3d audio pipeline + Phase 3d-3
 * `AudioRoomConnectionViewModel` are all wired against the
 * [WebTransportSession] interface, so the moment this class returns a real
 * session the entire stack starts producing audible output without further
 * changes upstream.
 *
 * ## Phase 3b-2 integration plan
 *
 * ### 1. Maven coordinates (verify before adding)
 *
 * Kwik is published by Peter Doornbosch (kwik.tech). As of writing the
 * coordinates appear to be `tech.kwik:kwik-core` and `tech.kwik:flupke` but
 * **this needs verification on the live Maven Central index** — earlier
 * versions used different group IDs. Suggested verification command:
 *
 * ```
 * curl -sf https://repo1.maven.org/maven2/tech/kwik/kwik-core/maven-metadata.xml
 * curl -sf https://repo1.maven.org/maven2/tech/kwik/flupke/maven-metadata.xml
 * ```
 *
 * Known-good versions to try (newest first): 0.11.x, 0.10.x, 0.9.x.
 *
 * If the `tech.kwik` group fails, fall back to `net.luminis.quic:kwik` (the
 * pre-2024 group) but pin to the latest 0.x release on that group.
 *
 * Add to `gradle/libs.versions.toml`:
 * ```toml
 * [versions]
 * kwik = "0.11.0"  # confirm against maven-metadata.xml first
 *
 * [libraries]
 * kwik-core = { group = "tech.kwik", name = "kwik-core", version.ref = "kwik" }
 * kwik-flupke = { group = "tech.kwik", name = "flupke", version.ref = "kwik" }
 * ```
 *
 * Add to `nestsClient/build.gradle.kts` under `androidMain.dependencies`:
 * ```kotlin
 * implementation(libs.kwik.core)
 * implementation(libs.kwik.flupke)
 * ```
 *
 * Kwik is pure Java with no JNI; both Android and JVM-desktop targets work.
 *
 * ### 2. Handshake sequence
 *
 *   a. **Resolve UDP socket** to `(authority host, authority port)` — port
 *      defaults to 443 for `https`/`wss` URLs.
 *   b. **QUIC dial** with ALPN list `["h3"]` (HTTP/3) via Kwik's connection
 *      builder. Kwik uses TLS 1.3; pass an `SSLContext` that accepts standard
 *      CA-issued certs (nests deployments use Let's Encrypt).
 *   c. **HTTP/3 SETTINGS** exchange via Flupke. Send a control stream with:
 *        - `SETTINGS_ENABLE_CONNECT_PROTOCOL = 1` (RFC 8441, identifier 0x08)
 *        - `SETTINGS_ENABLE_WEBTRANSPORT = 1` (WebTransport-H3 draft, identifier 0x2b603742)
 *        - `SETTINGS_H3_DATAGRAM = 1` (RFC 9297, identifier 0x33)
 *      Wait for the peer's SETTINGS frame; verify it advertises the same
 *      WebTransport setting before proceeding.
 *   d. **Extended CONNECT** request on a new client-bidi stream, as
 *      compressed HEADERS:
 *        - `:method = CONNECT`
 *        - `:protocol = webtransport`
 *        - `:scheme = https`
 *        - `:authority = <host>` (or `<host>:<port>` if non-default)
 *        - `:path = <path>` (defaults to `/`, nests uses `/moq`)
 *        - `Authorization: Bearer <bearerToken>` if non-null
 *        - `sec-webtransport-http3-draft02 = 1` for legacy server compat
 *      Read the response HEADERS; on `:status = 2xx` the session is open.
 *      On 4xx / 5xx throw [WebTransportException] with
 *      [WebTransportException.Kind.ConnectRejected].
 *
 * ### 3. Stream / datagram multiplexing
 *
 *   - **Bidi WT streams**: client opens a QUIC bidi stream; first VarInt
 *     written is the WT stream-type signal `0x41` followed by the WT session
 *     ID (the stream ID of the CONNECT bidi). Bytes that follow are
 *     application data (MoQ frames, in our case).
 *   - **Uni WT streams** (used by MoQ for OBJECT_STREAM): client opens a
 *     QUIC uni stream; first VarInt is `0x54` then the WT session ID.
 *   - **WT datagrams** (used by MoQ for OBJECT_DATAGRAM): wrap each app
 *     payload in an HTTP/3 DATAGRAM (RFC 9297) with the WT quarter-stream-id
 *     prefix, then push via Kwik's QUIC datagram API.
 *
 *   Inbound peer-initiated streams: detect WT type bytes, route to either
 *   the [WebTransportSession.incomingUniStreams] flow or the (Phase 3b-3 if
 *   needed) bidi-stream flow.
 *
 * ### 4. Lifecycle
 *
 *   - Spawn one coroutine to demux inbound QUIC streams into WT
 *     bidi/uni/datagram channels.
 *   - On [WebTransportSession.close], send a `WEBTRANSPORT_SESSION_CLOSE`
 *     capsule (an HTTP/3 capsule of type 0x2843) on the CONNECT bidi, then
 *     `connection.close()` on the underlying Kwik QUIC connection.
 *
 * ### 5. Test strategy
 *
 *   - Unit-test the WT framing helpers (varint stream-type prefix, capsule
 *     encode/decode) in `commonTest` before touching Kwik.
 *   - Instrumented `@LargeTest` against `nostrnests.com`:
 *     ```
 *     val factory = KwikWebTransportFactory()
 *     val session = factory.connect("nostrnests.com", "/moq")
 *     assertTrue(session.isOpen)
 *     session.sendDatagram(byteArrayOf(0x40)) // CLIENT_SETUP varint
 *     session.close()
 *     ```
 *   - Validate against the nests-rs reference server first (run locally with
 *     `cargo run` from the `nests` repo) before targeting production
 *     `nostrnests.com` — easier to read the server-side logs.
 *
 * ### Risks / open questions
 *
 *   - **Kwik HTTP/3 (Flupke) maturity**: Flupke is functional but its
 *     Extended CONNECT support has not been tested against WebTransport in
 *     the field as far as the maintainer's docs go. Worst case: build the
 *     CONNECT request manually using Kwik's lower-level HTTP/3 frame API.
 *   - **Draft churn**: WebTransport-H3 is a moving target. Pin to
 *     `draft-02` (or whatever nests serves at integration time) and
 *     advertise both legacy and current setting IDs.
 *   - **Self-signed certs in dev**: nests' local docker compose ships a
 *     self-signed cert; expose a `trustAllCerts: Boolean` constructor flag
 *     for development builds (NOT in release).
 *   - **Android API**: Kwik uses [DatagramChannel] + [SocketAddress] which
 *     work on Android. No JNI / no Cronet, so APK size impact is small.
 */
class KwikWebTransportFactory : WebTransportFactory {
    override suspend fun connect(
        authority: String,
        path: String,
        bearerToken: String?,
    ): WebTransportSession =
        throw WebTransportException(
            kind = WebTransportException.Kind.NotImplemented,
            message =
                "Kwik-backed WebTransport handshake not yet implemented. " +
                    "See KwikWebTransportFactory.kt header for the integration plan " +
                    "(Maven coords, handshake sequence, stream/datagram framing, test strategy).",
        )
}
