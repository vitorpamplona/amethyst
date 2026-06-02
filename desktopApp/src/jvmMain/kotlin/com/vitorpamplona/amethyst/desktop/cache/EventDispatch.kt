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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Canonical local-first dispatch for user-action events on desktop: write to
 * the local cache before broadcasting so the UI reflects the action immediately,
 * even if relay round-trips fail.
 *
 * Replaces five inlined `consume + broadcastToAll` couplets that had drifted
 * in ordering (reactions/follows did broadcast-then-consume, replies did
 * consume-then-broadcast). Use this everywhere a signed event must be both
 * persisted locally and pushed to outbox relays.
 */
fun dispatch(
    signed: Event,
    localCache: DesktopLocalCache,
    relayManager: RelayConnectionManager,
) {
    localCache.consume(signed, relay = null)
    relayManager.broadcastToAll(signed)
}
