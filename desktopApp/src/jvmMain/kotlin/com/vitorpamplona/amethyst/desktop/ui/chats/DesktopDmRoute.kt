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
package com.vitorpamplona.amethyst.desktop.ui.chats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-process bridge for "open a DM with this pubkey" requests coming from
 * anywhere in the app (e.g. the profile screen's Message button).
 *
 * The caller [request]s a pubkey and opens the Messages column; the Messages
 * screen observes [pendingTarget], selects the 1:1 room, then [consume]s it.
 * A StateFlow replays the latest value to a late subscriber, so the Messages
 * screen still picks it up if it composes after the request.
 *
 * Desktop is single-process, so a shared object (like GlobalMediaPlayer) is the
 * idiomatic bridge here — no cross-process/IPC concern.
 */
object DesktopDmRoute {
    private val pendingTargetInternal = MutableStateFlow<String?>(null)
    val pendingTarget: StateFlow<String?> = pendingTargetInternal.asStateFlow()

    /** Ask the Messages screen to open a 1:1 room with [pubKeyHex]. */
    fun request(pubKeyHex: String) {
        pendingTargetInternal.value = pubKeyHex
    }

    /** Called by the Messages screen once it has selected the room. */
    fun consume() {
        pendingTargetInternal.value = null
    }
}
