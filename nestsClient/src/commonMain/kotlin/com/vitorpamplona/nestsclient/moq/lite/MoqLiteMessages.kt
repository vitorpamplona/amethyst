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

/**
 * moq-lite ALPN strings. Lite-03 is preferred; `"moql"` is the legacy
 * combined ALPN that requires an in-band SETUP exchange.
 *
 * Source: `kixelated/moq-rs/rs/moq-lite/src/version.rs:21-26`,
 * `@moq/lite/connection/connect.js:277`.
 */
object MoqLiteAlpn {
    const val LITE_03: String = "moq-lite-03"
    const val LEGACY: String = "moql"
}

/**
 * ControlType varint discriminator written as the first datum on every
 * client-initiated bidi stream. Selects which message body the peer
 * should expect to read next.
 *
 * Source: `rs/moq-lite/src/lite/stream.rs:7-15`.
 */
enum class MoqLiteControlType(
    val code: Long,
) {
    /** Lite-01/02 only — unused on Lite-03. Reserved here for completeness. */
    Session(0L),
    Announce(1L),
    Subscribe(2L),
    Fetch(3L),
    Probe(4L),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqLiteControlType? = byCode[code]
    }
}

/**
 * DataType varint written as the first byte of every uni stream that
 * carries payload. Lite-03 currently uses `Group=0` only.
 *
 * Source: `rs/moq-lite/src/lite/stream.rs:32-36`.
 */
enum class MoqLiteDataType(
    val code: Long,
) {
    Group(0L),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqLiteDataType? = byCode[code]
    }
}

/**
 * Status byte at the head of an [MoqLiteAnnounce] payload. `Active=1`
 * is sent on first publish; `Ended=0` on explicit unannounce. Disconnect
 * is NOT signalled with `Ended` — the bidi just closes.
 *
 * Source: `rs/moq-lite/src/lite/announce.rs:84-90`.
 */
enum class MoqLiteAnnounceStatus(
    val code: Int,
) {
    Ended(0),
    Active(1),
    ;

    companion object {
        fun fromCode(code: Int): MoqLiteAnnounceStatus? =
            when (code) {
                0 -> Ended
                1 -> Active
                else -> null
            }
    }
}

/**
 * Type discriminator at the head of a SubscribeResponse on the response
 * side of a Subscribe bidi. Wire layout (Lite-03+):
 *
 *   type   varint  (0 = Ok, 1 = Drop)
 *   body   size-prefixed bytes
 *
 * The type sits OUTSIDE the body size prefix — see
 * `rs/moq-lite/src/lite/subscribe.rs::SubscribeResponse::encode` (the
 * `_` arm covers Lite03+). Earlier drafts wrapped type+body in one
 * outer size prefix; Lite03 split them.
 */
enum class MoqLiteSubscribeResponseType(
    val code: Long,
) {
    Ok(0L),
    Drop(1L),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqLiteSubscribeResponseType? = byCode[code]
    }
}

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
 * Decode-only as far as the client cares — we never send Drop back
 * upstream.
 */
data class MoqLiteSubscribeDrop(
    val errorCode: Long,
    val reasonPhrase: String,
)

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
