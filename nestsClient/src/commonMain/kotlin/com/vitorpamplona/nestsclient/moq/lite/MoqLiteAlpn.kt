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
 * moq-lite ALPN strings. ONLY [LITE_03] is wire-compatible with the
 * codec in [MoqLiteCodec]; the other constants are kept for
 * documentation and future codec upgrades.
 *
 * Subscribe / Group framing is identical across Lite-03 and Lite-04,
 * but Lite-04 reshapes Announce.hops into an `OriginList`
 * (`kixelated/moq` commit 45db108, "moq-lite/moq-relay: hop-based
 * clustering"), adds an `exclude_hop` field to AnnounceInterest, and
 * adds an `rtt` field to Probe. A relay that picks Lite-04 with our
 * Lite-03 codec on the wire desyncs on the first Announce exchange.
 * Don't add [LITE_04] to `wt-available-protocols` until
 * [MoqLiteCodec] is version-aware and [MoqLiteAnnounce.hops] is a
 * list rather than a single varint.
 *
 * `"moql"` is the legacy combined ALPN that requires an in-band SETUP
 * exchange — kept here for completeness; not advertised by the
 * factory.
 *
 * Source: `kixelated/moq/rs/moq-lite/src/lite/version.rs`,
 * `kixelated/moq/rs/moq-lite/src/lite/announce.rs:11-105`,
 * `kixelated/moq/rs/moq-lite/src/lite/probe.rs:9-55`.
 */
object MoqLiteAlpn {
    const val LITE_03: String = "moq-lite-03"

    /**
     * `moq-lite-04` ALPN string. Wire-incompatible with [MoqLiteCodec]
     * today — see the object kdoc for the codec diff. Defined here so
     * a future patch that lands version-aware Announce / Probe codecs
     * can drop it into the [QuicWebTransportFactory] sub-protocol list
     * without re-deriving the constant.
     */
    const val LITE_04: String = "moq-lite-04"
    const val LEGACY: String = "moql"
}
