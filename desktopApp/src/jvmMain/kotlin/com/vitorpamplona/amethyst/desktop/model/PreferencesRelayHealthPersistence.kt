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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.commons.relays.health.LatencyMetric
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthPersistence
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthRecord
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthSnapshot
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.security.MessageDigest
import java.util.prefs.Preferences

/**
 * Desktop-side persistence for the relay-health feature. Backed by the same
 * `java.util.prefs.Preferences` node DesktopAccountRelays uses (different keys);
 * scoped per-account via the 8-char pubkey prefix that matches the rest of the
 * desktop persistence layer.
 *
 * Storage shape (single string value, line-delimited):
 *   first scan timestamp (seconds)
 *   last-seen-any timestamp (seconds)
 *   relay\tlastConnect\tlastIncoming\tsnoozedUntil
 *   ...
 *
 * 8 KB Preferences ceiling is generous (~150 relays at 50 B/row); if a user
 * has more, the tail is dropped silently and the classifier just sees fewer
 * records — no functional break.
 */
class PreferencesRelayHealthPersistence(
    private val userPubKeyHex: HexKey,
) : RelayHealthPersistence {
    private val prefs = Preferences.userNodeForPackage(PreferencesRelayHealthPersistence::class.java)

    private val storageKey: String = "health_${userPubKeyHex.take(8)}"

    private val latencyKeyPrefix: String = "lat_${userPubKeyHex.take(8)}_"

    override fun load(): RelayHealthSnapshot {
        return try {
            val raw = prefs.get(storageKey, null) ?: return RelayHealthSnapshot(latencySamples = loadLatencySamples())
            val lines = raw.split('\n')
            if (lines.size < 2) return RelayHealthSnapshot(latencySamples = loadLatencySamples())
            val firstScanAt = lines[0].toLongOrNull() ?: 0L
            val lastSeenAny = lines[1].toLongOrNull() ?: 0L
            val records =
                buildMap {
                    for (i in 2 until lines.size) {
                        val parts = lines[i].split('\t')
                        if (parts.size < 4) continue
                        val url = RelayUrlNormalizer.normalizeOrNull(parts[0]) ?: continue
                        val rec =
                            RelayHealthRecord(
                                lastConnectAt = parts[1].toLongOrNull() ?: 0L,
                                lastIncomingAt = parts[2].toLongOrNull() ?: 0L,
                                snoozedUntil = parts[3].toLongOrNull() ?: 0L,
                            )
                        put(url, rec)
                    }
                }
            RelayHealthSnapshot(records, firstScanAt, lastSeenAny, loadLatencySamples())
        } catch (_: Exception) {
            RelayHealthSnapshot()
        }
    }

    override fun save(snapshot: RelayHealthSnapshot) {
        try {
            val sb = StringBuilder()
            sb.append(snapshot.firstScanAt).append('\n')
            sb.append(snapshot.lastSeenAny)
            for ((url, rec) in snapshot.records) {
                sb.append('\n')
                sb.append(url.url).append('\t')
                sb.append(rec.lastConnectAt).append('\t')
                sb.append(rec.lastIncomingAt).append('\t')
                sb.append(rec.snoozedUntil)
                if (sb.length > MAX_PREFS_VALUE_LENGTH) break
            }
            prefs.put(storageKey, sb.toString().take(MAX_PREFS_VALUE_LENGTH))
            saveLatencySamples(snapshot.latencySamples)
            prefs.flush()
        } catch (_: Exception) {
        }
    }

    // ------ latency samples ------

    /**
     * Per-relay latency rings live in their own keys (`lat_<account>_<hash>`) so the 8 KB
     * Preferences ceiling on the main `health_<account>` key isn't blown by a heavy user.
     * Each key holds one relay's four metric rings: `wss://relay.url\tok:csv|eose:csv|fr:csv|ping:csv`.
     */
    private fun loadLatencySamples(): Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>> {
        return try {
            val keys = prefs.keys()
            if (keys.isEmpty()) return emptyMap()
            buildMap {
                for (k in keys) {
                    if (!k.startsWith(latencyKeyPrefix)) continue
                    val raw = prefs.get(k, null) ?: continue
                    val tab = raw.indexOf('\t')
                    if (tab <= 0) continue
                    val url = RelayUrlNormalizer.normalizeOrNull(raw.substring(0, tab)) ?: continue
                    val perMetric = decodeMetricCsv(raw.substring(tab + 1))
                    if (perMetric.isNotEmpty()) put(url, perMetric)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveLatencySamples(samples: Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>>) {
        val wantedKeys = mutableSetOf<String>()
        for ((url, perMetric) in samples) {
            if (perMetric.isEmpty()) continue
            val key = latencyKeyPrefix + relayKeySuffix(url)
            wantedKeys.add(key)
            val packed = url.url + "\t" + encodeMetricCsv(perMetric)
            // 8 KB Preferences ceiling per key; 50 samples × 4 metrics × ~5 chars ≈ 1 KB,
            // so single relays never come close. Truncate as a defensive measure only.
            prefs.put(key, packed.take(MAX_PREFS_VALUE_LENGTH))
        }
        // Drop keys for relays that are no longer in the snapshot (relay removed from account).
        for (k in prefs.keys()) {
            if (k.startsWith(latencyKeyPrefix) && k !in wantedKeys) prefs.remove(k)
        }
    }

    private fun encodeMetricCsv(perMetric: Map<LatencyMetric, IntArray>): String {
        val sb = StringBuilder()
        var first = true
        for (metric in LatencyMetric.entries) {
            val arr = perMetric[metric] ?: continue
            if (arr.isEmpty()) continue
            if (!first) sb.append('|')
            first = false
            sb.append(metricTag(metric)).append(':')
            for ((i, v) in arr.withIndex()) {
                if (i > 0) sb.append(',')
                sb.append(v)
            }
        }
        return sb.toString()
    }

    private fun decodeMetricCsv(raw: String): Map<LatencyMetric, IntArray> {
        if (raw.isBlank()) return emptyMap()
        val out = mutableMapOf<LatencyMetric, IntArray>()
        for (section in raw.split('|')) {
            val colon = section.indexOf(':')
            if (colon <= 0) continue
            val metric = parseMetricTag(section.substring(0, colon)) ?: continue
            val csv = section.substring(colon + 1)
            if (csv.isBlank()) continue
            val parts = csv.split(',')
            val arr = IntArray(parts.size)
            var n = 0
            for (p in parts) {
                val v = p.toIntOrNull() ?: continue
                arr[n++] = v
            }
            out[metric] = if (n == arr.size) arr else arr.copyOf(n)
        }
        return out
    }

    private fun metricTag(metric: LatencyMetric): String =
        when (metric) {
            LatencyMetric.OK_ACK -> "ok"
            LatencyMetric.EOSE -> "eose"
            LatencyMetric.FIRST_RESULT -> "fr"
            LatencyMetric.PING -> "ping"
        }

    private fun parseMetricTag(tag: String): LatencyMetric? =
        when (tag) {
            "ok" -> LatencyMetric.OK_ACK
            "eose" -> LatencyMetric.EOSE
            "fr" -> LatencyMetric.FIRST_RESULT
            "ping" -> LatencyMetric.PING
            else -> null
        }

    /** Short stable suffix from the relay URL. SHA-256 first 16 hex chars — 64 bits of entropy. */
    private fun relayKeySuffix(url: NormalizedRelayUrl): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.url.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(16)
        for (i in 0 until 8) {
            val b = digest[i].toInt() and 0xff
            sb.append(HEX[b shr 4])
            sb.append(HEX[b and 0xf])
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_PREFS_VALUE_LENGTH = 8000
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
