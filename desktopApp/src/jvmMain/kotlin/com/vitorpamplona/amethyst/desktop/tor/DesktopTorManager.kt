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
package com.vitorpamplona.amethyst.desktop.tor

import com.vitorpamplona.amethyst.commons.tor.ITorManager
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.utils.Log
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonSync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Desktop Tor daemon manager using kmp-tor.
 *
 * Reactive: automatically starts/stops Tor daemon based on [torTypeFlow] changes.
 * Supports INTERNAL (embedded kmp-tor) and EXTERNAL (user-provided SOCKS port) modes.
 */
class DesktopTorManager(
    private val torTypeFlow: StateFlow<TorType>,
    private val externalPortFlow: StateFlow<Int>,
    private val scope: CoroutineScope,
) : ITorManager {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    override val activePortOrNull: StateFlow<Int?> =
        _status
            .map { (it as? TorServiceStatus.Active)?.port }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val runtime: TorRuntime by lazy {
        TorRuntime.Builder(desktopEnvironment()) {
            observerStatic(
                RuntimeEvent.LISTENERS,
                OnEvent.Executor.Immediate,
                OnEvent { listeners ->
                    val port =
                        listeners.socks
                            .firstOrNull()
                            ?.port
                            ?.value
                    if (port != null) {
                        _status.value = TorServiceStatus.Active(port)
                    }
                },
            )

            observerStatic(
                RuntimeEvent.STATE,
                OnEvent.Executor.Immediate,
                OnEvent { state ->
                    val daemon = state.daemon
                    when {
                        daemon is TorState.Daemon.Off -> {
                            _status.value = TorServiceStatus.Off
                        }

                        daemon is TorState.Daemon.Starting -> {
                            _status.value = TorServiceStatus.Connecting
                        }

                        daemon is TorState.Daemon.Stopping -> {
                            _status.value = TorServiceStatus.Connecting
                        }
                    }
                },
            )

            // Suppress verbose logging to prevent relay hostname leaks
            required(TorEvent.ERR)
            required(TorEvent.WARN)

            config { _ ->
                TorOption.__SocksPort.configure { auto() }
            }
        }
    }

    init {
        // Reactive: auto-derive Tor lifecycle from settings changes
        scope.launch {
            torTypeFlow.collect { mode ->
                when (mode) {
                    TorType.INTERNAL -> {
                        _status.value = TorServiceStatus.Connecting
                        try {
                            runtime.startDaemonAsync()
                            // LISTENERS event will set Active(port)
                        } catch (e: Exception) {
                            Log.e("DesktopTorManager", "Failed to start Tor", e)
                            _status.value = TorServiceStatus.Error(e.message ?: "Unknown error")
                        }
                    }

                    TorType.EXTERNAL -> {
                        _status.value = TorServiceStatus.Active(externalPortFlow.value)
                    }

                    TorType.OFF -> {
                        try {
                            runtime.stopDaemonAsync()
                        } catch (_: Exception) {
                            // May not be running
                        }
                        _status.value = TorServiceStatus.Off
                    }
                }
            }
        }
    }

    override suspend fun dormant() {
        if (_status.value is TorServiceStatus.Active) {
            runtime.enqueue(TorCmd.Signal.Dormant, OnFailure.noOp(), OnSuccess.noOp())
        }
    }

    override suspend fun active() {
        if (_status.value is TorServiceStatus.Active) {
            runtime.enqueue(TorCmd.Signal.Active, OnFailure.noOp(), OnSuccess.noOp())
        }
    }

    override suspend fun newIdentity() {
        if (_status.value is TorServiceStatus.Active) {
            runtime.enqueue(TorCmd.Signal.NewNym, OnFailure.noOp(), OnSuccess.noOp())
        }
    }

    /** Call from shutdown hook to stop Tor synchronously. */
    fun stopSync() {
        try {
            runtime.stopDaemonSync()
        } catch (_: Exception) {
            // Best-effort
        }
    }

    companion object {
        private fun desktopEnvironment(): TorRuntime.Environment {
            val appDir = torDataDirectory()
            appDir.mkdirs()
            // Restrict permissions to owner only (700)
            appDir.setReadable(false, false)
            appDir.setReadable(true, true)
            appDir.setWritable(false, false)
            appDir.setWritable(true, true)
            appDir.setExecutable(false, false)
            appDir.setExecutable(true, true)

            return TorRuntime.Environment.Builder(
                workDirectory = appDir.resolve("work"),
                cacheDirectory = appDir.resolve("cache"),
                loader = ResourceLoaderTorExec::getOrCreate,
            ) {}
        }

        /** OS-specific data directory for Tor. */
        internal fun torDataDirectory(): File {
            val osName = System.getProperty("os.name", "").lowercase()
            val home = System.getProperty("user.home") ?: "."
            return when {
                osName.contains("mac") -> {
                    File(home, "Library/Application Support/Amethyst/tor")
                }

                osName.contains("win") -> {
                    File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Amethyst/tor")
                }

                else -> {
                    val xdgData = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
                    File(xdgData, "Amethyst/tor")
                }
            }
        }
    }
}
