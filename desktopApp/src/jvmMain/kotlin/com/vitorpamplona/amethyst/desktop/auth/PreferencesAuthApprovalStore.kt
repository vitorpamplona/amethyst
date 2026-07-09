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
package com.vitorpamplona.amethyst.desktop.auth

import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalScope
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalStore
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.prefs.Preferences

/**
 * Desktop persistence backend for [AuthApprovalStore] using
 * `java.util.prefs.Preferences`.
 *
 * Trade-offs vs the full SQLite `auth_approvals` table proposed in the plan:
 *
 * - **Pro**: zero new dependencies, no schema migration, already proven for
 *   other small desktop settings (per memory: `SearchHistoryStore`,
 *   `DesktopPreferences`).
 * - **Con**: flat key/value, no transactions, no native TTL. Acceptable here
 *   because the approval set per account is small (≪50 relays for any user)
 *   and the read pattern is "look up before signing AUTH" — once per relay
 *   per session, easily cached in memory by the [AuthApprovalPolicy] layer.
 *
 * Per-account scoping is by Preferences node: each account gets its own node
 * at `/com/vitorpamplona/amethyst/desktop/auth/<full-pubkey>/`. Logout calls
 * [clear] which `removeNode()`s the per-account subtree.
 *
 * `ONCE` scope is never persisted — that's the in-memory contract enforced
 * by the [AuthApprovalStore] interface. This implementation only writes
 * `ALWAYS` and `BLOCKED`.
 */
class PreferencesAuthApprovalStore(
    private val accountPubKeyHex: String,
) : AuthApprovalStore {
    private val node: Preferences =
        Preferences.userRoot().node(
            "/com/vitorpamplona/amethyst/desktop/auth/$accountPubKeyHex",
        )

    override suspend fun getScope(relayUrl: NormalizedRelayUrl): AuthApprovalScope? {
        val raw = node.get(relayUrl.url, null) ?: return null
        return runCatching { AuthApprovalScope.valueOf(raw) }.getOrNull()
    }

    override suspend fun setScope(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    ) {
        if (scope == AuthApprovalScope.ONCE) {
            // ONCE is the session-only contract from AuthApprovalStore — must
            // not touch the persistent store, otherwise it would silently
            // upgrade to "until next clear()".
            return
        }
        node.put(relayUrl.url, scope.name)
        node.flush()
    }

    override suspend fun clear() {
        node.removeNode()
        node.flush()
    }
}
