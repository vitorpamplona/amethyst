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
package com.vitorpamplona.amethyst.commons.mavenReleases

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule
import com.vitorpamplona.quartz.nipFCMavenRepositories.release.MavenReleaseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The newest release of a library by one signer, kept live from the cache for the NIP-FC cards that
 * show it.
 *
 * Releases are a regular kind, so starting a cache observer walks every note in memory. One flow per
 * library (signer and module) is shared by every card of it on screen, so a profile listing twenty
 * releases of one library walks the cache once, not twenty times, and the walk stops five seconds
 * after the last card leaves.
 */
@OptIn(ExperimentalAtomicApi::class)
object MavenLibraryVersions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val flows = AtomicReference(emptyMap<String, StateFlow<MavenReleaseEvent?>>())

    fun newest(
        cache: EventCache,
        signer: HexKey,
        module: MavenModule,
    ): StateFlow<MavenReleaseEvent?> {
        val key = signer + ":" + module.key
        while (true) {
            val current = flows.load()
            current[key]?.let { return it }
            val flow =
                cache
                    .observeNotes(MavenReleaseEvent.filter(listOf(signer), module))
                    .map { notes -> newestRelease(notes.mapNotNull { it.event as? MavenReleaseEvent }, module) }
                    .distinctUntilChanged()
                    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
            if (flows.compareAndSet(current, current + (key to flow))) return flow
        }
    }

    /**
     * The newest version among [releases] of [module], as its canonical release: NIP-FC's earliest
     * event for that version (`created_at`, then the lowest id). Malformed events and ones that
     * don't cover [module] are skipped. The caller picks the signer: pass only events by the key
     * it trusts.
     */
    fun newestRelease(
        releases: List<MavenReleaseEvent>,
        module: MavenModule,
    ): MavenReleaseEvent? {
        var best: MavenReleaseEvent? = null
        var bestVersion: String? = null
        for (event in releases) {
            val version = event.version() ?: continue
            if (!event.covers(module, version) || !event.isWellFormed()) continue
            val c = bestVersion?.let { MavenVersion.compare(version, it) } ?: 1
            if (c > 0 || (c == 0 && best != null && isEarlier(event, best))) {
                best = event
                bestVersion = version
            }
        }
        return best
    }

    private fun isEarlier(
        a: MavenReleaseEvent,
        b: MavenReleaseEvent,
    ) = a.createdAt < b.createdAt || (a.createdAt == b.createdAt && a.id < b.id)
}
