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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Durable daily buckets for the resource-usage ledger, one JSON file in the
 * app's private filesDir. Same persistence idiom as ScheduledPostStore:
 * Jackson + Mutex + write-to-tmp-then-rename + version envelope.
 *
 * Day keys are UTC epoch-days (stringified for JSON). Buckets older than
 * [keepDays] are pruned on every merge, so the file stays small (a few KB).
 * Also carries the high-consumption alert state (last prompt time, opt-out)
 * so the whole feature has exactly one file.
 */
class ResourceUsageStore(
    private val storageFile: File,
    private val keepDays: Long = 30,
) {
    data class UsageFile(
        val version: Int = 1,
        val days: Map<String, Map<String, Long>> = emptyMap(),
        val lastAlertAtSec: Long = 0L,
        val alertsOptOut: Boolean = false,
    )

    private val mapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val mutex = Mutex()
    private var loaded = false
    private var data = UsageFile()

    suspend fun mergeInto(
        day: Long,
        deltas: Map<String, Long>,
    ) {
        if (deltas.isEmpty()) return
        mutex.withLock {
            ensureLoaded()
            val dayKey = day.toString()
            val bucket = data.days[dayKey].orEmpty().toMutableMap()
            deltas.forEach { (key, amount) -> bucket[key] = (bucket[key] ?: 0L) + amount }
            val pruned =
                data.days
                    .filterKeys { (it.toLongOrNull() ?: Long.MAX_VALUE) >= day - keepDays }
                    .toMutableMap()
            pruned[dayKey] = bucket
            data = data.copy(days = pruned)
            persist()
        }
    }

    /** All persisted daily buckets, keyed by epoch-day. */
    suspend fun allDays(): Map<Long, Map<String, Long>> =
        mutex.withLock {
            ensureLoaded()
            data.days.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()
        }

    suspend fun lastAlertAtSec(): Long =
        mutex.withLock {
            ensureLoaded()
            data.lastAlertAtSec
        }

    suspend fun alertsOptOut(): Boolean =
        mutex.withLock {
            ensureLoaded()
            data.alertsOptOut
        }

    suspend fun markAlertPrompted(atSec: Long) =
        mutex.withLock {
            ensureLoaded()
            data = data.copy(lastAlertAtSec = atSec)
            persist()
        }

    suspend fun setAlertsOptOut(optOut: Boolean) =
        mutex.withLock {
            ensureLoaded()
            data = data.copy(alertsOptOut = optOut)
            persist()
        }

    private fun ensureLoaded() {
        if (loaded) return
        data =
            try {
                if (storageFile.exists() && storageFile.length() > 0) {
                    mapper.readValue<UsageFile>(storageFile)
                } else {
                    UsageFile()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load resource usage from $storageFile", e)
                UsageFile()
            }
        loaded = true
    }

    private fun persist() {
        storageFile.parentFile?.mkdirs()
        val tmp = File(storageFile.parentFile, storageFile.name + ".tmp")
        try {
            mapper.writeValue(tmp, data)
            if (!tmp.renameTo(storageFile)) {
                if (!storageFile.delete() || !tmp.renameTo(storageFile)) {
                    Log.e(TAG) { "Failed to rename $tmp to $storageFile" }
                    if (!tmp.delete()) {
                        Log.w(TAG) { "Failed to clean up temp file $tmp" }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist resource usage to $storageFile", e)
            if (!tmp.delete()) {
                Log.w(TAG) { "Failed to clean up temp file $tmp" }
            }
        }
    }

    companion object {
        private const val TAG = "ResourceUsageStore"
        const val FILE_NAME = "resource_usage.json"
    }
}
