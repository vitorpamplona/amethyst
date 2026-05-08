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
package com.vitorpamplona.amethyst.ui.tor

import android.content.Context
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.model.preferences.TorSharedPreferences
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * There should be only one instance of the Tor binding per app.
 *
 * Tor will connect as soon as status is listened to.
 */
class TorManager(
    private val torPrefs: TorSharedPreferences,
    app: Context,
    private val scope: CoroutineScope,
) {
    val service = TorService(app)

    /**
     * In-memory only — when true, the manager emits [TorServiceStatus.Off] regardless of
     * the persisted [TorType]. Cleared on process death, on network change, and on any
     * user-initiated change to [TorType].
     */
    val sessionBypass = MutableStateFlow(false)

    /**
     * Epoch-millis of the user's most recent "Use regular connection" choice, persisted
     * across cold starts. While [APPROVAL_REMEMBER_MS] hasn't elapsed, a stuck-connecting
     * timeout silently flips [sessionBypass] without re-prompting.
     */
    @Volatile private var lastBypassApprovalMs: Long = 0L

    init {
        scope.launch(Dispatchers.IO) {
            lastBypassApprovalMs = torPrefs.loadLastBypassApprovalMs()
        }

        // Any user-initiated change to torType clears the in-memory bypass so the
        // explicit user action wins over the implicit override.
        torPrefs.value.torType
            .drop(1)
            .onEach { sessionBypass.value = false }
            .launchIn(scope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val status =
        combine(
            torPrefs.value.torType,
            torPrefs.value.externalSocksPort,
            sessionBypass,
        ) { torType, externalSocksPort, bypass ->
            Triple(torType, externalSocksPort, bypass)
        }.transformLatest { (torType, externalSocksPort, bypass) ->
            if (bypass) {
                service.stop()
                emit(TorServiceStatus.Off)
                return@transformLatest
            }
            when (torType) {
                TorType.INTERNAL -> {
                    service.start()
                    emitAll(service.status)
                }

                TorType.OFF -> {
                    service.stop()
                    emit(TorServiceStatus.Off)
                }

                TorType.EXTERNAL -> {
                    service.stop()
                    if (externalSocksPort > 0) {
                        emit(TorServiceStatus.Active(externalSocksPort))
                    } else {
                        emit(TorServiceStatus.Off)
                    }
                }
            }
        }.catch { e ->
            Log.e("TorManager") { "Tor service error: ${e.message}" }
            emit(TorServiceStatus.Off)
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(30000),
                TorServiceStatus.Off,
            )

    val activePortOrNull: StateFlow<Int?> =
        status
            .map {
                (it as? TorServiceStatus.Active)?.port
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                (status.value as? TorServiceStatus.Active)?.port,
            )

    /**
     * Emits true after [BOOTSTRAP_TIMEOUT_MS] of continuous [TorServiceStatus.Connecting]
     * (and we are not already bypassing). When the user has approved a bypass within the
     * last [APPROVAL_REMEMBER_MS] this auto-flips [sessionBypass] silently and stays at
     * false; otherwise it emits true so the UI can show the prompt.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionFailure: StateFlow<Boolean> =
        status
            .transformLatest { s ->
                if (s is TorServiceStatus.Connecting) {
                    emit(false)
                    delay(BOOTSTRAP_TIMEOUT_MS)
                    if (rememberedApprovalActive()) {
                        sessionBypass.value = true
                        emit(false)
                    } else {
                        emit(true)
                    }
                } else {
                    emit(false)
                }
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                false,
            )

    fun rememberedApprovalActive(): Boolean {
        val ts = lastBypassApprovalMs
        return ts > 0 && (System.currentTimeMillis() - ts) < APPROVAL_REMEMBER_MS
    }

    /** Called when the user picks "Use regular connection". Starts a fresh 1-hour window. */
    fun approveBypassForOneHour() {
        val now = System.currentTimeMillis()
        lastBypassApprovalMs = now
        sessionBypass.value = true
        scope.launch(Dispatchers.IO) {
            torPrefs.saveLastBypassApprovalMs(now)
        }
    }

    /**
     * Re-attempt Tor on this session — used on network change. Does not clear the
     * remembered-approval window: if Tor stays stuck, we will silently bypass again
     * after the timeout fires.
     */
    fun clearSessionBypass() {
        sessionBypass.value = false
    }

    fun isSocksReady() = status.value is TorServiceStatus.Active

    fun socksPort(): Int = (status.value as? TorServiceStatus.Active)?.port ?: 17392

    companion object {
        const val BOOTSTRAP_TIMEOUT_MS: Long = 60_000L
        const val APPROVAL_REMEMBER_MS: Long = 60L * 60L * 1000L
    }
}
