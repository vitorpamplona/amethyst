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
package com.vitorpamplona.amethyst.service.resourceusage

/**
 * Counter-key grammar for the resource-usage ledger. Keys are flat strings so
 * the on-disk store is schema-free — adding a counter never needs a migration.
 *
 * Dimensions:
 *  - network: `mobile` (cellular/metered) vs `wifi` (everything else)
 *  - visibility: `fg` (an activity is started) vs `bg`
 *  - direction: `rx` (downloaded) vs `tx` (uploaded)
 *
 * Counters are sizes, durations, and counts only — never URLs, relay names, or
 * content. See plans/2026-07-12-resource-usage-ledger.md.
 */
object UsageKeys {
    const val MOBILE = "mobile"
    const val WIFI = "wifi"
    const val FG = "fg"
    const val BG = "bg"
    const val RX = "rx"
    const val TX = "tx"

    /** HTTP subsystems, matching IRoleBasedHttpClientBuilder's roles. */
    const val ROLE_IMAGE = "image"
    const val ROLE_VIDEO = "video"
    const val ROLE_UPLOADS = "uploads"
    const val ROLE_MONEY = "money"
    const val ROLE_NIP05 = "nip05"
    const val ROLE_PREVIEW = "preview"
    const val ROLE_PUSH = "push"

    val HTTP_ROLES = listOf(ROLE_IMAGE, ROLE_VIDEO, ROLE_UPLOADS, ROLE_MONEY, ROLE_NIP05, ROLE_PREVIEW, ROLE_PUSH)

    /** `net.image.mobile.bg.rx` — HTTP bytes for a subsystem. */
    fun net(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
        received: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.${if (received) RX else TX}"

    /** `relay.msg.mobile.bg.rx` — approximate relay websocket payload bytes. */
    fun relayMsg(
        mobile: Boolean,
        foreground: Boolean,
        received: Boolean,
    ): String = "relay.msg.${dim(mobile, foreground)}.${if (received) RX else TX}"

    /** `relay.connms.mobile.bg` — Σ(open relay connections × elapsed ms). */
    fun relayConnMs(
        mobile: Boolean,
        foreground: Boolean,
    ): String = "relay.connms.${dim(mobile, foreground)}"

    /** `relay.connects.mobile.bg` — completed relay (re)connections: each one paid a TCP+TLS handshake. */
    fun relayConnects(
        mobile: Boolean,
        foreground: Boolean,
    ): String = "relay.connects.${dim(mobile, foreground)}"

    /** `relay.connfails.mobile.bg` — dials that failed before the websocket opened. */
    fun relayConnectFails(
        mobile: Boolean,
        foreground: Boolean,
    ): String = "relay.connfails.${dim(mobile, foreground)}"

    /** `worker.scheduledPost.runs` */
    fun workerRuns(worker: String): String = "worker.$worker.runs"

    const val WAKELOCK_NOTIF_MS = "wakelock.notif.ms"
    const val WAKELOCK_NOTIF_COUNT = "wakelock.notif.count"
    const val APP_STARTS = "app.starts"

    /** Whole-process CPU time (user+system) — the honest aggregate of parsing, crypto, coroutines, and UI. */
    const val CPU_MS = "cpu.ms"

    /** Time with at least one activity STARTED — the denominator that makes the other counters interpretable. */
    const val APP_FG_MS = "app.fgms"

    /** Event signature verifications (LocalCache.justVerify). */
    const val VERIFY_COUNT = "crypto.verify.count"
    const val VERIFY_US = "crypto.verify.us"

    fun dim(
        mobile: Boolean,
        foreground: Boolean,
    ): String = "${if (mobile) MOBILE else WIFI}.${if (foreground) FG else BG}"

    /** Sums every counter whose key matches all the given dot-delimited parts. */
    fun Map<String, Long>.sumMatching(vararg parts: String): Long {
        var total = 0L
        for ((key, value) in this) {
            val segments = key.split('.')
            if (parts.all { it in segments }) total += value
        }
        return total
    }
}
