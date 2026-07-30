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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Arti's log marker for "no usable guard could be found". */
private const val ALL_GUARDS_DOWN_MARKER = "AllGuardsDown"

/** How many [ALL_GUARDS_DOWN_MARKER] lines inside [GUARDS_DOWN_WINDOW_MS] mean the sample is rotten. */
private const val GUARDS_DOWN_THRESHOLD = 40

/** Window for [GUARDS_DOWN_THRESHOLD]. Wide enough that ordinary transient churn never trips it. */
private const val GUARDS_DOWN_WINDOW_MS = 60_000L

private const val DEFAULT_SOCKS_PORT = 17392
private const val MAX_PORT_RETRIES = 10

/**
 * Return code from [ArtiNative.initialize] when the native bootstrap exceeds its
 * internal timeout. The native side has already torn down the half-built client;
 * we treat this differently from a hard failure (see [TorService.start]).
 */
private const val ARTI_ERROR_BOOTSTRAP_TIMEOUT = -4

/**
 * Manages the Arti Tor client via custom JNI bindings.
 *
 * The native TorClient is initialized once and persists for the app's
 * lifetime — its state file lock is never released until the process exits.
 * The SOCKS proxy can be started/stopped independently without affecting
 * the TorClient or its file locks.
 *
 * All JNI calls (including System.loadLibrary) run on [Dispatchers.IO]
 * to avoid blocking the main thread.
 */
