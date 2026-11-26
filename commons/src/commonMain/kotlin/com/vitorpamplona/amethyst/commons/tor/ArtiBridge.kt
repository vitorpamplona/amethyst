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
package com.vitorpamplona.amethyst.commons.tor

/**
 * The native Arti client that [TorService] drives: our own wrapper crate in `tools/arti-build`,
 * reached through JNI on Android (`ArtiNativeBridge` over `ArtiNative`, `libarti_android.so`)
 * and through the C ABI on iOS (`ArtiCinteropBridge` over the cinterop `ArtiNative`,
 * `tools/arti-build/src/ffi.rs`). One engine and one wrapper on both, so the lifecycle above it
 * (its watchdogs, retries and guard heuristics) is shared rather than rediscovered per platform.
 *
 * Arti's state is process-global: one TorClient and one SOCKS listener per process, so there is
 * one bridge per process too. Every call blocks the calling thread and ignores coroutine
 * cancellation; [initialize], [stopSocksProxy] and [destroy] can take from ~100 ms to over a
 * second. [TorService] serializes them under its lifecycle lock and runs them on its IO
 * dispatcher; nothing else should call them.
 *
 * Return codes are the wrapper's: 0 on success, negative on error.
 */
interface ArtiBridge {
    /**
     * Forwards every Arti log line to [callback], on Arti's own threads. [callback] must be cheap
     * (a wedged Arti emits tens of thousands of lines) and must not call back into the bridge.
     */
    fun setLogCallback(callback: (String) -> Unit)

    /**
     * Creates the process-lifetime TorClient over [dataDir] with on-demand bootstrap: it returns in
     * ~130 ms and the directory download runs in the background. 0 on success, also when a client
     * already exists; negative on error.
     */
    fun initialize(dataDir: String): Int

    /**
     * Whether Tor can carry traffic right now: Arti's *live* readiness, polled, so a download that
     * failed once and then succeeded on a later stream is picked up.
     *
     * @return 1 when ready for traffic, 0 when not yet, -1 when there is no client.
     */
    fun isBootstrapped(): Int

    /** Directory-download progress in permille (0..1000), or -1 when there is no client. */
    fun bootstrapProgressPermille(): Int

    /**
     * Starts the SOCKS5 listener on 127.0.0.1:[port], stopping any existing one first.
     * 0 on success; negative on error, including the port being in use.
     */
    fun startSocksProxy(port: Int): Int

    /** Stops the SOCKS5 listener and releases the port. The TorClient stays alive. 0 on success. */
    fun stopSocksProxy(): Int

    /**
     * Drops the TorClient, the SOCKS listener and every in-flight connection handler, so the state
     * file lock is released and the next [initialize] rebuilds the client from scratch. 0 on success.
     */
    fun destroy(): Int
}
