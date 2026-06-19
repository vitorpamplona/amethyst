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
package com.vitorpamplona.amethyst.desktop

import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore

/**
 * Bundle of optional substitutes for the heavyweight dependencies that
 * `App()` normally constructs inline via `remember { … }`. Production
 * code passes `null` (the default) and `App()` builds the real instances;
 * Compose UI tests pass a non-null `LaunchTestOverrides` so they can hand
 * the composable an in-process fixture relay, a temp-dir local relay
 * store, etc.
 *
 * Keeping this off in production paths means there is no runtime cost in
 * normal use — `App()` performs one null check per field before falling
 * through to its existing `remember { … }` construction.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 1.4.
 */
data class LaunchTestOverrides(
    val localCache: DesktopLocalCache? = null,
    val relayManager: DesktopRelayConnectionManager? = null,
    val localRelayStore: LocalRelayStore? = null,
    /**
     * When `true`, `App()` skips the `relayManager.addDefaultRelays()` +
     * `relayManager.connect()` + `subscriptionsCoordinator.start()` calls
     * normally fired from its startup `DisposableEffect`. Tests that wire
     * their own deterministic fixture relay set this to keep the boot path
     * from racing the production default-relay wiring.
     */
    val skipStartupRelayBootstrap: Boolean = false,
    /**
     * Optional Tor-settings override. Production callers (and most tests)
     * pass `null`, which keeps `App()` loading [TorSettings] from
     * [DesktopTorPreferences] (system-wide `java.util.prefs`). Compose
     * UI tests pass a value whose `torType = OFF` so the Tor splash gate
     * does not block the rest of the composition behind a real kmp-tor
     * runtime that would never come up in headless CI.
     */
    val torSettingsOverride: com.vitorpamplona.amethyst.commons.tor.TorSettings? = null,
)
