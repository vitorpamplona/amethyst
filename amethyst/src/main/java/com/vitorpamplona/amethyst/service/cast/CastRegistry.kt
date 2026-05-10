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
package com.vitorpamplona.amethyst.service.cast

import android.content.Context
import com.vitorpamplona.amethyst.model.CastProtocolType
import com.vitorpamplona.amethyst.service.cast.chromecast.ChromecastCaster
import com.vitorpamplona.amethyst.service.cast.dlna.DlnaCaster
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "CastRegistry"

/**
 * Aggregates every [VideoCaster] implementation available on this build into
 * a single device list and a single active-session view, so the cast UI
 * doesn't care whether a device speaks Chromecast or DLNA.
 *
 * Discovery is started/stopped explicitly by callers (typically the picker
 * dialog's DisposableEffect) so that SSDP multicast traffic + the
 * MulticastLock are only paid for while the user is choosing.
 *
 * The registry honours [protocolFlow]: when the user has narrowed cast to a
 * single protocol, the disabled caster never starts discovery and its devices
 * are filtered out of [devices]. Switching protocols mid-session leaves any
 * active cast running — only future discovery is affected.
 */
class CastRegistry(
    appContext: Context,
    scope: CoroutineScope,
    private val protocolFlow: StateFlow<CastProtocolType> =
        MutableStateFlow(CastProtocolType.BOTH),
) {
    private val casters: List<VideoCaster> =
        listOf(
            ChromecastCaster(appContext),
            DlnaCaster(appContext),
        )

    val devices: StateFlow<List<CastDevice>> =
        combine(
            combine(casters.map { it.devices }) { snapshots -> snapshots.toList() },
            protocolFlow,
        ) { snapshots, proto ->
            val merged =
                snapshots.flatMapIndexed { index, list ->
                    if (isCasterEnabled(casters[index], proto)) list else emptyList()
                }
            Log.d(TAG) {
                "devices flow update proto=$proto " +
                    snapshots.mapIndexed { i, l -> "${casters[i].id}=${l.size}" }.joinToString() +
                    " merged=${merged.size} -> [${merged.joinToString { "${it.casterId}:${it.name}" }}]"
            }
            merged
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val sessionState: StateFlow<CastSessionState> =
        combine(casters.map { it.sessionState }) { states ->
            states.firstOrNull { it !is CastSessionState.Idle } ?: CastSessionState.Idle
        }.stateIn(scope, SharingStarted.Eagerly, CastSessionState.Idle)

    private val refCount = AtomicInteger(0)
    private val active =
        ConcurrentHashMap<String, Boolean>().apply {
            casters.forEach { put(it.id, false) }
        }

    init {
        Log.d(TAG) {
            "init casters=[${casters.joinToString { it.id }}] initialProtocol=${protocolFlow.value}"
        }
        // While the picker is open, react to protocol changes by stopping/starting
        // the affected casters. Idle case is a no-op because reconcile only runs
        // when refCount > 0.
        scope.launch {
            protocolFlow.collect { proto ->
                Log.d(TAG) { "protocolFlow change -> $proto refCount=${refCount.get()}" }
                if (refCount.get() > 0) reconcile()
            }
        }
    }

    private fun isCasterEnabled(
        caster: VideoCaster,
        proto: CastProtocolType,
    ): Boolean =
        when (proto) {
            CastProtocolType.BOTH -> true
            CastProtocolType.CHROMECAST -> caster.id == "chromecast"
            CastProtocolType.DLNA -> caster.id == "dlna"
        }

    @Synchronized
    private fun reconcile() {
        val proto = protocolFlow.value
        casters.forEach { caster ->
            val shouldRun = isCasterEnabled(caster, proto)
            val running = active[caster.id] == true
            if (shouldRun && !running) {
                Log.d(TAG) { "reconcile START ${caster.id} (proto=$proto)" }
                runCatching { caster.startDiscovery() }
                    .onFailure { Log.w(TAG, "reconcile START ${caster.id} threw: ${it.message}", it) }
                active[caster.id] = true
            } else if (!shouldRun && running) {
                Log.d(TAG) { "reconcile STOP ${caster.id} (proto=$proto)" }
                runCatching { caster.stopDiscovery() }
                    .onFailure { Log.w(TAG, "reconcile STOP ${caster.id} threw: ${it.message}", it) }
                active[caster.id] = false
            }
        }
    }

    @Synchronized
    private fun stopAll() {
        Log.d(TAG) { "stopAll active=${active.filterValues { it }.keys}" }
        casters.forEach { caster ->
            if (active[caster.id] == true) {
                runCatching { caster.stopDiscovery() }
                    .onFailure { Log.w(TAG, "stopAll ${caster.id} threw: ${it.message}", it) }
                active[caster.id] = false
            }
        }
    }

    fun startDiscovery() {
        val before = refCount.getAndIncrement()
        Log.d(TAG) { "startDiscovery refCount $before -> ${before + 1}" }
        if (before == 0) reconcile()
    }

    fun stopDiscovery() {
        val after = refCount.decrementAndGet()
        Log.d(TAG) { "stopDiscovery refCount -> $after" }
        if (after <= 0) {
            refCount.set(0)
            stopAll()
        }
    }

    suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    ) {
        Log.d(TAG) {
            "cast device=${device.casterId}:${device.name} url=${request.url} mime=${request.mimeType}"
        }
        val caster = casters.firstOrNull { it.id == device.casterId }
        if (caster == null) {
            Log.w(TAG, "cast: no caster found for casterId=${device.casterId}")
            return
        }
        caster.cast(device, request)
    }

    suspend fun stopCasting() {
        Log.d(TAG) { "stopCasting (broadcast to all casters)" }
        casters.forEach { runCatching { it.stopCasting() } }
    }
}
