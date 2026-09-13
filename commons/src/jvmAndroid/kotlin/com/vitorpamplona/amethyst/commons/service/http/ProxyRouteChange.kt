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
package com.vitorpamplona.amethyst.commons.service.http

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Runs [onChange] whenever the Tor SOCKS port this app routes through actually changes — Tor
 * coming up (`null` -> 9050), going away (9050 -> `null`), or moving (9050 -> 9150).
 *
 * The managers use this to drop pooled connections once per real route change. Reuse is *not*
 * the reason: OkHttp keys its pool by `Address`, which includes the proxy, so a connection on
 * the old route is already unreachable by calls on the new one and would age out on its own.
 * The reason is hygiene at the moment the user's intent changes — when Tor is switched on, idle
 * sockets the direct client opened to real hosts should not linger for the pool's 5-minute
 * keepalive after the user has asked for everything to go through Tor.
 *
 * Deliberately driven by the port and nothing else:
 *
 *  - **Not by client rebuilds.** That is what the old `lastProxy`-per-factory check did, and
 *    since one factory mints both the proxied and the direct client, the two alternated that
 *    field forever and wiped the shared pool on every network-state emission and resubscribe.
 *  - **Not by the per-feature Tor toggles** (`imagesViaTor`, `videosViaTor`, …). Those change
 *    which of the two existing clients a request picks, not the route either one uses, so no
 *    pooled connection becomes stale.
 *
 * [drop] skips the current value, so subscribing does not itself count as a change — only a
 * later move away from the port in force when this was wired.
 */
fun CoroutineScope.evictOnProxyRouteChange(
    proxyPortProvider: StateFlow<Int?>,
    onChange: () -> Unit,
): Job =
    launch {
        proxyPortProvider
            .drop(1)
            .distinctUntilChanged()
            .collect { onChange() }
    }
