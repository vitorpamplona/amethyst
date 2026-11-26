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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The slice of [TorService] that [TorManager] drives. Extracted so the manager
 * can be unit-tested without booting Arti — production wires a [TorService] over
 * the platform's [ArtiBridge] (JNI on Android, the C ABI on iOS), tests wire an
 * in-memory fake.
 */
interface TorBackend {
    val status: StateFlow<TorServiceStatus>

    /**
     * True while a native bootstrap attempt is actually running.
     *
     * [TorServiceStatus.Connecting] conflates two states that need opposite responses: a bootstrap
     * that is working through a cold consensus download (leave it alone) and a lifecycle that has
     * stopped trying (reset it). The stuck-Connecting watchdog cannot tell them apart from status
     * alone, and a fresh install spends its first minute in the first one — so the watchdog used to
     * queue a reset behind the in-flight attempt's lifecycle lock and tear the client down the
     * moment it succeeded. This flag is the missing half of the signal.
     */
    val bootstrapInFlight: StateFlow<Boolean>

    /**
     * Directory-download progress in permille while [TorServiceStatus.Bootstrapping]; -1 when there
     * is no client or nothing is being downloaded.
     *
     * Emits only on change (it is a [StateFlow]), which is exactly the signal the stall detector
     * needs: the timestamp of the last distinct value is the last time Tor made forward progress.
     */
    val bootstrapProgress: StateFlow<Int>

    suspend fun start()

    suspend fun stop()

    suspend fun reset()

    suspend fun resetWithCleanState()

    /**
     * True when on-disk state proves Tor bootstrapped successfully on this
     * install before. Lets [TorManager] seed `hasEverBootstrapped` across process
     * restarts so the stuck-Connecting watchdog wipes stale/poisoned state rather
     * than waiting it out as a first bootstrap. Implementations do file IO, so
     * this is a `suspend` call.
     */
    suspend fun hasBootstrappedBefore(): Boolean

    /**
     * Emits when Arti has reported `AllGuardsDown` persistently enough that the guard sample is
     * considered rotten at runtime, regardless of what `guards.json` claims.
     *
     * The on-disk heuristic ([ArtiGuardState.hasNoUsableGuards]) only sees guards Arti has
     * permanently retired. A guard that is merely unreachable stays "usable" on disk forever, so a
     * sample can be entirely dead in practice while still looking healthy to that check — which is
     * exactly the state that survived repeated restarts in the field. This is the runtime half:
     * Arti itself says every guard was rejected, so believe it.
     */
    val guardsDownSignal: Flow<Unit>

    /**
     * Makes sure the SOCKS listener the status reports is still accepting, and binds it again if it
     * is not. For a platform that suspends the app's process: iOS may reclaim a suspended app's
     * listening socket, and Arti's accept loop then ends with nothing to restart it, so the status
     * keeps reporting a port that refuses every connection (`TorManager.onReturnToForeground`).
     *
     * Defaults to [ListenerRecovery.NOT_CHECKED]: a backend that cannot probe its listener (Android,
     * whose process is never suspended with the listener reclaimed) has nothing to recover.
     */
    suspend fun recoverListener(): ListenerRecovery = ListenerRecovery.NOT_CHECKED
}

/** What [TorBackend.recoverListener] found. */
enum class ListenerRecovery {
    /** The backend cannot tell (no probe), so nothing was done. */
    NOT_CHECKED,

    /** No listener is supposed to be up (Tor off, or not started yet): `start()` will bind one. */
    NOT_RUNNING,

    /** The listener answered: nothing to do. */
    HEALTHY,

    /** The listener was gone and is bound again. Clients pooled on the old one should be rebuilt. */
    RESTARTED,

    /** The listener was gone and could not be bound again, or no client is left behind it. Reset Tor. */
    FAILED,
}
