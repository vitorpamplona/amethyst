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

import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * There should be only one instance of the Tor binding per app.
 *
 * Tor will connect as soon as status is listened to.
 *
 * [service] and [torPrefs] are constructor-injected so the manager can be unit-tested
 * with in-memory fakes — see `TorManagerTest`. [ioDispatcher] is the dispatcher for
 * background I/O (DataStore reads/writes, [TorBackend] calls); tests pass a
 * `TestDispatcher` so virtual time controls scheduling.
 */
class TorManager(
    private val torPrefs: TorPreferencesPort,
    val service: TorBackend,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
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

    /**
     * Bumped by self-heal paths ([onNetworkChange], stuck-Connecting watcher) so the
     * [status] combine re-fires and re-enters the [TorType.INTERNAL] branch — which
     * calls [TorService.start] again and, because [TorService.reset] flipped
     * `initialized` back to false, runs full Arti re-initialization with a fresh
     * bootstrap, new guards, new circuits.
     */
    private val resetEpoch = MutableStateFlow(0)

    /** Wall-clock of the last automatic self-heal — rate-limits the stuck-Connecting reset. */
    @Volatile private var lastSelfHealAtMs: Long = 0L

    /**
     * Flipped the first time [status] reaches [TorServiceStatus.Active] in this process. Before
     * that, the stuck-Connecting watchdog uses the gentler [TorService.reset] (drop client only)
     * rather than [TorService.resetWithCleanState] — because on a slow legitimate first
     * bootstrap there is no stale state to wipe, and wiping just forces an unnecessary
     * re-bootstrap cycle. Once we've seen Tor work once, persisted `arti/state/` is fair game
     * for the recovery to wipe.
     */
    @Volatile private var hasEverBootstrapped: Boolean = false

    init {
        scope.launch(ioDispatcher) {
            lastBypassApprovalMs = torPrefs.loadLastBypassApprovalMs()
        }

        // Any user-initiated change to torType clears the in-memory bypass AND the
        // remembered-approval window. Otherwise a single past "Use regular connection"
        // traps the user in a silent-bypass loop: every Connecting span >60s
        // auto-flips sessionBypass without showing the dialog, force-stop preserves
        // the DataStore-backed approval, and toggling Tor off/on only clears the
        // in-memory half — so wiping app data becomes the only recovery path.
        torPrefs.torType
            .drop(1)
            .onEach {
                sessionBypass.value = false
                lastBypassApprovalMs = 0L
                torPrefs.saveLastBypassApprovalMs(0L)
            }.launchIn(scope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val status =
        combine(
            torPrefs.torType,
            torPrefs.externalSocksPort,
            sessionBypass,
            resetEpoch,
        ) { torType, externalSocksPort, bypass, _ ->
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
        }.flowOn(ioDispatcher)
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

    /**
     * Fires once after [SELF_HEAL_AFTER_MS] of continuous [TorServiceStatus.Connecting].
     * Drives the watchdog wired up below. `transformLatest` cancels the pending delay
     * whenever the status changes, so a brief Connecting blip never fires.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val selfHealSignal =
        status.transformLatest { s ->
            if (s is TorServiceStatus.Connecting) {
                delay(SELF_HEAL_AFTER_MS)
                emit(Unit)
            }
        }

    init {
        // Self-heal watchdog. When status sits at Connecting for longer than
        // SELF_HEAL_AFTER_MS, the in-memory Arti state is likely stuck — bad guards,
        // broken circuits, expired consensus. Drop the TorClient and bump resetEpoch
        // so the status combine re-fires and re-enters the INTERNAL branch, which
        // runs full Arti re-init. Rate-limited so a permanently broken network
        // doesn't loop us. Fires BEFORE the 60s connectionFailure dialog so most
        // users never see it.
        //
        // Pre-first-bootstrap: gentle reset (drop client, keep state). On a slow
        // legitimate first bootstrap there's nothing on disk worth wiping, and
        // wiping just costs another full bootstrap cycle.
        // Post-first-bootstrap: full reset (drop client + wipe state). Once we've
        // seen Tor work once, a stuck Connecting almost certainly means stale on-disk
        // state from a different network needs to go.
        status
            .onEach {
                if (it is TorServiceStatus.Active) hasEverBootstrapped = true
            }.launchIn(scope)

        selfHealSignal
            .onEach {
                val now = nowMs()
                if (now - lastSelfHealAtMs < SELF_HEAL_COOLDOWN_MS) return@onEach
                lastSelfHealAtMs = now
                if (hasEverBootstrapped) {
                    Log.w("TorManager") { "Tor stuck Connecting >${SELF_HEAL_AFTER_MS}ms — self-healing (drop client + wipe state)" }
                    service.resetWithCleanState()
                } else {
                    Log.w("TorManager") { "Tor stuck Connecting >${SELF_HEAL_AFTER_MS}ms on first bootstrap — self-healing (drop client only)" }
                    service.reset()
                }
                resetEpoch.update { it + 1 }
            }.launchIn(scope)
    }

    fun rememberedApprovalActive(): Boolean {
        val ts = lastBypassApprovalMs
        return ts > 0 && (nowMs() - ts) < APPROVAL_REMEMBER_MS
    }

    /** Called when the user picks "Use regular connection". Starts a fresh 1-hour window. */
    fun approveBypassForOneHour() {
        val now = nowMs()
        lastBypassApprovalMs = now
        sessionBypass.value = true
        scope.launch(ioDispatcher) {
            torPrefs.saveLastBypassApprovalMs(now)
        }
    }

    /**
     * Network identity changed (wifi↔cellular, captive portal cleared, regained from
     * offline). The old network's guards and circuits are dead, but Arti's in-memory
     * TorClient doesn't always notice — and even if it does, on-disk `state/` can hold
     * unreachable guards that the next process load will pick up again. Drop the
     * client, clear `sessionBypass`, clear the persisted approval, and bump
     * [resetEpoch] so the status flow re-enters the INTERNAL branch with
     * `initialized=false` — forcing a full Arti re-init with fresh bootstrap.
     */
    fun onNetworkChange() {
        sessionBypass.value = false
        lastBypassApprovalMs = 0L
        // Prevent the stuck-Connecting watchdog from firing a second reset while the
        // network-change bootstrap is still legitimately in progress (initial bootstrap
        // on a new network can take ~10–30s, sometimes longer).
        lastSelfHealAtMs = nowMs()
        scope.launch(ioDispatcher) {
            torPrefs.saveLastBypassApprovalMs(0L)
            service.reset()
            resetEpoch.update { it + 1 }
        }
    }

    fun isSocksReady() = status.value is TorServiceStatus.Active

    fun socksPort(): Int = (status.value as? TorServiceStatus.Active)?.port ?: 17392

    companion object {
        const val BOOTSTRAP_TIMEOUT_MS: Long = 60_000L
        const val APPROVAL_REMEMBER_MS: Long = 60L * 60L * 1000L

        /** Self-heal kicks in BEFORE the 60s [BOOTSTRAP_TIMEOUT_MS] dialog so most users never see it. */
        const val SELF_HEAL_AFTER_MS: Long = 45_000L
        const val SELF_HEAL_COOLDOWN_MS: Long = 5L * 60L * 1000L
    }
}
