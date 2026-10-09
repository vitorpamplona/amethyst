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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps the relay health snapshot (liveness timestamps and the latency samples behind the p50s)
 * in one JSON file, so the response times survive a restart instead of starting from zero.
 * Writes go to a temp file that is renamed over the old one; a missing or unreadable file loads
 * as an empty snapshot.
 */
class FileRelayHealthPersistence(
    private val file: File,
) : RelayHealthPersistence {
    @Serializable
    private class Stored(
        val records: Map<String, StoredRecord> = emptyMap(),
        val firstScanAt: Long = 0,
        val lastSeenAny: Long = 0,
        val latency: Map<String, Map<String, IntArray>> = emptyMap(),
    )

    @Serializable
    private class StoredRecord(
        val lastConnectAt: Long = 0,
        val lastIncomingAt: Long = 0,
        val snoozedUntil: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): RelayHealthSnapshot =
        try {
            if (!file.exists()) {
                RelayHealthSnapshot()
            } else {
                val stored = json.decodeFromString<Stored>(file.readText())
                RelayHealthSnapshot(
                    records =
                        stored.records.mapKeys { NormalizedRelayUrl(it.key) }.mapValues {
                            RelayHealthRecord(it.value.lastConnectAt, it.value.lastIncomingAt, it.value.snoozedUntil)
                        },
                    firstScanAt = stored.firstScanAt,
                    lastSeenAny = stored.lastSeenAny,
                    latencySamples =
                        stored.latency.mapKeys { NormalizedRelayUrl(it.key) }.mapValues { (_, perMetric) ->
                            perMetric
                                .mapNotNull { (name, samples) ->
                                    LatencyMetric.entries.firstOrNull { it.name == name }?.let { it to samples }
                                }.toMap()
                        },
                )
            }
        } catch (e: Exception) {
            Log.w("FileRelayHealthPersistence", "Starting over: could not read ${file.path}", e)
            RelayHealthSnapshot()
        }

    @Synchronized
    override fun save(snapshot: RelayHealthSnapshot) {
        val stored =
            Stored(
                records =
                    snapshot.records.entries.associate { (url, rec) ->
                        url.url to StoredRecord(rec.lastConnectAt, rec.lastIncomingAt, rec.snoozedUntil)
                    },
                firstScanAt = snapshot.firstScanAt,
                lastSeenAny = snapshot.lastSeenAny,
                latency =
                    snapshot.latencySamples.entries.associate { (url, perMetric) ->
                        url.url to perMetric.entries.associate { (metric, samples) -> metric.name to samples }
                    },
            )
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(Stored.serializer(), stored))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w("FileRelayHealthPersistence", "Could not save ${file.path}", e)
        }
    }
}
