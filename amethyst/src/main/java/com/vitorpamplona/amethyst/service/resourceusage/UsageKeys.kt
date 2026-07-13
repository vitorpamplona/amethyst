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

    /** Catch-all for HTTP requests that reach the shared clients without a role tag. */
    const val ROLE_OTHER = "other"

    val HTTP_ROLES = listOf(ROLE_IMAGE, ROLE_VIDEO, ROLE_UPLOADS, ROLE_MONEY, ROLE_NIP05, ROLE_PREVIEW, ROLE_PUSH, ROLE_OTHER)

    /** `net.image.mobile.bg.rx` — HTTP bytes for a subsystem. */
    fun net(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
        received: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.${if (received) RX else TX}"

    /** `net.image.mobile.bg.reqs` — HTTP request count for a subsystem. */
    fun netReqs(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.reqs"

    /** `net.image.mobile.bg.activems` — wall time spent actively transferring. */
    fun netActiveMs(
        role: String,
        mobile: Boolean,
        foreground: Boolean,
    ): String = "net.$role.${dim(mobile, foreground)}.activems"

    /** `net.bursts.mobile.bg` — estimated radio wake-ups caused by HTTP traffic. */
    fun radioBursts(
        mobile: Boolean,
        foreground: Boolean,
    ): String = "net.bursts.${dim(mobile, foreground)}"

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

    /** Media (video/audio) playback time — decoder + screen + streaming all at once. */
    const val MEDIA_PLAY_MS = "media.playms"

    /** NIP-13 proof-of-work mining: full-core CPU for as long as it runs. */
    const val POW_MS = "pow.ms"
    const val POW_SESSIONS = "pow.sessions"

    /** In-app (Arti) Tor: circuit crypto + directory/guard keep-alives while up; each start pays a bootstrap. */
    const val TOR_MS = "tor.ms"
    const val TOR_STARTS = "tor.starts"

    /** Always-on notification relay service uptime — the mode context for its relay connections. */
    const val ALWAYS_ON_MS = "service.alwayson.ms"

    /** Calls and NIP-53 audio rooms: mic + Opus + a live media connection. */
    const val CALL_MS = "call.ms"
    const val CALL_SESSIONS = "call.sessions"
    const val NESTS_MS = "nests.ms"
    const val NESTS_SESSIONS = "nests.sessions"

    /** Time spent actively listening for GPS/location updates (geohash tagging). */
    const val LOCATION_MS = "location.ms"

    /**
     * NIP-04/44 decryptions and encryptions through account signers. Durations
     * are only metered for local-key signers (CPU cost); external/remote
     * signer waits are IPC/network, tracked by the sign/decrypt counts alone.
     */
    const val DECRYPT_COUNT = "crypto.decrypt.count"
    const val DECRYPT_US = "crypto.decrypt.us"
    const val ENCRYPT_COUNT = "crypto.encrypt.count"
    const val ENCRYPT_US = "crypto.encrypt.us"

    /** `sign.nip46.count` — signatures by signer kind: local key, NIP-55 (Amber IPC), NIP-46 (relay round-trip). */
    fun signs(kind: String): String = "sign.$kind.count"

    const val SIGNER_LOCAL = "local"
    const val SIGNER_NIP46 = "nip46"
    const val SIGNER_NIP55 = "nip55"

    /**
     * Measured battery drain (percent points while discharging), split by
     * visibility. Not app-isolated — it's the ground truth the other counters
     * get correlated against across reports.
     */
    const val BATTERY_DRAIN_FG = "battery.drain.fg"
    const val BATTERY_DRAIN_BG = "battery.drain.bg"

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
