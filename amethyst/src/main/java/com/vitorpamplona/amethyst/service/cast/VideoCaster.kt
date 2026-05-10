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

import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for any LAN cast protocol that Amethyst can drive — currently
 * Chromecast (Google Cast SDK, play flavor only) and DLNA / UPnP AVTransport
 * (jUPnP, both flavors). The registry layer aggregates one or more of these
 * to give the UI a unified device list and active-session state.
 */
interface VideoCaster {
    /** Stable identifier for diagnostics and merging device IDs across casters. */
    val id: String

    /** Discovered receiver devices; emits a snapshot whenever the set changes. */
    val devices: StateFlow<List<CastDevice>>

    /** Status of the active casting session owned by this caster. */
    val sessionState: StateFlow<CastSessionState>

    /**
     * Called by the registry while at least one observer wants device updates
     * (typically: the picker dialog is open, or a session is active).
     * Implementations should be idempotent.
     */
    fun startDiscovery()

    /** Counterpart to [startDiscovery]. Idempotent; no-op when not running. */
    fun stopDiscovery()

    /**
     * Hand a video off to [device] and start playback. Updates [sessionState]
     * to Connecting → Casting (or Error). [device] must be one this caster
     * emitted in [devices] — the registry routes by [CastDevice.casterId].
     */
    suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    )

    /** End the active session if any. Idempotent. */
    suspend fun stopCasting()
}
