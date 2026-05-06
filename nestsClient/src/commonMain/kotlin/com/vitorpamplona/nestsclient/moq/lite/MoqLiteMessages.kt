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
package com.vitorpamplona.nestsclient.moq.lite

// Wire-format payload data classes for moq-lite messages.
//
// The discriminator enums (which message-body to expect at the top of
// a bidi / uni stream) live in `MoqLiteControlCodes.kt`; the ALPN
// negotiation strings live in `MoqLiteAlpn.kt`. This file holds only
// the body shapes the codec encodes / decodes once a discriminator
// has selected one.

/**
 * "I'm interested in broadcasts under this prefix" — the first message
 * the subscriber writes on an Announce bidi.
 *
 * Wire layout (size-prefixed):
 *   prefix  string  (varint length + UTF-8)
 *
 * Empty prefix means "everything".
 */
data class MoqLiteAnnouncePlease(
    val prefix: String,
)

/**
 * Per-broadcast announce update streamed by the publisher (or relay)
 * back to the subscriber on the same Announce bidi. One message per
 * known broadcast at attach time, then live updates as broadcasts come
 * and go.
 *
 * Wire layout (size-prefixed):
 *   status  u8       (0 = Ended, 1 = Active)
 *   suffix  string   (broadcast path with the requested `prefix`
 *                     stripped — `MoqLitePath.join(prefix, suffix)`
 *                     reconstitutes the absolute path)
 *   hops    u62      (relay routing depth, Lite-03 only)
 */
data class MoqLiteAnnounce(
    val status: MoqLiteAnnounceStatus,
    val suffix: String,
    val hops: Long,
)

/**
 * "Subscribe me to (broadcast, track)" — the first message the
 * subscriber writes on a Subscribe bidi.
 *
 * Wire layout (size-prefixed):
 *   id          u62 varint  (subscriber-chosen, monotonically increasing)
 *   broadcast   string      (absolute broadcast path, normalized)
 *   track       string      (opaque app string — `"audio/data"` /
 *                            `"catalog.json"` for nests)
 *   priority    u8          (raw byte 0..255)
 *   ordered     u8          (Lite-03; 0/1)
 *   maxLatency  varint      (Lite-03; *milliseconds*; 0 = unlimited)
 *   startGroup  varint      (Lite-03; 0 = "from latest",
 *                            else group_seq + 1)
 *   endGroup    varint      (Lite-03; 0 = "no end",
 *                            else group_seq + 1)
 */
data class MoqLiteSubscribe(
    val id: Long,
    val broadcast: String,
    val track: String,
    val priority: Int,
    val ordered: Boolean,
    val maxLatencyMillis: Long,
    val startGroup: Long?,
    val endGroup: Long?,
) {
    init {
        require(priority in 0..255) { "moq-lite priority must fit in a byte: $priority" }
        require(maxLatencyMillis >= 0) { "maxLatencyMillis must be non-negative: $maxLatencyMillis" }
        if (startGroup != null) require(startGroup >= 0) { "startGroup must be non-negative: $startGroup" }
        if (endGroup != null) require(endGroup >= 0) { "endGroup must be non-negative: $endGroup" }
    }
}

/**
 * Publisher's accept reply to a [MoqLiteSubscribe]. Echoes the
 * negotiated subscription parameters; the publisher may have narrowed
 * `startGroup` / `endGroup` from the subscriber's request.
 */
data class MoqLiteSubscribeOk(
    val priority: Int,
    val ordered: Boolean,
    val maxLatencyMillis: Long,
    val startGroup: Long?,
    val endGroup: Long?,
) {
    init {
        // Mirror the bounds [MoqLiteSubscribe] enforces on the request side
        // so a publisher building a malformed Ok reply fails loudly here
        // rather than silently writing a truncated byte on the wire.
        require(priority in 0..255) { "moq-lite priority must fit in a byte: $priority" }
        require(maxLatencyMillis >= 0) { "maxLatencyMillis must be non-negative: $maxLatencyMillis" }
        if (startGroup != null) require(startGroup >= 0) { "startGroup must be non-negative: $startGroup" }
        if (endGroup != null) require(endGroup >= 0) { "endGroup must be non-negative: $endGroup" }
    }
}

/**
 * Publisher's reject / drop reply. moq-lite has no SUBSCRIBE_ERROR
 * code; failures during a live subscription are conveyed by
 * RESET_STREAM, but the publisher can pre-emptively decline a
 * subscription with [MoqLiteSubscribeDrop] before any group flows.
 *
 * Sent by [MoqLiteSession] when a relay opens a SUBSCRIBE bidi for a
 * `track` we don't publish (e.g. a watcher subscribes to a `video/data`
 * rendition but our broadcast only declares `audio/data` + `catalog.json`
 * in its catalog). Without a Drop reply the watcher's response wait
 * resolves only when the bidi is FIN'd, with no indication WHY — Drop
 * carries the error code and a reason phrase the watcher can log /
 * surface.
 *
 * Decoded by [MoqLiteSession] when the relay or upstream publisher
 * rejects one of OUR subscriptions — we surface it as
 * [MoqLiteSubscribeException] so callers see a typed protocol-level
 * rejection rather than a silent end-of-flow.
 */
data class MoqLiteSubscribeDrop(
    val errorCode: Long,
    val reasonPhrase: String,
)

/**
 * Error codes carried in the [MoqLiteSubscribeDrop.errorCode] varint.
 * moq-lite leaves these application-defined; we mirror the IETF-MoQ
 * conventions in `com.vitorpamplona.nestsclient.moq.ErrorCode` so a
 * cross-protocol reader gets the same semantic from either path.
 */
object MoqLiteSubscribeDropCode {
    /**
     * The publisher does not serve this `(broadcast, track)` tuple.
     * Sent for a subscribe whose `track` doesn't match any of the
     * publishers we registered on this session. Mirrors
     * `com.vitorpamplona.nestsclient.moq.ErrorCode.TRACK_DOES_NOT_EXIST`.
     */
    const val TRACK_DOES_NOT_EXIST: Long = 0x04L
}

/**
 * Header at the start of a Group uni stream. After the
 * [MoqLiteDataType.Group] type byte, the publisher writes one
 * size-prefixed [MoqLiteGroupHeader] payload, then a sequence of
 * `varint(size) + payload` frames until QUIC FIN.
 */
data class MoqLiteGroupHeader(
    val subscribeId: Long,
    val sequence: Long,
)

/**
 * Probe message written by the *publisher* on a subscriber-initiated
 * Probe bidi (ControlType=4). `bitrate` is encoded as a u62 varint in
 * a size-prefixed body. Decode-only for now — the listener path
 * exposes the most recent reading, and we don't initiate probes from
 * the speaker side.
 *
 * Source: `rs/moq-lite/src/lite/probe.rs`.
 */
data class MoqLiteProbe(
    val bitrate: Long,
)
