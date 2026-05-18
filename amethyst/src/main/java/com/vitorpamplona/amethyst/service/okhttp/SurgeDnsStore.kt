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

import com.vitorpamplona.quartz.utils.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Persists [SurgeDns]'s positive cache to a small binary blob under the app's cache directory
 * so the resolver starts hot after a process restart.
 *
 * Cold starts are the resolver's worst case — every host is a sync `getaddrinfo`. With a
 * persisted snapshot, every previously-seen host falls into the stale-while-revalidate path on
 * first lookup: the cached IP is served immediately, and a background refresh updates it. That
 * turns ~700 blocking system calls at app start into zero.
 *
 * Stored under `cacheDir` because the snapshot is pure perf — if the OS evicts it under storage
 * pressure, the resolver just falls back to sync `getaddrinfo` and rebuilds the cache as
 * lookups happen. The blob is plain (not encrypted) — hostnames are already exposed in the
 * user's signed relay list, Coil's image cache, and the system resolver's own state.
 *
 * Format (big-endian, no padding):
 * ```
 * header:
 *   [u32 magic = 0x534E5343]   // 'SNSC'
 *   [u16 version = 1]
 *   [u32 record_count]
 * record (repeated record_count times):
 *   [u8  host_len]              // hostname ASCII, < 256 bytes
 *   [host_len bytes]            // UTF-8 hostname
 *   [u8  ip_count]
 *   per ip:
 *     [u8 ip_byte_len]          // 4 or 16
 *     [ip_byte_len bytes]       // raw, from InetAddress.address
 *   [i64 expiresAtMillis]
 * ```
 *
 * ~700 hosts × ~36 bytes ≈ ~25 KB.
 */
class SurgeDnsStore(
    private val file: File,
    private val dns: SurgeDns,
) {
    /**
     * Read the persisted snapshot and merge it into the resolver. Existing in-memory entries
     * are preserved (see [SurgeDns.restore]). Safe to call once at app start. Blocking I/O —
     * call from a background thread.
     */
    fun load() {
        if (!file.exists()) return
        val records =
            try {
                readRecords(file)
            } catch (t: Throwable) {
                Log.w(TAG) { "Dropping corrupt DNS cache blob: ${t.message}" }
                if (!file.delete()) Log.w(TAG) { "Failed to delete corrupt DNS cache blob at ${file.path}" }
                return
            }
        // restore() uses putIfAbsent and never marks dirty, so we deliberately do NOT clear the
        // dirty flag here — any concurrent put that happened before load completed must still be
        // persisted on the next save.
        dns.restore(records)
        Log.d(TAG) { "Restored ${records.size} DNS cache entries" }
    }

    /**
     * Write the current snapshot to disk if the cache has changed since the last save. Blocking
     * I/O — call from a background thread.
     */
    fun save() {
        // Clear BEFORE snapshot so any put racing with the snapshot/write re-marks dirty and is
        // captured by the next save instead of being silently lost. compareAndSet ensures two
        // concurrent saves don't both proceed.
        if (!dns.tryClearDirty()) return
        // Write atomically: write to a sibling tmp file then rename so a crash mid-write
        // can't leave a half-written blob that load() would have to throw away.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            val records = dns.snapshot()
            file.parentFile?.mkdirs()
            writeRecords(tmp, records)
            if (!tmp.renameTo(file)) {
                // If delete fails the second rename will fail too; skip straight to the copy fallback.
                if (!file.delete() || !tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                }
            }
            Log.d(TAG) { "Persisted ${records.size} DNS cache entries" }
        } catch (t: Throwable) {
            Log.w(TAG) { "Failed to persist DNS cache: ${t.message}" }
            // Restore the dirty signal so the next save retries.
            dns.markDirty()
        } finally {
            // Cleans up after both happy paths (copyTo fallback) and failure paths (writeRecords
            // crashed partway, leaving a partial blob) so a corrupt tmp can't accumulate.
            if (tmp.exists() && !tmp.delete()) Log.w(TAG) { "Failed to delete DNS cache tmp file at ${tmp.path}" }
        }
    }

    /** Force-clear the on-disk cache. Useful for diagnostics or when the user wipes data. */
    fun clear() {
        if (file.exists() && !file.delete()) Log.w(TAG) { "Failed to clear DNS cache blob at ${file.path}" }
    }

    private fun writeRecords(
        target: File,
        records: List<DnsCacheRecord>,
    ) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(target))).use { out ->
            out.writeInt(MAGIC)
            out.writeShort(VERSION)
            out.writeInt(records.size)
            for (record in records) {
                val hostBytes = record.hostname.toByteArray(Charsets.UTF_8)
                require(hostBytes.size <= MAX_HOST_LEN) { "hostname too long: ${hostBytes.size}" }
                require(record.addresses.size <= MAX_IPS_PER_HOST) { "too many IPs: ${record.addresses.size}" }
                out.writeByte(hostBytes.size)
                out.write(hostBytes)
                out.writeByte(record.addresses.size)
                for (ip in record.addresses) {
                    require(ip.size == 4 || ip.size == 16) { "bad ip byte length: ${ip.size}" }
                    out.writeByte(ip.size)
                    out.write(ip)
                }
                out.writeLong(record.expiresAtMillis)
            }
        }
    }

    private fun readRecords(source: File): List<DnsCacheRecord> =
        DataInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
            val magic = input.readInt()
            if (magic != MAGIC) throw IllegalStateException("bad magic: 0x${Integer.toHexString(magic)}")
            val version = input.readUnsignedShort()
            if (version != VERSION) throw IllegalStateException("unsupported version: $version")
            val count = input.readInt()
            if (count < 0 || count > MAX_RECORDS) throw IllegalStateException("bad record count: $count")
            val out = ArrayList<DnsCacheRecord>(count)
            repeat(count) {
                val hostLen = input.readUnsignedByte()
                // 0-length hostnames are nonsense; treat as corruption rather than restoring them.
                if (hostLen == 0) throw IllegalStateException("empty hostname")
                val hostBytes = ByteArray(hostLen)
                input.readFully(hostBytes)
                val hostname = String(hostBytes, Charsets.UTF_8)
                val ipCount = input.readUnsignedByte()
                if (ipCount == 0 || ipCount > MAX_IPS_PER_HOST) throw IllegalStateException("bad ip count: $ipCount")
                val ips = ArrayList<ByteArray>(ipCount)
                repeat(ipCount) {
                    val ipLen = input.readUnsignedByte()
                    if (ipLen != 4 && ipLen != 16) throw IllegalStateException("bad ip byte length: $ipLen")
                    val ipBytes = ByteArray(ipLen)
                    input.readFully(ipBytes)
                    ips += ipBytes
                }
                val expiresAt = input.readLong()
                out += DnsCacheRecord(hostname, ips, expiresAt)
            }
            out
        }

    companion object {
        private const val TAG = "SurgeDnsStore"
        const val FILE_NAME = "dns_cache_v1.bin"

        // 'SNSC' — Surge dNS Cache.
        private const val MAGIC = 0x534E5343
        private const val VERSION = 1
        private const val MAX_HOST_LEN = 255
        private const val MAX_IPS_PER_HOST = 255

        // Sane cap so a corrupt header can't trick load() into allocating gigabytes.
        private const val MAX_RECORDS = 100_000
    }
}
