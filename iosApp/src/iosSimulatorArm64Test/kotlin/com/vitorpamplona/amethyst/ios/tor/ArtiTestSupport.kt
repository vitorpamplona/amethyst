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
package com.vitorpamplona.amethyst.ios.tor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.darwin.KERN_SUCCESS
import platform.darwin.TASK_VM_INFO
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.natural_tVar
import platform.darwin.task_info
import platform.darwin.task_threads
import platform.darwin.task_vm_info_data_t
import platform.darwin.thread_act_array_tVar
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.concurrent.AtomicReference

/** Collects log lines from Arti's threads. */
class LogCollector {
    private val ref = AtomicReference(emptyList<String>())

    fun add(line: String) {
        while (true) {
            val current = ref.value
            if (ref.compareAndSet(current, current + line)) return
        }
    }

    val lines: List<String> get() = ref.value
}

fun freshArtiDir(tag: String): String = NSTemporaryDirectory() + "arti-$tag-" + NSUUID().UUIDString

/** Binds the SOCKS listener on the first free port from 17392 (Android's default) up. */
fun startSocksOnFreePort(): Int {
    for (port in 17392..17412) {
        if (ArtiNative.startSocksProxy(port) == 0) return port
    }
    return -1
}

/**
 * Sends a SOCKS5 greeting (version 5, one method: no auth) to 127.0.0.1:[port] over a raw socket
 * and returns the reply bytes, or null when nothing accepts the connection.
 */
@OptIn(ExperimentalForeignApi::class)
fun socksGreeting(port: Int): ByteArray? =
    memScoped {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return null
        try {
            val addr = alloc<sockaddr_in>()
            addr.sin_len = sizeOf<sockaddr_in>().convert()
            addr.sin_family = AF_INET.convert()
            // Network byte order by hand (htons is a macro the klib does not expose; arm64 is
            // little-endian): the port's bytes swapped, and 127.0.0.1 read back as 0x0100007F.
            addr.sin_port = (((port and 0xff) shl 8) or ((port shr 8) and 0xff)).toUShort()
            addr.sin_addr.s_addr = 0x0100007Fu
            if (connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) return null
            val greeting = byteArrayOf(5, 1, 0)
            greeting.usePinned { send(fd, it.addressOf(0), greeting.size.convert(), 0) }
            val reply = ByteArray(2)
            val n = reply.usePinned { recv(fd, it.addressOf(0), reply.size.convert(), 0) }
            reply.copyOf(n.toInt().coerceAtLeast(0))
        } finally {
            close(fd)
        }
    }

/** phys_footprint (what iOS's memory limit counts), resident size and thread count of this process. */
@OptIn(ExperimentalForeignApi::class)
fun processStats(): String =
    memScoped {
        val info = alloc<task_vm_info_data_t>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info_data_t>() / sizeOf<natural_tVar>()).convert()
        val kr = task_info(mach_task_self_, TASK_VM_INFO.convert(), info.ptr.reinterpret(), count.ptr)
        val threads = alloc<thread_act_array_tVar>()
        val threadCount = alloc<mach_msg_type_number_tVar>()
        // The thread list is not deallocated: a few bytes per call, in a test process.
        val threadsKr = task_threads(mach_task_self_, threads.ptr, threadCount.ptr)
        val mb = { bytes: ULong -> ((bytes.toDouble() / 1048576.0) * 10).toLong() / 10.0 }
        if (kr != KERN_SUCCESS) {
            "task_info failed ($kr)"
        } else {
            "phys_footprint=${mb(info.phys_footprint)}MB resident=${mb(info.resident_size)}MB " +
                "threads=${if (threadsKr == KERN_SUCCESS) threadCount.value.toString() else "?"}"
        }
    }
