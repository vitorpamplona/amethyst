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

import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_bootstrap_progress_permille
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_destroy
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_initialize
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_is_bootstrapped
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_set_log_callback
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_socks_accepted_count
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_start_socks_proxy
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_stop_socks_proxy
import com.vitorpamplona.amethyst.ios.tor.cinterop.arti_version
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlin.concurrent.AtomicReference

/**
 * Kotlin face of the Arti wrapper's C ABI (`tools/arti-build/src/ffi.rs`, header
 * `tools/arti-build/include/arti_ffi.h`): the calls Android's JNI `ArtiNative` has, so B12b can
 * put one `ArtiBridge` interface over both.
 *
 * Arti's state is process-global: one TorClient, one SOCKS listener. Calls are cheap except
 * [initialize] (opens the state files, ~100 ms), [stopSocksProxy] (~100 ms) and [destroy]
 * (~0.5-1.5 s), which block the calling thread; keep them off the main thread.
 *
 * Linking: nothing here links `libarti.a` by itself. AmethystKit is a static framework, so its
 * `_arti_*` references stay undefined until the final link: Xcode's app link and the test
 * executables (`iosApp/build.gradle.kts`, `iosApp/xcode/project.yml`) pass `-larti -liconv`.
 * `internal`, so it stays out of AmethystKit's Objective-C header.
 */
@OptIn(ExperimentalForeignApi::class)
internal object ArtiNative {
    /** Returned by any call whose Rust body panicked (the panic is caught, not fatal). */
    const val ERR_PANIC = -100

    private val logSink = AtomicReference<((String) -> Unit)?>(null)

    // A C function cannot capture, so it reads the current sink. Runs on Arti's own threads.
    private val logTrampoline =
        staticCFunction { line: CPointer<ByteVar>? ->
            val text = line?.toKString() ?: return@staticCFunction
            try {
                logSink.value?.invoke(text)
            } catch (e: Throwable) {
                // An exception must not unwind into Rust.
                println("ArtiNative log sink threw: $e")
            }
        }

    val version: String
        get() = arti_version()?.toKString() ?: ""

    /**
     * Every log line the wrapper emits, including the SOCKS handler's connect failures (what
     * Android's `AllGuardsDown` detection reads). [sink] must not call back into [ArtiNative].
     */
    fun setLogCallback(sink: ((String) -> Unit)?): Int {
        logSink.value = sink
        return arti_set_log_callback(if (sink != null) logTrampoline else null)
    }

    /** 0 on success (also when already initialized), negative on error. */
    fun initialize(dataDir: String): Int = arti_initialize(dataDir)

    /** 1 = ready for traffic, 0 = not yet, -1 = no client. */
    fun isBootstrapped(): Int = arti_is_bootstrapped()

    /** 0..1000, or -1 without a client. */
    fun bootstrapProgressPermille(): Int = arti_bootstrap_progress_permille()

    /** Binds 127.0.0.1:[port]. 0 ok, -1 no client, -2 no runtime, -3 bind failed. */
    fun startSocksProxy(port: Int): Int = arti_start_socks_proxy(port)

    /** Closes the listener. Accepted connections run to their end; the client stays. */
    fun stopSocksProxy(): Int = arti_stop_socks_proxy()

    /** Drops the client and everything holding it, so the next [initialize] starts fresh. */
    fun destroy(): Int = arti_destroy()

    /** SOCKS connections accepted since load. A test hook: proves traffic used the proxy. */
    fun socksAcceptedCount(): Long = arti_socks_accepted_count()
}
