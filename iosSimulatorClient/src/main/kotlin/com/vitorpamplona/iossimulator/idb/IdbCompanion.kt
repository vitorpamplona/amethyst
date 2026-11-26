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
package com.vitorpamplona.iossimulator.idb

import com.vitorpamplona.androiddevice.grpc.GrpcChannel
import com.vitorpamplona.androiddevice.grpc.UnixSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * One `idb_companion` serving one booted simulator, started by this app and stopped with it.
 *
 * It listens on a Unix socket in a folder only this user can open (`--grpc-domain-sock`): idb's
 * TCP mode binds every interface with no authentication, and its gRPC service installs apps and
 * reads files. When it says it is ready (a JSON line on its stdout naming the socket, or a port
 * from a companion that doesn't take sockets), [client] talks to it.
 */
class IdbCompanion private constructor(
    val udid: String,
    val process: Process,
    val client: IdbClient,
    private val folder: Path?,
) : Closeable {
    val isAlive: Boolean get() = process.isAlive

    override fun close() {
        process.destroy()
        if (!process.waitFor(STOP_WAIT_S, TimeUnit.SECONDS)) process.destroyForcibly()
        folder?.toFile()?.deleteRecursively()
    }

    companion object {
        private const val STOP_WAIT_S = 5L
        private const val READY_TIMEOUT_MS = 60_000L

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Starts [binary] for [udid] and returns once it listens. Throws [IOException] when it exits
         * first or doesn't say where it listens within [readyTimeoutMs]; [log] gets what it prints.
         */
        suspend fun start(
            binary: File,
            udid: String,
            log: File? = null,
            readyTimeoutMs: Long = READY_TIMEOUT_MS,
            /** Added to the companion's environment. */
            environment: Map<String, String> = emptyMap(),
        ): IdbCompanion {
            // Short on purpose: a socket path is limited to 104 bytes on macOS, and its temporary
            // folder (/var/folders/…/T/) takes half of that already.
            val folder =
                withContext(Dispatchers.IO) {
                    Files.createTempDirectory(Path.of("/tmp"), "amy-idb", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                }
            val socket = folder.resolve("${udid.take(8)}.sock")
            val command = listOf(binary.path, "--udid", udid, "--grpc-domain-sock", socket.toString(), "--log-level", "info")
            val process =
                withContext(Dispatchers.IO) {
                    ProcessBuilder(command)
                        .apply { environment().putAll(environment) }
                        .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                        .apply { if (log != null) redirectError(ProcessBuilder.Redirect.appendTo(log)) else redirectError(ProcessBuilder.Redirect.DISCARD) }
                        .start()
                }
            val ready = ReadyLine(process)
            try {
                val where =
                    try {
                        withTimeout(readyTimeoutMs) { runInterruptible(Dispatchers.IO) { ready.await() } }
                    } catch (e: TimeoutCancellationException) {
                        throw IOException("idb_companion didn't start listening in ${readyTimeoutMs / 1000}s", e)
                    } ?: throw IOException("idb_companion exited (code ${process.waitFor()}) before it listened${log?.let { ": ${tail(it)}" }.orEmpty()}")
                val channel =
                    when (where) {
                        is Listening.Socket -> GrpcChannel("idb-companion", 80, socketFactory = UnixSocketFactory(Path.of(where.path)), callTimeoutMs = 30_000)
                        is Listening.Port -> GrpcChannel("127.0.0.1", where.port, callTimeoutMs = 30_000)
                    }
                return IdbCompanion(udid, process, IdbClient(channel), folder)
            } catch (e: Throwable) {
                process.destroyForcibly()
                folder.toFile().deleteRecursively()
                throw e
            }
        }

        /** The last lines [log] holds, for an error. */
        fun tail(
            log: File,
            lines: Int = 6,
        ): String =
            try {
                log
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .takeLast(lines)
                    .joinToString(" / ")
            } catch (_: IOException) {
                ""
            }

        /** Where a companion's stdout line says it listens, or null for another line. */
        fun parseListening(line: String): Listening? {
            val o = runCatching { json.parseToJsonElement(line.trim()) as? JsonObject }.getOrNull() ?: return null
            o["grpc_path"]?.jsonPrimitive?.contentOrNull?.let { return Listening.Socket(it) }
            (o["grpc_port"] ?: o["grpc_swift_port"])?.jsonPrimitive?.intOrNull?.let { return Listening.Port(it) }
            return null
        }
    }

    sealed class Listening {
        data class Socket(
            val path: String,
        ) : Listening()

        data class Port(
            val port: Int,
        ) : Listening()
    }

    /** Reads the companion's stdout until it says where it listens, then keeps draining it. */
    private class ReadyLine(
        process: Process,
    ) {
        private val lock = Object()
        private var found: Listening? = null
        private var ended = false

        init {
            thread(isDaemon = true, name = "idb-companion-stdout") {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            parseListening(line)?.let { where ->
                                synchronized(lock) {
                                    if (found == null) found = where
                                    lock.notifyAll()
                                }
                            }
                        }
                    }
                } catch (_: IOException) {
                    // The process went: ended below says so.
                }
                synchronized(lock) {
                    ended = true
                    lock.notifyAll()
                }
            }
        }

        /** Where it listens, or null when its stdout ended first. Blocks; interruptible. */
        fun await(): Listening? =
            synchronized(lock) {
                while (found == null && !ended) lock.wait()
                found
            }
    }
}