class TorService(
    val context: Context,
) : TorBackend {
    private var socksPort = DEFAULT_SOCKS_PORT
    private val initialized = AtomicBoolean(false)
    private val proxyRunning = AtomicBoolean(false)

    /**
     * Wall-clock at which the current `initialize()` started, so the bootstrap log can
     * report the elapsed time to "Sufficiently bootstrapped". -1 when no init is in flight.
     * Diagnostic only.
     */
    @Volatile private var bootstrapStartedAtMs: Long = -1L

    /**
     * Serializes every native lifecycle transition ([start], [stop], [reset],
     * [resetWithCleanState]). [ArtiNative] is a process-global singleton over a
     * single native Arti client, and `initialize`/`destroy` are blocking JNI
     * calls that ignore coroutine cancellation. Without this lock a self-heal
     * `reset()` (or `onNetworkChange()`) can call `destroy()` while a `start()`
     * is mid-`initialize()`, tearing down the client ~0.5s after it bootstraps
     * and leaving the SOCKS listener up with no live client behind it — every
     * Tor dial then times out at the exit. The lock makes a reset wait for an
     * in-flight bootstrap to finish before tearing it down cleanly.
     */
    private val lifecycleMutex = Mutex()

    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    /**
     * Every status change goes through here so the transition is logged exactly once, at INFO, with
     * the time since bootstrap started.
     *
     * Tor state is the single biggest determinant of whether a boot works — a stuck `Connecting`,
     * an `Active` that took 40s, and a silent drop back to `Off` are three completely different
     * bugs that used to look identical in the log, because the per-status detail lived only in
     * Arti's own DEBUG chatter. Self-transitions are not logged: the reset paths reassign `Off`
     * defensively and repeating it adds nothing.
     */
    private fun setStatus(next: TorServiceStatus) {
        val prev = _status.value
        _status.value = next
        if (prev::class == next::class && prev.toString() == next.toString()) return
        val since = if (bootstrapStartedAtMs > 0) " after ${System.currentTimeMillis() - bootstrapStartedAtMs}ms" else ""
        Log.i("TorService") { "${prev::class.simpleName} -> ${next::class.simpleName}$since" }
    }

    /**
     * Runtime detector for a rotten guard sample. Arti logs [ALL_GUARDS_DOWN_MARKER] every time it
     * fails to find a usable guard; [GUARDS_DOWN_THRESHOLD] of those inside [GUARDS_DOWN_WINDOW_MS]
     * means every guard in the sample is unreachable *right now* — which the on-disk check cannot
     * see, because an unreachable guard is not a `disabled` one (see
     * [ArtiGuardState.hasNoUsableGuards]).
     *
     * Counted here because the log callback is the only place Arti surfaces it; acted on by
     * [TorManager], which owns the reset cadence and its rate limiting. Both counters are only
     * touched from Arti's log thread.
     */
    private var guardsDownCount = 0

    private var guardsDownWindowStartMs = 0L

    private val _guardsDownSignal =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val guardsDownSignal: Flow<Unit> = _guardsDownSignal.asSharedFlow()

    /**
     * Feeds one Arti log line to the [guardsDownSignal] detector. Cheap by design: this runs for
     * every log line, and a wedged Arti emits tens of thousands of them.
     */
    private fun trackGuardFailures(text: String) {
        if (!text.contains(ALL_GUARDS_DOWN_MARKER)) return

        val now = System.currentTimeMillis()
        if (now - guardsDownWindowStartMs > GUARDS_DOWN_WINDOW_MS) {
            guardsDownWindowStartMs = now
            guardsDownCount = 0
        }
        guardsDownCount++
        if (guardsDownCount >= GUARDS_DOWN_THRESHOLD) {
            guardsDownCount = 0
            guardsDownWindowStartMs = now
            Log.w("TorService") { "Arti reported $ALL_GUARDS_DOWN_MARKER $GUARDS_DOWN_THRESHOLD times in ${GUARDS_DOWN_WINDOW_MS}ms — guard sample is rotten at runtime" }
            _guardsDownSignal.tryEmit(Unit)
        }
    }

    private fun artiDataDir() = File(context.filesDir, "arti")

    /** Diagnostic: total bytes of the consensus/descriptor cache, to correlate with bootstrap time. */
    private fun cacheSizeBytes(): Long {
        val cacheDir = File(artiDataDir(), "cache")
        if (!cacheDir.exists()) return 0
        return cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * The on-disk guard sample Arti persists between runs.
     * Path: `<filesDir>/arti/state/state/guards.json`.
     */
    private fun guardsFile() = File(File(File(artiDataDir(), "state"), "state"), "guards.json")

    /** Reads and parses [guardsFile], or null if it is absent/unreadable. */
    private fun readGuardsTree(): JsonNode? {
        val file = guardsFile()
        if (!file.exists()) return null
        return try {
            jacksonObjectMapper().readTree(file)
        } catch (e: Exception) {
            Log.w("TorService") { "Could not inspect guards.json: ${e.message}" }
            null
        }
    }

    /**
     * Detects the wedged-guard-sample state behind the long-standing "can't
     * connect to Tor" bug (see [ArtiGuardState.hasNoUsableGuards]).
     *
     * On a flaky network, Arti records circuit failures past the first hop as
     * "indeterminate" (it can't tell whether the guard or a later hop was at
     * fault). Once a guard's indeterminate ratio crosses 0.7, Arti
     * *permanently* disables it (`TooManyIndeterminateFailures`). Disabled
     * guards are never re-enabled and never removed from the sample (kept for
     * the 60-day confirmed lifetime), and the sample is capped at
     * `max_sample_size` (60). Arti normally refills usable guards from the
     * network when they drop below `min_filtered_sample_size` (20), but once
     * the sample is full of unusable guards there is no room to add more — so
     * replenishment is permanently wedged and every circuit returns
     * `AllGuardsDown`. The state persists in `guards.json`, and bootstrap still
     * "succeeds" (it reads cached directory data), so none of the init-failure
     * self-heal paths ever fire and Tor is stuck across restarts.
     */
    private fun noUsableGuards(): Boolean = readGuardsTree()?.let { ArtiGuardState.hasNoUsableGuards(it) } ?: false

    /**
     * True when the persisted guard sample proves Tor bootstrapped successfully
     * on this install before (a confirmed guard on disk; see
     * [ArtiGuardState.hasConfirmedGuard]). [TorManager] seeds its in-memory
     * `hasEverBootstrapped` from this so a stuck bootstrap on a fresh process
     * wipes stale/poisoned state instead of nursing it as a first bootstrap.
     */
    override suspend fun hasBootstrappedBefore(): Boolean =
        withContext(Dispatchers.IO) {
            readGuardsTree()?.let { ArtiGuardState.hasConfirmedGuard(it) } ?: false
        }

    /**
     * Clears all Arti persistent data (state + cache). Used as a last resort
     * when initialization fails, to recover from corrupted state.
     */
    private fun clearAllArtiData() {
        val dataDir = artiDataDir()
        if (dataDir.exists()) {
            dataDir.deleteRecursively()
            Log.d("TorService") { "Cleared all Arti data" }
        }
    }

    /**
     * Initialize the TorClient (once) and start the SOCKS proxy.
     * Must be called from a coroutine on [Dispatchers.IO].
     */
    override suspend fun start() =
        lifecycleMutex.withLock {
            if (proxyRunning.get()) {
                if (_status.value is TorServiceStatus.Active) return@withLock
                setStatus(TorServiceStatus.Connecting)
                return@withLock
            }

            setStatus(TorServiceStatus.Connecting)

            withContext(Dispatchers.IO) {
                // Initialize TorClient once — this bootstraps the Tor network.
                // setLogCallback and initialize are the first ArtiNative calls,
                // which triggers System.loadLibrary on this IO thread.
                if (initialized.compareAndSet(false, true)) {
                    // Forward Arti's internal logs. The Active transition is NOT driven
                    // from here: the "Sufficiently bootstrapped" line is emitted from a
                    // tokio task that races startSocksProxy returning, so honoring it here
                    // (gated on proxyRunning) intermittently dropped the transition. start()
                    // now sets Active deterministically once the proxy is bound.
                    ArtiNative.setLogCallback { text ->
                        trackGuardFailures(text)
                        Log.d("TorService") {
                            val newLine = text.indexOf('\n')
                            if (newLine > 1) {
                                "Arti: ${text.substring(0, newLine)}"
                            } else {
                                "Arti: $text"
                            }
                        }
                    }

                    // Preserve the consensus/descriptor cache across cold starts. We used to
                    // wipe it on every launch, which forced a full microdescriptor consensus
                    // re-download and turned a ~7s warm bootstrap into ~24s. That wipe was an
                    // early attempt at what turned out to be the wedged-guard problem (now
                    // handled by [noUsableGuards]); it never actually helped, since guards live
                    // in state/ — not cache/ — and Arti already validates consensus freshness
                    // and refetches whatever has expired. The reset/clean-state paths still call
                    // clearAllArtiData() for genuine corruption recovery.
                    Log.d("TorService") { "Preserving Arti cache for warm bootstrap (cache size: ${cacheSizeBytes()} bytes)" }

                    // Self-heal the wedged guard sample (see [noUsableGuards]): if
                    // the persisted sample has no usable guard left, Arti can
                    // neither build circuits nor replenish, and would return
                    // AllGuardsDown forever. Wipe the on-disk state so the next
                    // bootstrap rebuilds a fresh guard sample.
                    if (noUsableGuards()) {
                        Log.w("TorService") { "No usable Arti guards left on disk — wiping state to rebuild the guard sample" }
                        clearAllArtiData()
                    }

                    val dataDir = artiDataDir().absolutePath
                    Log.d("TorService") { "Initializing Arti with data dir: $dataDir" }

                    bootstrapStartedAtMs = System.currentTimeMillis()
                    var initResult = ArtiNative.initialize(dataDir)

                    if (initResult == ARTI_ERROR_BOOTSTRAP_TIMEOUT) {
                        // The native bootstrap hit its timeout (hostile network) and
                        // already tore down the half-built client. Don't wipe state or
                        // retry inline — that would hold lifecycleMutex for another full
                        // timeout. Drop the init flag and leave status at Connecting so
                        // TorManager's self-heal watchdog resets and retries on its own
                        // cadence (and the connection-failure dialog can still surface).
                        Log.w("TorService") { "Arti bootstrap timed out — leaving Connecting for the self-heal watchdog to retry" }
                        initialized.set(false)
                        return@withContext
                    }

                    if (initResult != 0) {
                        Log.e("TorService") { "Failed to initialize Arti: error $initResult, clearing data and retrying" }
                        clearAllArtiData()
                        initResult = ArtiNative.initialize(dataDir)
                    }
                    if (initResult != 0) {
                        Log.e("TorService") { "Failed to initialize Arti on retry: error $initResult" }
                        initialized.set(false)
                        setStatus(TorServiceStatus.Off)
                        return@withContext
                    }
                }

                // Start the SOCKS proxy, retrying on next port if address is in use
                var port = socksPort
                var started = false
                for (attempt in 0 until MAX_PORT_RETRIES) {
                    val proxyResult = ArtiNative.startSocksProxy(port)
                    if (proxyResult == 0) {
                        socksPort = port
                        started = true
                        break
                    }
                    Log.w("TorService") { "Port $port in use, trying ${port + 1}" }
                    port++
                }

                if (!started) {
                    Log.e("TorService") { "Failed to start SOCKS proxy after $MAX_PORT_RETRIES attempts" }
                    setStatus(TorServiceStatus.Off)
                    return@withContext
                }

                proxyRunning.set(true)

                // Transition to Active deterministically here rather than relying on
                // the "Sufficiently bootstrapped" log callback. That callback is emitted
                // from a tokio task spawned inside the native startSocksProxy (see
                // arti-android-wrapper lib.rs), which races the native call returning and
                // this `proxyRunning.set(true)` above. When the task wins the race, the
                // callback's `proxyRunning.get()` guard is still false, the Active
                // transition is dropped, and status stays Connecting until the 60s
                // connection-failure dialog fires. The client is bootstrapped (initialize
                // returned 0) and the SOCKS proxy is bound, so this is the correct point
                // to declare Active — and we hold lifecycleMutex, so a concurrent
                // reset/stop can't clobber it.
                val startedAt = bootstrapStartedAtMs
                val elapsed = if (startedAt > 0) System.currentTimeMillis() - startedAt else -1
                setStatus(TorServiceStatus.Active(socksPort))
                Log.d("TorService") { "Arti SOCKS proxy active on port $socksPort (bootstrap took ${elapsed}ms)" }
            }
        }

    /**
     * Stop the SOCKS proxy and release the port.
     * The TorClient stays alive — no file lock issues on restart.
     */
    override suspend fun stop() {
        lifecycleMutex.withLock {
            if (!proxyRunning.compareAndSet(true, false)) return@withLock

            withContext(Dispatchers.IO) {
                ArtiNative.stopSocksProxy()
                Log.d("TorService") { "SOCKS proxy stopped" }
            }

            setStatus(TorServiceStatus.Off)
        }
    }

    /**
     * Drop the native TorClient so the next [start] runs full initialization
     * with a fresh bootstrap, new guards, and new circuits. Used by self-heal
     * paths in [TorManager] — network identity change, stuck-Connecting
     * recovery — when the in-memory Arti state is suspected of being broken.
     * The `arti/state/` directory on disk is preserved.
     */
    override suspend fun reset() {
        lifecycleMutex.withLock {
            resetLocked()
        }
        setStatus(TorServiceStatus.Off)
    }

    /**
     * Like [reset] but additionally wipes `arti/state/` so the next
     * initialization rebuilds guard selection from scratch. Used when stale
     * on-disk state (e.g. unreachable guards persisted from a previous
     * network) is the suspected cause of a bootstrap that never completes.
     */
    override suspend fun resetWithCleanState() {
        lifecycleMutex.withLock {
            resetLocked()
            withContext(Dispatchers.IO) {
                clearAllArtiData()
            }
        }
        setStatus(TorServiceStatus.Off)
    }

    /**
     * Tears down the native client. Assumes [lifecycleMutex] is already held so
     * the `destroy()` can never overlap a `start()`'s `initialize()`. Both
     * [reset] and [resetWithCleanState] funnel through here.
     */
    private suspend fun resetLocked() =
        withContext(Dispatchers.IO) {
            if (proxyRunning.compareAndSet(true, false)) {
                ArtiNative.stopSocksProxy()
            }
            ArtiNative.destroy()
            initialized.set(false)
            Log.d("TorService") { "Tor service reset — next start will re-initialize" }
        }
}
