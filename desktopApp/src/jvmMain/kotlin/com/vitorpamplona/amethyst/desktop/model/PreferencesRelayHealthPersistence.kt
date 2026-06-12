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

import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthPersistence
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthRecord
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthSnapshot
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
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

    override fun load(): RelayHealthSnapshot {
        return try {
            val raw = prefs.get(storageKey, null) ?: return RelayHealthSnapshot()
            val lines = raw.split('\n')
            if (lines.size < 2) return RelayHealthSnapshot()
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
            RelayHealthSnapshot(records, firstScanAt, lastSeenAny)
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
            prefs.flush()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MAX_PREFS_VALUE_LENGTH = 8000
    }
}
