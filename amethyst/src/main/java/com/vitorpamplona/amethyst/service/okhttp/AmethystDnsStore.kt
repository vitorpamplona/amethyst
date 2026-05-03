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
package com.vitorpamplona.amethyst.service.okhttp

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.Log

/**
 * Persists [AmethystDns]'s positive cache to a small `SharedPreferences` blob so the resolver
 * starts hot after a process restart.
 *
 * Cold starts are the resolver's worst case — every host is a sync `getaddrinfo`. With a
 * persisted snapshot, every previously-seen host falls into the stale-while-revalidate path on
 * first lookup: the cached IP is served immediately, and a background refresh updates it. That
 * turns ~700 blocking system calls at app start into zero.
 *
 * The blob is plain (not encrypted) — hostnames are already exposed in the user's signed relay
 * list, Coil's image cache, and the system resolver's own state. ~700 entries × ~80 bytes ≈
 * ~55 KB of JSON.
 */
class AmethystDnsStore(
    private val context: Context,
    private val dns: AmethystDns = AmethystDns.shared,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Read the persisted snapshot and merge it into the resolver. Existing in-memory entries
     * are preserved (see [AmethystDns.restore]). Safe to call once at app start. Blocking I/O —
     * call from a background thread.
     */
    fun load() {
        val json = prefs.getString(KEY_CACHE, null) ?: return
        val records =
            try {
                MAPPER.readValue<List<DnsCacheRecord>>(json)
            } catch (t: Throwable) {
                Log.w(TAG) { "Dropping corrupt DNS cache blob: ${t.message}" }
                prefs.edit().remove(KEY_CACHE).apply()
                return
            }
        dns.restore(records)
        // Restoring entries that already existed in memory is a no-op, but the act of loading
        // shouldn't mark the cache dirty.
        dns.clearDirty()
        Log.d(TAG) { "Restored ${records.size} DNS cache entries" }
    }

    /**
     * Write the current snapshot to disk if the cache has changed since the last save. Blocking
     * I/O — call from a background thread.
     */
    fun save() {
        if (!dns.isDirty()) return
        val records = dns.snapshot()
        try {
            val json = MAPPER.writeValueAsString(records)
            prefs.edit().putString(KEY_CACHE, json).apply()
            dns.clearDirty()
            Log.d(TAG) { "Persisted ${records.size} DNS cache entries" }
        } catch (t: Throwable) {
            Log.w(TAG) { "Failed to persist DNS cache: ${t.message}" }
        }
    }

    /** Force-clear the on-disk cache. Useful for diagnostics or when the user wipes data. */
    fun clear() {
        prefs.edit().remove(KEY_CACHE).apply()
    }

    companion object {
        private const val TAG = "AmethystDnsStore"
        private const val PREFS_NAME = "amethyst_dns_cache"
        private const val KEY_CACHE = "dns_cache_v1"
        private val MAPPER = jacksonObjectMapper()
    }
}
