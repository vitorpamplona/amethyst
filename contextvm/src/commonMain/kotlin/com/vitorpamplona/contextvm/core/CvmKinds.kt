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
package com.vitorpamplona.contextvm.core

import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral

/**
 * Nostr event kinds used by ContextVM.
 *
 * Implemented from the ContextVM specification and its CEPs, not from any
 * reference SDK source. See `quartz/plans/2026-09-17-cordn-interop.md` §6-§7
 * for the sourcing rule and the revision this targets.
 */
object CvmKinds {
    /**
     * The single kind carrying every ContextVM message. `content` is the
     * stringified MCP JSON-RPC message; addressing and correlation live in tags.
     *
     * This kind is **ephemeral**, so relays are not expected to retain it. A
     * client must already be subscribed when the peer publishes, because there
     * is no fetch-after-the-fact recovery (rule `CVM-CORE-06`).
     */
    const val MESSAGE: Kind = 25910

    /**
     * NIP-59 gift wrap carrying an encrypted [MESSAGE] (CEP-4).
     *
     * Not ephemeral: relays may retain the encrypted envelope. [EPHEMERAL_GIFT_WRAP]
     * exists to avoid that.
     */
    const val GIFT_WRAP: Kind = 1059

    /**
     * Ephemeral gift wrap (CEP-19) — identical structure and semantics to
     * [GIFT_WRAP], but in NIP-01's ephemeral range so relays do not store the
     * envelope either.
     */
    const val EPHEMERAL_GIFT_WRAP: Kind = 21059

    /** Addressable server announcement (CEP-6). `content` is the initialize result. */
    const val SERVER_ANNOUNCEMENT: Kind = 11316

    /** Addressable `tools/list` announcement (CEP-6). */
    const val TOOLS_LIST: Kind = 11317

    /** Addressable `resources/list` announcement (CEP-6). */
    const val RESOURCES_LIST: Kind = 11318

    /** Addressable `resources/templates/list` announcement (CEP-6). */
    const val RESOURCE_TEMPLATES_LIST: Kind = 11319

    /** Addressable `prompts/list` announcement (CEP-6). */
    const val PROMPTS_LIST: Kind = 11320

    /** NIP-65 relay list metadata, reused by CEP-17 for server reachability. */
    const val RELAY_LIST: Kind = 10002

    /** NIP-01 profile metadata, optionally published by servers (CEP-23). */
    const val PROFILE_METADATA: Kind = 0

    /** NIP-22 comment, reused by CEP-24 for server reviews. */
    const val REVIEW: Kind = 1111

    /** The announcement kinds a client subscribes to when discovering a server (CEP-6). */
    val ANNOUNCEMENTS =
        intArrayOf(
            SERVER_ANNOUNCEMENT,
            TOOLS_LIST,
            RESOURCES_LIST,
            RESOURCE_TEMPLATES_LIST,
            PROMPTS_LIST,
        )

    /**
     * Both gift wrap kinds. A client subscribes to both regardless of which it
     * sends: CEP-19 requires falling back to [GIFT_WRAP] for peers that do not
     * advertise ephemeral support, so either may arrive.
     */
    val GIFT_WRAPS = intArrayOf(GIFT_WRAP, EPHEMERAL_GIFT_WRAP)

    fun isGiftWrap(kind: Kind) = kind == GIFT_WRAP || kind == EPHEMERAL_GIFT_WRAP

    /**
     * True when relays are not expected to retain this kind.
     *
     * [MESSAGE] and [EPHEMERAL_GIFT_WRAP] are ephemeral; [GIFT_WRAP] is not,
     * which is the whole reason CEP-19 exists.
     */
    fun isTransient(kind: Kind) = kind.isEphemeral()
}
